package com.fatec.printec.impressao

import com.fatec.printec.etiqueta.Alinhamento
import com.fatec.printec.etiqueta.Bloco
import com.fatec.printec.etiqueta.Impressora
import com.fatec.printec.etiqueta.LabelDocument
import com.fatec.printec.etiqueta.Pc860
import com.fatec.printec.etiqueta.QuebraDeLinha
import com.fatec.printec.etiqueta.normalizado
import com.github.anastaciocintra.escpos.EscPos
import com.github.anastaciocintra.escpos.EscPosConst
import com.github.anastaciocintra.escpos.barcode.QRCode
import java.io.ByteArrayOutputStream

object EscPosRenderer {

    private const val ESC = 0x1B
    private const val GS = 0x1D
    private const val LF = 0x0A

    fun renderizar(documento: LabelDocument, avancoFinalMm: Int): ByteArray {
        val saida = ByteArrayOutputStream()

        saida.comando(ESC, 0x40)              // ESC @  — inicializa
        saida.comando(ESC, 0x74, 0x03)        // ESC t 3 — PC860, enviada sempre

        documento.blocos.map { it.normalizado() }.forEach { bloco ->
            when (bloco) {
                is Bloco.Linha -> saida.escreverLinha(bloco)
                is Bloco.Qr -> saida.escreverQr(bloco)
                is Bloco.Avanco -> saida.avancar(bloco.milimetros)
                is Bloco.Titulo -> error("normalizado() deveria ter convertido Titulo em Linha")
            }
        }

        if (avancoFinalMm > 0) saida.avancar(avancoFinalMm)
        return saida.toByteArray()
    }

    private fun ByteArrayOutputStream.escreverLinha(bloco: Bloco.Linha) {
        comando(ESC, 0x61, bloco.alinhamento.codigo())
        comando(GS, 0x21, tamanho(bloco.escala))
        comando(ESC, 0x45, if (bloco.negrito) 1 else 0)

        val colunas = QuebraDeLinha.colunasPara(bloco.escala)
        QuebraDeLinha.quebrar(bloco.texto, colunas).forEach { linha ->
            write(Pc860.codificar(linha).bytes)
            write(LF)
        }
    }

    private fun ByteArrayOutputStream.escreverQr(bloco: Bloco.Qr) {
        // Delegado ao escpos-coffee: GS ( k encadeia quatro comandos com
        // comprimento little-endian, e errar isso a mao e facil demais.
        val escpos = EscPos(this)
        val qr = QRCode().apply {
            setSize(bloco.tamanhoModulo)
            setJustification(EscPosConst.Justification.Center)
        }
        escpos.write(qr, bloco.conteudo)
        escpos.flush()
    }

    private fun ByteArrayOutputStream.avancar(milimetros: Int) {
        val dots = (milimetros * Impressora.DOTS_POR_MM).coerceIn(0, 255)
        comando(ESC, 0x4A, dots)             // ESC J n
    }

    private fun ByteArrayOutputStream.comando(vararg bytes: Int) {
        bytes.forEach { write(it) }
    }

    private fun Alinhamento.codigo(): Int = when (this) {
        Alinhamento.ESQUERDA -> 0
        Alinhamento.CENTRO -> 1
        Alinhamento.DIREITA -> 2
    }

    /** GS ! n — nibble alto = largura, nibble baixo = altura, ambos 0-based. */
    private fun tamanho(escala: Int): Int {
        val n = (escala - 1).coerceIn(0, 7)
        return (n shl 4) or n
    }
}
