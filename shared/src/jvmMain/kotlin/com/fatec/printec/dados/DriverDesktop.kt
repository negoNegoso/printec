package com.fatec.printec.dados

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.fatec.printec.db.PrintecDatabase
import java.io.File

class DriverDesktop(private val diretorio: File = diretorioPadrao()) : FabricaDeDriver {

    override fun criar(): SqlDriver {
        diretorio.mkdirs()
        val arquivo = File(diretorio, "printec.db")
        // Para um banco em arquivo, o JdbcSqliteDriver abre e fecha uma conexao
        // por statement — "PRAGMA foreign_keys=ON;" executado uma vez so vale
        // para a conexao que morre em seguida. A propriedade de conexao do
        // driver JDBC do SQLite e aplicada a TODA conexao nova, inclusive as
        // que os DELETEs subsequentes abrem.
        val driver = JdbcSqliteDriver(
            "jdbc:sqlite:${arquivo.absolutePath}",
            java.util.Properties().apply { put("foreign_keys", "true") },
        )

        // "arquivo.exists()" nao diz nada sobre em qual versao de schema o
        // arquivo esta -- inclusive cobre o arquivo criado pela metade, que
        // existe mas nunca chegou a gravar user_version. PRAGMA user_version
        // e a fonte da verdade: 0 e banco novo (ou incompleto), qualquer coisa
        // abaixo da versao do Schema precisa migrar antes de ser usado.
        val versaoNoArquivo = lerUserVersion(driver)
        when {
            versaoNoArquivo == 0L -> {
                PrintecDatabase.Schema.create(driver)
                gravarUserVersion(driver, PrintecDatabase.Schema.version)
            }
            versaoNoArquivo < PrintecDatabase.Schema.version -> {
                PrintecDatabase.Schema.migrate(driver, versaoNoArquivo, PrintecDatabase.Schema.version)
                gravarUserVersion(driver, PrintecDatabase.Schema.version)
            }
        }
        return driver
    }

    private fun lerUserVersion(driver: SqlDriver): Long = driver.executeQuery(
        identifier = null,
        sql = "PRAGMA user_version;",
        mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
        parameters = 0,
    ).value

    private fun gravarUserVersion(driver: SqlDriver, versao: Long) {
        // PRAGMA nao aceita parametro bind; a versao vem do proprio Schema, nao
        // de entrada do usuario, entao a interpolacao aqui e segura.
        driver.execute(null, "PRAGMA user_version = $versao;", 0)
    }

    companion object {
        fun diretorioPadrao(): File {
            val appData = System.getenv("APPDATA")
            return if (appData != null) File(appData, "Printec")
            else File(System.getProperty("user.home"), ".printec")
        }
    }
}
