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
    
    // Optimización: Reutilizar el RectF para no crear basura en cada frame
    private val drawRect = RectF()
    private val shadowRect = RectF()

    // ESCALA DEL PERSONAJE: Ajusta este valor si quieres que Capri sea más grande o pequeño
    private val scale = 1.8f

    // MAPEO DE FOTOGRAMAS: Si ves que las poses están cambiadas, intercambia los números aquí.
    // Orden esperado: WALK_R(0), WALK_L(1), IDLE_R(2), IDLE_L(3), JUMP(4), SLEEP(5)
    private val frameOrder = intArrayOf(
        0, // WALK_R
        1, // WALK_L
        2, // IDLE_R
        3, // IDLE_L
        4, // JUMP
        5  // SLEEP
    )

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
            engine.update()
            // postInvalidateOnAnimation se sincroniza mejor con la pantalla y ahorra batería
            postInvalidateOnAnimation()
            handler.postDelayed(this, engine.nextTickDelayMs())
        }
    }

    fun setup(e: PetEngine) { engine = e }
    fun initEngine(w: Int, h: Int, floorY: Int) {
        val startX = w / 2 - (PetEngine.SIZE * scale / 2).toInt()
        engine.init(w, h, floorY, startX)
    }
    
    fun startLoop() { if (!running) { running = true; handler.post(tickRunnable) } }
    fun stopLoop() { running = false; handler.removeCallbacks(tickRunnable) }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) startLoop() else stopLoop()
    }

    fun loadAtlas() {
        try {
            val raw = context.assets.open("pet_atlas.bmp").use { BitmapFactory.decodeStream(it) } ?: return
            val px = IntArray(raw.width * raw.height)
            raw.getPixels(px, 0, raw.width, 0, 0, raw.width, raw.height)
            // Chroma key: negro a transparente
            for (i in px.indices) if (px[i] == 0xFF000000.toInt()) px[i] = 0
            val keyed = Bitmap.createBitmap(px, raw.width, raw.height, Bitmap.Config.ARGB_8888)
            raw.recycle()
            
            for (f in PetFrame.values()) {
                val realIndex = frameOrder[f.index]
                frames[f.index] = Bitmap.createBitmap(keyed, realIndex * PetEngine.SIZE, 0, PetEngine.SIZE, PetEngine.SIZE)
            }
            keyed.recycle()
        } catch (e: Exception) { 
            e.printStackTrace() 
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!::engine.isInitialized || engine.screenW == 0) return
        val frame = frames[engine.activeFrame.index] ?: return

        val scaledSize = PetEngine.SIZE * scale

        // Dibujar sombra (solo si salta o cae)
        if (engine.state == PetState.JUMP || engine.state == PetState.FALL) {
            val sx = engine.posX + (scaledSize - PetEngine.SHADOW_W) / 2f
            val sy = engine.floorY + scaledSize - PetEngine.SHADOW_H
            shadowRect.set(sx, sy, sx + PetEngine.SHADOW_W, sy + PetEngine.SHADOW_H)
            canvas.drawOval(shadowRect, shadowPaint)
        }

        // Calcular posición Y con rebote o salto
        val y = engine.posY + engine.visualBounce() + engine.visualJump()
        
        // Dibujar el personaje escalado
        drawRect.set(
            engine.posX.toFloat(),
            y.toFloat(),
            engine.posX + scaledSize,
            y + scaledSize
        )
        canvas.drawBitmap(frame, null, drawRect, paint)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!::engine.isInitialized || engine.screenW == 0) return false
        
        // Ajustar las coordenadas del toque a la escala
        val touchX = (ev.x / scale) + engine.posX - (ev.x / scale)
        val touchY = (ev.y / scale) + engine.posY - (ev.y / scale)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; downTime = System.currentTimeMillis(); isTap = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX; val dy = ev.y - downY
                if (isTap && dx * dx + dy * dy > DRAG_TH * DRAG_TH) {
                    engine.handleTouchDown(ev.x.toInt(), ev.y.toInt()); isTap = false
                }
                if (!isTap) { 
                    engine.handleTouchMove(ev.x.toInt(), ev.y.toInt()); 
                    postInvalidateOnAnimation() 
                }
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
                        if (!engine.isThinking && popup?.isShowing() != true)
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
