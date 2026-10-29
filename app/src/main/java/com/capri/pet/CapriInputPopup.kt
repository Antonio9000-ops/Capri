package com.capri.pet

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.PopupWindow
import android.widget.TextView

class CapriInputPopup(
    private val context: Context,
    private val anchor: View,
    private val capriX: Int,
    private val capriY: Int,
    private val onSend: (String) -> Unit,
    private val onOlvidar: () -> Unit
) {
    private val popup: PopupWindow
    private val edit: EditText

    init {
        val v = LayoutInflater.from(context).inflate(R.layout.popup_bubble_input, null)
        edit = v.findViewById(R.id.edit_input)
        val btn = v.findViewById<Button>(R.id.btn_send)
        val olv = v.findViewById<TextView>(R.id.btn_olvidar)
        
        popup = PopupWindow(v, ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            isFocusable = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NEEDED
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // Evita que el teclado oculte la vista
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        
        btn.setOnClickListener {
            val t = edit.text.toString().trim()
            if (t.isNotEmpty()) { 
                popup.dismiss()
                onSend(t) 
            }
        }
        olv.setOnClickListener { onOlvidar() }
    }

    fun show() {
        val dm = context.resources.displayMetrics
        popup.contentView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val pw = popup.contentView.measuredWidth
        val ph = popup.contentView.measuredHeight
        
        // Calcular X e Y asegurando que no se salga de los bordes
        val x = (capriX - pw / 2).coerceIn(10, dm.widthPixels - pw - 10)
        // Lo ponemos encima de la cabeza de Capri
        val y = (capriY - ph).coerceIn(10, dm.heightPixels - ph - 10)
        
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
        
        edit.requestFocus()
        // Retrasar un poco la apertura del teclado para que no choque con el Popup
        edit.postDelayed({
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

    fun dismiss() { if (popup.isShowing) popup.dismiss() }
    fun isShowing() = popup.isShowing
}
