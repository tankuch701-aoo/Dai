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
    var provider by mutableStateOf(store.provider)
    var system by mutableStateOf(store.system)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var draft by mutableStateOf<String?>(null)
    private var stopped = false

    val current: Chat? get() = chats.firstOrNull { it.id == currentId }

    fun key(p: Provider) = store.key(p)
    fun model(p: Provider) = store.model(p)

    fun newChat(): Chat {
        stop()
        val c = Chat(UUID.randomUUID().toString(), "New chat")
        chats.add(0, c); currentId = c.id
        store.saveChats(chats)
        return c
    }

    fun select(id: String) { stop(); currentId = id }

    fun delete(id: String) {
        stop(); chats.removeAll { it.id == id }
        if (currentId == id) currentId = chats.firstOrNull()?.id
        store.saveChats(chats)
    }

    fun setProvider(p: Provider) { provider = p; store.provider = p }

    fun saveSettings(keys: Map<Provider, String>, models: Map<Provider, String>, sys: String) {
        keys.forEach { (p, v) -> store.setKey(p, v) }
        models.forEach { (p, v) -> store.setModel(p, v) }
        system = sys.trim(); store.system = system
    }

    fun stop() { if (busy) { stopped = true; Api.cancel() } }

    fun send(text: String) {
        val t = text.trim()
        if (busy || t.isEmpty()) return
        val p = provider
        val key = store.key(p)
        if (key.isBlank()) { error = getApplication<Application>().getString(R.string.add_key, p.label); draft = t; return }
        val chat = current ?: newChat()
        chat.messages.add(Msg("user", t))
        val reply = Msg("assistant", "")
        chat.messages.add(reply)
        if (chat.title == "New chat") chat.title = t.replace(Regex("\\s+"), " ").take(40)
        val history = chat.messages.toList().dropLast(1)
        busy = true; stopped = false; error = null
        viewModelScope.launch {
            var failure: String? = null
            try {
                Api.stream(p, key, store.model(p), system, history) { reply.content += it }
            } catch (e: Exception) {
                if (!stopped) failure = e.message ?: "Request failed"
            }
            busy = false
            if (reply.content.isEmpty()) {
                chat.messages.remove(reply)
                chat.messages.removeAt(chat.messages.lastIndex)
                draft = t
            }
            error = failure
            store.saveChats(chats)
        }
    }

    override fun onCleared() { Api.cancel() }
}
