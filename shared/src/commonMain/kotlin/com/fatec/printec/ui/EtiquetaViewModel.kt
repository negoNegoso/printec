package com.fatec.printec.ui

import com.fatec.printec.dados.LabelStore
import com.fatec.printec.etiqueta.LabelDocument
import com.fatec.printec.impressao.ErroImpressao
import com.fatec.printec.impressao.PrinterTarget
import com.fatec.printec.impressao.PrinterTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

sealed interface EstadoImpressao {
    data object Ocioso : EstadoImpressao
    data object Renderizando : EstadoImpressao
    data object Conectando : EstadoImpressao
    data object Enviando : EstadoImpressao
    data object Sucesso : EstadoImpressao
    data class Falha(val erro: ErroImpressao) : EstadoImpressao
}

class EtiquetaViewModel(
    private val store: LabelStore,
    private val transporte: PrinterTransport,
    private val renderizar: (LabelDocument, Int) -> ByteArray,
) {
    private val _documento = MutableStateFlow(LabelDocument())
    val documento: StateFlow<LabelDocument> = _documento.asStateFlow()

    private val _estado = MutableStateFlow<EstadoImpressao>(EstadoImpressao.Ocioso)
    val estado: StateFlow<EstadoImpressao> = _estado.asStateFlow()

    fun atualizarDocumento(novo: LabelDocument) {
        _documento.value = novo
    }

    /**
     * Chamado ao trocar de tela. Sem isto, o estado e global ao ViewModel e a
     * mensagem de uma reimpressao feita na aba "Salvas" reaparece na tela de
     * composicao, atribuida a um documento que nao tem nada a ver com ela.
     * So limpa estados terminais — nunca interrompe uma impressao em andamento.
     */
    fun limparEstadoSeConcluido() {
        val atual = _estado.value
        if (atual is EstadoImpressao.Sucesso || atual is EstadoImpressao.Falha) {
            _estado.value = EstadoImpressao.Ocioso
        }
    }

    suspend fun imprimir() {
        val doc = _documento.value
        val config = store.configuracoes().first()

        val id = config.impressoraId
        if (id.isNullOrBlank()) {
            _estado.value = EstadoImpressao.Falha(ErroImpressao.NenhumaImpressoraSelecionada)
            return
        }

        _estado.value = EstadoImpressao.Renderizando
        val umaCopia = try {
            // O rascunho e salvo na impressao, nao a cada tecla.
            store.salvarRascunho(doc)
            renderizar(doc, config.avancoFinalMm)
        } catch (e: CancellationException) {
            throw e   // cancelamento nao e erro de impressao
        } catch (e: ErroImpressao) {
            _estado.value = EstadoImpressao.Falha(e)
            return
        } catch (e: Exception) {
            // Nada pode escapar daqui sem virar estado. Uma excecao crua
            // deixaria a UI presa em "Renderizando" para sempre, sem caminho
            // para Falha e sem o usuario poder tentar de novo.
            _estado.value = EstadoImpressao.Falha(
                ErroImpressao.FalhaAoPreparar(e.message ?: e::class.simpleName.orEmpty()),
            )
            return
        }
        val payload = ByteArray(umaCopia.size * doc.copias.coerceAtLeast(1))
        repeat(doc.copias.coerceAtLeast(1)) { i ->
            umaCopia.copyInto(payload, destinationOffset = i * umaCopia.size)
        }

        val destino = PrinterTarget(id, config.impressoraNome ?: id)

        // Retry unico: a LT-8359 costuma acordar na primeira tentativa e
        // aceitar a segunda. Mais que isso so faria o usuario esperar.
        repeat(2) { tentativa ->
            _estado.value = EstadoImpressao.Conectando
            try {
                _estado.value = EstadoImpressao.Enviando
                transporte.imprimir(destino, payload)
                _estado.value = EstadoImpressao.Sucesso
                return
            } catch (e: ErroImpressao) {
                val ultima = tentativa == 1
                val naoAdiantaRepetir = e !is ErroImpressao.FalhaAoConectar
                if (ultima || naoAdiantaRepetir) {
                    _estado.value = EstadoImpressao.Falha(e)
                    return
                }
            }
        }
    }
}
