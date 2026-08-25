package com.fatec.printec.dados

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.fatec.printec.db.PrintecDatabase
import java.io.File

class DriverDesktop(private val diretorio: File = diretorioPadrao()) : FabricaDeDriver {

    override fun criar(): SqlDriver {
        diretorio.mkdirs()
        val arquivo = File(diretorio, "printec.db")
        val existia = arquivo.exists()
        // Para um banco em arquivo, o JdbcSqliteDriver abre e fecha uma conexao
        // por statement — "PRAGMA foreign_keys=ON;" executado uma vez so vale
        // para a conexao que morre em seguida. A propriedade de conexao do
        // driver JDBC do SQLite e aplicada a TODA conexao nova, inclusive as
        // que os DELETEs subsequentes abrem.
        val driver = JdbcSqliteDriver(
            "jdbc:sqlite:${arquivo.absolutePath}",
            java.util.Properties().apply { put("foreign_keys", "true") },
        )
        if (!existia) PrintecDatabase.Schema.create(driver)
        return driver
    }

    companion object {
        fun diretorioPadrao(): File {
            val appData = System.getenv("APPDATA")
            return if (appData != null) File(appData, "Printec")
            else File(System.getProperty("user.home"), ".printec")
        }
    }
}
