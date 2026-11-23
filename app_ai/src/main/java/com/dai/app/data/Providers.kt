package com.dai.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class Provider(val id: String, val label: String, val defaultModel: String) {
    GEMINI("gemini", "Gemini", "gemini-2.5-flash"),
    OPENAI("openai", "ChatGPT", "gpt-4o-mini"),
    CLAUDE("claude", "Claude", "claude-sonnet-5-5");

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: GEMINI
    }
}

object Api {
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.SECONDS).build()
    private val JSON = "application/json".toMediaType()

    @Volatile private var current: Call? = null
    fun cancel() { current?.cancel() }

    /** Streams a reply and calls onDelta for each chunk. Throws IOException on failure or cancel. */
    suspend fun stream(
        p: Provider, key: String, model: String, system: String, msgs: List<Msg>, onDelta: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val call = client.newCall(build(p, key, model, system, msgs))
        current = call
        call.execute().use { res ->
            if (!res.isSuccessful) throw IOException("${res.code}: ${errorText(res.body?.string().orEmpty())}")
            val src = res.body!!.source()
            while (!src.exhausted()) {
                val line = src.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val d = line.removePrefix("data:").trim()
                if (d.isEmpty() || d == "[DONE]") continue
                val j = try { JSONObject(d) } catch (e: Exception) { continue }
                j.optJSONObject("error")?.let { throw IOException(it.optString("message", "Stream error")) }
                val t = pick(p, j)
                if (t.isNotEmpty()) onDelta(t)
            }
        }
        current = null
    }

    private fun JSONObject.str(k: String) = if (isNull(k)) "" else optString(k)

    private fun pick(p: Provider, j: JSONObject): String = when (p) {
        Provider.GEMINI -> j.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")
            ?.optJSONArray("parts")?.let { a -> (0 until a.length()).joinToString("") { a.getJSONObject(it).str("text") } } ?: ""
        Provider.OPENAI -> j.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.str("content") ?: ""
        Provider.CLAUDE -> if (j.optString("type") == "content_block_delta") j.optJSONObject("delta")?.str("text") ?: "" else ""
    }

    private fun errorText(t: String): String = try {
        JSONObject(t).optJSONObject("error")?.optString("message") ?: t
    } catch (e: Exception) { t }.take(300)

    private fun build(p: Provider, key: String, model: String, system: String, msgs: List<Msg>): Request {
        val b = Request.Builder()
        val body = JSONObject()
        val list = JSONArray()
        when (p) {
            Provider.GEMINI -> {
                b.url("https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse")
                    .header("x-goog-api-key", key)
                msgs.forEach {
                    list.put(JSONObject().put("role", if (it.role == "user") "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", it.content))))
                }
                body.put("contents", list)
                if (system.isNotBlank()) body.put("systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            }
            Provider.OPENAI -> {
                b.url("https://api.openai.com/v1/chat/completions").header("Authorization", "Bearer $key")
                if (system.isNotBlank()) list.put(JSONObject().put("role", "system").put("content", system))
                msgs.forEach { list.put(JSONObject().put("role", it.role).put("content", it.content)) }
                body.put("model", model).put("stream", true).put("messages", list)
            }
            Provider.CLAUDE -> {
                b.url("https://api.anthropic.com/v1/messages").header("x-api-key", key)
                    .header("anthropic-version", "2023-06-01")
                msgs.forEach { list.put(JSONObject().put("role", it.role).put("content", it.content)) }
                body.put("model", model).put("max_tokens", 4096).put("stream", true).put("messages", list)
                if (system.isNotBlank()) body.put("system", system)
            }
        }
        return b.post(body.toString().toRequestBody(JSON)).build()
    }
}
