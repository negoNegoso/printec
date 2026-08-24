package com.fatec.printec.impressao

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.print.DocFlavor
import javax.print.PrintServiceLookup
import javax.print.SimpleDoc
import javax.print.attribute.HashPrintRequestAttributeSet

/**
 * Envia bytes crus pelo spooler do Windows. Se o driver da impressora insistir
 * em converter a impressao em imagem, o plano B da spec e escrever direto na
 * porta COM/USB com jSerialComm — nao implementado ate que se prove necessario.
 */
class DesktopUsbTransport : PrinterTransport {

    override suspend fun listarDestinos(): List<PrinterTarget> = withContext(Dispatchers.IO) {
        PrintServiceLookup.lookupPrintServices(null, null)
            .map { PrinterTarget(id = it.name, nome = it.name) }
    }

    override suspend fun imprimir(destino: PrinterTarget, bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            val servico = PrintServiceLookup.lookupPrintServices(null, null)
                .firstOrNull { it.name == destino.id }
                ?: throw ErroImpressao.FalhaAoConectar("Impressora '${destino.nome}' não encontrada")

            try {
                val doc = SimpleDoc(bytes, DocFlavor.BYTE_ARRAY.AUTOSENSE, null)
                servico.createPrintJob().print(doc, HashPrintRequestAttributeSet())
            } catch (e: Exception) {
                throw ErroImpressao.FalhaAoEscrever(e.message ?: e::class.simpleName.orEmpty())
            }
        }
}
