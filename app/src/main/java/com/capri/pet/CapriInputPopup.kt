package com.capri.pet

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.PopupWindow

class CapriInputPopup(
    private val context: Context,
    private val anchor: View,
    private val capriX: Int,
    private val capriY: Int,
    private val onSend: (String) -> Unit
) {
    private val popup: PopupWindow
    private val edit: EditText

    init {
        val v = LayoutInflater.from(context).inflate(R.layout.popup_bubble_input, null)
        edit = v.findViewById(R.id.edit_input)
        val btn = v.findViewById<Button>(R.id.btn_send)
        popup = PopupWindow(v, ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            isFocusable = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NEEDED
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        btn.setOnClickListener {
            val t = edit.text.toString().trim()
            if (t.isNotEmpty()) { popup.dismiss(); onSend(t) }
        }
    }

    fun show() {
        val dm = context.resources.displayMetrics
        popup.contentView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val pw = popup.contentView.measuredWidth
        val ph = popup.contentView.measuredHeight
        val x = (capriX + PetEngine.SIZE / 2 - pw / 2).coerceIn(0, dm.widthPixels - pw)
        val y = (capriY - ph).coerceIn(0, dm.heightPixels - ph)
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
        edit.requestFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
    }

    fun dismiss() { if (popup.isShowing) popup.dismiss() }
    fun isShowing() = popup.isShowing
}
