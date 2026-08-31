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
    fun `titulo vira escala dobrada centralizada e SEM negrito`() {
        val doc = LabelDocument(listOf(Bloco.Titulo("OI")))
        val hex = hex(EscPosRenderer.renderizar(doc, avancoFinalMm = 0))
        // ESC a 1 (centro), GS ! 0x11 (2x2), ESC E 0 -- negrito DESLIGADO.
        // A LT-8359 faz enfase como duplo impacto deslocado na horizontal. Em
        // largura 2x o segundo impacto cai dentro do proprio glifo e preenche o
        // vao entre as colunas de dots: o titulo saia borrado no papel enquanto
        // uma linha 2x comum, logo abaixo, saia nitida. Foi o que a foto da
        // etiqueta de calibracao mostrou -- "CALIBRACAO" ilegivel, "escala 2x"
        // perfeito, mesma escala, so o negrito de diferenca.
        assertTrue(hex.contains("1B 61 01 1D 21 11 1B 45 00 4F 49 0A"), "obtido: $hex")
    }

    @Test
    fun `alinhamento a direita usa ESC a 2`() {
        val doc = LabelDocument(listOf(Bloco.Linha("X", alinhamento = Alinhamento.DIREITA)))
        assertTrue(hex(EscPosRenderer.renderizar(doc, 0)).contains("1B 61 02"))
    }

    @Test
    fun `texto longo e quebrado em duas linhas com um LF cada`() {
        // 'z' (0x7A) de proposito: 'a' seria 0x61, que e o SEGUNDO byte do
        // comando ESC a (alinhamento). Contar 0x61 no array inteiro somaria o
        // byte do comando as letras do texto e daria 34 em vez de 33.
        val doc = LabelDocument(listOf(Bloco.Linha("z".repeat(33))))
        val bytes = EscPosRenderer.renderizar(doc, avancoFinalMm = 0)
        assertEquals(2, bytes.count { it == 0x0A.toByte() })
        // 32 letras na primeira linha, 1 na segunda
        assertEquals(33, bytes.count { it == 'z'.code.toByte() })
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
    fun `qr com acentos usa o comprimento dos bytes UTF-8, nao dos caracteres`() {
        // "acao" em UTF-8: 61 C3 A7 C3 A3 6F -- 6 bytes para 4 caracteres. Se o
        // comprimento fosse contado em caracteres (como o QRCode do
        // escpos-coffee fazia), pL/pH sairia 7 (4+3) e o payload seria
        // truncado no meio de um caractere multibyte.
        val doc = LabelDocument(listOf(Bloco.Qr("ação")))
        val hex = hex(EscPosRenderer.renderizar(doc, 0))

        // Comando "armazenar dados" (cn=31 fn=50): pL pH = 09 00 (6 bytes + 3).
        assertTrue(hex.contains("1D 28 6B 09 00 31 50 30 61 C3 A7 C3 A3 6F"), "obtido: $hex")
    }

    @Test
    fun `qr ascii segue emitindo o prefixo GS parenteses k`() {
        val doc = LabelDocument(listOf(Bloco.Qr("ABC")))
        val hex = hex(EscPosRenderer.renderizar(doc, 0))
        assertTrue(hex.contains("1D 28 6B"), "obtido: $hex")
        // "ABC" -> 3 bytes + 3 = 6 = pL, pH = 00.
        assertTrue(hex.contains("1D 28 6B 06 00 31 50 30 41 42 43"), "obtido: $hex")
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
