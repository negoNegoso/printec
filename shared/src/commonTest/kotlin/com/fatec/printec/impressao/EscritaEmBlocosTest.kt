package com.fatec.printec.impressao

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A LT-8359 nao segura o remetente quando seu buffer de entrada lota: ela
 * descarta o excedente em silencio. Como o avanco final sao os ULTIMOS bytes
 * do payload, era exatamente ele que sumia — junto com as escalas grandes do
 * fim da etiqueta. Estes testes fixam a politica de vazao que evita isso.
 */
class EscritaEmBlocosTest {

    private class Espiao {
        val blocos = mutableListOf<ByteArray>()
        val pausas = mutableListOf<Long>()
        fun escrever(b: ByteArray) { blocos += b }
        suspend fun pausar(ms: Long) { pausas += ms }
        fun tudoQueFoiEscrito(): ByteArray =
            blocos.fold(ByteArray(0)) { acc, b -> acc + b }
    }

    private fun payload(tamanho: Int) = ByteArray(tamanho) { (it % 251).toByte() }

    @Test
    fun `payload maior que o bloco sai fatiado, na ordem, sem perder byte`() = runTest {
        val espiao = Espiao()
        val original = payload(EscritaEmBlocos.BYTES_POR_BLOCO * 2 + 7)

        EscritaEmBlocos.escrever(original, espiao::pausar, espiao::escrever)

        assertEquals(3, espiao.blocos.size, "esperava 3 blocos, veio ${espiao.blocos.size}")
        assertContentEquals(original, espiao.tudoQueFoiEscrito())
    }

    @Test
    fun `depois do ultimo bloco pausa para a impressora drenar`() = runTest {
        val espiao = Espiao()

        EscritaEmBlocos.escrever(payload(10), espiao::pausar, espiao::escrever)

        // Um bloco so, e ainda assim uma pausa: e ela que da a impressora tempo
        // de consumir o buffer ANTES de quem chamou fechar o socket. Sem esta
        // pausa o avanco final -- os ultimos bytes -- morre no fechamento.
        assertEquals(1, espiao.blocos.size)
        assertEquals(listOf(EscritaEmBlocos.DRENAGEM_FINAL_MS), espiao.pausas)
    }

    @Test
    fun `pausa entre os blocos, para o buffer nao lotar no meio da etiqueta`() = runTest {
        val espiao = Espiao()

        EscritaEmBlocos.escrever(
            payload(EscritaEmBlocos.BYTES_POR_BLOCO * 3),
            espiao::pausar,
            espiao::escrever,
        )

        // Tres blocos => duas pausas no meio, e a drenagem por ultimo. Sem as
        // pausas do meio a impressora recebe mais rapido do que imprime e
        // descarta o excedente: foi assim que as escalas 6x-8x se perderam.
        assertEquals(3, espiao.blocos.size)
        assertEquals(
            listOf(
                EscritaEmBlocos.PAUSA_ENTRE_BLOCOS_MS,
                EscritaEmBlocos.PAUSA_ENTRE_BLOCOS_MS,
                EscritaEmBlocos.DRENAGEM_FINAL_MS,
            ),
            espiao.pausas,
        )
    }
}
