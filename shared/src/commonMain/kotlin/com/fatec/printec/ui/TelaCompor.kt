package com.fatec.printec.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fatec.printec.dados.LabelStore
import com.fatec.printec.etiqueta.LabelDocument
import kotlinx.coroutines.launch

@Composable
fun TelaCompor(vm: EtiquetaViewModel, store: LabelStore, imprimir: (LabelDocument, Boolean) -> Unit) {
    var campos by remember { mutableStateOf(CamposDoFormulario()) }
    var nomeParaSalvar by remember { mutableStateOf("") }
    val escopo = rememberCoroutineScope()
    val estado by vm.estado.collectAsState()

    // Restaura os ultimos valores digitados ao abrir.
    LaunchedEffect(Unit) {
        store.carregarRascunho()?.let { campos = it.paraCampos() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val largo = maxWidth > 600.dp
        val preview = @Composable {
            PreviewEtiqueta(campos.paraDocumento(), Modifier.padding(8.dp))
        }
        val formulario = @Composable {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = campos.titulo,
                    onValueChange = { campos = campos.copy(titulo = it) },
                    label = { Text("Título") },
                    modifier = Modifier.fillMaxWidth(),
                )
                campos.linhas.forEachIndexed { i, linha ->
                    OutlinedTextField(
                        value = linha,
                        onValueChange = {
                            campos = campos.copy(
                                linhas = campos.linhas.toMutableList().also { l -> l[i] = it },
                            )
                        },
                        label = { Text("Linha ${i + 1}") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedButton(
                    onClick = { campos = campos.copy(linhas = campos.linhas + "") },
                ) { Text("+ adicionar linha") }

                OutlinedTextField(
                    value = campos.qr,
                    onValueChange = { campos = campos.copy(qr = it) },
                    label = { Text("Conteúdo do QR") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = campos.copias.toString(),
                    onValueChange = {
                        campos = campos.copy(copias = it.toIntOrNull()?.coerceIn(1, 99) ?: 1)
                    },
                    label = { Text("Cópias") },
                    modifier = Modifier.width(120.dp),
                )

                // Sem esta guarda, um duplo toque dispara duas impressoes
                // concorrentes e saem duas etiquetas.
                val imprimindo = estado is EstadoImpressao.Renderizando ||
                    estado is EstadoImpressao.Conectando ||
                    estado is EstadoImpressao.Enviando
                Button(
                    onClick = { imprimir(campos.paraDocumento(), true) },
                    enabled = !imprimindo,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (imprimindo) "IMPRIMINDO…" else "IMPRIMIR") }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nomeParaSalvar,
                        onValueChange = { nomeParaSalvar = it },
                        label = { Text("Nome") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        enabled = nomeParaSalvar.isNotBlank(),
                        onClick = {
                            escopo.launch {
                                store.salvarEtiqueta(nomeParaSalvar, campos.paraDocumento())
                                nomeParaSalvar = ""
                            }
                        },
                    ) { Text("Salvar") }
                }

                Text(
                    text = mensagemDe(estado),
                    color = corDe(estado),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (largo) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f)) { preview() }
                Column(Modifier.weight(1f)) { formulario() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                preview()
                formulario()
            }
        }
    }
}

private fun mensagemDe(estado: EstadoImpressao): String = when (estado) {
    EstadoImpressao.Ocioso -> ""
    EstadoImpressao.Renderizando -> "Preparando…"
    EstadoImpressao.Conectando -> "Conectando…"
    EstadoImpressao.Enviando -> "Enviando…"
    EstadoImpressao.Sucesso -> "Impresso"
    is EstadoImpressao.Falha -> estado.erro.message.orEmpty()
}

@Composable
private fun corDe(estado: EstadoImpressao) = when (estado) {
    is EstadoImpressao.Falha -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurface
}
