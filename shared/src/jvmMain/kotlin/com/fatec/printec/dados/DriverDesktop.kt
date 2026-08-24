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
        val driver = JdbcSqliteDriver("jdbc:sqlite:${arquivo.absolutePath}")
        if (!existia) PrintecDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys=ON;", 0)
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
