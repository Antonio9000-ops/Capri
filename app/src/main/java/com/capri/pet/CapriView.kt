package com.capri.pet

import android.content.Context
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.sqrt

class CapriView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle), SensorEventListener {

    private lateinit var engine: PetEngine
    private val frames = arrayOfNulls<Bitmap>(PetFrame.values().size)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 0, 0, 0) }
    
    // --- PINTURA Y LAYOUT PARA LA BURBUJA ---
    private val bubbleBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(4f, 0f, 2f, Color.argb(100, 0, 0, 0))
    }
    private val bubbleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 40f
    }
    private val bubbleRect = RectF()
    private val bubblePath = Path()
    private var currentBubbleText = ""
    private var bubbleLayout: StaticLayout? = null
    private var bubbleW = 0f
    private var bubbleH = 0f

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    
    private val drawRect = RectF()
    private val srcRect = Rect()
    private val shadowRect = RectF()
    
    val scale = 1.8f
    private val cropPadding = 8f
    private val frameOrder = intArrayOf(0, 1, 2, 3, 4, 5)

    var popup: CapriInputPopup? = null
    var onRequestInput: ((Int, Int) -> Unit)? = null

    private var downX = 0f; private var downY = 0f; private var downTime = 0L
    private var isTap = false
    private var lastTapTime = 0L; private var lastTapX = 0f; private var lastTapY = 0f
    private val DT_TIMEOUT = 500L; private val DT_SLOP = 40f; private val DRAG_TH = 20f

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

    fun setup(e: PetEngine) { 
        engine = e 
        engine.onBubble = { text, visible ->
            currentBubbleText = text
            if (visible && text.isNotEmpty()) {
                buildBubbleLayout(text)
            }
            postInvalidateOnAnimation()
        }
    }

    private fun buildBubbleLayout(text: String) {
        val maxWidth = (width * 0.8f).toInt()
        if (maxWidth <= 0) return
        
        bubbleLayout = StaticLayout.Builder.obtain(text, 0, text.length, bubbleTextPaint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1f)
            .build()
        
        bubbleW = bubbleLayout!!.width.toFloat() + 60f
        bubbleH = bubbleLayout!!.height.toFloat() + 60f
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && ::engine.isInitialized && engine.screenW == 0) {
            val insets = ViewCompat.getRootWindowInsets(this)
            val bottomInset = insets?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
            
            val visualSize = (PetEngine.SIZE * scale).toInt()
            val startX = w / 2 - (visualSize / 2)
            engine.init(w, h, h - bottomInset - visualSize, startX)
        }
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
            val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
            val acceleration = sqrt((x * x + y * y + z * z).toDouble()) - SensorManager.GRAVITY_EARTH
            if (acceleration > 12 && System.currentTimeMillis() - lastShakeTime > 2000) {
                lastShakeTime = System.currentTimeMillis()
                if (engine.state != PetState.DRAG && engine.state != PetState.JUMP) engine.triggerShake()
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
        val y = (engine.posY + engine.visualBounce() + engine.visualJump()).toFloat()
        
        if (engine.state == PetState.JUMP || engine.state == PetState.FALL) {
            val sx = engine.posX + (scaledSize - PetEngine.SHADOW_W) / 2f
            val sy = engine.floorY + scaledSize - PetEngine.SHADOW_H
            shadowRect.set(sx, sy, sx + PetEngine.SHADOW_W, sy + PetEngine.SHADOW_H)
            canvas.drawOval(shadowRect, shadowPaint)
        }

        val posXFloat = engine.posX.toFloat()
        drawRect.set(posXFloat, y, posXFloat + scaledSize, y + scaledSize)
        val paddingInt = cropPadding.toInt()
        srcRect.set(paddingInt, paddingInt, PetEngine.SIZE - paddingInt, PetEngine.SIZE - paddingInt)
        canvas.drawBitmap(frame, srcRect, drawRect, paint)

        // --- DIBUJAR BURBUJA MULTILÍNEA ---
        if (engine.bubbleVisible && currentBubbleText.isNotEmpty() && bubbleLayout != null) {
            val bx = posXFloat + scaledSize / 2f
            val by = y - bubbleH - 20f
            
            // Contenedor de la burbuja
            bubbleRect.set(bx - bubbleW / 2, by, bx + bubbleW / 2, by + bubbleH)
            canvas.drawRoundRect(bubbleRect, 20f, 20f, bubbleBgPaint)
            
            // Pico de la burbuja
            bubblePath.reset()
            bubblePath.moveTo(bx - 15f, by + bubbleH)
            bubblePath.lineTo(bx + 15f, by + bubbleH)
            bubblePath.lineTo(bx, by + bubbleH + 20f)
            bubblePath.close()
            canvas.drawPath(bubblePath, bubbleBgPaint)
            
            // Dibujar texto con StaticLayout
            canvas.save()
            // Centrar el texto dentro de la burbuja
            canvas.translate(bx - bubbleW / 2 + 30f, by + 30f)
            bubbleLayout?.draw(canvas)
            canvas.restore()
        }
    }

    private fun isTouchingCharacter(x: Float, y: Float): Boolean {
        if (!::engine.isInitialized) return false
        val scaledSize = PetEngine.SIZE * scale
        val visualY = (engine.posY + engine.visualBounce() + engine.visualJump()).toFloat()
        val padding = 20f
        val posXFloat = engine.posX.toFloat()
        return x >= posXFloat - padding && x <= posXFloat + scaledSize + padding &&
               y >= visualY - padding && y <= visualY + scaledSize + padding
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!::engine.isInitialized || engine.screenW == 0) return false
        
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
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
