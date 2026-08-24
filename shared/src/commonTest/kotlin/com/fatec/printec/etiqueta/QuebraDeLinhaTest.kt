package com.fatec.printec.etiqueta

import kotlin.test.Test
import kotlin.test.assertEquals

class QuebraDeLinhaTest {

    @Test
    fun `texto menor que a largura fica em uma linha`() {
        assertEquals(listOf("bancada A"), QuebraDeLinha.quebrar("bancada A", 32))
    }

    @Test
    fun `texto com exatamente 32 caracteres nao quebra`() {
        val texto = "a".repeat(32)
        assertEquals(listOf(texto), QuebraDeLinha.quebrar(texto, 32))
    }

    @Test
    fun `texto com 33 caracteres quebra em duas linhas`() {
        val texto = "a".repeat(33)
        assertEquals(listOf("a".repeat(32), "a"), QuebraDeLinha.quebrar(texto, 32))
    }

    @Test
    fun `quebra preferencialmente no espaco entre palavras`() {
        val texto = "peca de reposicao para bancada central"
        assertEquals(
            listOf("peca de reposicao para bancada", "central"),
            QuebraDeLinha.quebrar(texto, 32),
        )
    }

    @Test
    fun `palavra maior que a linha e cortada em vez de sumir`() {
        val texto = "b".repeat(40)
        assertEquals(listOf("b".repeat(32), "b".repeat(8)), QuebraDeLinha.quebrar(texto, 32))
    }

    @Test
    fun `acentos contam como um caractere`() {
        // Na PC860 cada acentuada ocupa 1 byte, entao conta 1 coluna.
        val texto = "ação não coração"  // 16 caracteres
        assertEquals(listOf(texto), QuebraDeLinha.quebrar(texto, 16))
    }

    @Test
    fun `texto vazio produz uma linha vazia`() {
        assertEquals(listOf(""), QuebraDeLinha.quebrar("", 32))
    }

    @Test
    fun `escala dobrada reduz as colunas pela metade`() {
        assertEquals(32, QuebraDeLinha.colunasPara(1))
        assertEquals(16, QuebraDeLinha.colunasPara(2))
        assertEquals(8, QuebraDeLinha.colunasPara(4))
    }
}
