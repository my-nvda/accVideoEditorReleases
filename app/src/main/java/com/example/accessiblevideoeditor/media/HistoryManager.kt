package com.example.accessiblevideoeditor.media

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class HistoryItem(
    val uriString: String,
    val name: String,
    val timestamp: Long,
    val type: String // "video" or "image"
)

object HistoryManager {
    private const val FILE_NAME = "history.json"

    fun saveToHistory(context: Context, item: HistoryItem) {
        val file = File(context.filesDir, FILE_NAME)
        val history = loadHistory(context).toMutableList()
        
        // Add new item at the top
        history.add(0, item)
        
        // Keep only top 50 items
        if (history.size > 50) {
            history.removeAt(history.size - 1)
        }
        
        val jsonArray = JSONArray()
        for (h in history) {
            val obj = JSONObject()
            obj.put("uriString", h.uriString)
            obj.put("name", h.name)
            obj.put("timestamp", h.timestamp)
            obj.put("type", h.type)
            jsonArray.put(obj)
        }
        
        file.writeText(jsonArray.toString())
    }

    fun saveFullHistory(context: Context, history: List<HistoryItem>) {
        val file = File(context.filesDir, FILE_NAME)
        val jsonArray = JSONArray()
        for (h in history) {
            val obj = JSONObject()
            obj.put("uriString", h.uriString)
            obj.put("name", h.name)
            obj.put("timestamp", h.timestamp)
            obj.put("type", h.type)
            jsonArray.put(obj)
        }
        file.writeText(jsonArray.toString())
    }

    fun loadHistory(context: Context): List<HistoryItem> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        
        val list = mutableListOf<HistoryItem>()
        try {
            val jsonString = file.readText()
            if (jsonString.isBlank()) return list
            
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    HistoryItem(
                        uriString = obj.getString("uriString"),
                        name = obj.getString("name"),
                        timestamp = obj.getLong("timestamp"),
                        type = obj.getString("type")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
