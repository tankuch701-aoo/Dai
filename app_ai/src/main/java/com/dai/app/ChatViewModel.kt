package com.dai.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dai.app.data.*
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    val chats = mutableStateListOf<Chat>().apply { addAll(store.loadChats()) }
    var currentId by mutableStateOf(chats.firstOrNull()?.id)
    var apiKey by mutableStateOf(store.apiKey)
    var model by mutableStateOf(store.model)
    var system by mutableStateOf(store.system)
    var temperature by mutableStateOf(store.temperature)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var draft by mutableStateOf<String?>(null)
    private var stopped = false

    val current: Chat? get() = chats.firstOrNull { it.id == currentId }
    val hasKey: Boolean get() = apiKey.isNotBlank() || BuildConfig.GEMINI_API_KEY.isNotBlank()

    fun showWhatsNew() = store.seenVersion != BuildConfig.VERSION_NAME
    fun markSeen() { store.seenVersion = BuildConfig.VERSION_NAME }

    fun newChat(): Chat {
        stop()
        val c = Chat(UUID.randomUUID().toString(), "New chat")
        chats.add(0, c)
        currentId = c.id
        store.saveChats(chats)
        return c
    }

    fun select(id: String) { stop(); currentId = id }

    fun delete(id: String) {
        stop()
        chats.removeAll { it.id == id }
        if (currentId == id) currentId = chats.firstOrNull()?.id
        store.saveChats(chats)
    }

    fun setModel(m: String) { model = m; store.model = m }
    fun saveKey(k: String) { apiKey = k.trim(); store.apiKey = apiKey }

    fun saveSettings(key: String, m: String, sys: String, temp: Float) {
        saveKey(key)
        model = m.trim().ifEmpty { MODELS[0] }; store.model = model
        system = sys.trim(); store.system = system
        temperature = temp; store.temperature = temp
    }

    fun transcript(): String = current?.messages?.joinToString("\n\n") {
        (if (it.role == "user") "You: " else "Dai: ") + it.content
    } ?: ""

    fun stop() { if (busy) { stopped = true; GeminiApi.cancel() } }

    fun send(text: String) {
        val t = text.trim()
        if (busy || t.isEmpty()) return
        if (!hasKey) {
            error = getApplication<Application>().getString(R.string.error_no_key)
            draft = t
            return
        }
        val chat = current ?: newChat()
        chat.messages.add(Msg("user", t))
        if (chat.title == "New chat") chat.title = t.replace(Regex("\\s+"), " ").take(40)
        generate(chat, t)
    }

    fun regenerate() {
        val chat = current ?: return
        if (busy || chat.messages.lastOrNull()?.role != "assistant") return
        chat.messages.removeAt(chat.messages.lastIndex)
        generate(chat, null)
    }

    private fun generate(chat: Chat, rollback: String?) {
        val key = apiKey.ifBlank { BuildConfig.GEMINI_API_KEY }
        val history = chat.messages.toList()
        val reply = Msg("assistant", "")
        chat.messages.add(reply)
        busy = true; stopped = false; error = null
        viewModelScope.launch {
            var failure: String? = null
            try {
                GeminiApi.stream(key, model, system, temperature, history) { reply.content += it }
            } catch (e: Exception) {
                if (!stopped) failure = e.message ?: "Request failed"
            }
            busy = false
            if (reply.content.isEmpty()) {
                chat.messages.remove(reply)
                if (rollback != null && chat.messages.isNotEmpty()) {
                    chat.messages.removeAt(chat.messages.lastIndex)
                    draft = rollback
                }
            }
            error = failure
            store.saveChats(chats)
        }
    }

    override fun onCleared() { GeminiApi.cancel() }
}
