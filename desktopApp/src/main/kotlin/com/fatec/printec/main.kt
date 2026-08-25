package com.fatec.printec

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.fatec.printec.dados.DriverDesktop
import com.fatec.printec.dados.LabelStoreSqlDelight
import com.fatec.printec.etiqueta.etiquetaDeCalibracao
import com.fatec.printec.impressao.TransporteDesktop
import com.fatec.printec.impressao.EscPosRenderer
import com.fatec.printec.ui.AppEtiquetas
import com.fatec.printec.ui.EtiquetaViewModel
import kotlinx.coroutines.launch

fun main() = application {
    val store = LabelStoreSqlDelight(DriverDesktop().criar())
    // Lista USB e portas seriais (Bluetooth pareado no Windows) juntas.
    val transporte = TransporteDesktop()
    val vm = EtiquetaViewModel(store, transporte, EscPosRenderer::renderizar)

    Window(onCloseRequest = ::exitApplication, title = "Printec") {
        val escopo = rememberCoroutineScope()
        AppEtiquetas(
            vm = vm,
            store = store,
            destinos = { transporte.listarDestinos() },
            aoAbrirConfigBluetooth = { },   // no desktop a conexao e USB
            aoImprimirTeste = {
                escopo.launch {
                    vm.imprimir(etiquetaDeCalibracao(), salvarRascunho = false)
                }
            },
            aoConcederPermissao = { },   // no desktop nao ha permissao de runtime
        )
    }
}
