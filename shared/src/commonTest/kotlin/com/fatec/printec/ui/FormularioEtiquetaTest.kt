package com.fatec.printec.ui

import com.fatec.printec.etiqueta.Alinhamento
import com.fatec.printec.etiqueta.Bloco
import com.fatec.printec.etiqueta.LabelDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormularioEtiquetaTest {

    @Test
    fun `campos preenchidos viram titulo linhas e qr nesta ordem`() {
        val campos = CamposDoFormulario(
            titulo = "Bancada A",
            linhas = listOf(LinhaDoFormulario("linha 1"), LinhaDoFormulario("linha 2")),
            qr = "https://exemplo.com",
            copias = 2,
        )
        val doc = campos.paraDocumento()
        assertEquals(Bloco.Titulo("Bancada A"), doc.blocos[0])
        assertEquals(Bloco.Linha("linha 1"), doc.blocos[1])
        assertEquals(Bloco.Linha("linha 2"), doc.blocos[2])
        assertEquals(Bloco.Qr("https://exemplo.com"), doc.blocos[3])
        assertEquals(2, doc.copias)
    }

    @Test
    fun `campos vazios nao viram blocos`() {
        val doc = CamposDoFormulario("", listOf(LinhaDoFormulario(""), LinhaDoFormulario("  ")), "", 1).paraDocumento()
        assertTrue(doc.blocos.isEmpty())
    }

    @Test
    fun `ida e volta preserva o conteudo`() {
        val campos = CamposDoFormulario("T", listOf(LinhaDoFormulario("a"), LinhaDoFormulario("b")), "q", 4)
        assertEquals(campos, campos.paraDocumento().paraCampos())
    }

    @Test
    fun `documento sem titulo volta com titulo vazio`() {
        val doc = LabelDocument(listOf(Bloco.Linha("so uma linha")))
        val campos = doc.paraCampos()
        assertEquals("", campos.titulo)
        assertEquals(listOf(LinhaDoFormulario("so uma linha")), campos.linhas)
    }

    @Test
    fun `alinhamento escolhido na linha chega ao bloco`() {
        val campos = CamposDoFormulario(
            linhas = listOf(
                LinhaDoFormulario("esquerda"),
                LinhaDoFormulario("direita", Alinhamento.DIREITA),
            ),
        )

        val linhas = campos.paraDocumento().blocos.filterIsInstance<Bloco.Linha>()

        assertEquals(Alinhamento.ESQUERDA, linhas[0].alinhamento)
        assertEquals(Alinhamento.DIREITA, linhas[1].alinhamento)
    }

    @Test
    fun `etiqueta recarregada preserva o alinhamento de cada linha`() {
        // O banco ja guardava a coluna `alinhamento`, mas paraCampos() lia so o
        // texto: abrir uma etiqueta salva silenciosamente reendireitava tudo.
        val doc = LabelDocument(
            listOf(
                Bloco.Linha("a", alinhamento = Alinhamento.CENTRO),
                Bloco.Linha("b", alinhamento = Alinhamento.DIREITA),
            ),
        )

        assertEquals(
            listOf(Alinhamento.CENTRO, Alinhamento.DIREITA),
            doc.paraCampos().linhas.map { it.alinhamento },
        )
    }
}
