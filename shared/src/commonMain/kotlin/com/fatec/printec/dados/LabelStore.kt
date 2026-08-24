package com.fatec.printec.dados

import com.fatec.printec.etiqueta.LabelDocument
import kotlinx.coroutines.flow.Flow

enum class PerfilMidia { CONTINUO, GAP }

data class Configuracoes(
    val impressoraId: String? = null,
    val impressoraNome: String? = null,
    val perfilMidia: PerfilMidia = PerfilMidia.CONTINUO,
    val avancoFinalMm: Int = 3,
)

data class EtiquetaSalva(
    val id: Long,
    val nome: String,
    val documento: LabelDocument,
)

interface LabelStore {
    fun configuracoes(): Flow<Configuracoes>
    fun etiquetasSalvas(): Flow<List<EtiquetaSalva>>
    suspend fun salvarConfiguracoes(configuracoes: Configuracoes)
    suspend fun salvarEtiqueta(nome: String, documento: LabelDocument): Long
    suspend fun excluirEtiqueta(id: Long)
    suspend fun salvarRascunho(documento: LabelDocument)
    suspend fun carregarRascunho(): LabelDocument?
}
