package com.fatec.printec.impressao

data class PrinterTarget(val id: String, val nome: String)

/**
 * Falha de impressora e fluxo normal neste app, nao excecao rara: a LT-8359
 * hiberna sozinha em 10 minutos. Por isso os erros sao tipados — cada um vira
 * uma mensagem com uma acao concreta na UI.
 */
sealed class ErroImpressao(mensagem: String) : Exception(mensagem) {
    data object NaoPareada : ErroImpressao("Impressora não pareada")
    data object PermissaoNegada : ErroImpressao("Permissão de Bluetooth necessária")
    data object NenhumaImpressoraSelecionada : ErroImpressao("Escolha uma impressora")
    data class FalhaAoConectar(val causa: String) : ErroImpressao(
        "A impressora pode estar desligada — ela hiberna após 10 minutos",
    )
    data class FalhaAoEscrever(val causa: String) : ErroImpressao(
        "A conexão caiu durante a impressão",
    )
    /** Falha ANTES de falar com a impressora: renderizar, salvar rascunho. */
    data class FalhaAoPreparar(val causa: String) : ErroImpressao(
        "Não foi possível preparar a etiqueta",
    )
}

interface PrinterTransport {
    suspend fun listarDestinos(): List<PrinterTarget>

    /** Abre, escreve e fecha. Nunca mantém a conexão aberta entre trabalhos. */
    suspend fun imprimir(destino: PrinterTarget, bytes: ByteArray)
}
