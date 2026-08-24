package com.fatec.printec.etiqueta

import kotlin.test.Test
import kotlin.test.assertEquals

class Pc860Test {

    @Test
    fun `ascii passa inalterado`() {
        val r = Pc860.codificar("Bancada A1")
        assertEquals("Bancada A1".map { it.code.toByte() }, r.bytes.toList())
        assertEquals(0, r.substituidos)
    }

    @Test
    fun `acentuadas do portugues viram os bytes da PC860`() {
        val r = Pc860.codificar("ação")
        // a=0x61, ç=0x87, ã=0x84, o=0x6F
        assertEquals(listOf<Byte>(0x61, 0x87.toByte(), 0x84.toByte(), 0x6F), r.bytes.toList())
        assertEquals(0, r.substituidos)
    }

    @Test
    fun `maiusculas acentuadas tambem sao mapeadas`() {
        val r = Pc860.codificar("ÃÇÉÔÚ")
        assertEquals(
            listOf<Byte>(0x8E.toByte(), 0x80.toByte(), 0x90.toByte(), 0x8C.toByte(), 0x96.toByte()),
            r.bytes.toList(),
        )
        assertEquals(0, r.substituidos)
    }

    @Test
    fun `caractere fora da pagina vira interrogacao e e contado`() {
        val r = Pc860.codificar("preço €")
        assertEquals(1, r.substituidos)
        assertEquals('?'.code.toByte(), r.bytes.last())
    }

    @Test
    fun `emoji conta cada unidade nao mapeavel`() {
        val r = Pc860.codificar("ok 🎉")
        // O emoji ocupa duas unidades UTF-16, ambas nao mapeaveis.
        assertEquals(2, r.substituidos)
    }

    @Test
    fun `texto vazio produz zero bytes`() {
        val r = Pc860.codificar("")
        assertEquals(0, r.bytes.size)
        assertEquals(0, r.substituidos)
    }
}
