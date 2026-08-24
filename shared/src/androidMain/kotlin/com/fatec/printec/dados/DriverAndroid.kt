package com.fatec.printec.dados

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.fatec.printec.db.PrintecDatabase

class DriverAndroid(private val context: Context) : FabricaDeDriver {
    override fun criar(): SqlDriver =
        AndroidSqliteDriver(PrintecDatabase.Schema, context, "printec.db").also {
            // O SQLite ignora ON DELETE CASCADE sem esta pragma, e ela e por conexao.
            it.execute(null, "PRAGMA foreign_keys=ON;", 0)
        }
}
