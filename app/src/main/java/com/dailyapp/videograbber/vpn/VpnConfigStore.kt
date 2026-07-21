package com.dailyapp.videograbber.vpn

import android.content.Context

/** Stores the user's own WireGuard config text locally so it doesn't need to be re-pasted. */
class VpnConfigStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("vpn_store", Context.MODE_PRIVATE)

    fun load(): String = prefs.getString(KEY_CONFIG, "") ?: ""

    fun save(config: String) {
        prefs.edit().putString(KEY_CONFIG, config).apply()
    }

    companion object {
        private const val KEY_CONFIG = "config_text"
    }
}
