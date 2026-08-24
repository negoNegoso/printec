package com.fatec.printec.dados

import app.cash.sqldelight.db.SqlDriver

/**
 * Interface em vez de expect/actual: o Android precisa de Context no construtor
 * e o Desktop de um caminho de arquivo. Cada app monta o seu e injeta.
 */
interface FabricaDeDriver {
    fun criar(): SqlDriver
}
