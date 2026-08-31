package com.fatec.printec.impressao

import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Impressao por porta serial no desktop — que e como o Bluetooth chega ate aqui.
 *
 * A JVM nao tem Bluetooth nativo. No Windows, parear a LT-8359 cria uma porta
 * COM virtual (perfil SPP), e escrever nessa porta e escrever na impressora.
 * Da otica do usuario e "conectar por Bluetooth"; deste lado e serial.
 *
 * Isso tambem explica por que nao ha descoberta nem pareamento aqui, pelo mesmo
 * motivo do Android: quem pareia e o sistema operacional, uma vez.
 */
class DesktopSerialTransport : PrinterTransport {

    override suspend fun listarDestinos(): List<PrinterTarget> = withContext(Dispatchers.IO) {
        portas().map {
            // O nome descritivo do Windows para uma porta pareada e literalmente
            // "Standard Serial over Bluetooth link (COM3)", entao o usuario
            // reconhece a impressora sem precisar decorar numero de COM.
            PrinterTarget(id = it.systemPortName, nome = it.descriptivePortName)
        }
    }

    override suspend fun imprimir(destino: PrinterTarget, bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            val porta = portas().firstOrNull { it.systemPortName == destino.id }
                ?: throw ErroImpressao.FalhaAoConectar("Porta '${destino.nome}' não encontrada")

            // Em porta COM virtual de SPP estes parametros sao ignorados — quem
            // manda e o enlace Bluetooth. Ficam fixos ate que algum teste com
            // hardware mostre o contrario.
            porta.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
            porta.setComPortTimeouts(SerialPort.TIMEOUT_WRITE_BLOCKING, 0, TIMEOUT_ESCRITA_MS)

            if (!porta.openPort()) {
                throw ErroImpressao.FalhaAoConectar("Não foi possível abrir ${destino.nome}")
            }

            // Abrir, escrever, fechar a cada trabalho: a impressora hiberna em 10
            // minutos, e manter a porta aberta produziria "o app diz conectado e a
            // impressora esta desligada".
            try {
                val saida = porta.outputStream
                // Mesma razao do transporte Android: escrever tudo de uma vez
                // estoura o buffer da impressora e perde a cauda do documento.
                EscritaEmBlocos.escrever(bytes, ::delay) { bloco ->
                    saida.write(bloco)
                    saida.flush()
                }
            } catch (e: Exception) {
                throw ErroImpressao.FalhaAoEscrever(e.message ?: e::class.simpleName.orEmpty())
            } finally {
                porta.closePort()
            }
        }

    /** Nenhuma excecao crua pode escapar: a UI so sabe tratar ErroImpressao. */
    private fun portas(): List<SerialPort> = try {
        SerialPort.getCommPorts().toList()
    } catch (e: Exception) {
        throw ErroImpressao.FalhaAoConectar(e.message ?: e::class.simpleName.orEmpty())
    }

    private companion object {
        const val TIMEOUT_ESCRITA_MS = 5_000
    }
}
