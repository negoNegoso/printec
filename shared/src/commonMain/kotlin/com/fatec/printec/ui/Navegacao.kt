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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fatec.printec.dados.LabelStore

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
                    Tela.COMPOR -> TelaCompor(vm, store)
                    Tela.ETIQUETAS -> TelaEtiquetas(vm, store)
                    Tela.CONFIGURACOES -> TelaConfiguracoes(
                        store, destinos, aoAbrirConfigBluetooth, aoImprimirTeste,
                    )
                }
            }
        }
    }
}
