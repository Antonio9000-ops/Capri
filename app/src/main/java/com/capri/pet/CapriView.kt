package com.capri.pet

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class CapriView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private lateinit var engine: PetEngine
    private val frames = arrayOfNulls<Bitmap>(PetFrame.values().size)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 0, 0, 0) }
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    var popup: CapriInputPopup? = null
    var onRequestInput: ((Int, Int) -> Unit)? = null

    // Tap / doble tap / drag
    private var downX = 0f; private var downY = 0f; private var downTime = 0L
    private var isTap = false
    private var lastTapTime = 0L; private var lastTapX = 0f; private var lastTapY = 0f
    private val DT_TIMEOUT = 500L; private val DT_SLOP = 40f; private val DRAG_TH = 20f

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            engine.update(); invalidate()
            handler.postDelayed(this, engine.nextTickDelayMs())
        }
    }

    fun setup(e: PetEngine) { engine = e }
    fun initEngine(w: Int, h: Int, floorY: Int) = engine.init(w, h, floorY, w / 2 - PetEngine.SIZE / 2)
    fun startLoop() { if (!running) { running = true; handler.post(tickRunnable) } }
    fun stopLoop() { running = false; handler.removeCallbacks(tickRunnable) }

    fun loadAtlas() {
        try {
            val raw = context.assets.open("pet_atlas.bmp").use { BitmapFactory.decodeStream(it) } ?: return
            val px = IntArray(raw.width * raw.height)
            raw.getPixels(px, 0, raw.width, 0, 0, raw.width, raw.height)
            for (i in px.indices) if (px[i] == 0xFF000000.toInt()) px[i] = 0
            val keyed = Bitmap.createBitmap(px, raw.width, raw.height, Bitmap.Config.ARGB_8888)
            raw.recycle()
            for (f in PetFrame.values()) {
                frames[f.index] = Bitmap.createBitmap(keyed, f.index * PetEngine.SIZE, 0, PetEngine.SIZE, PetEngine.SIZE)
            }
            keyed.recycle()
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!::engine.isInitialized || engine.screenW == 0) return
        val frame = frames[engine.activeFrame.index] ?: return

        if (engine.state == PetState.JUMP || engine.state == PetState.FALL) {
            val sx = engine.posX + (PetEngine.SIZE - PetEngine.SHADOW_W) / 2f
            val sy = engine.floorY + PetEngine.SIZE - PetEngine.SHADOW_H.toFloat()
            canvas.drawOval(RectF(sx, sy, sx + PetEngine.SHADOW_W, sy + PetEngine.SHADOW_H), shadowPaint)
        }

        val y = engine.posY + engine.visualBounce() + engine.visualJump()
        canvas.drawBitmap(frame, engine.posX.toFloat(), y.toFloat(), paint)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!::engine.isInitialized || engine.screenW == 0) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; downTime = System.currentTimeMillis(); isTap = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX; val dy = ev.y - downY
                if (isTap && dx * dx + dy * dy > DRAG_TH * DRAG_TH) {
                    engine.handleTouchDown(downX.toInt(), downY.toInt()); isTap = false
                }
                if (!isTap) { engine.handleTouchMove(ev.x.toInt(), ev.y.toInt()); invalidate() }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isTap) {
                    val now = System.currentTimeMillis()
                    val dt = now - lastTapTime
                    val ddx = ev.x - lastTapX; val ddy = ev.y - lastTapY
                    val near = ddx * ddx + ddy * ddy <= DT_SLOP * DT_SLOP
                    if (dt in 1..DT_TIMEOUT && near) {
                        lastTapTime = 0L
                        if (!engine.isThinking && popup?.isShowing != true)
                            onRequestInput?.invoke(engine.posX, engine.posY)
                    } else {
                        lastTapTime = now; lastTapX = ev.x; lastTapY = ev.y
                    }
                } else {
                    engine.handleTouchUp()
                }
                isTap = false
                return true
            }
        }
        return super.onTouchEvent(ev)
    }
}
