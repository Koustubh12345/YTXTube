package com.tensei.ytxtube

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Whole-look presets and your own saved ones. A profile is a snapshot of the YTX Tweaks, the ambient
 * light settings and the theme mode and colour (keys start with "t:" and "a:"). Applying one only
 * changes what it contains. The same snapshot can be copied out and pasted back, which doubles as
 * a settings backup.
 */
object Profiles {
    private fun profile(vararg pairs: Pair<String, Any>) = JSONObject().also { o -> pairs.forEach { (k, v) -> o.put(k, v) } }

    val builtIn: List<Pair<String, JSONObject>> = listOf(
        "Minimal" to profile(
            "t:hide_views" to true, "t:hide_duration" to true, "t:hide_progress" to true, "t:thumb_width_dp" to 110,
            "t:title_lines" to 1, "t:hide_download" to true, "t:extra_copy_buttons" to false, "a:ambient_light" to false,
        ),
        "Liquid Glass" to profile(
            "t:hide_views" to false, "t:hide_duration" to false, "t:hide_progress" to false, "t:thumb_width_dp" to 170,
            "t:title_lines" to 2, "a:ambient_light" to true, "a:ambient_intensity" to 0.9, "a:ambient_reach" to 1.2,
            "a:ambient_status_bar" to true, "a:ambient_fill_bands" to true,
        ),
        "AMOLED" to profile(
            "a:mode" to "amoled", "a:ambient_light" to false, "t:thumb_width_dp" to 140, "t:title_lines" to 2,
        ),
        "Cinematic" to profile(
            "a:mode" to "dark", "a:ambient_light" to true, "a:ambient_intensity" to 1.0, "a:ambient_reach" to 1.5,
            "a:ambient_saturation" to 1.8, "a:ambient_fill_bands" to true, "a:ambient_fill_dim" to 0.8,
            "t:thumb_width_dp" to 200, "t:fullscreen_clock" to true, "t:hide_views" to true,
        ),
        "Music" to profile(
            "t:start_tab" to 1, "t:hide_shorts" to true, "t:thumb_width_dp" to 110, "t:title_lines" to 1,
            "a:ambient_light" to false,
        ),
    )

    fun snapshot(context: Context): JSONObject {
        val out = JSONObject()
        context.getSharedPreferences("tweaks", Context.MODE_PRIVATE).all.forEach { (k, v) -> if (v != null) out.put("t:$k", v) }
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).all
            .filterKeys { it == "mode" || it == "seed" || it.startsWith("ambient") }
            .forEach { (k, v) -> if (v != null) out.put("a:$k", v) }
        return out
    }

    /**
     * Applies a look profile. Only known keys are written, and each value is converted to the type
     * that setting is stored as ([ProfileRules]). Unknown keys and values that cannot be converted
     * are skipped, so a hand-edited or pasted profile can never leave a setting with the wrong
     * type (which would crash the app on the next start). Returns how many settings were written.
     */
    fun apply(context: Context, json: JSONObject): Int {
        val tweaks = context.getSharedPreferences("tweaks", Context.MODE_PRIVATE).edit()
        val look = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
        var count = 0
        json.keys().forEach { key ->
            val editor = when {
                key.startsWith("t:") -> tweaks
                key.startsWith("a:") -> look
                else -> return@forEach
            }
            val name = key.substring(2)
            val typed = ProfileRules.typedValue(key, json.opt(key)) ?: return@forEach
            when (typed) {
                is Boolean -> editor.putBoolean(name, typed)
                is Int -> editor.putInt(name, typed)
                is Float -> editor.putFloat(name, typed)
                is String -> editor.putString(name, typed)
                else -> return@forEach
            }
            count++
        }
        tweaks.apply()
        look.apply()
        Tweaks.init(context)
        AmbientSettings.init(context)
        ThemeSettings.init(context)
        return count
    }

    private fun store(context: Context) = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)

    fun custom(context: Context): List<Pair<String, JSONObject>> = runCatching {
        val arr = JSONArray(store(context).getString("list", "[]"))
        (0 until arr.length()).map { arr.getJSONObject(it).let { o -> o.getString("name") to o.getJSONObject("data") } }
    }.getOrDefault(emptyList())

    fun saveCurrent(context: Context, name: String) {
        val list = custom(context).filterNot { it.first.equals(name, ignoreCase = true) } + (name to snapshot(context))
        write(context, list)
    }

    fun delete(context: Context, name: String) = write(context, custom(context).filterNot { it.first == name })

    private fun write(context: Context, list: List<Pair<String, JSONObject>>) {
        val arr = JSONArray()
        list.take(20).forEach { (n, d) -> arr.put(JSONObject().put("name", n).put("data", d)) }
        store(context).edit().putString("list", arr.toString()).apply()
    }

    fun isHexColour(text: String) = Regex("^#?[0-9a-fA-F]{6}$").matches(text.trim())
}

