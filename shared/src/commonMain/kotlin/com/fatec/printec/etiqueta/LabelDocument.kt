package com.fatec.printec.etiqueta

enum class Alinhamento { ESQUERDA, CENTRO, DIREITA }

sealed interface Bloco {
    /** Açúcar para Linha(escala = 2, CENTRO, negrito = true). */
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

/** Normaliza Titulo para Linha, para que renderizador e preview tratem um caso a menos. */
fun Bloco.normalizado(): Bloco = when (this) {
    is Bloco.Titulo -> Bloco.Linha(
        texto = texto,
        escala = 2,
        alinhamento = Alinhamento.CENTRO,
        negrito = true,
    )
    else -> this
}
