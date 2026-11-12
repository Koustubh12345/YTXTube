package com.tensei.ytxtube

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local backup and restore of app settings.
 *
 * A backup is one JSON file holding every settings file (SharedPreferences) the app owns, with
 * typed values so booleans, numbers and sets come back as the same type. Nothing leaves the
 * device: files are written to and read from a location the user picks.
 *
 * Safety rules:
 *  - Files whose names suggest credentials (account, token, cookie, session, secure...) are never
 *    exported and are ignored on import, even if a hand-edited file contains them.
 *  - Only the app's own settings files (names without a dot) are touched, so library-internal
 *    preference files are left alone.
 *  - Restored values are written to disk immediately, but screens read many settings once at
 *    start-up, so the app should be restarted after a restore ([ImportResult.needsRestart]).
 */
object Backup {
    const val FORMAT = 1

    /** What to do when a setting exists both on this device and in the backup. */
    enum class ConflictMode(val label: String) {
        /** The backup wins. */
        Replace("Replace with backup"),
        /** Keep what is on this device and only add settings that are missing. */
        KeepExisting("Keep current, add missing"),
    }

    data class ImportResult(
        val applied: Int,
        val skipped: Int,
        val ignoredFiles: List<String>,
    ) {
        val needsRestart: Boolean get() = applied > 0
    }

    private val sensitive = listOf(
        "secure", "account", "auth", "token", "cookie", "session", "credential", "login", "password",
    )

    fun isExportable(fileName: String): Boolean =
        '.' !in fileName && sensitive.none { fileName.contains(it, ignoreCase = true) }

    /** The whole backup as JSON text. */
    fun export(context: Context): String {
        val dir = File(context.applicationInfo.dataDir, "shared_prefs")
        val names = dir.listFiles { f -> f.name.endsWith(".xml") }
            ?.map { it.name.removeSuffix(".xml") }
            ?.filter(::isExportable)
            .orEmpty()
        val data = names.associateWith { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all
        }
        return BackupCodec.encode(data, System.currentTimeMillis(), BuildConfig.VERSION_NAME)
    }

    fun import(context: Context, json: String, mode: ConflictMode): ImportResult {
        val data = BackupCodec.decode(json)
        var applied = 0
        var skipped = 0
        val ignored = mutableListOf<String>()
        for ((name, values) in data) {
            if (!isExportable(name)) {
                ignored += name
                continue
            }
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val existing = prefs.all
            val plan = BackupCodec.mergePlan(existing, values, mode)
            skipped += plan.skipped
            if (plan.writes.isEmpty() && mode != ConflictMode.Replace) continue
            val removed = if (mode == ConflictMode.Replace) (existing.keys - plan.writes.keys).size else 0
            val edit = prefs.edit()
            // Replace means a true snapshot restore: remove stale keys that are absent
            // from the imported preference file before writing the backup values.
            if (mode == ConflictMode.Replace) edit.clear()
            for ((key, v) in plan.writes) {
                when (v) {
                    is Boolean -> edit.putBoolean(key, v)
                    is Int -> edit.putInt(key, v)
                    is Long -> edit.putLong(key, v)
                    is Float -> edit.putFloat(key, v)
                    is String -> edit.putString(key, v)
                    is Set<*> -> edit.putStringSet(key, v.filterIsInstance<String>().toSet())
                }
            }
            edit.apply()
            applied += plan.writes.size + removed
        }
        return ImportResult(applied, skipped, ignored)
    }

    /** Writes a backup to a file the user picked (Storage Access Framework uri). */
    fun exportTo(context: Context, uri: Uri) {
        val text = export(context)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            ?: error("Could not open the backup file for writing")
    }

    /** Reads and restores a backup from a file the user picked. */
    fun importFrom(context: Context, uri: Uri, mode: ConflictMode): ImportResult {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Could not open the backup file")
        return import(context, text, mode)
    }
}

/** Pure encoding and merge rules, separate from Android so they can be unit tested. */
internal object BackupCodec {
    data class MergePlan(val writes: Map<String, Any?>, val skipped: Int)

    fun encode(prefs: Map<String, Map<String, *>>, exportedAt: Long, appVersion: String): String {
        val files = JSONObject()
        for ((name, values) in prefs.toSortedMap()) {
            val obj = JSONObject()
            for ((key, v) in values.toSortedMap()) typed(v)?.let { obj.put(key, it) }
            files.put(name, obj)
        }
        return JSONObject()
            .put("format", Backup.FORMAT)
            .put("appVersion", appVersion)
            .put("exportedAt", exportedAt)
            .put("prefs", files)
            .toString(2)
    }

    private fun typed(v: Any?): JSONObject? = when (v) {
        is Boolean -> JSONObject().put("t", "b").put("v", v)
        is Int -> JSONObject().put("t", "i").put("v", v)
        is Long -> JSONObject().put("t", "l").put("v", v)
        is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
        is String -> JSONObject().put("t", "s").put("v", v)
        is Set<*> -> JSONObject().put("t", "ss")
            .put("v", JSONArray(v.filterIsInstance<String>().sorted()))
        else -> null
    }

    /** Throws IllegalArgumentException (or JSONException) when the text is not a usable backup. */
    fun decode(json: String): Map<String, Map<String, Any?>> {
        val root = JSONObject(json)
        require(root.optInt("format", -1) in 1..Backup.FORMAT) { "Unsupported backup format" }
        val files = root.getJSONObject("prefs")
        val out = LinkedHashMap<String, Map<String, Any?>>()
        for (name in files.keys()) {
            val obj = files.getJSONObject(name)
            val values = LinkedHashMap<String, Any?>()
            for (key in obj.keys()) {
                val cell = obj.optJSONObject(key) ?: continue
                val value: Any? = when (cell.optString("t")) {
                    "b" -> cell.getBoolean("v")
                    "i" -> cell.getInt("v")
                    "l" -> cell.getLong("v")
                    "f" -> cell.getDouble("v").toFloat()
                    "s" -> cell.getString("v")
                    "ss" -> {
                        val arr = cell.getJSONArray("v")
                        (0 until arr.length()).map { arr.getString(it) }.toSet()
                    }
                    else -> continue
                }
                values[key] = value
            }
            out[name] = values
        }
        return out
    }

    fun mergePlan(existing: Map<String, *>, incoming: Map<String, Any?>, mode: Backup.ConflictMode): MergePlan {
        if (mode == Backup.ConflictMode.Replace) return MergePlan(incoming, 0)
        val writes = LinkedHashMap<String, Any?>()
        var skipped = 0
        for ((key, v) in incoming) {
            if (existing.containsKey(key)) skipped++ else writes[key] = v
        }
        return MergePlan(writes, skipped)
    }
}
