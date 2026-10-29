package com.capri.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetEngineTest {
    private val spriteW = (PetEngine.SIZE * 1.8f).toInt() // 172: el mismo tamaño dibujado por CapriView

    private fun newEngine(w: Int = 1080, h: Int = 2000): PetEngine {
        val e = PetEngine()
        e.init(w, h, h - 300, w / 2)
        e.boundMaxX = w - spriteW // lo que hace CapriView.onSizeChanged
        return e
    }

    @Test
    fun `al caminar el sprite nunca se sale de la pantalla`() {
        val w = 1080
        val e = newEngine(w = w)
        repeat(20_000) {
            e.update()
            assertTrue("posX=${e.posX}", e.posX >= e.boundMinX)
            assertTrue("posX=${e.posX}", e.posX + spriteW <= w)
        }
    }

    @Test
    fun `el salto termina de vuelta en el suelo`() {
        val e = newEngine()
        e.triggerShake()
        assertEquals(PetState.JUMP, e.state)
        repeat(40) { e.update() }
        assertEquals(PetState.IDLE, e.state)
        assertEquals(e.floorY, e.posY)
    }
}
