package com.fatec.printec.impressao

import com.fatec.printec.etiqueta.Bloco
import com.fatec.printec.etiqueta.LabelDocument
import com.fatec.printec.etiqueta.Pc860
import com.fatec.printec.ui.linhasDePreview
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S4 da onda de correcao: a promessa central da spec e "o preview mostra
 * exatamente o que sai no papel". EscPosRenderer e PreviewEtiqueta.linhasDePreview
 * compartilham QuebraDeLinha ha varias tarefas, mas ate este teste nenhum caso
 * comparava a SAIDA de um caminho contra a do outro -- so a logica interna de
 * cada um isoladamente. Nada falharia se os dois divergissem.
 *
 * Cada documento aqui tem UM Bloco.Linha so (sem Titulo/Qr/Avanco) e
 * avancoFinalMm = 0: isso mantem a estrutura de bytes do renderizador
 * previsivel o bastante para separar comando/texto sem duplicar QuebraDeLinha
 * neste arquivo (ver [prefixoDaPrimeiraLinha]).
 */
class RendererIgualAoPreviewTest {

    // ESC @ (2 bytes) + ESC t 3 (3 bytes) = preambulo fixo do documento.
    // ESC a X (3) + GS ! X (3) + ESC E X (3) = comando fixo de UM Bloco.Linha,
    // emitido uma vez so antes do texto (mesmo se o texto quebrar em varias
    // linhas fisicas).
    private val prefixoDaPrimeiraLinha = 5 + 9

    private fun linhasRenderizadas(doc: LabelDocument): List<String> {
        val bytes = EscPosRenderer.renderizar(doc, avancoFinalMm = 0)

        val segmentos = mutableListOf<MutableList<Byte>>()
        var atual = mutableListOf<Byte>()
        for (b in bytes) {
            if (b == 0x0A.toByte()) {
                segmentos += atual
                atual = mutableListOf()
            } else {
                atual += b
            }
        }
        check(atual.isEmpty()) {
            "bytes sobrando apos o ultimo LF -- documento de teste precisa ter " +
                "avancoFinalMm = 0 e um unico Bloco.Linha: $atual"
        }

        return segmentos.mapIndexed { i, seg ->
            val semComando = if (i == 0) seg.drop(prefixoDaPrimeiraLinha) else seg
            Pc860.decodificar(semComando.toByteArray())
        }
    }

    private fun verificarWysiwyg(doc: LabelDocument) {
        val doRenderizador = linhasRenderizadas(doc)
        val doPreview = linhasDePreview(doc).map { it.texto }
        assertEquals(doPreview, doRenderizador, "preview e renderizador divergiram para: $doc")
    }

    @Test
    fun `33 caracteres quebra igual nos dois caminhos`() {
        verificarWysiwyg(LabelDocument(listOf(Bloco.Linha("z".repeat(33)))))
    }

    @Test
    fun `palavra de 40 caracteres sem espaco quebra igual nos dois caminhos`() {
        verificarWysiwyg(LabelDocument(listOf(Bloco.Linha("a".repeat(40)))))
    }

    @Test
    fun `escala 2 quebra igual nos dois caminhos`() {
        val texto = "linha razoavelmente comprida para escala dois"
        verificarWysiwyg(LabelDocument(listOf(Bloco.Linha(texto, escala = 2))))
    }

    @Test
    fun `acentos decodificam identicos ao texto original nos dois caminhos`() {
        verificarWysiwyg(LabelDocument(listOf(Bloco.Linha("ação não coração Ângela"))))
    }
}
