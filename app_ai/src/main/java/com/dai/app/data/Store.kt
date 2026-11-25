package com.dai.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

const val PRESET_CHAT = "You are Dai, a helpful and concise AI assistant."
const val PRESET_CODE = "You are Dai, an expert software engineer. Reply with complete, working code in fenced code blocks with a language tag, then a short explanation and any edge cases."
val MODELS = listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.5-flash-lite")

class Msg(val role: String, content: String) {
    var content by mutableStateOf(content)
}

class Chat(val id: String, title: String) {
    var title by mutableStateOf(title)
    val messages = mutableStateListOf<Msg>()
}

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("dai", Context.MODE_PRIVATE)

    var apiKey: String
        get() = p.getString("api_key", "") ?: ""
        set(v) = p.edit().putString("api_key", v).apply()
    var model: String
        get() = p.getString("model", null)?.takeIf { it.isNotBlank() } ?: MODELS[0]
        set(v) = p.edit().putString("model", v).apply()
    var system: String
        get() = p.getString("system", PRESET_CHAT) ?: PRESET_CHAT
        set(v) = p.edit().putString("system", v).apply()
    var temperature: Float
        get() = p.getFloat("temperature", 1f)
        set(v) = p.edit().putFloat("temperature", v).apply()
    var seenVersion: String
        get() = p.getString("seen_version", "") ?: ""
        set(v) = p.edit().putString("seen_version", v).apply()

    fun loadChats(): List<Chat> = try {
        val a = JSONArray(p.getString("chats", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val m = o.getJSONArray("messages")
            Chat(o.getString("id"), o.getString("title")).also { c ->
                for (j in 0 until m.length()) {
                    val x = m.getJSONObject(j)
                    c.messages.add(Msg(x.getString("role"), x.getString("content")))
                }
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveChats(chats: List<Chat>) {
        val a = JSONArray()
        chats.forEach { c ->
            val m = JSONArray()
            c.messages.forEach { m.put(JSONObject().put("role", it.role).put("content", it.content)) }
            a.put(JSONObject().put("id", c.id).put("title", c.title).put("messages", m))
        }
        p.edit().putString("chats", a.toString()).apply()
    }
}
