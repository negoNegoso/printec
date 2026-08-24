package com.fatec.printec.impressao

import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidBluetoothTransportTest {

    @Test
    fun `o UUID de porta serial e o padrao SPP`() {
        assertEquals(
            "00001101-0000-1000-8000-00805F9B34FB",
            AndroidBluetoothTransport.UUID_SPP.toString().uppercase(),
        )
    }
}
