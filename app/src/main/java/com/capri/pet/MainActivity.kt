package com.capri.pet

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
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

        view.onRequestInput = { x, y -> showInputPopup(x, y) }

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (v.width > 0 && v.height > 0 && engine.screenW == 0) {
                view.initEngine(v.width, v.height, v.height - bars.bottom)
            }
            insets
        }

        if (!SecurityHelper.hasApiKey(this)) askApiKey()
    }

    private fun askApiKey() {
        val input = EditText(this).apply {
            hint = "API Key (OpenRouter)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(android.graphics.Color.BLACK) // Fix para que se vea el texto
        }
        AlertDialog.Builder(this)
            .setTitle("Configurar API Key")
            .setMessage("Pega tu clave de OpenRouter para que Capri pueda pensar.")
            .setView(input).setCancelable(false)
            .setPositiveButton("Guardar") { _, _ ->
                input.text.toString().trim().takeIf { it.isNotEmpty() }
                    ?.let { SecurityHelper.saveApiKey(this, it) }
            }.show()
    }

    private fun showInputPopup(x: Int, y: Int) {
        val popup = CapriInputPopup(this, view, x, y) { text ->
            engine.isThinking = true
            engine.triggerBubble("Pensando...")
            AiService.ask(this, text,
                onSuccess = { engine.triggerBubble(it.take(120)); engine.isThinking = false },
                onError   = { engine.triggerBubble("No puedo pensar ahora..."); engine.isThinking = false })
        }
        view.popup = popup
        popup.show()
    }

    override fun onUserLeaveHint() { super.onUserLeaveHint(); userLeaving = true }

    override fun onResume() { 
        super.onResume(); 
        view.startLoop() 
        view.invalidate() // Fuerza el redibujado al volver a la app
    }

    override fun onPause() {
        super.onPause()
        view.popup?.dismiss()
        view.stopLoop()
        if (userLeaving && !isChangingConfigurations) finishAndRemoveTask()
        userLeaving = false
    }
}
