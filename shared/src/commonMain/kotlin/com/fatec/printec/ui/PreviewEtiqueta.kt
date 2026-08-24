package com.fatec.printec.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fatec.printec.etiqueta.Alinhamento
import com.fatec.printec.etiqueta.Bloco
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
            is Bloco.Qr -> listOf(
                LinhaDePreview("[ QR: ${bloco.conteudo.take(20)} ]", 1, Alinhamento.CENTRO, false),
            )
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

@Composable
fun PreviewEtiqueta(documento: LabelDocument, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(8.dp),
    ) {
        linhasDePreview(documento).forEach { linha ->
            Text(
                text = linha.texto,
                color = Color.Black,
                fontFamily = FontFamily.Monospace,
                fontSize = (11 * linha.escala).sp,
                fontWeight = if (linha.negrito) FontWeight.Bold else FontWeight.Normal,
                textAlign = when (linha.alinhamento) {
                    Alinhamento.ESQUERDA -> TextAlign.Start
                    Alinhamento.CENTRO -> TextAlign.Center
                    Alinhamento.DIREITA -> TextAlign.End
                },
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
