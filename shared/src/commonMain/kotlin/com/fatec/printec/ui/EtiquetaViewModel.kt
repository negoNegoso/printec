package com.fatec.printec.ui

import com.fatec.printec.dados.LabelStore
import com.fatec.printec.etiqueta.LabelDocument
import com.fatec.printec.impressao.ErroImpressao
import com.fatec.printec.impressao.PrinterTarget
import com.fatec.printec.impressao.PrinterTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
    private val _estado = MutableStateFlow<EstadoImpressao>(EstadoImpressao.Ocioso)
    val estado: StateFlow<EstadoImpressao> = _estado.asStateFlow()

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

    /**
     * @param documento o que imprimir — vem do chamador, nunca do proprio
     *   ViewModel. Reimprimir uma etiqueta salva ou disparar a etiqueta de
     *   teste nao pode sobrescrever o que o usuario esta compondo na outra
     *   tela (ver [salvarRascunho]).
     * @param salvarRascunho so `true` quando [documento] E o que o usuario
     *   estava digitando na tela Compor. Reimpressao pela lista de salvas e a
     *   etiqueta de teste imprimem um documento que NAO e o rascunho do
     *   usuario e por isso nao podem gravar por cima dele.
     */
    suspend fun imprimir(documento: LabelDocument, salvarRascunho: Boolean = false) {
        // Guarda de reentrancia. A UI desabilita o botao IMPRIMIR, mas ha mais
        // de um ponto de entrada — compor, reimprimir pela lista de salvas, e a
        // etiqueta de teste nas configuracoes — e nem todos tem botao para
        // desabilitar. Duas impressoes concorrentes gastam papel de verdade.
        //
        // O estado e marcado Renderizando ANTES de qualquer suspensao —
        // inclusive a leitura de configuracoes() logo abaixo. Se a marcacao
        // esperasse a config chegar, duas chamadas concorrentes fariam a
        // checagem acima antes que qualquer uma delas marcasse o estado, e as
        // duas passariam pela guarda.
        val emAndamento = _estado.value
        if (emAndamento is EstadoImpressao.Renderizando ||
            emAndamento is EstadoImpressao.Conectando ||
            emAndamento is EstadoImpressao.Enviando
        ) return
        _estado.value = EstadoImpressao.Renderizando

        val config = store.configuracoes().first()

        val id = config.impressoraId
        if (id.isNullOrBlank()) {
            _estado.value = EstadoImpressao.Falha(ErroImpressao.NenhumaImpressoraSelecionada)
            return
        }

        val umaCopia = try {
            // O rascunho e salvo na impressao, nao a cada tecla — e somente
            // quando quem chamou pediu (ver documentacao do parametro acima).
            if (salvarRascunho) store.salvarRascunho(documento)
            renderizar(documento, config.avancoFinalMm)
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
        val payload = ByteArray(umaCopia.size * documento.copias.coerceAtLeast(1))
        repeat(documento.copias.coerceAtLeast(1)) { i ->
            umaCopia.copyInto(payload, destinationOffset = i * umaCopia.size)
        }

        val destino = PrinterTarget(id, config.impressoraNome ?: id)

        // Retry unico: a LT-8359 costuma acordar na primeira tentativa e
        // aceitar a segunda. Mais que isso so faria o usuario esperar.
        repeat(2) { tentativa ->
            // So Conectando: o transporte conecta e escreve numa chamada so,
            // entao nao ha suspensao entre marcar Conectando e marcar Enviando
            // -- nenhum coletor jamais veria o estado intermediario. Conectar
            // e a fase que de fato falha (impressora hibernada), entao e ela
            // que fica visivel durante toda a chamada.
            _estado.value = EstadoImpressao.Conectando
            try {
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
                // A impressora hibernada tem mais chance de ter acordado se
                // damos um instante antes de repetir. Tentar de novo no mesmo
                // instante e o pior caso justamente para esse cenario -- e e
                // essa politica que o teste de hardware vai avaliar.
                delay(1_000)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nada nao-tipado pode escapar do laco de envio: uma excecao
                // crua aqui deixaria a UI presa em "Conectando" para sempre.
                _estado.value = EstadoImpressao.Falha(
                    ErroImpressao.FalhaAoEscrever(e.message ?: e::class.simpleName.orEmpty()),
                )
                return
            }
        }
    }
}
