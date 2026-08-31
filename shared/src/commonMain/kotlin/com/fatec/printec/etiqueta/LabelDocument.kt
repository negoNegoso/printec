package com.fatec.printec.etiqueta

enum class Alinhamento { ESQUERDA, CENTRO, DIREITA }

sealed interface Bloco {
    /** Açúcar para Linha(escala = 2, CENTRO). Ver [normalizado] quanto ao negrito. */
    data class Titulo(val texto: String) : Bloco

    data class Linha(
        val texto: String,
        val escala: Int = 1,
        val alinhamento: Alinhamento = Alinhamento.ESQUERDA,
        val negrito: Boolean = false,
    ) : Bloco

    data class Qr(val conteudo: String, val tamanhoModulo: Int = 6) : Bloco

    data class Avanco(val milimetros: Int) : Bloco
}

data class LabelDocument(
    val blocos: List<Bloco> = emptyList(),
    val copias: Int = 1,
)

/**
 * Normaliza Titulo para Linha, para que renderizador e preview tratem um caso a menos.
 *
 * Sem negrito de proposito. A LT-8359 imprime enfase como duplo impacto
 * deslocado na horizontal; em largura 2x esse segundo impacto cai dentro do
 * proprio glifo, preenche o vao entre as colunas de dots e o titulo sai
 * borrado. Na etiqueta de calibracao isso aparece lado a lado: "CALIBRACAO"
 * (2x + negrito) ilegivel logo acima de "escala 2x" (2x puro) nitido.
 */
fun Bloco.normalizado(): Bloco = when (this) {
    is Bloco.Titulo -> Bloco.Linha(
        texto = texto,
        escala = 2,
        alinhamento = Alinhamento.CENTRO,
    )
    else -> this
}
