package com.dailyapp.videograbber.data

import android.content.Context
import com.dailyapp.videograbber.model.DownloadItem
import com.dailyapp.videograbber.model.DownloadType
import org.json.JSONArray
import org.json.JSONObject

/** Persists the lightweight list of queued/known downloads across process restarts. */
class DownloadStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("downloads_store", Context.MODE_PRIVATE)

    fun loadAll(): List<DownloadItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            DownloadItem(
                id = obj.getString("id"),
                url = obj.getString("url"),
                title = obj.getString("title"),
                type = DownloadType.valueOf(obj.getString("type"))
            )
        }
    }

    fun saveAll(items: List<DownloadItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("url", item.url)
                    put("title", item.title)
                    put("type", item.type.name)
                }
            )
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    companion object {
        private const val KEY_ITEMS = "items"
    }
}
