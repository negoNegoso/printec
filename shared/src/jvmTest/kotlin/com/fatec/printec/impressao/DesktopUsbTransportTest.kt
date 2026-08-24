package com.fatec.printec.impressao

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopUsbTransportTest {

    @Test
    fun `listar destinos nao estoura mesmo sem impressora instalada`() = runTest {
        val destinos = DesktopUsbTransport().listarDestinos()
        assertTrue(destinos.all { it.id.isNotBlank() && it.nome.isNotBlank() })
    }

    @Test
    fun `imprimir para destino inexistente reporta FalhaAoConectar`() = runTest {
        assertFailsWith<ErroImpressao.FalhaAoConectar> {
            DesktopUsbTransport().imprimir(
                PrinterTarget("impressora-que-nao-existe-xyz", "Fantasma"),
                byteArrayOf(0x1B, 0x40),
            )
        }
    }
}