/**
 * Which settings a look profile may change and what type each is stored as. Pure Kotlin so it can
 * be unit tested. Keys are "t:<tweak key>" or "a:<appearance key>".
 */
internal object ProfileRules {
    private val appearanceTypes: Map<String, Char> = mapOf(
        "mode" to 's', "seed" to 's',
        "ambient_light" to 'b', "ambient_fill_bands" to 'b', "ambient_status_bar" to 'b',
        "ambient_intensity" to 'f', "ambient_reach" to 'f', "ambient_saturation" to 'f', "ambient_fill_dim" to 'f',
        "ambient_blur" to 'i', "ambient_interval" to 'i', "ambient_fade" to 'i',
    )

    /** The storage type for a profile key, or null when the key is not one a profile may set. */
    fun typeOf(key: String, tweakTypes: Map<String, Char> = Tweaks.keyTypes): Char? = when {
        key.startsWith("t:") -> tweakTypes[key.substring(2)]
        key.startsWith("a:") -> appearanceTypes[key.substring(2)]
        else -> null
    }

    /** [value] converted to the setting's type (Boolean, Int, Float or String), or null to skip it. */
    fun typedValue(key: String, value: Any?, tweakTypes: Map<String, Char> = Tweaks.keyTypes): Any? {
        val type = typeOf(key, tweakTypes) ?: return null
        return when (type) {
            'b' -> value as? Boolean
            's' -> (value as? String)?.takeIf { it.length <= 64 }
            'i' -> (value as? Number)?.toDouble()
                ?.takeIf { it.isFinite() && it == Math.rint(it) && Math.abs(it) <= Int.MAX_VALUE }
                ?.toInt()
            'f' -> (value as? Number)?.toFloat()?.takeIf { it.isFinite() }
            else -> null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesSheet(onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }
    var customs by remember { mutableStateOf(Profiles.custom(context)) }
    var newName by remember { mutableStateOf("") }
    var hex by remember { mutableStateOf(ThemeSettings.seed.orEmpty()) }
    var conflictMode by remember { mutableStateOf(Backup.ConflictMode.Replace) }
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }
    var confirmImport by remember { mutableStateOf(false) }

    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runCatching { Backup.exportTo(context, uri) }
                .onSuccess { message = "Full settings backup exported successfully" }
                .onFailure { message = "Export failed: ${it.message ?: "unknown error"}" }
        }
    }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingImport = uri
            confirmImport = true
        }
    }

    FloatingSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(top = 18.dp, bottom = 22.dp)) {
            Text("Profiles and backup", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
            Text(
                "Build a setup you love, save it as a profile, or move your settings safely between installs.",
                color = c.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
            )
            if (message.isNotBlank()) {
                Text(message, color = c.primary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }

            ProfileHeader("One-tap looks")
            Profiles.builtIn.forEach { (name, data) ->
                ProfileRow(name, "Apply") { message = "Applied $name (${Profiles.apply(context, data)} settings)" }
            }

            ProfileHeader("Your profiles")
            OutlinedTextField(
                value = newName, onValueChange = { newName = it.take(24) }, singleLine = true,
                label = { Text("Name this setup") }, modifier = Modifier.fillMaxWidth(),
            )
            ProfileRow("Save current setup", "Save") {
                if (newName.isBlank()) message = "Give your profile a name first" else {
                    Profiles.saveCurrent(context, newName.trim())
                    customs = Profiles.custom(context)
                    message = "Saved profile: ${newName.trim()}"
                    newName = ""
                }
            }
            if (customs.isEmpty()) {
                Text("Your saved profiles will appear here.", color = c.onSurfaceVariant, fontSize = 12.sp)
            }
            customs.forEach { (name, data) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Pill("Apply") { message = "Applied $name (${Profiles.apply(context, data)} settings)" }
                    Spacer(Modifier.width(8.dp))
                    Pill("Delete") { Profiles.delete(context, name); customs = Profiles.custom(context); message = "Deleted $name" }
                }
            }

            ProfileHeader("Accent colour")
            OutlinedTextField(
                value = hex, onValueChange = { hex = it.take(7) }, singleLine = true,
                label = { Text("Hex colour, like #7B4DFF") }, modifier = Modifier.fillMaxWidth(),
            )
            ProfileRow("Apply accent colour", "Apply") {
                if (Profiles.isHexColour(hex)) {
                    ThemeSettings.choose("#" + hex.trim().removePrefix("#").uppercase())
                    message = "Accent colour applied"
                } else message = "Enter 6 hex digits, like #7B4DFF"
            }

            ProfileHeader("Full device backup")
            Text(
                "Exports app settings to a JSON file you choose. Login details, cookies and secure credentials are excluded.",
                color = c.onSurfaceVariant, fontSize = 12.sp,
            )
            ProfileRow("Export all safe settings", "Export") {
                exportLauncher.launch("YTXTube-settings-backup.json")
            }
            ProfileRow("Restore from backup file", "Import") {
                importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
            }

            ProfileHeader("Restore conflict handling")
            Text(
                "Choose what happens when a backup contains a setting that already exists on this device.",
                color = c.onSurfaceVariant, fontSize = 12.sp,
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                Backup.ConflictMode.values().forEach { mode ->
                    androidx.compose.material3.FilterChip(
                        selected = conflictMode == mode,
                        onClick = { conflictMode = mode },
                        label = { Text(mode.label) },
                    )
                }
            }
            Text(
                if (conflictMode == Backup.ConflictMode.Replace)
                    "Backup values replace matching settings. Settings absent from the backup are removed from included preference files."
                else "Your current values stay untouched. Only missing settings are added.",
                color = c.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
            )

            ProfileHeader("Quick profile transfer")
            ProfileRow("Copy current look", "Copy") {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("YTXTube profile", Profiles.snapshot(context).toString()))
                message = "Look profile copied to clipboard"
            }
            ProfileRow("Paste look profile", "Paste") {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                val n = json?.let { Profiles.apply(context, it) } ?: 0
                message = if (n > 0) "Restored $n look settings" else "Clipboard does not contain a valid YTX look profile"
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Look profiles contain YTX Tweaks and appearance settings. Full backups include all safe app preference files and are better for moving to a new install.",
                color = c.onSurfaceVariant, fontSize = 12.sp,
            )
        }
    }

    if (confirmImport) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmImport = false; pendingImport = null },
            title = { Text("Restore settings?") },
            text = {
                Text(
                    if (conflictMode == Backup.ConflictMode.Replace)
                        "This replaces settings in the backup's included preference files. Consider exporting your current settings first."
                    else "This keeps existing values and adds only settings missing from this device.",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val uri = pendingImport
                    confirmImport = false
                    pendingImport = null
                    if (uri != null) {
                        runCatching { Backup.importFrom(context, uri, conflictMode) }
                            .onSuccess { result ->
                                message = "Restored ${result.applied} settings; skipped ${result.skipped}. Restart the app to apply everything."
                            }
                            .onFailure { message = "Import failed: ${it.message ?: "invalid backup file"}" }
                    }
                }) { Text("Restore") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmImport = false; pendingImport = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ProfileHeader(text: String) {
    Text(
        text.uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp,
        letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
    )
}

@Composable
private fun ProfileRow(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Pill(action, onClick)
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Text(
        text, color = c.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(c.primaryContainer)
            .pressable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
