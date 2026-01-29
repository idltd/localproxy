package com.localproxy.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class AddressHistoryRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun getHistory(): List<String> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { array.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addAddress(address: String) {
        val trimmed = address.trim()
        if (trimmed.isEmpty()) return

        val current = getHistory().toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)

        val limited = current.take(MAX_HISTORY_SIZE)

        val array = JSONArray()
        limited.forEach { array.put(it) }

        prefs.edit()
            .putString(KEY_HISTORY, array.toString())
            .apply()
    }

    fun removeAddress(address: String) {
        val current = getHistory().toMutableList()
        current.remove(address)

        val array = JSONArray()
        current.forEach { array.put(it) }

        prefs.edit()
            .putString(KEY_HISTORY, array.toString())
            .apply()
    }

    fun clearHistory() {
        prefs.edit()
            .remove(KEY_HISTORY)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "local_proxy_prefs"
        private const val KEY_HISTORY = "address_history"
        private const val MAX_HISTORY_SIZE = 10
    }
}
