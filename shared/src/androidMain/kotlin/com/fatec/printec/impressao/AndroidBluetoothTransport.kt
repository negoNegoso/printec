package com.fatec.printec.impressao

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Bluetooth clássico (SPP) sobre dispositivos JA PAREADOS.
 *
 * Nao ha descoberta nem pareamento no app, de proposito: descoberta na API 28
 * exigiria permissao de localizacao em runtime, e a impressora — que hiberna em
 * 10 minutos — nao aparece na varredura quando esta dormindo. A lista de
 * pareados a mostra mesmo desligada.
 */
class AndroidBluetoothTransport(private val context: Context) : PrinterTransport {

    private val adaptador: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    override suspend fun listarDestinos(): List<PrinterTarget> = withContext(Dispatchers.IO) {
        exigirPermissao()
        val adaptador = adaptador ?: return@withContext emptyList()
        if (!adaptador.isEnabled) return@withContext emptyList()
        dispositivosPareados(adaptador).map { PrinterTarget(it.address, it.name ?: it.address) }
    }

    override suspend fun imprimir(destino: PrinterTarget, bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            exigirPermissao()
            val adaptador = adaptador ?: throw ErroImpressao.FalhaAoConectar("Bluetooth indisponível")
            // Bluetooth desligado e um diagnostico diferente de "impressora nao
            // pareada": pareamento sobrevive ao desligar o radio, entao sem esta
            // checagem o usuario veria "nao pareada" com a impressora pareada
            // havia meses, so porque o Bluetooth do aparelho esta desligado.
            if (!adaptador.isEnabled) throw ErroImpressao.FalhaAoConectar("Bluetooth desligado")
            val dispositivo = dispositivosPareados(adaptador)
                .firstOrNull { it.address == destino.id }
                ?: throw ErroImpressao.NaoPareada

            val socket = try {
                dispositivo.createRfcommSocketToServiceRecord(UUID_SPP)
            } catch (e: Exception) {
                throw ErroImpressao.FalhaAoConectar(e.message.orEmpty())
            }

            try {
                socket.connect()
            } catch (e: Exception) {
                runCatching { socket.close() }
                throw ErroImpressao.FalhaAoConectar(e.message.orEmpty())
            }

            try {
                socket.outputStream.use { saida ->
                    // Nao basta um write() unico: a impressora nao faz controle
                    // de fluxo e descarta o que nao couber no buffer dela.
                    EscritaEmBlocos.escrever(bytes, ::delay) { bloco ->
                        saida.write(bloco)
                        saida.flush()
                    }
                }
            } catch (e: Exception) {
                throw ErroImpressao.FalhaAoEscrever(e.message.orEmpty())
            } finally {
                runCatching { socket.close() }
            }
        }

    // Isolado porque bondedDevices pode lancar SecurityException em alguns
    // fabricantes mesmo com a permissao concedida (checagem de permissao e
    // acesso de fato nao sao atomicos) -- sem este try/catch, essa excecao
    // nao tipada derrubava o app.
    private fun dispositivosPareados(adaptador: BluetoothAdapter) = try {
        adaptador.bondedDevices.orEmpty()
    } catch (e: SecurityException) {
        throw ErroImpressao.FalhaAoConectar(e.message.orEmpty())
    }

    private fun exigirPermissao() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return  // API 28: permissao de instalacao
        // Context.checkSelfPermission existe desde a API 23 e este ramo so roda
        // na API 31+, entao nao ha motivo para depender do androidx-core so por
        // causa do ContextCompat.
        val concedida = context.checkSelfPermission(
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
        if (!concedida) throw ErroImpressao.PermissaoNegada
    }

    companion object {
        val UUID_SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
