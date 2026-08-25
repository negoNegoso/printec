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

        // So 12+ precisa pedir (API 28 concede na instalacao). Sem isto, o
        // primeiro uso nunca chega a ter permissao: listarDestinos() lanca
        // PermissaoNegada, a lista de impressoras fica vazia, e sem impressora
        // selecionada o unico erro que a UI mostra e NenhumaImpressoraSelecionada
        // -- cuja acao abre as configuracoes de Bluetooth do sistema, que nao
        // concedem permissao de app nenhuma. O botao "Conceder permissao" (via
        // aoConcederPermissao, ligado ao mesmo launcher abaixo) so aparece
        // quando algum ponto do fluxo efetivamente reporta PermissaoNegada.
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
                        vm.imprimir(etiquetaDeCalibracao(), salvarRascunho = false)
                    }
                },
                // Mesmo launcher do pedido de arranque acima. Fica tambem
                // ligado aqui, como acao de recuperacao do PermissaoNegada,
                // para cobrir o caso de o usuario ter negado da primeira vez
                // (ou revogado depois em Ajustes do sistema) e precisar de um
                // segundo pedido, desta vez com o contexto de por que.
                aoConcederPermissao = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        pedirPermissao.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                },
            )
        }
    }
}
