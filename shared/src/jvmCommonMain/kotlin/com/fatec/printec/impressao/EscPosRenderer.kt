package com.fatec.printec.impressao

import com.fatec.printec.etiqueta.Alinhamento
import com.fatec.printec.etiqueta.Bloco
import com.fatec.printec.etiqueta.Impressora
import com.fatec.printec.etiqueta.LabelDocument
import com.fatec.printec.etiqueta.Pc860
import com.fatec.printec.etiqueta.QuebraDeLinha
import com.fatec.printec.etiqueta.normalizado
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
        // GS ( k a mao: o QRCode do escpos-coffee dimensiona o payload em
        // CARACTERES e escreve bytes, truncando qualquer conteudo nao-ASCII em
        // silencio. QR guarda bytes; UTF-8 e o que os leitores esperam.
        comando(ESC, 0x61, 1)   // centralizado, mesmo ESC a das linhas

        val dados = bloco.conteudo.encodeToByteArray()

        comando(GS, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00)   // modelo 2
        comando(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, bloco.tamanhoModulo)
        comando(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x31)         // correcao M

        val n = dados.size + 3
        comando(GS, 0x28, 0x6B, n and 0xFF, (n shr 8) and 0xFF, 0x31, 0x50, 0x30)
        write(dados)

        comando(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30)         // imprimir
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
