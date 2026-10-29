package com.capri.pet

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import org.json.JSONObject

data class Recordatorio(val texto: String, val min: Int)

// Detecta 'recuérdame <algo> en <n> minutos' sin gastar la IA ni llamadas de red.
fun parsearRecordatorio(msg: String): Recordatorio? {
    val m = Regex("""(?:recu[eé]rdame|recordar)\s+(.+?)\s+en\s+(\d{1,3})\s*(?:min|minutos?)\b""", RegexOption.IGNORE_CASE)
        .find(msg.trim()) ?: return null
    val min = m.groupValues[2].toInt()
    if (min == 0) return null
    return Recordatorio(m.groupValues[1].trim(), min)
}

class MainActivity : AppCompatActivity() {
    private lateinit var view: CapriView
    private lateinit var engine: PetEngine
    private var userLeaving = false
    private val fallos = listOf("No puedo pensar ahora...", "Mmm... mi cabeza está en blanco", "Dame un segundo y vuelve a intentarlo")
    private val historial = mutableListOf<JSONObject>()
    private val reloj = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        view = CapriView(this)
        setContentView(view)

        engine = PetEngine()
        engine.animo = Memoria.animo(this)
        view.setup(engine)
        view.loadAtlas()

        historial.addAll(Memoria.cargar(this))
        view.onReady = {
            if (historial.isNotEmpty()) engine.triggerBubble("¡De vuelta! ¿Seguimos donde lo dejamos?")
        }

        view.onRequestInput = { x, y -> 
            showInputPopup(x, y) 
        }

        if (!SecurityHelper.hasApiKey(this)) {
            askApiKey()
        }
    }

    private fun askApiKey() {
        val input = EditText(this).apply {
            hint = "API Key (OpenRouter)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(android.graphics.Color.BLACK)
        }
        AlertDialog.Builder(this)
            .setTitle("Configurar API Key")
            .setMessage("Pega tu clave de OpenRouter para que Capri pueda pensar.")
            .setView(input).setCancelable(true)
            .setNegativeButton("Ahora no", null)
            .setPositiveButton("Guardar") { _, _ ->
                val key = input.text.toString().trim()
                if (key.isNotEmpty()) {
                    SecurityHelper.saveApiKey(this, key)
                }
            }.show()
    }

    private fun showInputPopup(x: Int, y: Int) {
        if (!SecurityHelper.hasApiKey(this)) { askApiKey(); return }
        val popup = CapriInputPopup(this, view, x, y,
            onSend = { text ->
                val cita = parsearRecordatorio(text)
                if (cita != null) { programar(cita); return@CapriInputPopup }
                engine.isThinking = true
                engine.triggerBubble("Pensando...")
                
                AiService.ask(this, text, historial.toList(), engine.animo,
                    onSuccess = { respuesta ->
                        engine.isThinking = false
                        engine.triggerBubble(respuesta.take(120))
                        // Solo guardamos intercambios que sí salieron bien
                        Memoria.agregar(this, historial, "user", text)
                        Memoria.agregar(this, historial, "assistant", respuesta)
                        Memoria.animo(this, engine.animo)
                    },
                    onError   = { code ->
                        engine.isThinking = false
                        // 401/402/403 = clave inválida, sin saldo o sin permiso: volver a pedirla
                        if (code == 401 || code == 402 || code == 403) askApiKey()
                        else engine.triggerBubble(fallos.random())
                    })
            },
            onOlvidar = {
                Memoria.olvidar(this)
                historial.clear()
                engine.triggerBubble("Memoria borrada. ¿Y tú quién eres?")
            })
        view.popup = popup
        popup.show()
    }

    private fun programar(r: Recordatorio) {
        engine.triggerBubble("Listo: te aviso en ${r.min} min")
        reloj.postDelayed({
            engine.triggerBubble("⏰ ${r.texto}")
        }, r.min * 60_000L)
    }

    override fun onUserLeaveHint() { super.onUserLeaveHint(); userLeaving = true }

    override fun onResume() { 
        super.onResume(); 
        view.startLoop() 
        view.invalidate()
    }

    override fun onPause() {
        super.onPause()
        view.popup?.dismiss()
        view.stopLoop()
        if (userLeaving && !isChangingConfigurations) finishAndRemoveTask()
        userLeaving = false
    }
}
