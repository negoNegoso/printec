package com.fatec.printec.etiqueta

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EtiquetaDeTesteTest {

    @Test
    fun `a regua tem exatamente 32 caracteres`() {
        val regua = etiquetaDeCalibracao().blocos
            .filterIsInstance<Bloco.Linha>()
            .first { it.texto.length >= 32 }
        assertEquals(32, regua.texto.length)
        assertEquals(1, regua.escala)
    }

    @Test
    fun `a calibracao exercita acentos do portugues`() {
        val textos = etiquetaDeCalibracao().blocos
            .filterIsInstance<Bloco.Linha>().map { it.texto }
        assertTrue(textos.any { it.contains("ação") })
    }

    @Test
    fun `a calibracao inclui um QR de referencia`() {
        assertTrue(etiquetaDeCalibracao().blocos.any { it is Bloco.Qr })
    }

    @Test
    fun `nenhum caractere da calibracao e substituido`() {
        val doc = etiquetaDeCalibracao()
        val fora = doc.blocos.filterIsInstance<Bloco.Linha>()
            .sumOf { Pc860.codificar(it.texto).substituidos }
        assertEquals(0, fora)
    }
}
