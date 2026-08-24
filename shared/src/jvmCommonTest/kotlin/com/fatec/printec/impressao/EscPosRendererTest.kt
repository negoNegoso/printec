package com.fatec.printec.impressao

import com.fatec.printec.etiqueta.Alinhamento
import com.fatec.printec.etiqueta.Bloco
import com.fatec.printec.etiqueta.LabelDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EscPosRendererTest {

    private fun hex(bytes: ByteArray) = bytes.joinToString(" ") { "%02X".format(it) }

    @Test
    fun `documento vazio ainda inicializa e seleciona a code page`() {
        val bytes = EscPosRenderer.renderizar(LabelDocument(), avancoFinalMm = 0)
        assertTrue(hex(bytes).startsWith("1B 40 1B 74 03"), "obtido: ${hex(bytes)}")
    }

    @Test
    fun `linha simples sai com alinhamento escala e texto`() {
        val doc = LabelDocument(listOf(Bloco.Linha("AB")))
        val hex = hex(EscPosRenderer.renderizar(doc, avancoFinalMm = 0))
        // ESC a 0 (esquerda), GS ! 0x00 (escala 1), ESC E 0 (sem negrito), "AB", LF
        assertTrue(hex.contains("1B 61 00 1D 21 00 1B 45 00 41 42 0A"), "obtido: $hex")
    }

    @Test
    fun `titulo vira escala dobrada centralizada e negrito`() {
        val doc = LabelDocument(listOf(Bloco.Titulo("OI")))
        val hex = hex(EscPosRenderer.renderizar(doc, avancoFinalMm = 0))
        // ESC a 1 (centro), GS ! 0x11 (2x2), ESC E 1 (negrito)
        assertTrue(hex.contains("1B 61 01 1D 21 11 1B 45 01 4F 49 0A"), "obtido: $hex")
    }

    @Test
    fun `alinhamento a direita usa ESC a 2`() {
        val doc = LabelDocument(listOf(Bloco.Linha("X", alinhamento = Alinhamento.DIREITA)))
        assertTrue(hex(EscPosRenderer.renderizar(doc, 0)).contains("1B 61 02"))
    }

    @Test
    fun `texto longo e quebrado em duas linhas com um LF cada`() {
        val doc = LabelDocument(listOf(Bloco.Linha("a".repeat(33))))
        val bytes = EscPosRenderer.renderizar(doc, avancoFinalMm = 0)
        assertEquals(2, bytes.count { it == 0x0A.toByte() })
        // 32 letras na primeira linha, 1 na segunda
        assertEquals(33, bytes.count { it == 'a'.code.toByte() })
    }

    @Test
    fun `acentos usam os bytes da PC860`() {
        val doc = LabelDocument(listOf(Bloco.Linha("ação")))
        assertTrue(hex(EscPosRenderer.renderizar(doc, 0)).contains("61 87 84 6F"))
    }

    @Test
    fun `qr emite o prefixo GS parenteses k`() {
        val doc = LabelDocument(listOf(Bloco.Qr("https://exemplo.com")))
        assertTrue(hex(EscPosRenderer.renderizar(doc, 0)).contains("1D 28 6B"))
    }

    @Test
    fun `avanco final e convertido de mm para dots`() {
        val bytes = EscPosRenderer.renderizar(LabelDocument(), avancoFinalMm = 3)
        // ESC J 24  (3 mm x 8 dots/mm)
        assertTrue(hex(bytes).endsWith("1B 4A 18"), "obtido: ${hex(bytes)}")
    }

    @Test
    fun `bloco de avanco explicito tambem vira ESC J`() {
        val doc = LabelDocument(listOf(Bloco.Avanco(2)))
        assertTrue(hex(EscPosRenderer.renderizar(doc, 0)).contains("1B 4A 10"))
    }
}
