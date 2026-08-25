package com.fatec.printec.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fatec.printec.impressao.ErroImpressao

/**
 * Estado de impressao com acao de recuperacao, compartilhado pelas tres
 * telas que podem disparar uma impressao (Compor, Salvas, Ajustes). Spec §10
 * exige que todo erro venha com uma acao concreta, nao so texto -- antes
 * desta extracao so a tela Compor mostrava algo, e reimprimir pela lista de
 * salvas ou tocar IMPRIMIR TESTE nao davam retorno nenhum: sucesso e falha
 * eram indistinguiveis de nada ter acontecido.
 */
@Composable
fun StatusImpressao(
    estado: EstadoImpressao,
    aoAbrirConfigBluetooth: () -> Unit,
    aoConcederPermissao: () -> Unit,
    aoTentarNovamente: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (estado == EstadoImpressao.Ocioso) return

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = mensagemDe(estado),
            color = corDe(estado),
            style = MaterialTheme.typography.bodySmall,
        )
        if (estado is EstadoImpressao.Falha) {
            // Unico diagnostico que o app tem para falhas de conexao/escrita;
            // sem mostrar a causa, ela e descartada silenciosamente.
            causaDe(estado.erro)?.let { causa ->
                Text(
                    text = causa,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            val (rotulo, aoClicar) = acaoDe(
                estado.erro, aoAbrirConfigBluetooth, aoConcederPermissao, aoTentarNovamente,
            )
            if (rotulo != null && aoClicar != null) {
                OutlinedButton(onClick = aoClicar) { Text(rotulo) }
            }
        }
    }
}

fun mensagemDe(estado: EstadoImpressao): String = when (estado) {
    EstadoImpressao.Ocioso -> ""
    EstadoImpressao.Renderizando -> "Preparando…"
    EstadoImpressao.Conectando -> "Conectando…"
    EstadoImpressao.Enviando -> "Enviando…"
    EstadoImpressao.Sucesso -> "Impresso"
    is EstadoImpressao.Falha -> estado.erro.message.orEmpty()
}

@Composable
fun corDe(estado: EstadoImpressao) = when (estado) {
    is EstadoImpressao.Falha -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurface
}

private fun causaDe(erro: ErroImpressao): String? = when (erro) {
    is ErroImpressao.FalhaAoConectar -> erro.causa.ifBlank { null }
    is ErroImpressao.FalhaAoEscrever -> erro.causa.ifBlank { null }
    else -> null
}

private fun acaoDe(
    erro: ErroImpressao,
    aoAbrirConfigBluetooth: () -> Unit,
    aoConcederPermissao: () -> Unit,
    aoTentarNovamente: () -> Unit,
): Pair<String?, (() -> Unit)?> = when (erro) {
    ErroImpressao.NaoPareada,
    ErroImpressao.NenhumaImpressoraSelecionada,
    -> "Abrir Bluetooth" to aoAbrirConfigBluetooth
    ErroImpressao.PermissaoNegada -> "Conceder permissão" to aoConcederPermissao
    is ErroImpressao.FalhaAoConectar,
    is ErroImpressao.FalhaAoEscrever,
    -> "Tentar novamente" to aoTentarNovamente
    is ErroImpressao.FalhaAoPreparar -> null to null
}
