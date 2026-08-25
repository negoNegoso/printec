package com.fatec.printec.etiqueta

/**
 * Etiqueta de diagnostico. Confirma em papel o que o autoteste da impressora
 * so deixou inferir: se a regua de 32 colunas ocupa a largura inteira sem
 * quebrar, os 384 dots estao certos.
 *
 * E tambem a primeira ferramenta quando algo parece errado: se ela imprime, o
 * problema esta no conteudo, nao na conexao.
 */
fun etiquetaDeCalibracao(): LabelDocument = LabelDocument(
    blocos = listOf(
        Bloco.Titulo("CALIBRACAO"),
        Bloco.Linha("12345678901234567890123456789012"),   // 32 colunas exatas
        // Spec §8 pede 1x a 8x: e este instrumento que vai responder as
        // perguntas de hardware em aberto sobre a LT-8359, e cobertura
        // reduzida aqui custa uma sessao de testes inteira.
        Bloco.Linha("escala 1x"),
        Bloco.Linha("escala 2x", escala = 2),
        Bloco.Linha("escala 3x", escala = 3),
        Bloco.Linha("escala 4x", escala = 4),
        Bloco.Linha("escala 5x", escala = 5),
        Bloco.Linha("escala 6x", escala = 6),
        Bloco.Linha("escala 7x", escala = 7),
        Bloco.Linha("escala 8x", escala = 8),
        Bloco.Linha("ação não coração Ângela"),
        Bloco.Linha("direita", alinhamento = Alinhamento.DIREITA),
        Bloco.Linha("centro", alinhamento = Alinhamento.CENTRO),
        Bloco.Qr("PRINTEC-CALIBRACAO"),
    ),
    copias = 1,
)
