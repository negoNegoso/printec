package com.fatec.printec.impressao

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.print.DocFlavor
import javax.print.PrintService
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
        servicos().map { PrinterTarget(id = it.name, nome = it.name) }
    }

    override suspend fun imprimir(destino: PrinterTarget, bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            val servico = servicos().firstOrNull { it.name == destino.id }
                ?: throw ErroImpressao.FalhaAoConectar("Impressora '${destino.nome}' não encontrada")

            try {
                val doc = SimpleDoc(bytes, DocFlavor.BYTE_ARRAY.AUTOSENSE, null)
                servico.createPrintJob().print(doc, HashPrintRequestAttributeSet())
            } catch (e: Exception) {
                throw ErroImpressao.FalhaAoEscrever(e.message ?: e::class.simpleName.orEmpty())
            }
        }

    /**
     * O subsistema de impressao do Windows estoura sozinho em casos reais —
     * spooler em mau estado, entrada de driver corrompida. Nenhuma excecao crua
     * pode escapar deste transporte: a UI so sabe tratar ErroImpressao, e o
     * ViewModel da Tarefa 8 captura exatamente esse tipo. Uma excecao nao tipada
     * atravessaria a corrotina e derrubaria o app.
     */
    private fun servicos(): List<PrintService> = try {
        PrintServiceLookup.lookupPrintServices(null, null).toList()
    } catch (e: Exception) {
        throw ErroImpressao.FalhaAoConectar(e.message ?: e::class.simpleName.orEmpty())
    }
}
