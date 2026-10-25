package com.capri.pet

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class MainActivity : AppCompatActivity() {
    private lateinit var view: CapriView
    private lateinit var engine: PetEngine
    private var userLeaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.e("CapriLog", "1. onCreate iniciado")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        view = CapriView(this)
        view.setBackgroundColor(Color.GREEN) // Fondo verde para saber si la vista se dibuja
        setContentView(view)
        Log.e("CapriLog", "2. Vista CapriView asignada")

        engine = PetEngine()
        view.setup(engine)
        Log.e("CapriLog", "3. Motor PetEngine inicializado")

        try {
            view.loadAtlas()
            Log.e("CapriLog", "4. Atlas cargado correctamente")
        } catch (e: Exception) {
            Log.e("CapriLog", "4. Error cargando Atlas: ${e.message}")
        }

        view.onRequestInput = { x, y -> 
            Log.e("CapriLog", "Toque detectado en X=$x, Y=$y")
            showInputPopup(x, y) 
        }

        // --- SALTEAMOS LA SEGURIDAD TEMPORALMENTE PARA PROBAR ---
        Log.e("CapriLog", "5. Llamando a askApiKey() directamente...")
        askApiKey()
        Log.e("CapriLog", "6. onCreate finalizado")
        Toast.makeText(this, "App iniciada", Toast.LENGTH_SHORT).show()
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
                    try {
                        SecurityHelper.saveApiKey(this, key)
                        Log.e("CapriLog", "Clave guardada exitosamente.")
                    } catch (e: Exception) {
                        Log.e("CapriLog", "Error guardando clave: ${e.message}")
                    }
                }
            }.show()
        Log.e("CapriLog", "7. Diálogo AlertDialog lanzado")
    }

    private fun showInputPopup(x: Int, y: Int) {
        val popup = CapriInputPopup(this, view, x, y) { text ->
            engine.isThinking = true
            engine.triggerBubble("Pensando...")
            
            AiService.ask(this, text,
                onSuccess = { 
                    engine.triggerBubble(it.take(120))
                    engine.isThinking = false 
                },
                onError   = { 
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
