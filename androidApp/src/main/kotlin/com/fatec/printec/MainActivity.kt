package com.fatec.printec

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import com.fatec.printec.etiqueta.etiquetaDeCalibracao
import com.fatec.printec.impressao.EscPosRenderer
import com.fatec.printec.ui.AppEtiquetas
import com.fatec.printec.ui.EtiquetaViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val pedirPermissao =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // API 28 concede na instalacao; so 12+ precisa pedir.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pedirPermissao.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val app = application as PrintecApp
        val vm = EtiquetaViewModel(app.store, app.transporte, EscPosRenderer::renderizar)

        setContent {
            val escopo = rememberCoroutineScope()
            AppEtiquetas(
                vm = vm,
                store = app.store,
                destinos = { app.transporte.listarDestinos() },
                aoAbrirConfigBluetooth = {
                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                },
                aoImprimirTeste = {
                    escopo.launch {
                        vm.atualizarDocumento(etiquetaDeCalibracao())
                        vm.imprimir()
                    }
                },
            )
        }
    }
}
