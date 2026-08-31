package com.fatec.printec.ui

import com.fatec.printec.etiqueta.Alinhamento
import com.fatec.printec.etiqueta.Bloco
import com.fatec.printec.etiqueta.LabelDocument

/**
 * Uma linha do formulario: o texto e como ele se posiciona no papel.
 *
 * O alinhamento precisa morar aqui, e nao so no Bloco.Linha, porque o
 * formulario e o unico lugar onde ele pode ser escolhido. Enquanto `linhas` era
 * List<String>, o banco persistia a coluna `alinhamento` e o renderizador sabia
 * emitir ESC a -- mas nao havia como produzir nada diferente de ESQUERDA, e
 * recarregar uma etiqueta salva reendireitava tudo em silencio.
 */
data class LinhaDoFormulario(
    val texto: String = "",
    val alinhamento: Alinhamento = Alinhamento.ESQUERDA,
)

data class CamposDoFormulario(
    val titulo: String = "",
    val linhas: List<LinhaDoFormulario> = listOf(LinhaDoFormulario()),
    val qr: String = "",
    val copias: Int = 1,
)

fun CamposDoFormulario.paraDocumento(): LabelDocument {
    val blocos = buildList {
        if (titulo.isNotBlank()) add(Bloco.Titulo(titulo))
        linhas.filter { it.texto.isNotBlank() }
            .forEach { add(Bloco.Linha(it.texto, alinhamento = it.alinhamento)) }
        if (qr.isNotBlank()) add(Bloco.Qr(qr))
    }
    return LabelDocument(blocos, copias)
}

fun LabelDocument.paraCampos(): CamposDoFormulario = CamposDoFormulario(
    titulo = blocos.filterIsInstance<Bloco.Titulo>().firstOrNull()?.texto.orEmpty(),
    linhas = blocos.filterIsInstance<Bloco.Linha>()
        .map { LinhaDoFormulario(it.texto, it.alinhamento) }
        .ifEmpty { listOf(LinhaDoFormulario()) },
    qr = blocos.filterIsInstance<Bloco.Qr>().firstOrNull()?.conteudo.orEmpty(),
    copias = copias,
)
