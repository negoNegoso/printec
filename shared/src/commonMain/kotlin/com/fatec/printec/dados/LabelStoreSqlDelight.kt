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
import kotlinx.coroutines.flow.map

class LabelStoreSqlDelight(driver: SqlDriver) : LabelStore {

    private val q = PrintecDatabase(driver).printecQueries

    init {
        q.garantirConfiguracao()
    }

    override fun configuracoes(): Flow<Configuracoes> =
        q.lerConfiguracao().asFlow().mapToOne(Dispatchers.Default).map { linha ->
            Configuracoes(
                impressoraId = linha.impressora_id,
                impressoraNome = linha.impressora_nome,
                perfilMidia = PerfilMidia.valueOf(linha.perfil_midia),
                avancoFinalMm = linha.avanco_final_mm.toInt(),
            )
        }

    override fun etiquetasSalvas(): Flow<List<EtiquetaSalva>> =
        q.listarEtiquetas().asFlow().mapToList(Dispatchers.Default).map { linhas ->
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
        }

    override suspend fun salvarConfiguracoes(configuracoes: Configuracoes) {
        q.atualizarConfiguracao(
            impressora_id = configuracoes.impressoraId,
            impressora_nome = configuracoes.impressoraNome,
            perfil_midia = configuracoes.perfilMidia.name,
            avanco_final_mm = configuracoes.avancoFinalMm.toLong(),
        )
    }

    override suspend fun salvarEtiqueta(nome: String, documento: LabelDocument): Long =
        gravar(nome = nome, rascunho = false, documento = documento)

    override suspend fun excluirEtiqueta(id: Long) {
        q.excluirEtiqueta(id)
    }

    override suspend fun salvarRascunho(documento: LabelDocument) {
        // Uma transacao SO, cobrindo as duas operacoes: entre apagar o rascunho
        // antigo e gravar o novo nao pode existir um instante com zero rascunhos.
        // Sem isso, um crash no meio perde o que o usuario digitou.
        q.transaction {
            q.excluirRascunhos()
            gravar(nome = null, rascunho = true, documento = documento)
        }
    }

    override suspend fun carregarRascunho(): LabelDocument? {
        val linha = q.lerRascunho().executeAsOneOrNull() ?: return null
        return LabelDocument(lerBlocos(linha.id), linha.copias.toInt())
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
        // tamanhoModulo e milimetros reaproveitam a coluna `escala`.
        is Bloco.Qr -> Descricao("QR", bloco.conteudo, bloco.tamanhoModulo, "CENTRO", false)
        is Bloco.Avanco -> Descricao("AVANCO", null, bloco.milimetros, "ESQUERDA", false)
    }
}

internal expect fun agoraEmMillis(): Long
