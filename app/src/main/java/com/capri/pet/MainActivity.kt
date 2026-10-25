package com.capri.pet

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private lateinit var view: CapriView
    private lateinit var engine: PetEngine
    private var userLeaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        view = CapriView(this); setContentView(view)

        engine = PetEngine()
        view.setup(engine)
        view.loadAtlas()

        view.onRequestInput = { x, y -> 
            Log.d("CapriLog", "Doble toque detectado, abriendo popup en X=$x, Y=$y")
            showInputPopup(x, y) 
        }

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (v.width > 0 && v.height > 0 && engine.screenW == 0) {
                view.initEngine(v.width, v.height, v.height - bars.bottom)
            }
            insets
        }

        if (!SecurityHelper.hasApiKey(this)) {
            Log.d("CapriLog", "No hay API Key, mostrando diálogo")
            askApiKey()
        } else {
            Log.d("CapriLog", "API Key encontrada en memoria segura")
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
            .setView(input).setCancelable(false)
            .setPositiveButton("Guardar") { _, _ ->
                val key = input.text.toString().trim()
                if (key.isNotEmpty()) {
                    SecurityHelper.saveApiKey(this, key)
                    Log.d("CapriLog", "API Key guardada con éxito")
                } else {
                    Log.e("CapriLog", "El usuario intentó guardar una API Key vacía")
                }
            }.show()
    }

    private fun showInputPopup(x: Int, y: Int) {
        val popup = CapriInputPopup(this, view, x, y) { text ->
            Log.d("CapriLog", "Popup envió texto: '$text'")
            engine.isThinking = true
            engine.triggerBubble("Pensando...")
            
            AiService.ask(this, text,
                onSuccess = { 
                    Log.d("CapriLog", "IA respondió: $it")
                    engine.triggerBubble(it.take(120))
                    engine.isThinking = false 
                },
                onError   = { 
                    Log.e("CapriLog", "La IA falló o dio error")
                    engine.triggerBubble("No puedo pensar ahora...")
                    engine.isThinking = false 
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
