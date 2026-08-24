package com.fatec.printec.dados

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.fatec.printec.db.PrintecDatabase
import com.fatec.printec.etiqueta.Alinhamento
import com.fatec.printec.etiqueta.Bloco
import com.fatec.printec.etiqueta.LabelDocument
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LabelStoreTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var store: LabelStoreSqlDelight

    @BeforeTest
    fun preparar() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        PrintecDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys=ON;", 0)
        store = LabelStoreSqlDelight(driver)
    }

    private val exemplo = LabelDocument(
        blocos = listOf(
            Bloco.Titulo("Bancada A"),
            Bloco.Linha("segunda linha", alinhamento = Alinhamento.DIREITA),
            Bloco.Qr("https://exemplo.com"),
        ),
        copias = 3,
    )

    @Test
    fun `etiqueta salva volta identica`() = runTest {
        val id = store.salvarEtiqueta("Modelo 1", exemplo)
        val salva = store.etiquetasSalvas().first().single()
        assertEquals(id, salva.id)
        assertEquals("Modelo 1", salva.nome)
        assertEquals(exemplo, salva.documento)
    }

    @Test
    fun `excluir etiqueta apaga os blocos em cascata`() = runTest {
        val id = store.salvarEtiqueta("Modelo 1", exemplo)
        assertTrue(PrintecDatabase(driver).printecQueries.contarBlocos().executeAsOne() > 0)
        store.excluirEtiqueta(id)
        assertEquals(0L, PrintecDatabase(driver).printecQueries.contarBlocos().executeAsOne())
    }

    @Test
    fun `rascunho e sobrescrito e nao aparece na lista de salvas`() = runTest {
        store.salvarRascunho(exemplo)
        store.salvarRascunho(exemplo.copy(copias = 9))
        assertEquals(9, store.carregarRascunho()?.copias)
        assertTrue(store.etiquetasSalvas().first().isEmpty())
    }

    @Test
    fun `salvar rascunho duas vezes mantem exatamente um rascunho, o mais recente`() = runTest {
        store.salvarRascunho(exemplo)
        store.salvarRascunho(exemplo.copy(copias = 9))

        val quantidadeDeRascunhos = driver.executeQuery(
            identifier = null,
            sql = "SELECT count(*) FROM etiqueta WHERE eh_rascunho = 1",
            mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0)!! else 0L) },
            parameters = 0,
        ).value

        assertEquals(1L, quantidadeDeRascunhos)
        assertEquals(9, store.carregarRascunho()?.copias)
    }

    @Test
    fun `sem rascunho salvo o carregamento devolve nulo`() = runTest {
        assertNull(store.carregarRascunho())
    }

    @Test
    fun `configuracoes tem padrao e sobrevivem a gravacao`() = runTest {
        assertEquals(PerfilMidia.CONTINUO, store.configuracoes().first().perfilMidia)
        assertEquals(3, store.configuracoes().first().avancoFinalMm)

        store.salvarConfiguracoes(
            Configuracoes("00:11:22", "KPrinter_aa9c", PerfilMidia.GAP, 5),
        )
        val c = store.configuracoes().first()
        assertEquals("00:11:22", c.impressoraId)
        assertEquals(PerfilMidia.GAP, c.perfilMidia)
        assertEquals(5, c.avancoFinalMm)
    }
}
