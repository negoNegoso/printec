package com.fatec.printec.ui

import com.fatec.printec.dados.LabelStore
import com.fatec.printec.etiqueta.LabelDocument
import com.fatec.printec.impressao.ErroImpressao
import com.fatec.printec.impressao.PrinterTarget
import com.fatec.printec.impressao.PrinterTransport
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

    suspend fun imprimir() {
        val doc = _documento.value
        val config = store.configuracoes().first()

        val id = config.impressoraId
        if (id.isNullOrBlank()) {
            _estado.value = EstadoImpressao.Falha(ErroImpressao.NenhumaImpressoraSelecionada)
            return
        }

        // O rascunho e salvo na impressao, nao a cada tecla.
        store.salvarRascunho(doc)

        _estado.value = EstadoImpressao.Renderizando
        val umaCopia = renderizar(doc, config.avancoFinalMm)
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
