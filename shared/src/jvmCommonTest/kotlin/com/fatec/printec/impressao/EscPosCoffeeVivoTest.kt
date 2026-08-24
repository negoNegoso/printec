package com.fatec.printec.impressao

import com.github.anastaciocintra.escpos.EscPos
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class EscPosCoffeeVivoTest {

    @Test
    fun `escpos-coffee escreve o comando de inicializacao no stream`() {
        val saida = ByteArrayOutputStream()
        EscPos(saida).use { escpos ->
            escpos.initializePrinter()
        }
        // ESC @ = 0x1B 0x40
        assertEquals(listOf<Byte>(0x1B, 0x40), saida.toByteArray().toList())
    }
}
