package com.fatec.printec

import android.app.Application
import com.fatec.printec.dados.DriverAndroid
import com.fatec.printec.dados.LabelStoreSqlDelight
import com.fatec.printec.impressao.AndroidBluetoothTransport

class PrintecApp : Application() {
    lateinit var store: LabelStoreSqlDelight
        private set
    lateinit var transporte: AndroidBluetoothTransport
        private set

    override fun onCreate() {
        super.onCreate()
        store = LabelStoreSqlDelight(DriverAndroid(this).criar())
        transporte = AndroidBluetoothTransport(this)
    }
}
