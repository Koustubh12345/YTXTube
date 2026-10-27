package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue




object SearchHistory {
    private const val PREFS_NAME = "search_history_prefs"
    private const val KEY_HISTORY = "recent_queries"
    private const val MAX_HISTORY = 30

    private var prefs: SharedPreferences? = null
    var queries by mutableStateOf<List<String>>(emptyList())
        private set

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        load()
    }

    private fun load() {
        val raw = prefs?.getString(KEY_HISTORY, null) ?: return
        queries = raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }
    }

    fun add(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        val current = queries.toMutableList()
        current.removeAll { it.equals(q, ignoreCase = true) }
        current.add(0, q)
        val updated = current.take(MAX_HISTORY)
        queries = updated
        prefs?.edit()?.putString(KEY_HISTORY, updated.joinToString("\n"))?.apply()
    }

    fun remove(query: String) {
        val updated = queries.filterNot { it.equals(query, ignoreCase = true) }
        queries = updated
        prefs?.edit()?.putString(KEY_HISTORY, updated.joinToString("\n"))?.apply()
    }

    fun clear() {
        queries = emptyList()
        prefs?.edit()?.remove(KEY_HISTORY)?.apply()
    }
}
