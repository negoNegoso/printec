package com.fatec.printec.impressao

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O roteamento e a unica logica nova do Bluetooth no desktop que NAO e hardware.
 * O I/O serial em si so se verifica com a impressora pareada.
 */
private class TransporteEspiao(private val destinos: List<PrinterTarget>) : PrinterTransport {
    var idRecebido: String? = null
    override suspend fun listarDestinos() = destinos
    override suspend fun imprimir(destino: PrinterTarget, bytes: ByteArray) {
        idRecebido = destino.id
    }
}

class TransporteDesktopTest {

    private fun usb() = TransporteEspiao(listOf(PrinterTarget("Impressora X", "Impressora X")))
    private fun serial() = TransporteEspiao(listOf(PrinterTarget("COM3", "Serial sobre Bluetooth (COM3)")))

    @Test
    fun `a lista mescla os dois transportes com o tipo no id`() = runTest {
        val destinos = TransporteDesktop(usb(), serial()).listarDestinos()
        assertEquals(listOf("usb:Impressora X", "serial:COM3"), destinos.map { it.id })
        // o nome legivel nao e prefixado — e o que o usuario le
        assertTrue(destinos.any { it.nome == "Serial sobre Bluetooth (COM3)" })
    }

    @Test
    fun `destino serial vai para o serial e nao para o usb`() = runTest {
        val u = usb(); val s = serial()
        TransporteDesktop(u, s).imprimir(PrinterTarget("serial:COM3", "x"), byteArrayOf(1))
        assertEquals("COM3", s.idRecebido)   // prefixo removido antes de repassar
        assertEquals(null, u.idRecebido)     // o USB nao foi tocado
    }

    @Test
    fun `destino usb vai para o usb e nao para o serial`() = runTest {
        val u = usb(); val s = serial()
        TransporteDesktop(u, s).imprimir(PrinterTarget("usb:Impressora X", "x"), byteArrayOf(1))
        assertEquals("Impressora X", u.idRecebido)
        assertEquals(null, s.idRecebido)
    }

    @Test
    fun `id sem prefixo, salvo antes do bluetooth existir, ainda vai para o usb`() = runTest {
        val u = usb(); val s = serial()
        TransporteDesktop(u, s).imprimir(PrinterTarget("Impressora X", "x"), byteArrayOf(1))
        assertEquals("Impressora X", u.idRecebido)
        assertEquals(null, s.idRecebido)
    }
}

/**
 * Nao ha impressora pareada nesta maquina, entao lista vazia e aprovacao valida —
 * mesmo criterio do DesktopUsbTransportTest. O que este teste PROVA e outra coisa:
 * que a biblioteca nativa do jSerialComm extrai e carrega. Se ela falhasse,
 * `getCommPorts()` estouraria em vez de devolver lista vazia.
 */
class DesktopSerialTransportTest {

    @Test
    fun `listar portas carrega a biblioteca nativa e devolve entradas bem formadas`() = runTest {
        val destinos = DesktopSerialTransport().listarDestinos()
        assertTrue(destinos.all { it.id.isNotBlank() && it.nome.isNotBlank() })
    }
}
