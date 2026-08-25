package com.fatec.printec.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fatec.printec.etiqueta.Alinhamento
import com.fatec.printec.etiqueta.Bloco
import com.fatec.printec.etiqueta.Impressora
import com.fatec.printec.etiqueta.LabelDocument
import com.fatec.printec.etiqueta.Pc860
import com.fatec.printec.etiqueta.QuebraDeLinha
import com.fatec.printec.etiqueta.normalizado

data class LinhaDePreview(
    val texto: String,
    val escala: Int,
    val alinhamento: Alinhamento,
    val negrito: Boolean,
)

/**
 * Usa a MESMA QuebraDeLinha do EscPosRenderer. Se as duas divergirem, o que o
 * usuario ve deixa de ser o que sai no papel.
 */
fun linhasDePreview(documento: LabelDocument): List<LinhaDePreview> =
    documento.blocos.map { it.normalizado() }.flatMap { bloco ->
        when (bloco) {
            is Bloco.Linha -> QuebraDeLinha
                .quebrar(bloco.texto, QuebraDeLinha.colunasPara(bloco.escala))
                .map { LinhaDePreview(it, bloco.escala, bloco.alinhamento, bloco.negrito) }
            is Bloco.Qr -> {
                val truncado = bloco.conteudo.length > 20
                val prefixo = bloco.conteudo.take(20) + if (truncado) "…" else ""
                listOf(LinhaDePreview("[ QR: $prefixo ]", 1, Alinhamento.CENTRO, false))
            }
            is Bloco.Avanco -> emptyList()
            is Bloco.Titulo -> emptyList()  // normalizado() ja converteu
        }
    }

fun caracteresSubstituidos(documento: LabelDocument): Int =
    documento.blocos.map { it.normalizado() }.sumOf { bloco ->
        when (bloco) {
            is Bloco.Linha -> Pc860.codificar(bloco.texto).substituidos
            else -> 0
        }
    }

/**
 * Tamanho de fonte em que as 32 colunas da impressora cabem EXATAMENTE na
 * largura disponivel.
 *
 * Fonte fixa nao serve aqui. Com fonte de acessibilidade grande, 32 caracteres
 * passam da largura do celular e o Compose quebra a linha uma SEGUNDA vez, por
 * conta propria — o preview deixa de bater com o papel em silencio, justamente
 * para quem aumentou a fonte. Medir e escalar mantem a promessa da spec §6 em
 * qualquer largura e qualquer escala de fonte do sistema.
 */
@Composable
private fun corpoQueCabe(largura: Dp): TextUnit {
    val medidor = rememberTextMeasurer()
    val densidade = LocalDensity.current
    val larguraDaReferencia = remember(medidor, densidade) {
        with(densidade) {
            medidor.measure(
                text = "M".repeat(Impressora.COLUNAS_BASE),
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = REFERENCIA),
                softWrap = false,
            ).size.width.toDp()
        }
    }
    // Piso para nao virar ilegivel em tela estreita; teto para nao inchar no desktop.
    return REFERENCIA * (largura / larguraDaReferencia).coerceIn(0.35f, 1.2f)
}

private val REFERENCIA = 20.sp

@Composable
fun PreviewEtiqueta(documento: LabelDocument, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(8.dp),
    ) {
        // maxWidth aqui ja vem descontado do padding acima.
        val corpo = corpoQueCabe(maxWidth)
        Column(Modifier.fillMaxWidth()) {
            linhasDePreview(documento).forEach { linha ->
                Text(
                    text = linha.texto,
                    color = Color.Black,
                    fontFamily = FontFamily.Monospace,
                    fontSize = corpo * linha.escala,
                    fontWeight = if (linha.negrito) FontWeight.Bold else FontWeight.Normal,
                    textAlign = when (linha.alinhamento) {
                        Alinhamento.ESQUERDA -> TextAlign.Start
                        Alinhamento.CENTRO -> TextAlign.Center
                        Alinhamento.DIREITA -> TextAlign.End
                    },
                    // A quebra de linha e decidida por QuebraDeLinha, nunca pelo
                    // Compose. Se algo nao couber, preferimos cortar visivelmente
                    // a reflowar em silencio e mentir sobre o que vai sair.
                    softWrap = false,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val substituidos = caracteresSubstituidos(documento)
            if (substituidos > 0) {
                Text(
                    text = "$substituidos caractere(s) não suportado(s) serão substituídos por ?",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
