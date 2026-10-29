package com.capri.pet

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object AiService {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    
    private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
    private const val MODEL = "openrouter/free"

    fun ask(ctx: Context, prompt: String, historial: List<JSONObject>, animo: Int,
            onSuccess: (String) -> Unit, onError: (Int) -> Unit) {
        val key = SecurityHelper.getApiKey(ctx) ?: return onError(401)
        
        executor.execute {
            try {
                val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 30_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $key")
                    setRequestProperty("HTTP-Referer", "https://github.com/Antonio9000-ops/Capri")
                    setRequestProperty("X-Title", "Capri Android")
                }
                
                val ahora = java.text.SimpleDateFormat("EEEE d 'de' MMMM, HH:mm", java.util.Locale("es")).format(java.util.Date())
                val humor = when {
                    animo >= 2 -> "Hoy estás de muy buen humor."
                    animo == 1 -> "Estás de buen humor."
                    animo <= -2 -> "Estás de mal humor."
                    animo == -1 -> "Andas algo mosqueada."
                    else -> "Estás neutral."
                }
                val persona = "Capri, una mascota cabezota que hace de asistente personal. " +
                    "Siempre tienes opinión, eres pícara y de vez en cuando burlona, nunca grosera. " +
                    "Respondes en UNA frase de máx. 120 caracteres, sin listas, sin markdown, sin emojis largos. " +
                    "Recuerdas conversaciones anteriores: no preguntes de nuevo lo que ya te dijeron. " +
                    "Ahora es $ahora. $humor"
                
                val body = JSONObject().apply {
                    put("model", MODEL)
                    put("max_tokens", 250) // Aumentado para modelos de razonamiento
                    put("temperature", 0.7)
                    put("messages", JSONArray().apply {
                        put(JSONObject().put("role", "system").put("content", persona))
                        // Últimas 8 idas y vueltas de la memoria
                        for (m in historial.subList(maxOf(0, historial.size - 8), historial.size)) put(m)
                        put(JSONObject().put("role", "user").put("content", prompt))
                    })
                }.toString()
                
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                
                if (code == 200) {
                    val txt = conn.inputStream.bufferedReader().use { it.readText() }
                    val message = JSONObject(txt).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                    
                    // Leemos 'content'. Si viene nulo o vacío, intentamos leer 'reasoning'
                    var content = message.optString("content", "").trim()
                    if (content.isEmpty() || content == "null" || content == "NULL") {
                        content = message.optString("reasoning", "").trim()
                    }
                    
                    // Si de plano no hay nada, mandamos un mensaje por defecto
                    if (content.isEmpty() || content == "null") {
                        content = "..."
                    }
                    
                    // Limpiamos un poco el texto de razonamiento para que no sea tan largo
                    val cleanContent = content.replace("\n", " ").take(120)
                    
                    Log.d("AiService", "Respuesta final: $cleanContent")
                    main.post { onSuccess(cleanContent) }
                } else {
                    Log.e("AiService", "Error HTTP: $code")
                    main.post { onError(code) }
                }
                conn.disconnect()
            } catch (e: Exception) { 
                Log.e("AiService", "Excepción: ${e.message}")
                main.post { onError(0) } 
            }
        }
    }
}
