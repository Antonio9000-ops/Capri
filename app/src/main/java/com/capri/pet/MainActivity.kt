package com.capri.pet

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class MainActivity : AppCompatActivity() {
    private lateinit var view: CapriView
    private lateinit var engine: PetEngine
    private var userLeaving = false
    private val fallos = listOf("No puedo pensar ahora...", "Mmm... mi cabeza está en blanco", "Dame un segundo y vuelve a intentarlo")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        view = CapriView(this)
        setContentView(view)

        engine = PetEngine()
        view.setup(engine)
        view.loadAtlas()

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
        val popup = CapriInputPopup(this, view, x, y) { text ->
            engine.isThinking = true
            engine.triggerBubble("Pensando...")
            
            AiService.ask(this, text,
                onSuccess = { 
                    engine.triggerBubble(it.take(120))
                    engine.isThinking = false 
                },
                onError   = { code ->
                    engine.isThinking = false
                    // 401/402/403 = clave inválida, sin saldo o sin permiso: volver a pedirla
                    if (code == 401 || code == 402 || code == 403) askApiKey()
                    else engine.triggerBubble(fallos.random())
                })
        }
        view.popup = popup
        popup.show()
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
