package com.fatec.printec.etiqueta

/**
 * Regra de quebra COMPARTILHADA entre o preview e o renderizador ESC/POS.
 * Se as duas divergirem, o WYSIWYG vira mentira — por isso mora num lugar só.
 */
object QuebraDeLinha {

    fun colunasPara(escala: Int): Int = Impressora.COLUNAS_BASE / escala.coerceAtLeast(1)

    fun quebrar(texto: String, colunas: Int): List<String> {
        if (texto.isEmpty()) return listOf("")

        val linhas = mutableListOf<String>()
        var restante = texto

        while (restante.isNotEmpty()) {
            if (restante.length <= colunas) {
                linhas += restante
                break
            }
            val janela = restante.substring(0, colunas + 1)
            val corte = janela.lastIndexOf(' ')
            if (corte <= 0) {
                // Palavra maior que a linha: corta na largura em vez de descartar.
                linhas += restante.substring(0, colunas)
                restante = restante.substring(colunas)
            } else {
                linhas += restante.substring(0, corte)
                restante = restante.substring(corte + 1)
            }
        }
        return linhas
    }
}
