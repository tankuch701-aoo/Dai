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

object GeminiApi {
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.SECONDS).build()

    @Volatile
    private var current: Call? = null

    fun cancel() {
        current?.cancel()
    }

    /** Streams a Gemini reply. Calls onDelta for every piece of text. Throws IOException on failure. */
    suspend fun stream(
        key: String, model: String, system: String, temperature: Float,
        msgs: List<Msg>, onDelta: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val merged = mutableListOf<Pair<String, String>>()
        for (m in msgs) {
            val role = if (m.role == "user") "user" else "model"
            val last = merged.lastOrNull()
            if (last != null && last.first == role) {
                merged[merged.lastIndex] = role to (last.second + "\n\n" + m.content)
            } else {
                merged.add(role to m.content)
            }
        }
        val contents = JSONArray()
        for ((role, text) in merged) {
            contents.put(JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", text))))
        }
        val body = JSONObject().put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", temperature.toDouble()))
        if (system.isNotBlank()) {
            body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        }
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val call = client.newCall(req)
        current = call
        try {
            call.execute().use { res ->
                if (!res.isSuccessful) throw IOException("${res.code}: ${errorText(res.body?.string().orEmpty())}")
                val src = res.body!!.source()
                while (!src.exhausted()) {
                    val line = src.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val d = line.removePrefix("data:").trim()
                    if (d.isEmpty()) continue
                    val j = try { JSONObject(d) } catch (e: Exception) { continue }
                    j.optJSONObject("error")?.let { throw IOException(it.optString("message", "Stream error")) }
                    val block = j.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                    if (block.isNotEmpty()) throw IOException("Blocked by Gemini: $block")
                    val parts = j.optJSONArray("candidates")?.optJSONObject(0)
                        ?.optJSONObject("content")?.optJSONArray("parts") ?: continue
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        if (!part.isNull("text")) onDelta(part.optString("text"))
                    }
                }
            }
        } finally {
            current = null
        }
    }

    private fun errorText(t: String): String {
        val msg = try {
            JSONObject(t).optJSONObject("error")?.optString("message") ?: t
        } catch (e: Exception) {
            t
        }
        return msg.take(300)
    }
}
