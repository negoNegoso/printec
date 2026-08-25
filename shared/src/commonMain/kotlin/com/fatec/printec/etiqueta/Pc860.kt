package com.fatec.printec.etiqueta

class ResultadoCodificacao(val bytes: ByteArray, val substituidos: Int)

/**
 * Codificação para a code page 3 (PC860, portuguesa) da LT-8359.
 *
 * Tabela própria em vez de Charset.forName("cp860"): o provedor de charsets do
 * Android é reduzido e pode não incluir a PC860. Uma tabela em Kotlin puro se
 * comporta igual nas duas plataformas e é testável sem dispositivo.
 *
 * Mapeia o ASCII imprimível (0x20..0x7E) e 0x80..0xAF (acentuadas e pontuação).
 * O restante da página é box-drawing e grego, sem uso em etiquetas.
 *
 * Caracteres de controle (\n, \t, ESC…) NÃO passam: viram `?` e são contados.
 * É deliberado — um 0x0A cru criaria uma quebra de linha que o preview não
 * mostra, quebrando o WYSIWYG, e os bytes de controle carregam significado
 * em ESC/POS. Melhor o usuário ver o aviso de substituição.
 */
object Pc860 {

    private const val SUBSTITUTO = '?'.code.toByte()

    /** Índice 0 corresponde ao byte 0x80. */
    private val ALTOS = charArrayOf(
        'Ç', 'ü', 'é', 'â', 'ã', 'à', 'Á', 'ç',   // 0x80..0x87
        'ê', 'Ê', 'è', 'Í', 'Ô', 'ì', 'Ã', 'Â',   // 0x88..0x8F
        'É', 'À', 'È', 'ô', 'õ', 'ò', 'Ú', 'ù',   // 0x90..0x97
        'Ì', 'Õ', 'Ü', '¢', '£', 'Ù', '₧', 'Ó',   // 0x98..0x9F
        'á', 'í', 'ó', 'ú', 'ñ', 'Ñ', 'ª', 'º',   // 0xA0..0xA7
        '¿', 'Ò', '¬', '½', '¼', '¡', '«', '»',   // 0xA8..0xAF
    )

    private val PARA_BYTE: Map<Char, Byte> =
        ALTOS.withIndex().associate { (i, c) -> c to (0x80 + i).toByte() }

    fun codificar(texto: String): ResultadoCodificacao {
        val saida = ByteArray(texto.length)
        var substituidos = 0
        for (i in texto.indices) {
            val c = texto[i]
            saida[i] = when {
                c.code in 0x20..0x7E -> c.code.toByte()
                PARA_BYTE.containsKey(c) -> PARA_BYTE.getValue(c)
                else -> {
                    substituidos++
                    SUBSTITUTO
                }
            }
        }
        return ResultadoCodificacao(saida, substituidos)
    }

    /**
     * Inverso de [codificar], byte a byte. Existe para o teste que prova que
     * o preview e o renderizador ESC/POS quebram linha da mesma forma
     * (RendererIgualAoPreviewTest): decodificar pela MESMA tabela evita que o
     * teste dependa de uma copia paralela dela, que poderia divergir da real
     * sem ninguem notar.
     */
    fun decodificar(bytes: ByteArray): String = buildString {
        for (byte in bytes) {
            val i = byte.toInt() and 0xFF
            when {
                i in 0x20..0x7E -> append(i.toChar())
                i - 0x80 in ALTOS.indices -> append(ALTOS[i - 0x80])
                else -> append(SUBSTITUTO.toInt().toChar())
            }
        }
    }
}
