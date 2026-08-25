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
        Bloco.Linha("escala 1x"),
        Bloco.Linha("escala 2x", escala = 2),
        Bloco.Linha("ação não coração Ângela"),
        Bloco.Linha("direita", alinhamento = Alinhamento.DIREITA),
        Bloco.Linha("centro", alinhamento = Alinhamento.CENTRO),
        Bloco.Qr("PRINTEC-CALIBRACAO"),
    ),
    copias = 1,
)
