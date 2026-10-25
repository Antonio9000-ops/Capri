package com.capri.pet

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object AiService {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "llama-3.3-70b-versatile"

    fun ask(ctx: Context, prompt: String, onSuccess: (String) -> Unit, onError: () -> Unit) {
        val key = SecurityHelper.getApiKey(ctx) ?: return onError()
        executor.execute {
            try {
                val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 30_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $key")
                }
                val body = JSONObject().apply {
                    put("model", MODEL)
                    put("max_tokens", 80)
                    put("temperature", 0.7)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", "Eres Capri, mascota virtual. Responde breve, máx 120 caracteres.")
                        })
                        put(JSONObject().apply { put("role", "user"); put("content", prompt) })
                    })
                }.toString()
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                if (code == 200) {
                    val txt = conn.inputStream.bufferedReader().use { it.readText() }
                    val content = JSONObject(txt).getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message").getString("content").trim()
                    main.post { onSuccess(content) }
                } else main.post { onError() }  // incluye 429
                conn.disconnect()
            } catch (e: Exception) { e.printStackTrace(); main.post { onError() } }
        }
    }
}
