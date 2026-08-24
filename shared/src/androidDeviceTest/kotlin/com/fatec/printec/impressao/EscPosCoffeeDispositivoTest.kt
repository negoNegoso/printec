package com.fatec.printec.impressao

import com.github.anastaciocintra.escpos.EscPos
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class EscPosCoffeeDispositivoTest {

    @Test
    fun escpos_coffee_carrega_e_executa_no_android() {
        val saida = ByteArrayOutputStream()
        EscPos(saida).use { it.initializePrinter() }
        assertEquals(listOf<Byte>(0x1B, 0x40), saida.toByteArray().toList())
    }
}
