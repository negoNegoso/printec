package com.fatec.printec.ui

import com.fatec.printec.etiqueta.Alinhamento
import com.fatec.printec.etiqueta.Bloco
import com.fatec.printec.etiqueta.LabelDocument
import kotlin.test.Test
import kotlin.test.assertEquals

class LinhasDePreviewTest {

    @Test
    fun `o preview quebra igual ao renderizador`() {
        val doc = LabelDocument(listOf(Bloco.Linha("a".repeat(33))))
        val linhas = linhasDePreview(doc)
        assertEquals(2, linhas.size)
        assertEquals(32, linhas[0].texto.length)
    }

    @Test
    fun `titulo aparece em escala 2 centralizado`() {
        val linhas = linhasDePreview(LabelDocument(listOf(Bloco.Titulo("Oi"))))
        assertEquals(2, linhas.single().escala)
        assertEquals(Alinhamento.CENTRO, linhas.single().alinhamento)
    }

    @Test
    fun `titulo longo quebra em 16 colunas e nao em 32`() {
        val linhas = linhasDePreview(LabelDocument(listOf(Bloco.Titulo("a".repeat(17)))))
        assertEquals(2, linhas.size)
        assertEquals(16, linhas[0].texto.length)
    }

    @Test
    fun `caracteres nao suportados sao contados para o aviso`() {
        val doc = LabelDocument(listOf(Bloco.Linha("preço €"), Bloco.Linha("ok 🎉")))
        assertEquals(3, caracteresSubstituidos(doc))
    }

    @Test
    fun `texto totalmente suportado nao gera aviso`() {
        val doc = LabelDocument(listOf(Bloco.Linha("ação não coração")))
        assertEquals(0, caracteresSubstituidos(doc))
    }
}
