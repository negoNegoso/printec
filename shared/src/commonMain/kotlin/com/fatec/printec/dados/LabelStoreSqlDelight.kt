package com.fatec.printec.dados

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.db.SqlDriver
import com.fatec.printec.db.PrintecDatabase
import com.fatec.printec.etiqueta.Alinhamento
import com.fatec.printec.etiqueta.Bloco
import com.fatec.printec.etiqueta.LabelDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

class LabelStoreSqlDelight(driver: SqlDriver) : LabelStore {

    private val q = PrintecDatabase(driver).printecQueries

    // Nao roda no construtor: LabelStoreSqlDelight e instanciado em
    // Application.onCreate/main(), na thread principal. Cada ponto de entrada
    // que precisa da linha de configuracao a garante por conta propria, ja
    // dentro de Dispatchers.IO.
    override fun configuracoes(): Flow<Configuracoes> =
        q.lerConfiguracao().asFlow().mapToOne(Dispatchers.Default).map { linha ->
            Configuracoes(
                impressoraId = linha.impressora_id,
                impressoraNome = linha.impressora_nome,
                perfilMidia = PerfilMidia.valueOf(linha.perfil_midia),
                avancoFinalMm = linha.avanco_final_mm.toInt(),
            )
        }.onStart { q.garantirConfiguracao() }.flowOn(Dispatchers.IO)

    override fun etiquetasSalvas(): Flow<List<EtiquetaSalva>> =
        q.listarEtiquetas().asFlow().mapToList(Dispatchers.Default).map { linhas ->
            // Uma consulta (blocosDe) por etiqueta da lista. Sem flowOn, este
            // map roda no contexto do coletor -- a UI -- fazendo N consultas
            // bloqueantes por emissao na thread principal.
            linhas.map { linha ->
                EtiquetaSalva(
                    id = linha.id,
                    nome = linha.nome.orEmpty(),
                    documento = LabelDocument(
                        blocos = lerBlocos(linha.id),
                        copias = linha.copias.toInt(),
                    ),
                )
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun salvarConfiguracoes(configuracoes: Configuracoes) {
        withContext(Dispatchers.IO) {
            q.garantirConfiguracao()
            q.atualizarConfiguracao(
                impressora_id = configuracoes.impressoraId,
                impressora_nome = configuracoes.impressoraNome,
                perfil_midia = configuracoes.perfilMidia.name,
                avanco_final_mm = configuracoes.avancoFinalMm.toLong(),
            )
        }
    }

    override suspend fun salvarEtiqueta(nome: String, documento: LabelDocument): Long =
        withContext(Dispatchers.IO) { gravar(nome = nome, rascunho = false, documento = documento) }

    override suspend fun excluirEtiqueta(id: Long) {
        withContext(Dispatchers.IO) { q.excluirEtiqueta(id) }
    }

    override suspend fun salvarRascunho(documento: LabelDocument) = withContext(Dispatchers.IO) {
        // Uma transacao SO, cobrindo as duas operacoes: entre apagar o rascunho
        // antigo e gravar o novo nao pode existir um instante com zero rascunhos.
        // Sem isso, um crash no meio perde o que o usuario digitou.
        q.transaction {
            q.excluirRascunhos()
            gravar(nome = null, rascunho = true, documento = documento)
        }
    }

    override suspend fun carregarRascunho(): LabelDocument? = withContext(Dispatchers.IO) {
        val linha = q.lerRascunho().executeAsOneOrNull() ?: return@withContext null
        LabelDocument(lerBlocos(linha.id), linha.copias.toInt())
    }

    private fun gravar(nome: String?, rascunho: Boolean, documento: LabelDocument): Long {
        var id = 0L
        q.transaction {
            val agora = agoraEmMillis()
            q.inserirEtiqueta(
                nome = nome,
                eh_rascunho = if (rascunho) 1L else 0L,
                copias = documento.copias.toLong(),
                criada_em = agora,
                atualizada_em = agora,
            )
            id = q.ultimoId().executeAsOne()
            documento.blocos.forEachIndexed { ordem, bloco ->
                val (tipo, conteudo, escala, alinhamento, negrito) = descrever(bloco)
                q.inserirBloco(
                    etiqueta_id = id,
                    ordem = ordem.toLong(),
                    tipo = tipo,
                    conteudo = conteudo,
                    escala = escala.toLong(),
                    alinhamento = alinhamento,
                    negrito = if (negrito) 1L else 0L,
                )
            }
        }
        return id
    }

    private fun lerBlocos(etiquetaId: Long): List<Bloco> =
        q.blocosDe(etiquetaId).executeAsList().map { b ->
            when (b.tipo) {
                "TITULO" -> Bloco.Titulo(b.conteudo.orEmpty())
                "QR" -> Bloco.Qr(b.conteudo.orEmpty(), b.escala.toInt())
                "AVANCO" -> Bloco.Avanco(b.escala.toInt())
                else -> Bloco.Linha(
                    texto = b.conteudo.orEmpty(),
                    escala = b.escala.toInt(),
                    alinhamento = Alinhamento.valueOf(b.alinhamento),
                    negrito = b.negrito == 1L,
                )
            }
        }

    private data class Descricao(
        val tipo: String,
        val conteudo: String?,
        val escala: Int,
        val alinhamento: String,
        val negrito: Boolean,
    )

    private fun descrever(bloco: Bloco): Descricao = when (bloco) {
        is Bloco.Titulo -> Descricao("TITULO", bloco.texto, 1, "CENTRO", true)
        is Bloco.Linha -> Descricao(
            "LINHA", bloco.texto, bloco.escala, bloco.alinhamento.name, bloco.negrito,
        )
        // Nao ha coluna dedicada para o tamanho do modulo do QR nem para os
        // milimetros do Avanco -- ambos reaproveitam a coluna `escala`, que so
        // significa "escala de fonte" para Bloco.Linha/Titulo. lerBlocos() (logo
        // acima) faz o caminho inverso lendo `b.escala` de volta como
        // tamanhoModulo/milimetros. Se motivo do reaproveitamento nao ficar
        // obvio aqui, o proximo a mexer em lerBlocos vai "consertar" o que
        // parece um bug e quebrar QR/Avanco de verdade.
        is Bloco.Qr -> Descricao("QR", bloco.conteudo, bloco.tamanhoModulo, "CENTRO", false)
        is Bloco.Avanco -> Descricao("AVANCO", null, bloco.milimetros, "ESQUERDA", false)
    }
}

internal expect fun agoraEmMillis(): Long
