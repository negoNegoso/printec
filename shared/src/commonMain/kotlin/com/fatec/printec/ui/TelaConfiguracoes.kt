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
import androidx.compose.runtime.collectAsState
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
import com.fatec.printec.impressao.ErroImpressao
import com.fatec.printec.impressao.PrinterTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun TelaConfiguracoes(
    vm: EtiquetaViewModel,
    store: LabelStore,
    destinos: suspend () -> List<PrinterTarget>,
    aoAbrirConfigBluetooth: () -> Unit,
    aoImprimirTeste: () -> Unit,
    aoConcederPermissao: () -> Unit,
) {
    var config by remember { mutableStateOf(Configuracoes()) }
    var lista by remember { mutableStateOf(emptyList<PrinterTarget>()) }
    val escopo = rememberCoroutineScope()
    val estado by vm.estado.collectAsState()

    LaunchedEffect(Unit) {
        config = store.configuracoes().first()
        // Descartar o erro aqui (como um runCatching cru faria) faz uma
        // permissao de Bluetooth negada parecer "nenhuma impressora existe":
        // lista fica vazia sem nenhuma explicacao, e o botao "Conceder
        // permissao" -- a unica saida do primeiro uso em Android 12+ -- nunca
        // chega a aparecer. ErroImpressao especificamente, nao Exception:
        // um erro nao tipado aqui deve continuar propagando.
        lista = try {
            destinos()
        } catch (e: ErroImpressao) {
            vm.reportarFalha(e)
            emptyList()
        }
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
            // "Etiqueta com gap" fica desabilitada: nenhum codigo consome
            // perfilMidia hoje, e persistir uma escolha que nao muda nada no
            // ESC/POS emitido so engana quem esta configurando.
            val disponivel = perfil == PerfilMidia.CONTINUO
            Row(Modifier.fillMaxWidth()) {
                RadioButton(
                    selected = config.perfilMidia == perfil,
                    enabled = disponivel,
                    onClick = {
                        config = config.copy(perfilMidia = perfil)
                        escopo.launch { store.salvarConfiguracoes(config) }
                    },
                )
                Text(
                    if (perfil == PerfilMidia.CONTINUO) "Contínuo"
                    else "Etiqueta com gap (em breve)",
                )
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

        StatusImpressao(
            estado = estado,
            aoAbrirConfigBluetooth = aoAbrirConfigBluetooth,
            aoConcederPermissao = aoConcederPermissao,
            aoTentarNovamente = aoImprimirTeste,
        )
    }
}
