package com.capri.pet

enum class PetState { WALK, IDLE, JUMP, SLEEP, LOOK, TALKING, DRAG, FALL }
enum class PetFrame(val index: Int) {
    WALK_R(0), WALK_L(1), IDLE_R(2), IDLE_L(3), JUMP(4), SLEEP(5)
}

class PetEngine {
    private val speed = 2
    private val jumpChance = 15
    private val lookChance = 30

    var screenW = 0; var screenH = 0
    var floorY = 0; var boundMinX = 0; var boundMaxX = 0

    var state = PetState.WALK; private set
    var activeFrame = PetFrame.WALK_R; private set
    var posX = 0; var posY = 0; var baseY = 0
    var dirX = speed; private set
    var velY = 0
    var ticks = 0; private set
    var stateDuration = 0; private set
    private var prngState = 0L

    var isThinking = false
    var bubbleVisible = false; private set
    var onBubble: ((String, Boolean) -> Unit)? = null

    private var dragOffX = 0; private var dragOffY = 0
    private var lastDragX = 0; private var lastDragY = 0
    private var flyVelX = 0

    // Animico: +1 mimo, -1 golpe de arrastre, tope ±5. Se guarda con la memoria.
    var animo = 0

    fun init(w: Int, h: Int, floor: Int, initialX: Int) {
        screenW = w; screenH = h; floorY = floor
        baseY = floor; posY = floor; posX = initialX
        boundMinX = 0; boundMaxX = w - SIZE
        dirX = speed; velY = 0; activeFrame = PetFrame.WALK_R
        var seed = ((System.currentTimeMillis() / 1000L) and 0xFFFFFFFFL) xor 0xC0FFEEL
        if (seed == 0L) seed = 0xDEADBEEFL
        prngState = seed
        enterState(PetState.WALK)
        activeFrame = PetFrame.WALK_R
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        showBubble(if (hour < 6 || hour >= 21) "Buenas noches" else if (hour < 12) "Buenos días" else "Buenas tardes")
    }

    private fun xs32(): Long {
        var x = prngState
        x = (x xor ((x shl 13) and 0xFFFFFFFFL)) and 0xFFFFFFFFL
        x = (x xor (x ushr 17)) and 0xFFFFFFFFL
        x = (x xor ((x shl 5) and 0xFFFFFFFFL)) and 0xFFFFFFFFL
        prngState = x
        return x
    }
    private fun randRange(lo: Int, hi: Int) = lo + (xs32() % (hi - lo + 1).toLong()).toInt()

    private fun enterState(s: PetState) {
        state = s; ticks = 0
        dismissBubble()
        stateDuration = when (s) {
            PetState.WALK -> randRange(80, 180)
            PetState.IDLE -> randRange(30, 60)
            PetState.SLEEP -> randRange(15, 30) * if (esNoche()) 4 else 1
            PetState.LOOK -> randRange(20, 40)
            PetState.JUMP -> 30
            PetState.TALKING -> 60
            PetState.FALL, PetState.DRAG -> 0
        }
        if (s == PetState.SLEEP) showBubble("Zzz")
    }

    fun triggerShake() {
        if (state == PetState.IDLE || state == PetState.WALK || state == PetState.SLEEP || state == PetState.LOOK) {
            enterState(PetState.JUMP)
            showBubble("¡Uy!")
        }
    }

    // Un toque: mimo. Salta y suelta un corazón mientras dura el salto.
    fun triggerPet() {
        if (state == PetState.DRAG) return
        animo = (animo + 1).coerceAtMost(5)
        enterState(PetState.JUMP)
        showBubble("♥")
    }

    fun triggerBubble(text: String) {
        if (state == PetState.DRAG) return
        enterState(PetState.TALKING)
        showBubble(text)
    }
    private fun showBubble(text: String) {
        bubbleVisible = true
        onBubble?.invoke(text, true)
    }
    private fun dismissBubble() {
        bubbleVisible = false
        onBubble?.invoke("", false)
    }

