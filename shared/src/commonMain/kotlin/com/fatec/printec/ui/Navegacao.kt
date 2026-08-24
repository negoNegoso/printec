package com.fatec.printec.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fatec.printec.dados.LabelStore
import kotlinx.coroutines.launch

enum class Tela(val rotulo: String) {
    COMPOR("Compor"),
    ETIQUETAS("Salvas"),
    CONFIGURACOES("Ajustes"),
}

@Composable
fun AppEtiquetas(
    vm: EtiquetaViewModel,
    store: LabelStore,
    destinos: suspend () -> List<com.fatec.printec.impressao.PrinterTarget>,
    aoAbrirConfigBluetooth: () -> Unit,
    aoImprimirTeste: () -> Unit,
) {
    var tela by remember { mutableStateOf(Tela.COMPOR) }

    // Escopo do APP, nao da tela. `AppEtiquetas` permanece composto ao trocar de
    // aba; as telas internas nao. Lancar a impressao no escopo de uma tela faria
    // a troca de aba CANCELAR a impressao — e como o cancelamento nao escreve
    // estado terminal, o botao IMPRIMIR ficaria desabilitado para sempre e o
    // fluxo de bytes poderia ser cortado no meio do envio para a impressora.
    val escopoDoApp = rememberCoroutineScope()
    val imprimir: () -> Unit = { escopoDoApp.launch { vm.imprimir() } }

    LaunchedEffect(tela) { vm.limparEstadoSeConcluido() }

    MaterialTheme {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Tela.entries.forEach { destino ->
                        NavigationBarItem(
                            selected = tela == destino,
                            onClick = { tela = destino },
                            icon = {},
                            label = { Text(destino.rotulo) },
                        )
                    }
                }
            },
        ) { paddings ->
            Box(Modifier.fillMaxSize().padding(paddings)) {
                when (tela) {
                    Tela.COMPOR -> TelaCompor(vm, store, imprimir)
                    Tela.ETIQUETAS -> TelaEtiquetas(vm, store, imprimir)
                    Tela.CONFIGURACOES -> TelaConfiguracoes(
                        store, destinos, aoAbrirConfigBluetooth, aoImprimirTeste,
                    )
                }
            }
        }
    }
}
