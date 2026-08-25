package com.fatec.printec.dados

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.fatec.printec.db.PrintecDatabase

class DriverAndroid(private val context: Context) : FabricaDeDriver {
    // O SQLite ignora ON DELETE CASCADE sem esta pragma, e ela e por conexao.
    // `.also { it.execute(...) }` so alcancaria a conexao de abertura; o
    // AndroidSqliteDriver reabre conexoes por baixo, entao a via suportada e o
    // callback onConfigure, que roda a cada conexao nova.
    override fun criar(): SqlDriver = AndroidSqliteDriver(
        schema = PrintecDatabase.Schema,
        context = context,
        name = "printec.db",
        callback = object : AndroidSqliteDriver.Callback(PrintecDatabase.Schema) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                super.onConfigure(db)
                db.setForeignKeyConstraintsEnabled(true)
            }
        },
    )
}
