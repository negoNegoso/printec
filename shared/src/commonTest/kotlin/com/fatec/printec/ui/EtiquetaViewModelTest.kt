package com.fatec.printec.ui

import com.fatec.printec.dados.Configuracoes
import com.fatec.printec.dados.EtiquetaSalva
import com.fatec.printec.dados.LabelStore
import com.fatec.printec.etiqueta.Bloco
import com.fatec.printec.etiqueta.LabelDocument
import com.fatec.printec.impressao.ErroImpressao
import com.fatec.printec.impressao.PrinterTarget
import com.fatec.printec.impressao.PrinterTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class StoreFalsa(
    private val config: Configuracoes = Configuracoes("00:11:22", "KPrinter", avancoFinalMm = 3),
) : LabelStore {
    var rascunhoSalvo: LabelDocument? = null
    override fun configuracoes(): Flow<Configuracoes> = MutableStateFlow(config)
    override fun etiquetasSalvas(): Flow<List<EtiquetaSalva>> = flowOf(emptyList())
    override suspend fun salvarConfiguracoes(configuracoes: Configuracoes) = Unit
    override suspend fun salvarEtiqueta(nome: String, documento: LabelDocument): Long = 1L
    override suspend fun excluirEtiqueta(id: Long) = Unit
    override suspend fun salvarRascunho(documento: LabelDocument) { rascunhoSalvo = documento }
    override suspend fun carregarRascunho(): LabelDocument? = null
}

private class TransporteFalso(
    private val falhasAntesDeAceitar: Int = 0,
) : PrinterTransport {
    var tentativas = 0
    var bytesRecebidos: ByteArray? = null
    override suspend fun listarDestinos() = listOf(PrinterTarget("00:11:22", "KPrinter"))
    override suspend fun imprimir(destino: PrinterTarget, bytes: ByteArray) {
        tentativas++
        if (tentativas <= falhasAntesDeAceitar) throw ErroImpressao.FalhaAoConectar("dormindo")
        bytesRecebidos = bytes
    }
}

class EtiquetaViewModelTest {

    private val documento = LabelDocument(listOf(Bloco.Titulo("Bancada A")))
    private fun renderizadorFalso(doc: LabelDocument, avanco: Int) = byteArrayOf(1, 2, 3)

    @Test
    fun `impressao bem sucedida termina em Sucesso`() = runTest {
        val transporte = TransporteFalso()
        val vm = EtiquetaViewModel(StoreFalsa(), transporte, ::renderizadorFalso)
        vm.atualizarDocumento(documento)
        vm.imprimir()
        assertIs<EstadoImpressao.Sucesso>(vm.estado.value)
        assertEquals(listOf<Byte>(1, 2, 3), transporte.bytesRecebidos?.toList())
    }

    @Test
    fun `uma falha de conexao e superada pelo retry automatico`() = runTest {
        val transporte = TransporteFalso(falhasAntesDeAceitar = 1)
        val vm = EtiquetaViewModel(StoreFalsa(), transporte, ::renderizadorFalso)
        vm.atualizarDocumento(documento)
        vm.imprimir()
        assertEquals(2, transporte.tentativas)
        assertIs<EstadoImpressao.Sucesso>(vm.estado.value)
    }

    @Test
    fun `duas falhas seguidas viram estado de Falha e param de tentar`() = runTest {
        val transporte = TransporteFalso(falhasAntesDeAceitar = 5)
        val vm = EtiquetaViewModel(StoreFalsa(), transporte, ::renderizadorFalso)
        vm.atualizarDocumento(documento)
        vm.imprimir()
        assertEquals(2, transporte.tentativas)
        val estado = vm.estado.value
        assertIs<EstadoImpressao.Falha>(estado)
        assertIs<ErroImpressao.FalhaAoConectar>(estado.erro)
    }

    @Test
    fun `sem impressora configurada nao chega a tentar conectar`() = runTest {
        val transporte = TransporteFalso()
        val vm = EtiquetaViewModel(
            StoreFalsa(Configuracoes(impressoraId = null)), transporte, ::renderizadorFalso,
        )
        vm.atualizarDocumento(documento)
        vm.imprimir()
        assertEquals(0, transporte.tentativas)
        val estado = vm.estado.value
        assertIs<EstadoImpressao.Falha>(estado)
        assertIs<ErroImpressao.NenhumaImpressoraSelecionada>(estado.erro)
    }

    @Test
    fun `imprimir salva o rascunho`() = runTest {
        val store = StoreFalsa()
        val vm = EtiquetaViewModel(store, TransporteFalso(), ::renderizadorFalso)
        vm.atualizarDocumento(documento)
        vm.imprimir()
        assertEquals(documento, store.rascunhoSalvo)
    }

    @Test
    fun `as copias resultam em um envio por copia numa conexao logica`() = runTest {
        val transporte = TransporteFalso()
        val vm = EtiquetaViewModel(StoreFalsa(), transporte, ::renderizadorFalso)
        vm.atualizarDocumento(documento.copy(copias = 3))
        vm.imprimir()
        assertEquals(1, transporte.tentativas)
        assertEquals(9, transporte.bytesRecebidos?.size)  // 3 bytes x 3 copias
    }
}
