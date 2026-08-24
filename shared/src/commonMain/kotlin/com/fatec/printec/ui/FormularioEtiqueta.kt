package com.fatec.printec.ui

import com.fatec.printec.etiqueta.Bloco
import com.fatec.printec.etiqueta.LabelDocument

data class CamposDoFormulario(
    val titulo: String = "",
    val linhas: List<String> = listOf(""),
    val qr: String = "",
    val copias: Int = 1,
)

fun CamposDoFormulario.paraDocumento(): LabelDocument {
    val blocos = buildList {
        if (titulo.isNotBlank()) add(Bloco.Titulo(titulo))
        linhas.filter { it.isNotBlank() }.forEach { add(Bloco.Linha(it)) }
        if (qr.isNotBlank()) add(Bloco.Qr(qr))
    }
    return LabelDocument(blocos, copias)
}

fun LabelDocument.paraCampos(): CamposDoFormulario = CamposDoFormulario(
    titulo = blocos.filterIsInstance<Bloco.Titulo>().firstOrNull()?.texto.orEmpty(),
    linhas = blocos.filterIsInstance<Bloco.Linha>().map { it.texto }.ifEmpty { listOf("") },
    qr = blocos.filterIsInstance<Bloco.Qr>().firstOrNull()?.conteudo.orEmpty(),
    copias = copias,
)
