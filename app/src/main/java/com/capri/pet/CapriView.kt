package com.capri.pet

import android.content.Context
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import kotlin.math.sqrt

class CapriView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle), SensorEventListener {

    private lateinit var engine: PetEngine
    private val frames = arrayOfNulls<Bitmap>(PetFrame.values().size)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 0, 0, 0) }
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    
    private val drawRect = RectF()
    private val srcRect = Rect()
    private val shadowRect = RectF()
    
    // --- AJUSTES VISUALES ---
    val scale = 1.8f
    // Margen para recortar el sprite si tiene mucho espacio vacío alrededor (ajusta esto si se ve mal)
    private val cropPadding = 8f 

    // Si el personaje camina al revés, cambia esto a intArrayOf(1, 0, 3, 2, 4, 5)
    private val frameOrder = intArrayOf(0, 1, 2, 3, 4, 5)

    var popup: CapriInputPopup? = null
    var onRequestInput: ((Int, Int) -> Unit)? = null

    private var downX = 0f; private var downY = 0f; private var downTime = 0L
    private var isTap = false
    private var lastTapTime = 0L; private var lastTapX = 0f; private var lastTapY = 0f
    private val DT_TIMEOUT = 500L; private val DT_SLOP = 40f; private val DRAG_TH = 20f

    // --- ACELERÓMETRO ---
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var lastShakeTime = 0L

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            engine.update()
            postInvalidateOnAnimation()
            handler.postDelayed(this, engine.nextTickDelayMs())
        }
    }

    fun setup(e: PetEngine) { engine = e }
    
    fun initEngine(w: Int, h: Int, floorY: Int) {
        // Calculamos el tamaño visual real para que los pies toquen el suelo exacto
        val visualSize = (PetEngine.SIZE * scale).toInt()
        val realFloorY = h - (h - floorY) - visualSize 
        val startX = w / 2 - (visualSize / 2)
        
        // Engañamos al engine para que su "suelo" sea el correcto
        engine.init(w, h, h - visualSize, startX)
    }
    
    fun startLoop() { if (!running) { running = true; handler.post(tickRunnable) } }
    fun stopLoop() { running = false; handler.removeCallbacks(tickRunnable) }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        sensorManager.unregisterListener(this)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) startLoop() else stopLoop()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val acceleration = sqrt((x * x + y * y + z * z).toDouble()) - SensorManager.GRAVITY_EARTH
            
            // Si la aceleración supera un umbral y el personaje está en el suelo
            if (acceleration > 12 && System.currentTimeMillis() - lastShakeTime > 2000) {
                lastShakeTime = System.currentTimeMillis()
                if (engine.state != PetState.DRAG && engine.state != PetState.JUMP) {
                    engine.triggerShake()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun loadAtlas() {
        try {
            val raw = context.assets.open("pet_atlas.bmp").use { BitmapFactory.decodeStream(it) } ?: return
            val px = IntArray(raw.width * raw.height)
            raw.getPixels(px, 0, raw.width, 0, 0, raw.width, raw.height)
            for (i in px.indices) if (px[i] == 0xFF000000.toInt()) px[i] = 0
            val keyed = Bitmap.createBitmap(px, raw.width, raw.height, Bitmap.Config.ARGB_8888)
            raw.recycle()
            
            for (f in PetFrame.values()) {
                val realIndex = frameOrder[f.index]
                frames[f.index] = Bitmap.createBitmap(keyed, realIndex * PetEngine.SIZE, 0, PetEngine.SIZE, PetEngine.SIZE)
            }
            keyed.recycle()
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!::engine.isInitialized || engine.screenW == 0) return
        val frame = frames[engine.activeFrame.index] ?: return

        val scaledSize = PetEngine.SIZE * scale
        val y = engine.posY + engine.visualBounce() + engine.visualJump()
        
        // Dibujar sombra
        if (engine.state == PetState.JUMP || engine.state == PetState.FALL) {
            val sx = engine.posX + (scaledSize - PetEngine.SHADOW_W) / 2f
            val sy = engine.floorY + scaledSize - PetEngine.SHADOW_H
            shadowRect.set(sx, sy, sx + PetEngine.SHADOW_W, sy + PetEngine.SHADOW_H)
            canvas.drawOval(shadowRect, shadowPaint)
        }

        // 1. Rectángulo de destino (dónde se dibuja en la pantalla)
        drawRect.set(engine.posX.toFloat(), y, engine.posX + scaledSize, y + scaledSize)
        
        // 2. Rectángulo de origen (recortando los bordes vacíos para que se vea mejor)
        srcRect.set(cropPadding.toInt(), cropPadding.toInt(), PetEngine.SIZE - cropPadding.toInt(), PetEngine.SIZE - cropPadding.toInt())
        
        canvas.drawBitmap(frame, srcRect, drawRect, paint)
    }

    // Comprueba si el dedo tocó al personaje
    private fun isTouchingCharacter(x: Float, y: Float): Boolean {
        if (!::engine.isInitialized) return false
        val scaledSize = PetEngine.SIZE * scale
        val visualY = engine.posY + engine.visualBounce() + engine.visualJump()
        
        // Añadimos un margen de tolerancia de 20 píxeles para que sea más fácil tocarlo
        val padding = 20f
        return x >= engine.posX - padding && x <= engine.posX + scaledSize + padding &&
               y >= visualY - padding && y <= visualY + scaledSize + padding
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!::engine.isInitialized || engine.screenW == 0) return false
        
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Si no toca al personaje, ignoramos el evento
                if (!isTouchingCharacter(ev.x, ev.y)) return false
                
                downX = ev.x; downY = ev.y; downTime = System.currentTimeMillis(); isTap = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isTap && engine.state != PetState.DRAG) return false
                
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
                        if (!engine.isThinking && popup?.isShowing() != true) {
                            // Posicionamos el popup justo encima de la cabeza
                            val headY = (engine.posY + engine.visualJump()).toInt() - 20
                            onRequestInput?.invoke(engine.posX, headY)
                        }
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
