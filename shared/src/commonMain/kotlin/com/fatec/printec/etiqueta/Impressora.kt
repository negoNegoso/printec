package com.fatec.printec.etiqueta

/**
 * Constantes físicas da Lintian LT-8359, apuradas pelo autoteste da impressora.
 * DOTS_LARGURA é inferência (32 colunas x 12 dots da Font A) até ser confirmada
 * em papel pela etiqueta de calibração.
 */
object Impressora {
    const val COLUNAS_BASE = 32
    const val DOTS_LARGURA = 384
    const val DOTS_POR_MM = 8
}
