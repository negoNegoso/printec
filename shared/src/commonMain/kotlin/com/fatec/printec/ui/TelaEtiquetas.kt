package com.fatec.printec.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fatec.printec.dados.EtiquetaSalva
import com.fatec.printec.dados.LabelStore
import com.fatec.printec.etiqueta.LabelDocument
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun TelaEtiquetas(
    vm: EtiquetaViewModel,
    store: LabelStore,
    imprimir: (LabelDocument, Boolean) -> Unit,
    aoAbrirConfigBluetooth: () -> Unit,
    aoConcederPermissao: () -> Unit,
) {
    var etiquetas by remember { mutableStateOf(emptyList<EtiquetaSalva>()) }
    // Guarda o ultimo pedido para o botao "Tentar novamente" do StatusImpressao
    // saber QUAL etiqueta reenviar -- o estado de impressao e global ao
    // ViewModel, mas o documento que falhou e local a esta tela.
    var ultimoPedido by remember { mutableStateOf<Pair<LabelDocument, Boolean>?>(null) }
    val escopo = rememberCoroutineScope()
    val estado by vm.estado.collectAsState()

    LaunchedEffect(Unit) {
        store.etiquetasSalvas().collectLatest { etiquetas = it }
    }

    fun disparar(documento: LabelDocument, salvarRascunho: Boolean) {
        ultimoPedido = documento to salvarRascunho
        imprimir(documento, salvarRascunho)
    }

    // Teto de largura pelo mesmo motivo da tela de configuracoes: linhas de
    // ponta a ponta numa janela larga ficam ilegiveis.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 600.dp).fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
            items(etiquetas, key = { it.id }) { etiqueta ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(etiqueta.nome)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            disparar(etiqueta.documento, false)
                        }) { Text("Imprimir") }
                        OutlinedButton(onClick = {
                            escopo.launch { store.excluirEtiqueta(etiqueta.id) }
                        }) { Text("Excluir") }
                    }
                }
            }
        }
        StatusImpressao(
            estado = estado,
            aoAbrirConfigBluetooth = aoAbrirConfigBluetooth,
            aoConcederPermissao = aoConcederPermissao,
            aoTentarNovamente = { ultimoPedido?.let { (doc, salvar) -> disparar(doc, salvar) } },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
        )
    }
    }
}
