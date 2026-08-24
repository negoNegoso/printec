package com.fatec.printec.impressao

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
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
        adaptador.bondedDevices.orEmpty().map { PrinterTarget(it.address, it.name ?: it.address) }
    }

    override suspend fun imprimir(destino: PrinterTarget, bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            exigirPermissao()
            val adaptador = adaptador ?: throw ErroImpressao.FalhaAoConectar("Bluetooth indisponível")
            val dispositivo = adaptador.bondedDevices.orEmpty()
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
                    saida.write(bytes)
                    saida.flush()
                }
            } catch (e: Exception) {
                throw ErroImpressao.FalhaAoEscrever(e.message.orEmpty())
            } finally {
                runCatching { socket.close() }
            }
        }

    private fun exigirPermissao() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return  // API 28: permissao de instalacao
        val concedida = ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
        if (!concedida) throw ErroImpressao.PermissaoNegada
    }

    companion object {
        val UUID_SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
