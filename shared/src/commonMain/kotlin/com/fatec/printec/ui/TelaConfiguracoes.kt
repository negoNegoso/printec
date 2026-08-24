package com.fatec.printec.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.fatec.printec.dados.Configuracoes
import com.fatec.printec.dados.LabelStore
import com.fatec.printec.dados.PerfilMidia
import com.fatec.printec.impressao.PrinterTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun TelaConfiguracoes(
    store: LabelStore,
    destinos: suspend () -> List<PrinterTarget>,
    aoAbrirConfigBluetooth: () -> Unit,
    aoImprimirTeste: () -> Unit,
) {
    var config by remember { mutableStateOf(Configuracoes()) }
    var lista by remember { mutableStateOf(emptyList<PrinterTarget>()) }
    val escopo = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        config = store.configuracoes().first()
        lista = runCatching { destinos() }.getOrDefault(emptyList())
    }

    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Impressora")
        lista.forEach { alvo ->
            Row(Modifier.fillMaxWidth()) {
                RadioButton(
                    selected = config.impressoraId == alvo.id,
                    onClick = {
                        config = config.copy(impressoraId = alvo.id, impressoraNome = alvo.nome)
                        escopo.launch { store.salvarConfiguracoes(config) }
                    },
                )
                Text(alvo.nome)
            }
        }
        OutlinedButton(onClick = aoAbrirConfigBluetooth) {
            Text("Não encontrou sua impressora?")
        }

        Text("Mídia")
        PerfilMidia.entries.forEach { perfil ->
            Row(Modifier.fillMaxWidth()) {
                RadioButton(
                    selected = config.perfilMidia == perfil,
                    onClick = {
                        config = config.copy(perfilMidia = perfil)
                        escopo.launch { store.salvarConfiguracoes(config) }
                    },
                )
                Text(if (perfil == PerfilMidia.CONTINUO) "Contínuo" else "Etiqueta com gap")
            }
        }

        OutlinedTextField(
            value = config.avancoFinalMm.toString(),
            onValueChange = {
                config = config.copy(avancoFinalMm = it.toIntOrNull()?.coerceIn(0, 30) ?: 0)
                escopo.launch { store.salvarConfiguracoes(config) }
            },
            label = { Text("Avanço final (mm)") },
            modifier = Modifier.width(180.dp),
        )

        Button(onClick = aoImprimirTeste, modifier = Modifier.fillMaxWidth()) {
            Text("IMPRIMIR TESTE")
        }
        Text(
            "A etiqueta de teste traz a régua de 32 colunas, as escalas, acentos " +
                "e um QR. Se ela imprime, o problema está no conteúdo, não na conexão.",
        )
    }
}