    fun handleTouchDown(tx: Int, ty: Int) {
        if (state == PetState.DRAG) return
        enterState(PetState.DRAG)
        showBubble("¡Alto!")
        dragOffX = tx - posX
        dragOffY = ty - posY
        lastDragX = tx; lastDragY = ty
    }
    fun handleTouchMove(tx: Int, ty: Int) {
        if (state != PetState.DRAG) return
        flyVelX = (2 * (tx - lastDragX)).coerceIn(-40, 40)
        velY = (2 * (ty - lastDragY)).coerceIn(-60, 60)
        lastDragX = tx; lastDragY = ty
        posX = (tx - dragOffX).coerceIn(boundMinX, boundMaxX)
        posY = (ty - dragOffY).coerceIn(0, floorY)
    }
    fun handleTouchUp() {
        if (state != PetState.DRAG) return
        if (posY < floorY) {
            if (kotlin.math.abs(velY) > 50) animo = (animo - 1).coerceAtLeast(-5)
            enterState(PetState.FALL)
        }
        else { posY = floorY; baseY = floorY; enterState(PetState.WALK) }
    }

    private fun esNoche(): Boolean {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return h >= 22 || h < 6
    }

    fun update() {
        if (state == PetState.DRAG) return
        if (state == PetState.FALL) {
            activeFrame = PetFrame.JUMP
            velY += 2; posY += velY
            posX = (posX + flyVelX).coerceIn(boundMinX, boundMaxX)
            if (posY >= floorY) {
                posY = floorY; baseY = floorY; velY = 0; flyVelX = 0
                enterState(PetState.WALK)
            }
            return
        }
        ticks++
        when (state) {
            PetState.WALK -> {
                posX += if (dirX > 0) speed else -speed
                if (posX <= boundMinX || posX >= boundMaxX) {
                    dirX = -dirX
                    if (posX < boundMinX) posX = boundMinX
                    if (posX > boundMaxX) posX = boundMaxX
                }
                activeFrame = if (dirX > 0) PetFrame.WALK_R else PetFrame.WALK_L
                if (ticks > stateDuration) {
                    val r = randRange(0, 100)
                    when {
                        r < jumpChance -> enterState(PetState.JUMP)
                        r < (jumpChance + lookChance) -> enterState(PetState.LOOK)
                        else -> enterState(PetState.IDLE)
                    }
                }
            }
            PetState.JUMP -> {
                activeFrame = PetFrame.JUMP
                if (ticks >= stateDuration) enterState(PetState.IDLE)
            }
            PetState.LOOK -> {
                if (ticks > 0 && ticks % 15 == 0) dirX = -dirX
                activeFrame = if (dirX > 0) PetFrame.IDLE_R else PetFrame.IDLE_L
                if (ticks > stateDuration) enterState(PetState.IDLE)
            }
            PetState.TALKING -> {
                activeFrame = if (dirX > 0) PetFrame.IDLE_R else PetFrame.IDLE_L
                if (ticks > stateDuration && !isThinking) enterState(PetState.IDLE)
            }
            PetState.IDLE -> {
                activeFrame = if (dirX > 0) PetFrame.IDLE_R else PetFrame.IDLE_L
                if (ticks > stateDuration) enterState(PetState.SLEEP)
            }
            PetState.SLEEP -> {
                activeFrame = PetFrame.SLEEP
                if (ticks > stateDuration) enterState(PetState.WALK)
            }
            else -> {}
        }
    }

    fun visualBounce() = if (state == PetState.WALK && (ticks % 4 == 1 || ticks % 4 == 3)) -3 else 0
    fun visualJump(): Int = if (state == PetState.JUMP)
        (-4.0f * (ticks * (stateDuration - ticks)) / 15.0f).toInt() else 0

    fun nextTickDelayMs(): Long = when (state) {
        PetState.WALK, PetState.JUMP, PetState.FALL, PetState.DRAG -> 16L
        else -> 100L
    }

    companion object { const val SIZE = 96; const val SHADOW_W = 48; const val SHADOW_H = 12 }
}
