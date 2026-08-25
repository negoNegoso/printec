package com.fatec.printec.impressao

/**
 * Transporte do desktop: apresenta impressoras USB e portas seriais (Bluetooth)
 * como uma lista so, e roteia a impressao para o lado certo.
 *
 * O tipo viaja no proprio id (`usb:` / `serial:`) porque o roteamento precisa ser
 * deterministico. Adivinhar pelo formato do nome — "parece COM alguma coisa" —
 * funcionaria ate o dia em que uma impressora USB se chamasse algo parecido.
 *
 * As duas implementacoes por tras sao injetaveis para que o roteamento, que e a
 * unica logica aqui que nao depende de hardware, possa ser testado de verdade.
 */
class TransporteDesktop(
    private val usb: PrinterTransport = DesktopUsbTransport(),
    private val serial: PrinterTransport = DesktopSerialTransport(),
) : PrinterTransport {

    override suspend fun listarDestinos(): List<PrinterTarget> =
        usb.listarDestinos().map { it.comPrefixo(PREFIXO_USB) } +
            serial.listarDestinos().map { it.comPrefixo(PREFIXO_SERIAL) }

    override suspend fun imprimir(destino: PrinterTarget, bytes: ByteArray) {
        val (transporte, idOriginal) = rotear(destino.id)
        transporte.imprimir(destino.copy(id = idOriginal), bytes)
    }

    private fun rotear(id: String): Pair<PrinterTransport, String> = when {
        id.startsWith(PREFIXO_SERIAL) -> serial to id.removePrefix(PREFIXO_SERIAL)
        id.startsWith(PREFIXO_USB) -> usb to id.removePrefix(PREFIXO_USB)
        // Configuracao salva antes de o Bluetooth existir: era USB, por definicao.
        // Sem isto, quem ja tinha uma impressora escolhida veria a impressao falhar
        // sem entender por que.
        else -> usb to id
    }

    private fun PrinterTarget.comPrefixo(prefixo: String) = copy(id = prefixo + id)

    private companion object {
        const val PREFIXO_USB = "usb:"
        const val PREFIXO_SERIAL = "serial:"
    }
}
