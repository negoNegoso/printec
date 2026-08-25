package com.fatec.printec.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
fun TelaCompor(
    vm: EtiquetaViewModel,
    store: LabelStore,
    imprimir: (LabelDocument, Boolean) -> Unit,
    aoAbrirConfigBluetooth: () -> Unit,
    aoConcederPermissao: () -> Unit,
) {
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
        // Capturado aqui: dentro do Column o receiver do BoxWithConstraints fica sombreado.
        val alturaDisponivel = maxHeight
        val preview = @Composable { seu: Modifier ->
            // O preview rola por conta propria: uma etiqueta longa passa da
            // altura disponivel e, sem isto, o excedente era simplesmente cortado.
            Box(seu.verticalScroll(rememberScrollState())) {
                PreviewEtiqueta(campos.paraDocumento(), Modifier.padding(8.dp))
            }
        }
        val formulario = @Composable { seu: Modifier ->
            Column(
                seu.verticalScroll(rememberScrollState()).padding(8.dp),
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

                StatusImpressao(
                    estado = estado,
                    aoAbrirConfigBluetooth = aoAbrirConfigBluetooth,
                    aoConcederPermissao = aoConcederPermissao,
                    aoTentarNovamente = { imprimir(campos.paraDocumento(), true) },
                )
            }
        }

        if (largo) {
            Row(Modifier.fillMaxSize()) {
                preview(Modifier.weight(1f).fillMaxHeight())
                formulario(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                // Teto no preview. Sem ele, um filho de Column sem peso toma toda
                // a altura que pedir: a etiqueta cresce, come a tela inteira e
                // sobra ZERO para o formulario, que fica inutilizavel.
                preview(Modifier.fillMaxWidth().heightIn(max = alturaDisponivel * 0.4f))
                formulario(Modifier.weight(1f))
            }
        }
    }
}
