package com.fatec.printec.impressao

/**
 * Politica de vazao para escrever um payload ESC/POS numa impressora que NAO
 * faz controle de fluxo.
 *
 * A LT-8359 aceita pelo enlace Bluetooth muito mais rapido do que o cabecote
 * termico imprime. Quando o buffer de entrada dela lota, ela nao segura o
 * remetente: descarta o excedente em silencio. O sintoma nao e um erro, e uma
 * etiqueta subtilmente errada -- comandos comidos no meio (as escalas grandes
 * saiam em 1x) e, pior, o fim do documento perdido, que e justamente onde mora
 * o avanco final.
 *
 * Por isso a escrita e fatiada com pausa entre os blocos, e termina com uma
 * pausa de drenagem: quem chama fecha a conexao logo depois, e fechar em cima
 * de bytes ainda nao consumidos os joga fora.
 */
object EscritaEmBlocos {

    const val BYTES_POR_BLOCO = 256

    const val PAUSA_ENTRE_BLOCOS_MS = 20L

    /**
     * Cobre o maior avanco configuravel (30 mm a ~50 mm/s na LT-8359) com folga.
     * E este o numero a aumentar se a cauda da impressao voltar a sumir.
     */
    const val DRENAGEM_FINAL_MS = 600L

    suspend fun escrever(
        bytes: ByteArray,
        pausar: suspend (Long) -> Unit,
        escrever: (ByteArray) -> Unit,
    ) {
        var inicio = 0
        while (inicio < bytes.size) {
            if (inicio > 0) pausar(PAUSA_ENTRE_BLOCOS_MS)
            val fim = minOf(inicio + BYTES_POR_BLOCO, bytes.size)
            escrever(bytes.copyOfRange(inicio, fim))
            inicio = fim
        }
        pausar(DRENAGEM_FINAL_MS)
    }
}
