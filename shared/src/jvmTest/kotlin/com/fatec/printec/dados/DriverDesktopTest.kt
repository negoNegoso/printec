package com.fatec.printec.dados

import app.cash.sqldelight.db.QueryResult
import com.fatec.printec.db.PrintecDatabase
import com.fatec.printec.etiqueta.Bloco
import com.fatec.printec.etiqueta.LabelDocument
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class DriverDesktopTest {

    @Test
    fun `banco pre-existente com user_version 0 abre sem recriar as tabelas`() = runTest {
        // Todo printec.db criado antes desta classe gravar user_version tem
        // versao 0 E tabelas ja populadas -- e exatamente o cenario que
        // quebrava com "table etiqueta already exists" (Printec.sq nao usa
        // IF NOT EXISTS). Este teste simula esse arquivo: cria um banco de
        // verdade, zera user_version na mao (imitando o arquivo antigo que
        // nunca chegou a gravar a pragma) e reabre pelo mesmo DriverDesktop.
        val diretorio = createTempDirectory("printec-migracao-").toFile()
        try {
            val documento = LabelDocument(blocos = listOf(Bloco.Titulo("Bancada A")), copias = 1)

            val driver1 = DriverDesktop(diretorio).criar()
            val id = LabelStoreSqlDelight(driver1).salvarEtiqueta("Modelo 1", documento)
            driver1.execute(null, "PRAGMA user_version = 0;", 0)
            driver1.close()

            // A abertura em si nao pode lancar -- antes da correcao, isto
            // jogava SQLiteException "table etiqueta already exists".
            val driver2 = DriverDesktop(diretorio).criar()
            try {
                val versao = driver2.executeQuery(
                    identifier = null,
                    sql = "PRAGMA user_version;",
                    mapper = { cursor ->
                        QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
                    },
                    parameters = 0,
                ).value
                assertEquals(PrintecDatabase.Schema.version, versao)

                // E as linhas gravadas antes de "reabrir" tem que ter sobrevivido --
                // um create() bem sucedido contra tabelas vazias novas passaria
                // aqui igual, entao so a versao carimbada nao provaria nada.
                val linha = PrintecDatabase(driver2).printecQueries
                    .blocosDe(id).executeAsList().single()
                assertEquals("Bancada A", linha.conteudo)

                val salva = LabelStoreSqlDelight(driver2).etiquetasSalvas().first().single()
                assertEquals(id, salva.id)
                assertEquals(documento, salva.documento)
            } finally {
                driver2.close()
            }
        } finally {
            diretorio.deleteRecursively()
        }
    }
}
