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
import kotlinx.coroutines.flow.flow
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
    private val erro: ErroImpressao = ErroImpressao.FalhaAoConectar("dormindo"),
) : PrinterTransport {
    var tentativas = 0
    var bytesRecebidos: ByteArray? = null
    override suspend fun listarDestinos() = listOf(PrinterTarget("00:11:22", "KPrinter"))
    override suspend fun imprimir(destino: PrinterTarget, bytes: ByteArray) {
        tentativas++
        if (tentativas <= falhasAntesDeAceitar) throw erro
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
        vm.imprimir(documento, salvarRascunho = true)
        assertIs<EstadoImpressao.Sucesso>(vm.estado.value)
        assertEquals(listOf<Byte>(1, 2, 3), transporte.bytesRecebidos?.toList())
    }

    @Test
    fun `uma falha de conexao e superada pelo retry automatico`() = runTest {
        val transporte = TransporteFalso(falhasAntesDeAceitar = 1)
        val vm = EtiquetaViewModel(StoreFalsa(), transporte, ::renderizadorFalso)
        vm.imprimir(documento, salvarRascunho = true)
        assertEquals(2, transporte.tentativas)
        assertIs<EstadoImpressao.Sucesso>(vm.estado.value)
    }

    @Test
    fun `duas falhas seguidas viram estado de Falha e param de tentar`() = runTest {
        val transporte = TransporteFalso(falhasAntesDeAceitar = 5)
        val vm = EtiquetaViewModel(StoreFalsa(), transporte, ::renderizadorFalso)
        vm.imprimir(documento, salvarRascunho = true)
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
        vm.imprimir(documento, salvarRascunho = true)
        assertEquals(0, transporte.tentativas)
        val estado = vm.estado.value
        assertIs<EstadoImpressao.Falha>(estado)
        assertIs<ErroImpressao.NenhumaImpressoraSelecionada>(estado.erro)
    }

    @Test
    fun `erro nao repetivel falha na primeira tentativa sem gastar a segunda`() = runTest {
        val transporte = TransporteFalso(
            falhasAntesDeAceitar = 5,
            erro = ErroImpressao.PermissaoNegada,
        )
        val vm = EtiquetaViewModel(StoreFalsa(), transporte, ::renderizadorFalso)
        vm.imprimir(documento, salvarRascunho = true)
        assertEquals(1, transporte.tentativas)   // NAO gastou a segunda
        val estado = vm.estado.value
        assertIs<EstadoImpressao.Falha>(estado)
        assertIs<ErroImpressao.PermissaoNegada>(estado.erro)
    }

    @Test
    fun `falha ao renderizar vira Falha em vez de escapar`() = runTest {
        val vm = EtiquetaViewModel(StoreFalsa(), TransporteFalso()) { _, _ ->
            throw IllegalStateException("documento invalido")
        }
        vm.imprimir(documento, salvarRascunho = true)
        val estado = vm.estado.value
        assertIs<EstadoImpressao.Falha>(estado)
        assertIs<ErroImpressao.FalhaAoPreparar>(estado.erro)
    }

    @Test
    fun `imprimir salva o rascunho`() = runTest {
        val store = StoreFalsa()
        val vm = EtiquetaViewModel(store, TransporteFalso(), ::renderizadorFalso)
        vm.imprimir(documento, salvarRascunho = true)
        assertEquals(documento, store.rascunhoSalvo)
    }

    @Test
    fun `as copias resultam em um envio por copia numa conexao logica`() = runTest {
        val transporte = TransporteFalso()
        val vm = EtiquetaViewModel(StoreFalsa(), transporte, ::renderizadorFalso)
        vm.imprimir(documento.copy(copias = 3), salvarRascunho = true)
        assertEquals(1, transporte.tentativas)
        assertEquals(9, transporte.bytesRecebidos?.size)  // 3 bytes x 3 copias
    }

    @Test
    fun `imprimir com salvarRascunho false nao altera o rascunho salvo`() = runTest {
        // Reimprimir pela lista de salvas ou disparar a etiqueta de teste
        // imprime um documento que NAO e o que o usuario esta compondo. Se
        // isso gravasse por cima do rascunho, um toque destruiria o que a
        // pessoa digitou na tela Compor.
        val store = StoreFalsa()
        store.salvarRascunho(documento)   // simula um rascunho ja existente
        val outraEtiqueta = LabelDocument(listOf(Bloco.Titulo("Bancada B")))

        val vm = EtiquetaViewModel(store, TransporteFalso(), ::renderizadorFalso)
        vm.imprimir(outraEtiqueta, salvarRascunho = false)

        assertIs<EstadoImpressao.Sucesso>(vm.estado.value)
        assertEquals(documento, store.rascunhoSalvo)   // continua o rascunho original
    }

    @Test
    fun `falha ao ler configuracoes vira Falha em vez de travar em Renderizando`() = runTest {
        // A primeira leitura de configuracoes() falha (ex.: linha ausente no
        // banco); a segunda tem sucesso -- simula o erro sendo transiente e
        // prova que o ViewModel nao ficou preso em Renderizando por causa dele.
        var falhar = true
        val store = object : LabelStore {
            private val config = Configuracoes("00:11:22", "KPrinter", avancoFinalMm = 3)
            override fun configuracoes(): Flow<Configuracoes> = flow {
                if (falhar) {
                    falhar = false
                    throw IllegalStateException("linha de configuracao ausente")
                }
                emit(config)
            }
            override fun etiquetasSalvas(): Flow<List<EtiquetaSalva>> = flowOf(emptyList())
            override suspend fun salvarConfiguracoes(configuracoes: Configuracoes) = Unit
            override suspend fun salvarEtiqueta(nome: String, documento: LabelDocument): Long = 1L
            override suspend fun excluirEtiqueta(id: Long) = Unit
            override suspend fun salvarRascunho(documento: LabelDocument) = Unit
            override suspend fun carregarRascunho(): LabelDocument? = null
        }
        val transporte = TransporteFalso()
        val vm = EtiquetaViewModel(store, transporte, ::renderizadorFalso)

        vm.imprimir(documento, salvarRascunho = true)
        val estado = vm.estado.value
        assertIs<EstadoImpressao.Falha>(estado)
        assertIs<ErroImpressao.FalhaAoPreparar>(estado.erro)

        // A guarda de reentrancia so barra estados "em andamento" -- Falha e
        // terminal, entao a MESMA instancia tem que aceitar uma chamada
        // seguinte e efetivamente executa-la (nao so devolver sem fazer nada).
        vm.imprimir(documento, salvarRascunho = true)
        assertIs<EstadoImpressao.Sucesso>(vm.estado.value)
        assertEquals(1, transporte.tentativas)
    }

    @Test
    fun `reportarFalha registra o erro para exibicao`() = runTest {
        val vm = EtiquetaViewModel(StoreFalsa(), TransporteFalso(), ::renderizadorFalso)
        vm.reportarFalha(ErroImpressao.PermissaoNegada)
        val estado = vm.estado.value
        assertIs<EstadoImpressao.Falha>(estado)
        assertIs<ErroImpressao.PermissaoNegada>(estado.erro)
    }

    @Test
    fun `reportarFalha nao sobrescreve uma impressao em andamento`() = runTest {
        // Cenario real: TelaConfiguracoes recarrega destinos() (ex.: reabrindo
        // a aba) enquanto uma impressao disparada de outra aba ainda esta
        // "Enviando" no escopo do app. destinos() falhando nao pode apagar o
        // status visivel dessa impressao em andamento.
        lateinit var vm: EtiquetaViewModel
        val transporte = object : PrinterTransport {
            var chamadas = 0
            override suspend fun listarDestinos(): List<PrinterTarget> = emptyList()
            override suspend fun imprimir(destino: PrinterTarget, bytes: ByteArray) {
                chamadas++
                vm.reportarFalha(ErroImpressao.PermissaoNegada)
            }
        }
        vm = EtiquetaViewModel(StoreFalsa(), transporte, ::renderizadorFalso)
        vm.imprimir(documento, salvarRascunho = true)
        assertIs<EstadoImpressao.Sucesso>(vm.estado.value)
    }

    @Test
    fun `chamada reentrante feita durante o envio e ignorada pela guarda`() = runTest {
        // Simula uma segunda origem (outro botao, outro toque) chamando
        // vm.imprimir() enquanto a primeira impressao ainda esta "Enviando".
        // A chamada reentrante acontece de dentro do proprio transporte.imprimir(),
        // ou seja, no mesmo call stack da primeira chamada, nao em sequencia apos
        // ela terminar — e exatamente o cenario que a guarda de reentrancia em
        // EtiquetaViewModel.imprimir() precisa barrar. Sem a guarda, o transporte
        // seria acionado DUAS vezes aqui; com ela, so uma.
        lateinit var vm: EtiquetaViewModel
        val transporte = object : PrinterTransport {
            var chamadas = 0
            override suspend fun listarDestinos(): List<PrinterTarget> = emptyList()
            override suspend fun imprimir(destino: PrinterTarget, bytes: ByteArray) {
                chamadas++
                if (chamadas == 1) vm.imprimir(documento)
            }
        }
        vm = EtiquetaViewModel(StoreFalsa(), transporte, ::renderizadorFalso)
        vm.imprimir(documento, salvarRascunho = true)
        assertEquals(1, transporte.chamadas)
        assertIs<EstadoImpressao.Sucesso>(vm.estado.value)
    }
}
