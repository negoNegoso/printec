package com.fatec.printec.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fatec.printec.dados.EtiquetaSalva
import com.fatec.printec.dados.LabelStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun TelaEtiquetas(vm: EtiquetaViewModel, store: LabelStore, imprimir: () -> Unit) {
    var etiquetas by remember { mutableStateOf(emptyList<EtiquetaSalva>()) }
    val escopo = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        store.etiquetasSalvas().collectLatest { etiquetas = it }
    }

    LazyColumn(Modifier.fillMaxWidth().padding(8.dp)) {
        items(etiquetas, key = { it.id }) { etiqueta ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(etiqueta.nome)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        vm.atualizarDocumento(etiqueta.documento)
                        imprimir()
                    }) { Text("Imprimir") }
                    OutlinedButton(onClick = {
                        escopo.launch { store.excluirEtiqueta(etiqueta.id) }
                    }) { Text("Excluir") }
                }
            }
        }
    }
}
