package com.dai.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

const val PRESET_CHAT = "You are Dai, a helpful and concise AI assistant."
const val PRESET_CODE = "You are Dai, an expert software engineer. Reply with complete, working code in fenced code blocks with a language tag, then a short explanation and any edge cases."

class Msg(val role: String, content: String) {
    var content by mutableStateOf(content)
}

class Chat(val id: String, title: String) {
    var title by mutableStateOf(title)
    val messages = mutableStateListOf<Msg>()
}

class Store(ctx: Context) {
    private val secure = EncryptedSharedPreferences.create(
        ctx, "dai_secure",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    private val plain = ctx.getSharedPreferences("dai", Context.MODE_PRIVATE)

    fun key(p: Provider): String = secure.getString("key_${p.id}", "") ?: ""
    fun setKey(p: Provider, v: String) = secure.edit().putString("key_${p.id}", v.trim()).apply()
    fun model(p: Provider): String = plain.getString("model_${p.id}", null)?.takeIf { it.isNotBlank() } ?: p.defaultModel
    fun setModel(p: Provider, v: String) = plain.edit().putString("model_${p.id}", v.trim()).apply()

    var provider: Provider
        get() = Provider.fromId(plain.getString("provider", "gemini") ?: "gemini")
        set(v) = plain.edit().putString("provider", v.id).apply()

    var system: String
        get() = plain.getString("system", PRESET_CHAT) ?: PRESET_CHAT
        set(v) = plain.edit().putString("system", v).apply()

    fun loadChats(): List<Chat> = try {
        val a = JSONArray(plain.getString("chats", "[]"))
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
    } catch (e: Exception) { emptyList() }

    fun saveChats(chats: List<Chat>) {
        val a = JSONArray()
        chats.forEach { c ->
            val m = JSONArray()
            c.messages.forEach { m.put(JSONObject().put("role", it.role).put("content", it.content)) }
            a.put(JSONObject().put("id", c.id).put("title", c.title).put("messages", m))
        }
        plain.edit().putString("chats", a.toString()).apply()
    }
}
