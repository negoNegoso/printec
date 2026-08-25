# Impressão de Etiquetas ESC/POS — Plano de Implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Um app Android e Desktop onde o usuário preenche um formulário fixo, vê a pré-visualização e imprime a etiqueta numa Lintian LT-8359 — Bluetooth no Android, USB no Desktop.

**Architecture:** Um modelo de documento puro em `commonMain` consumido por dois renderizadores (pré-visualização Compose e bytes ESC/POS). O transporte é uma interface com duas implementações de plataforma. Persistência em SQLite via SQLDelight. Nenhuma tela conhece ESC/POS, e nenhuma camada de impressão conhece Compose.

**Tech Stack:** Kotlin Multiplatform 2.4.10, Compose Multiplatform 1.11.1, escpos-coffee 4.1.0, SQLDelight 2.1.0, kotlinx-coroutines 1.11.0.

**Spec:** [`docs/superpowers/specs/2026-08-24-printec-etiquetas-design.md`](../specs/2026-08-24-printec-etiquetas-design.md)

**Branch:** `feat/etiquetas-escpos`

---

## Global Constraints

Estes valores valem para **todas** as tarefas. São fatos apurados do autoteste da impressora, não escolhas revisáveis.

- **Linguagem da impressora:** ESC/POS. A LT-8359 **não** usa TSPL.
- **Largura de impressão:** `384` dots. Ainda é inferência (32 col × 12 dots) até a Tarefa 11 confirmar em papel.
- **Resolução:** 203 dpi → **8 dots por milímetro**.
- **Colunas em escala 1×:** `32`. Em escala N, `32 / N` (escala 2 → 16 colunas).
- **Code page:** `3` = PC860 (portuguesa). Enviada explicitamente via `ESC t 3`, nunca assumida.
- **Caracteres fora da PC860:** substituídos por `?` e **contados**. Substituição silenciosa é proibida.
- **`minSdk` permanece `24`.** O app roda em Android 9 (API 28) e em Android 12+ (API 31+), que têm modelos de permissão diferentes.
- **Nenhuma permissão de localização**, em nenhuma versão do Android.
- **Conexão por trabalho de impressão:** abrir, escrever, fechar. Nunca manter conexão aberta — a impressora hiberna em 10 minutos.
- **Pacote base:** `com.fatec.printec`. Código novo de etiqueta em `com.fatec.printec.etiqueta`, persistência em `com.fatec.printec.dados`, impressão em `com.fatec.printec.impressao`.
- **Idioma da UI e das mensagens de erro:** português do Brasil.
- Não remover `Greeting`, `GreetingUtil` ou `Platform` — são do template e saem na Tarefa 11.

---

## Estrutura de Arquivos

| Arquivo | Responsabilidade | Tarefa |
|---|---|---|
| `shared/src/commonMain/kotlin/com/fatec/printec/etiqueta/LabelDocument.kt` | Modelo puro: `Bloco`, `LabelDocument`, `Alinhamento` | 2 |
| `shared/src/commonMain/kotlin/com/fatec/printec/etiqueta/Impressora.kt` | Constantes físicas (colunas, dots, dots/mm) | 2 |
| `shared/src/commonMain/kotlin/com/fatec/printec/etiqueta/QuebraDeLinha.kt` | Quebra de texto em N colunas — **compartilhada** entre preview e renderizador | 2 |
| `shared/src/commonMain/kotlin/com/fatec/printec/etiqueta/Pc860.kt` | Tabela de codificação PC860 com fallback `?` | 3 |
| `shared/src/jvmCommonMain/kotlin/com/fatec/printec/impressao/EscPosRenderer.kt` | `LabelDocument` → `ByteArray` | 4 |
| `shared/src/commonMain/sqldelight/com/fatec/printec/db/Printec.sq` | Esquema e consultas SQL | 5 |
| `shared/src/commonMain/kotlin/com/fatec/printec/dados/LabelStore.kt` | Interface de persistência + tipos (`Configuracoes`, `EtiquetaSalva`) | 5 |
| `shared/src/commonMain/kotlin/com/fatec/printec/dados/LabelStoreSqlDelight.kt` | Implementação + mapeamento Bloco ↔ linhas | 5 |
| `shared/src/commonMain/kotlin/com/fatec/printec/dados/FabricaDeDriver.kt` | Interface de criação de driver | 5 |
| `shared/src/androidMain/.../dados/DriverAndroid.kt` · `shared/src/jvmMain/.../dados/DriverDesktop.kt` | Drivers por plataforma | 5 |
| `shared/src/commonMain/kotlin/com/fatec/printec/impressao/PrinterTransport.kt` | Interface + `PrinterTarget` + `ErroImpressao` | 6 |
| `shared/src/jvmMain/kotlin/com/fatec/printec/impressao/DesktopUsbTransport.kt` | `javax.print` via escpos-coffee | 6 |
| `shared/src/androidMain/kotlin/com/fatec/printec/impressao/AndroidBluetoothTransport.kt` | `BluetoothSocket` SPP sobre pareados | 7 |
| `shared/src/commonMain/kotlin/com/fatec/printec/ui/EtiquetaViewModel.kt` | Estado do formulário + máquina de impressão | 8 |
| `shared/src/commonMain/kotlin/com/fatec/printec/ui/PreviewEtiqueta.kt` | Composable de pré-visualização | 9 |
| `shared/src/commonMain/kotlin/com/fatec/printec/ui/TelaCompor.kt` · `TelaConfiguracoes.kt` · `TelaEtiquetas.kt` · `Navegacao.kt` | As três telas e a navegação | 10 |
| `shared/src/commonMain/kotlin/com/fatec/printec/etiqueta/EtiquetaDeTeste.kt` | Documento de calibração | 11 |

---

## Task 1: Fundação do build e prova de vida do escpos-coffee

Esta tarefa existe para atacar o **risco nº 3 da spec** antes de qualquer outra coisa: se o escpos-coffee não rodar no Android, todo o resto do plano muda. Descobrir isso agora custa uma hora; descobrir na Tarefa 10 custa a semana.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `shared/build.gradle.kts`
- Modify: `androidApp/build.gradle.kts`
- Modify: `androidApp/proguard-rules.pro`
- Test: `shared/src/jvmCommonTest/kotlin/com/fatec/printec/impressao/EscPosCoffeeVivoTest.kt`
- Test: `shared/src/androidDeviceTest/kotlin/com/fatec/printec/impressao/EscPosCoffeeDispositivoTest.kt`

**Interfaces:**
- Consumes: nada
- Produces: source set `jvmCommonMain` (visível para `androidMain` e `jvmMain`) e `jvmCommonTest`; dependência `libs.escpos.coffee` disponível em `jvmCommonMain`

- [ ] **Step 1: Adicionar as versões ao catálogo**

Em `gradle/libs.versions.toml`, na seção `[versions]`:

```toml
escposCoffee = "4.1.0"
sqldelight = "2.1.0"
desugarJdkLibs = "2.1.5"
```

Na seção `[libraries]`:

```toml
escpos-coffee = { module = "com.github.anastaciocintra:escpos-coffee", version.ref = "escposCoffee" }
sqldelight-androidDriver = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-sqliteDriver = { module = "app.cash.sqldelight:sqlite-driver", version.ref = "sqldelight" }
sqldelight-coroutinesExtensions = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
desugar-jdkLibs = { module = "com.android.tools:desugar_jdk_libs", version.ref = "desugarJdkLibs" }
```

Na seção `[plugins]`:

```toml
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
```

- [ ] **Step 2: Criar o source set `jvmCommonMain`**

O KMP **não** cria automaticamente um source set compartilhado entre os alvos `android` e `jvm`. Em `shared/build.gradle.kts`, dentro do bloco `kotlin { sourceSets { ... } }`, adicione antes das declarações existentes:

```kotlin
        val jvmCommonMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.escpos.coffee)
            }
        }
        val jvmCommonTest by creating {
            dependsOn(commonTest.get())
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        jvmMain.get().dependsOn(jvmCommonMain)
        androidMain.get().dependsOn(jvmCommonMain)
        jvmTest.get().dependsOn(jvmCommonTest)
        androidHostTest.get().dependsOn(jvmCommonTest)
```

- [ ] **Step 3: Escrever o teste de prova de vida (JVM)**

Crie `shared/src/jvmCommonTest/kotlin/com/fatec/printec/impressao/EscPosCoffeeVivoTest.kt`:

```kotlin
package com.fatec.printec.impressao

import com.github.anastaciocintra.escpos.EscPos
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class EscPosCoffeeVivoTest {

    @Test
    fun `escpos-coffee escreve o comando de inicializacao no stream`() {
        val saida = ByteArrayOutputStream()
        EscPos(saida).use { escpos ->
            escpos.initializePrinter()
        }
        // ESC @ = 0x1B 0x40
        assertEquals(listOf<Byte>(0x1B, 0x40), saida.toByteArray().toList())
    }
}
```

- [ ] **Step 4: Rodar o teste e confirmar que falha por falta da dependência**

```bash
./gradlew :shared:jvmTest --tests "*EscPosCoffeeVivoTest*"
```

Esperado nesta ordem: primeiro erro de **compilação** (`Unresolved reference: com.github.anastaciocintra`) se o Step 2 não foi aplicado; depois de aplicado, o teste compila e passa. Se ele passar de primeira, a dependência já resolveu — siga.

- [ ] **Step 5: Verificar a API real do escpos-coffee antes de programar contra ela**

O plano assume nomes de método da biblioteca. Confirme-os agora, não na Tarefa 4:

```bash
find ~/.gradle/caches/modules-2 -name "escpos-coffee-4.1.0.jar" | head -1
```

Com o caminho retornado, liste a API pública das três classes usadas neste plano:

```bash
javap -classpath <caminho-do-jar> com.github.anastaciocintra.escpos.EscPos com.github.anastaciocintra.escpos.Style com.github.anastaciocintra.escpos.barcode.QRCode
```

Confirme que existem: `EscPos(OutputStream)`, `initializePrinter()`, `flush()`, `close()`, e em `QRCode` os métodos `setSize(int)`, `setErrorCorrectionLevel(...)` e `setJustification(...)`, além de um `EscPos.write(BarCodeWrapperInterface, String)`.

**Se algum nome divergir**, anote a assinatura real e use-a nas Tarefas 4 e 6 — o restante do plano não depende de mais nada da biblioteca. Registre a divergência no commit da Tarefa 4.

- [ ] **Step 6: Rodar o teste no Android (host) e confirmar que passa**

```bash
./gradlew :shared:testAndroidHostTest --tests "*EscPosCoffeeVivoTest*"
```

Esperado: PASS. Isto prova que a classe compila e roda no source set do Android — mas roda na JVM do host, **não** prova nada sobre o dispositivo. É o Step 8 que prova.

- [ ] **Step 7: Blindar o D8 contra as classes de `java.awt` e `javax.print` do jar**

O jar do escpos-coffee contém `CoffeeImageImpl` (usa `java.awt.image`) e `PrinterOutputStream` (usa `javax.print`) — nenhum dos dois existe no Android. Elas nunca são carregadas por este app, mas o D8/R8 reclama ao vê-las.

Em `androidApp/proguard-rules.pro`, adicione:

```proguard
# escpos-coffee traz classes de desktop que este app nunca carrega
-dontwarn java.awt.**
-dontwarn javax.print.**
-dontwarn com.github.anastaciocintra.escpos.image.CoffeeImageImpl
-dontwarn com.github.anastaciocintra.output.PrinterOutputStream
```

Em `androidApp/build.gradle.kts`, dentro de `android { compileOptions { ... } }`, adicione a linha do desugaring:

```kotlin
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
```

E no bloco `dependencies` do mesmo arquivo:

```kotlin
    coreLibraryDesugaring(libs.desugar.jdkLibs)
```

- [ ] **Step 8: Escrever e rodar o teste no dispositivo real**

Este é o teste que importa. Crie `shared/src/androidDeviceTest/kotlin/com/fatec/printec/impressao/EscPosCoffeeDispositivoTest.kt`:

```kotlin
package com.fatec.printec.impressao

import com.github.anastaciocintra.escpos.EscPos
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class EscPosCoffeeDispositivoTest {

    @Test
    fun escpos_coffee_carrega_e_executa_no_android() {
        val saida = ByteArrayOutputStream()
        EscPos(saida).use { it.initializePrinter() }
        assertEquals(listOf<Byte>(0x1B, 0x40), saida.toByteArray().toList())
    }
}
```

Adicione a dependência do runner ao source set de dispositivo, em `shared/build.gradle.kts`, junto às demais declarações de `sourceSets`:

```kotlin
        androidDeviceTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.androidx.testExt.junit)
        }
```

**Não há dispositivo nem emulador disponíveis nesta máquina** (`adb devices` vazio, nenhum AVD criado). O arquivo de teste acima é entregável e fica no repositório, mas a execução em aparelho entra no checklist de hardware da Tarefa 11.

No lugar dela, rode a verificação que **não** precisa de dispositivo e retira a maior parte do mesmo risco — o D8 dexando o jar do escpos-coffee:

```bash
./gradlew :androidApp:assembleDebug
```

Esperado: BUILD SUCCESSFUL. Isto prova que as classes da biblioteca são convertidas para dex sem referências irresolvíveis. Se as regras do Step 7 não bastarem, o erro aparece aqui, citando `java.awt` ou `javax.print`.

**Se o dex falhar mencionando classes ausentes**, o risco nº 3 se materializou: reporte antes de seguir para a Tarefa 2. A alternativa é não usar a biblioteca no `androidMain` e emitir `GS ( k` à mão — decisão que exige o usuário.

- [ ] **Step 9: Commit**

```bash
git add gradle/libs.versions.toml shared/build.gradle.kts androidApp/build.gradle.kts androidApp/proguard-rules.pro shared/src/jvmCommonTest shared/src/androidDeviceTest
git commit -m "build: source set jvmCommon e escpos-coffee validado nas duas plataformas"
```

---

## Task 2: Modelo do documento e quebra de linha

**Files:**
- Create: `shared/src/commonMain/kotlin/com/fatec/printec/etiqueta/LabelDocument.kt`
- Create: `shared/src/commonMain/kotlin/com/fatec/printec/etiqueta/Impressora.kt`
- Create: `shared/src/commonMain/kotlin/com/fatec/printec/etiqueta/QuebraDeLinha.kt`
- Test: `shared/src/commonTest/kotlin/com/fatec/printec/etiqueta/QuebraDeLinhaTest.kt`

**Interfaces:**
- Consumes: nada
- Produces:
  - `Alinhamento` (enum: `ESQUERDA`, `CENTRO`, `DIREITA`)
  - `Bloco` (sealed interface: `Titulo(texto)`, `Linha(texto, escala, alinhamento, negrito)`, `Qr(conteudo, tamanhoModulo)`, `Avanco(milimetros)`)
  - `LabelDocument(blocos: List<Bloco>, copias: Int)`
  - `Impressora.COLUNAS_BASE = 32`, `Impressora.DOTS_LARGURA = 384`, `Impressora.DOTS_POR_MM = 8`
  - `QuebraDeLinha.colunasPara(escala: Int): Int`
  - `QuebraDeLinha.quebrar(texto: String, colunas: Int): List<String>`

- [ ] **Step 1: Escrever os testes de quebra de linha**

Crie `shared/src/commonTest/kotlin/com/fatec/printec/etiqueta/QuebraDeLinhaTest.kt`:

```kotlin
package com.fatec.printec.etiqueta

import kotlin.test.Test
import kotlin.test.assertEquals

class QuebraDeLinhaTest {

    @Test
    fun `texto menor que a largura fica em uma linha`() {
        assertEquals(listOf("bancada A"), QuebraDeLinha.quebrar("bancada A", 32))
    }

    @Test
    fun `texto com exatamente 32 caracteres nao quebra`() {
        val texto = "a".repeat(32)
        assertEquals(listOf(texto), QuebraDeLinha.quebrar(texto, 32))
    }

    @Test
    fun `texto com 33 caracteres quebra em duas linhas`() {
        val texto = "a".repeat(33)
        assertEquals(listOf("a".repeat(32), "a"), QuebraDeLinha.quebrar(texto, 32))
    }

    @Test
    fun `quebra preferencialmente no espaco entre palavras`() {
        val texto = "peca de reposicao para bancada central"
        assertEquals(
            listOf("peca de reposicao para bancada", "central"),
            QuebraDeLinha.quebrar(texto, 32),
        )
    }

    @Test
    fun `palavra maior que a linha e cortada em vez de sumir`() {
        val texto = "b".repeat(40)
        assertEquals(listOf("b".repeat(32), "b".repeat(8)), QuebraDeLinha.quebrar(texto, 32))
    }

    @Test
    fun `acentos contam como um caractere`() {
        // Na PC860 cada acentuada ocupa 1 byte, entao conta 1 coluna.
        val texto = "ação não coração"  // 16 caracteres
        assertEquals(listOf(texto), QuebraDeLinha.quebrar(texto, 16))
    }

    @Test
    fun `texto vazio produz uma linha vazia`() {
        assertEquals(listOf(""), QuebraDeLinha.quebrar("", 32))
    }

    @Test
    fun `escala dobrada reduz as colunas pela metade`() {
        assertEquals(32, QuebraDeLinha.colunasPara(1))
        assertEquals(16, QuebraDeLinha.colunasPara(2))
        assertEquals(8, QuebraDeLinha.colunasPara(4))
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

```bash
./gradlew :shared:jvmTest --tests "*QuebraDeLinhaTest*"
```

Esperado: FAIL na compilação — `Unresolved reference: QuebraDeLinha`.

- [ ] **Step 3: Criar as constantes da impressora**

Crie `shared/src/commonMain/kotlin/com/fatec/printec/etiqueta/Impressora.kt`:

```kotlin
package com.fatec.printec.etiqueta

/**
 * Constantes físicas da Lintian LT-8359, apuradas pelo autoteste da impressora.
 * DOTS_LARGURA é inferência (32 colunas x 12 dots da Font A) até ser confirmada
 * em papel pela etiqueta de calibração.
 */
object Impressora {
    const val COLUNAS_BASE = 32
    const val DOTS_LARGURA = 384
    const val DOTS_POR_MM = 8
}
```

- [ ] **Step 4: Criar o modelo do documento**

Crie `shared/src/commonMain/kotlin/com/fatec/printec/etiqueta/LabelDocument.kt`:

```kotlin
package com.fatec.printec.etiqueta

enum class Alinhamento { ESQUERDA, CENTRO, DIREITA }

sealed interface Bloco {
    /** Açúcar para Linha(escala = 2, CENTRO, negrito = true). */
    data class Titulo(val texto: String) : Bloco

    data class Linha(
        val texto: String,
        val escala: Int = 1,
        val alinhamento: Alinhamento = Alinhamento.ESQUERDA,
        val negrito: Boolean = false,
    ) : Bloco

    data class Qr(val conteudo: String, val tamanhoModulo: Int = 6) : Bloco

    data class Avanco(val milimetros: Int) : Bloco
}

data class LabelDocument(
    val blocos: List<Bloco> = emptyList(),
    val copias: Int = 1,
)

/** Normaliza Titulo para Linha, para que renderizador e preview tratem um caso a menos. */
fun Bloco.normalizado(): Bloco = when (this) {
    is Bloco.Titulo -> Bloco.Linha(
        texto = texto,
        escala = 2,
        alinhamento = Alinhamento.CENTRO,
        negrito = true,
    )
    else -> this
}
```

- [ ] **Step 5: Implementar a quebra de linha**

Crie `shared/src/commonMain/kotlin/com/fatec/printec/etiqueta/QuebraDeLinha.kt`:

```kotlin
package com.fatec.printec.etiqueta

/**
 * Regra de quebra COMPARTILHADA entre o preview e o renderizador ESC/POS.
 * Se as duas divergirem, o WYSIWYG vira mentira — por isso mora num lugar só.
 */
object QuebraDeLinha {

    fun colunasPara(escala: Int): Int = Impressora.COLUNAS_BASE / escala

    fun quebrar(texto: String, colunas: Int): List<String> {
        if (texto.isEmpty()) return listOf("")

        val linhas = mutableListOf<String>()
        var restante = texto

        while (restante.isNotEmpty()) {
            if (restante.length <= colunas) {
                linhas += restante
                break
            }
            val janela = restante.substring(0, colunas + 1)
            val corte = janela.lastIndexOf(' ')
            if (corte <= 0) {
                // Palavra maior que a linha: corta na largura em vez de descartar.
                linhas += restante.substring(0, colunas)
                restante = restante.substring(colunas)
            } else {
                linhas += restante.substring(0, corte)
                restante = restante.substring(corte + 1)
            }
        }
        return linhas
    }
}
```

- [ ] **Step 6: Rodar e confirmar que passa**

```bash
./gradlew :shared:jvmTest --tests "*QuebraDeLinhaTest*"
```

Esperado: PASS, 8 testes.

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/fatec/printec/etiqueta shared/src/commonTest/kotlin/com/fatec/printec/etiqueta
git commit -m "feat: modelo de etiqueta e quebra de linha em 32 colunas"
```

---

## Task 3: Codificação PC860

**Decisão fixada:** tabela própria, **não** `Charset.forName("cp860")`. O provedor de charsets do Android é reduzido e pode não incluir a CP860 — e `androidHostTest` roda na JVM do host, então um teste ali daria falso positivo. Uma tabela em Kotlin puro é determinística nas duas plataformas e testável em `commonTest`.

Só o intervalo `0x80..0xAF` é mapeado (letras acentuadas e pontuação). Box-drawing e grego (`0xB0..0xFF`) ficam de fora de propósito: não servem a etiquetas e transcrevê-los à mão é fonte de erro.

**Files:**
- Create: `shared/src/commonMain/kotlin/com/fatec/printec/etiqueta/Pc860.kt`
- Test: `shared/src/commonTest/kotlin/com/fatec/printec/etiqueta/Pc860Test.kt`

**Interfaces:**
- Consumes: nada
- Produces:
  - `class ResultadoCodificacao(val bytes: ByteArray, val substituidos: Int)`
  - `Pc860.codificar(texto: String): ResultadoCodificacao`

- [ ] **Step 1: Escrever os testes**

Crie `shared/src/commonTest/kotlin/com/fatec/printec/etiqueta/Pc860Test.kt`:

```kotlin
package com.fatec.printec.etiqueta

import kotlin.test.Test
import kotlin.test.assertEquals

class Pc860Test {

    @Test
    fun `ascii passa inalterado`() {
        val r = Pc860.codificar("Bancada A1")
        assertEquals("Bancada A1".map { it.code.toByte() }, r.bytes.toList())
        assertEquals(0, r.substituidos)
    }

    @Test
    fun `acentuadas do portugues viram os bytes da PC860`() {
        val r = Pc860.codificar("ação")
        // a=0x61, ç=0x87, ã=0x84, o=0x6F
        assertEquals(listOf<Byte>(0x61, 0x87.toByte(), 0x84.toByte(), 0x6F), r.bytes.toList())
        assertEquals(0, r.substituidos)
    }

    @Test
    fun `maiusculas acentuadas tambem sao mapeadas`() {
        val r = Pc860.codificar("ÃÇÉÔÚ")
        assertEquals(
            listOf<Byte>(0x8E.toByte(), 0x80.toByte(), 0x90.toByte(), 0x8C.toByte(), 0x96.toByte()),
            r.bytes.toList(),
        )
        assertEquals(0, r.substituidos)
    }

    @Test
    fun `caractere fora da pagina vira interrogacao e e contado`() {
        val r = Pc860.codificar("preço €")
        assertEquals(1, r.substituidos)
        assertEquals('?'.code.toByte(), r.bytes.last())
    }

    @Test
    fun `emoji conta cada unidade nao mapeavel`() {
        val r = Pc860.codificar("ok 🎉")
        // O emoji ocupa duas unidades UTF-16, ambas nao mapeaveis.
        assertEquals(2, r.substituidos)
    }

    @Test
    fun `texto vazio produz zero bytes`() {
        val r = Pc860.codificar("")
        assertEquals(0, r.bytes.size)
        assertEquals(0, r.substituidos)
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

```bash
./gradlew :shared:jvmTest --tests "*Pc860Test*"
```

Esperado: FAIL na compilação — `Unresolved reference: Pc860`.

- [ ] **Step 3: Implementar a tabela**

Crie `shared/src/commonMain/kotlin/com/fatec/printec/etiqueta/Pc860.kt`:

```kotlin
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
}
```

- [ ] **Step 4: Rodar e confirmar que passa**

```bash
./gradlew :shared:jvmTest --tests "*Pc860Test*"
```

Esperado: PASS, 6 testes.

- [ ] **Step 5: Confirmar que a tabela também vale no Android**

```bash
./gradlew :shared:testAndroidHostTest --tests "*Pc860Test*"
```

Esperado: PASS. Como não há `Charset` envolvido, o resultado do host vale para o dispositivo.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/fatec/printec/etiqueta/Pc860.kt shared/src/commonTest/kotlin/com/fatec/printec/etiqueta/Pc860Test.kt
git commit -m "feat: codificacao PC860 com substituicao contabilizada"
```

---

## Task 4: EscPosRenderer

**Decisão fixada sobre o uso da biblioteca:** o escpos-coffee é usado onde ganha — o **QR** (`GS ( k` tem quatro comandos encadeados e comprimento little-endian; errar ali é fácil) e, na Tarefa 6, a saída `javax.print`. Os comandos simples (`ESC @`, `ESC t`, `ESC a`, `GS !`, `ESC E`, `ESC J`) são 2–3 bytes cada e são justamente o que os testes verificam byte a byte — emiti-los direto mantém o renderizador testável sem depender do comportamento interno da biblioteca. Não "corrija" isso trocando por chamadas de alto nível.

**Files:**
- Create: `shared/src/jvmCommonMain/kotlin/com/fatec/printec/impressao/EscPosRenderer.kt`
- Test: `shared/src/jvmCommonTest/kotlin/com/fatec/printec/impressao/EscPosRendererTest.kt`

**Interfaces:**
- Consumes: `LabelDocument`, `Bloco`, `Alinhamento`, `Bloco.normalizado()`, `QuebraDeLinha`, `Impressora`, `Pc860.codificar`
- Produces: `EscPosRenderer.renderizar(documento: LabelDocument, avancoFinalMm: Int): ByteArray`

- [ ] **Step 1: Escrever os testes de bytes exatos**

Crie `shared/src/jvmCommonTest/kotlin/com/fatec/printec/impressao/EscPosRendererTest.kt`:

```kotlin
package com.fatec.printec.impressao

import com.fatec.printec.etiqueta.Alinhamento
import com.fatec.printec.etiqueta.Bloco
import com.fatec.printec.etiqueta.LabelDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EscPosRendererTest {

    private fun hex(bytes: ByteArray) = bytes.joinToString(" ") { "%02X".format(it) }

    @Test
    fun `documento vazio ainda inicializa e seleciona a code page`() {
        val bytes = EscPosRenderer.renderizar(LabelDocument(), avancoFinalMm = 0)
        assertTrue(hex(bytes).startsWith("1B 40 1B 74 03"), "obtido: ${hex(bytes)}")
    }

    @Test
    fun `linha simples sai com alinhamento escala e texto`() {
        val doc = LabelDocument(listOf(Bloco.Linha("AB")))
        val hex = hex(EscPosRenderer.renderizar(doc, avancoFinalMm = 0))
        // ESC a 0 (esquerda), GS ! 0x00 (escala 1), ESC E 0 (sem negrito), "AB", LF
        assertTrue(hex.contains("1B 61 00 1D 21 00 1B 45 00 41 42 0A"), "obtido: $hex")
    }

    @Test
    fun `titulo vira escala dobrada centralizada e negrito`() {
        val doc = LabelDocument(listOf(Bloco.Titulo("OI")))
        val hex = hex(EscPosRenderer.renderizar(doc, avancoFinalMm = 0))
        // ESC a 1 (centro), GS ! 0x11 (2x2), ESC E 1 (negrito)
        assertTrue(hex.contains("1B 61 01 1D 21 11 1B 45 01 4F 49 0A"), "obtido: $hex")
    }

    @Test
    fun `alinhamento a direita usa ESC a 2`() {
        val doc = LabelDocument(listOf(Bloco.Linha("X", alinhamento = Alinhamento.DIREITA)))
        assertTrue(hex(EscPosRenderer.renderizar(doc, 0)).contains("1B 61 02"))
    }

    @Test
    fun `texto longo e quebrado em duas linhas com um LF cada`() {
        // 'z' (0x7A) de proposito: 'a' seria 0x61, que e o SEGUNDO byte do
        // comando ESC a (alinhamento). Contar 0x61 no array inteiro somaria o
        // byte do comando as letras do texto e daria 34 em vez de 33.
        val doc = LabelDocument(listOf(Bloco.Linha("z".repeat(33))))
        val bytes = EscPosRenderer.renderizar(doc, avancoFinalMm = 0)
        assertEquals(2, bytes.count { it == 0x0A.toByte() })
        // 32 letras na primeira linha, 1 na segunda
        assertEquals(33, bytes.count { it == 'z'.code.toByte() })
    }

    @Test
    fun `acentos usam os bytes da PC860`() {
        val doc = LabelDocument(listOf(Bloco.Linha("ação")))
        assertTrue(hex(EscPosRenderer.renderizar(doc, 0)).contains("61 87 84 6F"))
    }

    @Test
    fun `qr emite o prefixo GS parenteses k`() {
        val doc = LabelDocument(listOf(Bloco.Qr("https://exemplo.com")))
        assertTrue(hex(EscPosRenderer.renderizar(doc, 0)).contains("1D 28 6B"))
    }

    @Test
    fun `avanco final e convertido de mm para dots`() {
        val bytes = EscPosRenderer.renderizar(LabelDocument(), avancoFinalMm = 3)
        // ESC J 24  (3 mm x 8 dots/mm)
        assertTrue(hex(bytes).endsWith("1B 4A 18"), "obtido: ${hex(bytes)}")
    }

    @Test
    fun `bloco de avanco explicito tambem vira ESC J`() {
        val doc = LabelDocument(listOf(Bloco.Avanco(2)))
        assertTrue(hex(EscPosRenderer.renderizar(doc, 0)).contains("1B 4A 10"))
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

```bash
./gradlew :shared:jvmTest --tests "*EscPosRendererTest*"
```

Esperado: FAIL na compilação — `Unresolved reference: EscPosRenderer`.

- [ ] **Step 3: Implementar o renderizador**

Crie `shared/src/jvmCommonMain/kotlin/com/fatec/printec/impressao/EscPosRenderer.kt`:

```kotlin
package com.fatec.printec.impressao

import com.fatec.printec.etiqueta.Alinhamento
import com.fatec.printec.etiqueta.Bloco
import com.fatec.printec.etiqueta.Impressora
import com.fatec.printec.etiqueta.LabelDocument
import com.fatec.printec.etiqueta.Pc860
import com.fatec.printec.etiqueta.QuebraDeLinha
import com.fatec.printec.etiqueta.normalizado
import com.github.anastaciocintra.escpos.EscPos
import com.github.anastaciocintra.escpos.barcode.QRCode
import java.io.ByteArrayOutputStream

object EscPosRenderer {

    private const val ESC = 0x1B
    private const val GS = 0x1D
    private const val LF = 0x0A

    fun renderizar(documento: LabelDocument, avancoFinalMm: Int): ByteArray {
        val saida = ByteArrayOutputStream()

        saida.comando(ESC, 0x40)              // ESC @  — inicializa
        saida.comando(ESC, 0x74, 0x03)        // ESC t 3 — PC860, enviada sempre

        documento.blocos.map { it.normalizado() }.forEach { bloco ->
            when (bloco) {
                is Bloco.Linha -> saida.escreverLinha(bloco)
                is Bloco.Qr -> saida.escreverQr(bloco)
                is Bloco.Avanco -> saida.avancar(bloco.milimetros)
                is Bloco.Titulo -> error("normalizado() deveria ter convertido Titulo em Linha")
            }
        }

        if (avancoFinalMm > 0) saida.avancar(avancoFinalMm)
        return saida.toByteArray()
    }

    private fun ByteArrayOutputStream.escreverLinha(bloco: Bloco.Linha) {
        comando(ESC, 0x61, bloco.alinhamento.codigo())
        comando(GS, 0x21, tamanho(bloco.escala))
        comando(ESC, 0x45, if (bloco.negrito) 1 else 0)

        val colunas = QuebraDeLinha.colunasPara(bloco.escala)
        QuebraDeLinha.quebrar(bloco.texto, colunas).forEach { linha ->
            write(Pc860.codificar(linha).bytes)
            write(LF)
        }
    }

    private fun ByteArrayOutputStream.escreverQr(bloco: Bloco.Qr) {
        // Delegado ao escpos-coffee: GS ( k encadeia quatro comandos com
        // comprimento little-endian, e errar isso a mao e facil demais.
        val escpos = EscPos(this)
        val qr = QRCode().apply {
            setSize(bloco.tamanhoModulo)
            setJustification(EscPos.Justification.Center)
        }
        escpos.write(qr, bloco.conteudo)
        escpos.flush()
    }

    private fun ByteArrayOutputStream.avancar(milimetros: Int) {
        val dots = (milimetros * Impressora.DOTS_POR_MM).coerceIn(0, 255)
        comando(ESC, 0x4A, dots)             // ESC J n
    }

    private fun ByteArrayOutputStream.comando(vararg bytes: Int) {
        bytes.forEach { write(it) }
    }

    private fun Alinhamento.codigo(): Int = when (this) {
        Alinhamento.ESQUERDA -> 0
        Alinhamento.CENTRO -> 1
        Alinhamento.DIREITA -> 2
    }

    /** GS ! n — nibble alto = largura, nibble baixo = altura, ambos 0-based. */
    private fun tamanho(escala: Int): Int {
        val n = (escala - 1).coerceIn(0, 7)
        return (n shl 4) or n
    }
}
```

- [ ] **Step 4: Rodar e confirmar que passa**

```bash
./gradlew :shared:jvmTest --tests "*EscPosRendererTest*"
```

Esperado: PASS. **Se o teste do QR falhar**, compare a assinatura real de `QRCode` e `EscPos.write` anotada no Step 5 da Tarefa 1 e ajuste apenas `escreverQr`.

- [ ] **Step 5: Rodar a suíte inteira nas duas plataformas**

```bash
./gradlew :shared:jvmTest :shared:testAndroidHostTest
```

Esperado: PASS em ambas.

- [ ] **Step 6: Commit**

```bash
git add shared/src/jvmCommonMain shared/src/jvmCommonTest
git commit -m "feat: renderizador ESC/POS com testes byte a byte"
```

---

## Task 5: Persistência com SQLDelight

**Files:**
- Modify: `shared/build.gradle.kts`
- Modify: `build.gradle.kts` (registrar o plugin como `apply false`)
- Create: `shared/src/commonMain/sqldelight/com/fatec/printec/db/Printec.sq`
- Create: `shared/src/commonMain/kotlin/com/fatec/printec/dados/LabelStore.kt`
- Create: `shared/src/commonMain/kotlin/com/fatec/printec/dados/FabricaDeDriver.kt`
- Create: `shared/src/commonMain/kotlin/com/fatec/printec/dados/LabelStoreSqlDelight.kt`
- Create: `shared/src/androidMain/kotlin/com/fatec/printec/dados/DriverAndroid.kt`
- Create: `shared/src/jvmMain/kotlin/com/fatec/printec/dados/DriverDesktop.kt`
- Test: `shared/src/jvmTest/kotlin/com/fatec/printec/dados/LabelStoreTest.kt`

**Interfaces:**
- Consumes: `LabelDocument`, `Bloco`, `Alinhamento`
- Produces:
  - `enum class PerfilMidia { CONTINUO, GAP }`
  - `data class Configuracoes(impressoraId: String?, impressoraNome: String?, perfilMidia: PerfilMidia, avancoFinalMm: Int)`
  - `data class EtiquetaSalva(id: Long, nome: String, documento: LabelDocument)`
  - `interface FabricaDeDriver { fun criar(): SqlDriver }`
  - `interface LabelStore` com `configuracoes(): Flow<Configuracoes>`, `etiquetasSalvas(): Flow<List<EtiquetaSalva>>`, `salvarConfiguracoes(c)`, `salvarEtiqueta(nome, documento): Long`, `excluirEtiqueta(id)`, `salvarRascunho(documento)`, `carregarRascunho(): LabelDocument?`
  - `class LabelStoreSqlDelight(driver: SqlDriver) : LabelStore`
  - `class DriverAndroid(context: Context) : FabricaDeDriver` · `class DriverDesktop(caminho: String) : FabricaDeDriver`

- [ ] **Step 1: Registrar o plugin SQLDelight**

Em `build.gradle.kts` (raiz), adicione ao bloco `plugins`:

```kotlin
    alias(libs.plugins.sqldelight) apply false
```

Em `shared/build.gradle.kts`, adicione ao bloco `plugins`:

```kotlin
    alias(libs.plugins.sqldelight)
```

E, no nível superior do arquivo (fora do bloco `kotlin`), adicione:

```kotlin
sqldelight {
    databases {
        create("PrintecDatabase") {
            packageName.set("com.fatec.printec.db")
        }
    }
}
```

Adicione as dependências nos source sets correspondentes, dentro de `kotlin { sourceSets { ... } }`:

```kotlin
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.sqldelight.coroutinesExtensions)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.androidDriver)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqliteDriver)
        }
        jvmTest.dependencies {
            implementation(libs.sqldelight.sqliteDriver)
            implementation(libs.kotlinx.coroutinesTest)
        }
```

> As linhas de `commonMain.dependencies` se somam às que já existem no arquivo — não substitua o bloco.

- [ ] **Step 2: Escrever o esquema e as consultas**

Crie `shared/src/commonMain/sqldelight/com/fatec/printec/db/Printec.sq`:

```sql
CREATE TABLE etiqueta (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  nome          TEXT,
  eh_rascunho   INTEGER NOT NULL DEFAULT 0,
  copias        INTEGER NOT NULL DEFAULT 1,
  criada_em     INTEGER NOT NULL,
  atualizada_em INTEGER NOT NULL
);

CREATE TABLE bloco (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  etiqueta_id INTEGER NOT NULL REFERENCES etiqueta(id) ON DELETE CASCADE,
  ordem       INTEGER NOT NULL,
  tipo        TEXT    NOT NULL,
  conteudo    TEXT,
  escala      INTEGER NOT NULL DEFAULT 1,
  alinhamento TEXT    NOT NULL DEFAULT 'ESQUERDA',
  negrito     INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_bloco_etiqueta ON bloco(etiqueta_id, ordem);

CREATE TABLE configuracao (
  id              INTEGER PRIMARY KEY CHECK (id = 0),
  impressora_id   TEXT,
  impressora_nome TEXT,
  perfil_midia    TEXT    NOT NULL DEFAULT 'CONTINUO',
  avanco_final_mm INTEGER NOT NULL DEFAULT 3
);

garantirConfiguracao:
INSERT OR IGNORE INTO configuracao (id) VALUES (0);

lerConfiguracao:
SELECT * FROM configuracao WHERE id = 0;

atualizarConfiguracao:
UPDATE configuracao
SET impressora_id = ?, impressora_nome = ?, perfil_midia = ?, avanco_final_mm = ?
WHERE id = 0;

inserirEtiqueta:
INSERT INTO etiqueta (nome, eh_rascunho, copias, criada_em, atualizada_em)
VALUES (?, ?, ?, ?, ?);

ultimoId:
SELECT last_insert_rowid();

inserirBloco:
INSERT INTO bloco (etiqueta_id, ordem, tipo, conteudo, escala, alinhamento, negrito)
VALUES (?, ?, ?, ?, ?, ?, ?);

listarEtiquetas:
SELECT * FROM etiqueta WHERE eh_rascunho = 0 ORDER BY atualizada_em DESC;

lerRascunho:
SELECT * FROM etiqueta WHERE eh_rascunho = 1 LIMIT 1;

blocosDe:
SELECT * FROM bloco WHERE etiqueta_id = ? ORDER BY ordem;

excluirEtiqueta:
DELETE FROM etiqueta WHERE id = ?;

excluirRascunhos:
DELETE FROM etiqueta WHERE eh_rascunho = 1;

contarBlocos:
SELECT count(*) FROM bloco;
```

- [ ] **Step 3: Escrever os testes da store**

Crie `shared/src/jvmTest/kotlin/com/fatec/printec/dados/LabelStoreTest.kt`:

```kotlin
package com.fatec.printec.dados

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
```

- [ ] **Step 4: Rodar e confirmar que falha**

```bash
./gradlew :shared:jvmTest --tests "*LabelStoreTest*"
```

Esperado: FAIL na compilação — `Unresolved reference: LabelStoreSqlDelight`.

- [ ] **Step 5: Criar os tipos e a interface da store**

Crie `shared/src/commonMain/kotlin/com/fatec/printec/dados/LabelStore.kt`:

```kotlin
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
```

Crie `shared/src/commonMain/kotlin/com/fatec/printec/dados/FabricaDeDriver.kt`:

```kotlin
package com.fatec.printec.dados

import app.cash.sqldelight.db.SqlDriver

/**
 * Interface em vez de expect/actual: o Android precisa de Context no construtor
 * e o Desktop de um caminho de arquivo. Cada app monta o seu e injeta.
 */
interface FabricaDeDriver {
    fun criar(): SqlDriver
}
```

- [ ] **Step 6: Implementar a store**

Crie `shared/src/commonMain/kotlin/com/fatec/printec/dados/LabelStoreSqlDelight.kt`:

```kotlin
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

    override suspend fun excluirEtiqueta(id: Long) = q.excluirEtiqueta(id)

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
```

Crie `shared/src/androidMain/kotlin/com/fatec/printec/dados/Relogio.android.kt`:

```kotlin
package com.fatec.printec.dados

internal actual fun agoraEmMillis(): Long = System.currentTimeMillis()
```

Crie `shared/src/jvmMain/kotlin/com/fatec/printec/dados/Relogio.jvm.kt`:

```kotlin
package com.fatec.printec.dados

internal actual fun agoraEmMillis(): Long = System.currentTimeMillis()
```

- [ ] **Step 7: Implementar os drivers de plataforma**

Crie `shared/src/androidMain/kotlin/com/fatec/printec/dados/DriverAndroid.kt`:

```kotlin
package com.fatec.printec.dados

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.fatec.printec.db.PrintecDatabase

class DriverAndroid(private val context: Context) : FabricaDeDriver {
    override fun criar(): SqlDriver =
        AndroidSqliteDriver(PrintecDatabase.Schema, context, "printec.db").also {
            // O SQLite ignora ON DELETE CASCADE sem esta pragma, e ela e por conexao.
            it.execute(null, "PRAGMA foreign_keys=ON;", 0)
        }
}
```

Crie `shared/src/jvmMain/kotlin/com/fatec/printec/dados/DriverDesktop.kt`:

```kotlin
package com.fatec.printec.dados

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.fatec.printec.db.PrintecDatabase
import java.io.File

class DriverDesktop(private val diretorio: File = diretorioPadrao()) : FabricaDeDriver {

    override fun criar(): SqlDriver {
        diretorio.mkdirs()
        val arquivo = File(diretorio, "printec.db")
        val existia = arquivo.exists()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${arquivo.absolutePath}")
        if (!existia) PrintecDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys=ON;", 0)
        return driver
    }

    companion object {
        fun diretorioPadrao(): File {
            val appData = System.getenv("APPDATA")
            return if (appData != null) File(appData, "Printec")
            else File(System.getProperty("user.home"), ".printec")
        }
    }
}
```

- [ ] **Step 8: Rodar e confirmar que passa**

```bash
./gradlew :shared:jvmTest --tests "*LabelStoreTest*"
```

Esperado: PASS, 5 testes. O teste de cascata é o que prova que a pragma `foreign_keys` foi aplicada — se ele falhar com contagem maior que zero, a pragma não pegou.

- [ ] **Step 9: Commit**

```bash
git add build.gradle.kts shared/build.gradle.kts shared/src/commonMain/sqldelight shared/src/commonMain/kotlin/com/fatec/printec/dados shared/src/androidMain/kotlin/com/fatec/printec/dados shared/src/jvmMain/kotlin/com/fatec/printec/dados shared/src/jvmTest/kotlin/com/fatec/printec/dados
git commit -m "feat: persistencia SQLDelight com cascata verificada"
```

---

## Task 6: Transporte — interface e implementação Desktop

**Files:**
- Create: `shared/src/commonMain/kotlin/com/fatec/printec/impressao/PrinterTransport.kt`
- Create: `shared/src/jvmMain/kotlin/com/fatec/printec/impressao/DesktopUsbTransport.kt`
- Test: `shared/src/jvmTest/kotlin/com/fatec/printec/impressao/DesktopUsbTransportTest.kt`

**Interfaces:**
- Consumes: nada
- Produces:
  - `data class PrinterTarget(val id: String, val nome: String)`
  - `sealed class ErroImpressao : Exception()` com `NaoPareada`, `PermissaoNegada`, `NenhumaImpressoraSelecionada`, `FalhaAoConectar(causa)`, `FalhaAoEscrever(causa)`
  - `interface PrinterTransport { suspend fun listarDestinos(): List<PrinterTarget>; suspend fun imprimir(destino: PrinterTarget, bytes: ByteArray) }`
  - `class DesktopUsbTransport : PrinterTransport`

- [ ] **Step 1: Escrever o teste**

Crie `shared/src/jvmTest/kotlin/com/fatec/printec/impressao/DesktopUsbTransportTest.kt`:

```kotlin
package com.fatec.printec.impressao

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopUsbTransportTest {

    @Test
    fun `listar destinos nao estoura mesmo sem impressora instalada`() = runTest {
        val destinos = DesktopUsbTransport().listarDestinos()
        assertTrue(destinos.all { it.id.isNotBlank() && it.nome.isNotBlank() })
    }

    @Test
    fun `imprimir para destino inexistente reporta FalhaAoConectar`() = runTest {
        assertFailsWith<ErroImpressao.FalhaAoConectar> {
            DesktopUsbTransport().imprimir(
                PrinterTarget("impressora-que-nao-existe-xyz", "Fantasma"),
                byteArrayOf(0x1B, 0x40),
            )
        }
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

```bash
./gradlew :shared:jvmTest --tests "*DesktopUsbTransportTest*"
```

Esperado: FAIL na compilação — `Unresolved reference: DesktopUsbTransport`.

- [ ] **Step 3: Criar a interface e os erros**

Crie `shared/src/commonMain/kotlin/com/fatec/printec/impressao/PrinterTransport.kt`:

```kotlin
package com.fatec.printec.impressao

data class PrinterTarget(val id: String, val nome: String)

/**
 * Falha de impressora e fluxo normal neste app, nao excecao rara: a LT-8359
 * hiberna sozinha em 10 minutos. Por isso os erros sao tipados — cada um vira
 * uma mensagem com uma acao concreta na UI.
 */
sealed class ErroImpressao(mensagem: String) : Exception(mensagem) {
    data object NaoPareada : ErroImpressao("Impressora não pareada")
    data object PermissaoNegada : ErroImpressao("Permissão de Bluetooth necessária")
    data object NenhumaImpressoraSelecionada : ErroImpressao("Escolha uma impressora")
    data class FalhaAoConectar(val causa: String) : ErroImpressao(
        "A impressora pode estar desligada — ela hiberna após 10 minutos",
    )
    data class FalhaAoEscrever(val causa: String) : ErroImpressao(
        "A conexão caiu durante a impressão",
    )
    /** Falha ANTES de falar com a impressora: renderizar, salvar rascunho. */
    data class FalhaAoPreparar(val causa: String) : ErroImpressao(
        "Não foi possível preparar a etiqueta",
    )
}

interface PrinterTransport {
    suspend fun listarDestinos(): List<PrinterTarget>

    /** Abre, escreve e fecha. Nunca mantém a conexão aberta entre trabalhos. */
    suspend fun imprimir(destino: PrinterTarget, bytes: ByteArray)
}
```

- [ ] **Step 4: Implementar o transporte Desktop**

Crie `shared/src/jvmMain/kotlin/com/fatec/printec/impressao/DesktopUsbTransport.kt`:

```kotlin
package com.fatec.printec.impressao

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.print.DocFlavor
import javax.print.PrintService
import javax.print.PrintServiceLookup
import javax.print.SimpleDoc
import javax.print.attribute.HashPrintRequestAttributeSet

/**
 * Envia bytes crus pelo spooler do Windows. Se o driver da impressora insistir
 * em converter a impressao em imagem, o plano B da spec e escrever direto na
 * porta COM/USB com jSerialComm — nao implementado ate que se prove necessario.
 */
class DesktopUsbTransport : PrinterTransport {

    override suspend fun listarDestinos(): List<PrinterTarget> = withContext(Dispatchers.IO) {
        servicos().map { PrinterTarget(id = it.name, nome = it.name) }
    }

    override suspend fun imprimir(destino: PrinterTarget, bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            val servico = servicos().firstOrNull { it.name == destino.id }
                ?: throw ErroImpressao.FalhaAoConectar("Impressora '${destino.nome}' não encontrada")

            try {
                val doc = SimpleDoc(bytes, DocFlavor.BYTE_ARRAY.AUTOSENSE, null)
                servico.createPrintJob().print(doc, HashPrintRequestAttributeSet())
            } catch (e: Exception) {
                throw ErroImpressao.FalhaAoEscrever(e.message ?: e::class.simpleName.orEmpty())
            }
        }

    /**
     * O subsistema de impressao do Windows estoura sozinho em casos reais —
     * spooler em mau estado, entrada de driver corrompida. Nenhuma excecao crua
     * pode escapar deste transporte: a UI so sabe tratar ErroImpressao, e o
     * ViewModel da Tarefa 8 captura exatamente esse tipo. Uma excecao nao tipada
     * atravessaria a corrotina e derrubaria o app.
     */
    private fun servicos(): List<PrintService> = try {
        PrintServiceLookup.lookupPrintServices(null, null).toList()
    } catch (e: Exception) {
        throw ErroImpressao.FalhaAoConectar(e.message ?: e::class.simpleName.orEmpty())
    }
}
```

- [ ] **Step 5: Rodar e confirmar que passa**

```bash
./gradlew :shared:jvmTest --tests "*DesktopUsbTransportTest*"
```

Esperado: PASS, 2 testes. Eles rodam sem hardware: o primeiro aceita lista vazia, o segundo exercita o caminho de erro.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/fatec/printec/impressao shared/src/jvmMain/kotlin/com/fatec/printec/impressao shared/src/jvmTest/kotlin/com/fatec/printec/impressao
git commit -m "feat: transporte de impressao e implementacao USB no desktop"
```

---

## Task 7: Transporte Bluetooth no Android

**Files:**
- Create: `shared/src/androidMain/kotlin/com/fatec/printec/impressao/AndroidBluetoothTransport.kt`
- Modify: `androidApp/src/main/AndroidManifest.xml`
- Test: `shared/src/androidHostTest/kotlin/com/fatec/printec/impressao/AndroidBluetoothTransportTest.kt`

**Interfaces:**
- Consumes: `PrinterTransport`, `PrinterTarget`, `ErroImpressao`
- Produces: `class AndroidBluetoothTransport(context: Context) : PrinterTransport`

- [ ] **Step 1: Declarar as permissões**

Em `androidApp/src/main/AndroidManifest.xml`, adicione antes de `<application>`:

```xml
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

`maxSdkVersion="30"` impede que as permissões antigas apareçam nos aparelhos novos. **Nenhuma permissão de localização** — é o que a decisão de usar só dispositivos pareados nos compra.

- [ ] **Step 2: Escrever o teste**

Crie `shared/src/androidHostTest/kotlin/com/fatec/printec/impressao/AndroidBluetoothTransportTest.kt`:

```kotlin
package com.fatec.printec.impressao

import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidBluetoothTransportTest {

    @Test
    fun `o UUID de porta serial e o padrao SPP`() {
        assertEquals(
            "00001101-0000-1000-8000-00805F9B34FB",
            AndroidBluetoothTransport.UUID_SPP.toString().uppercase(),
        )
    }
}
```

> O restante desta classe depende de `BluetoothAdapter` e só se verifica em dispositivo. O checklist manual da Tarefa 11 cobre isso, em Android 9 **e** Android 12+, porque os caminhos de permissão são diferentes.

- [ ] **Step 3: Rodar e confirmar que falha**

```bash
./gradlew :shared:testAndroidHostTest --tests "*AndroidBluetoothTransportTest*"
```

Esperado: FAIL na compilação — `Unresolved reference: AndroidBluetoothTransport`.

- [ ] **Step 4: Implementar o transporte**

Crie `shared/src/androidMain/kotlin/com/fatec/printec/impressao/AndroidBluetoothTransport.kt`:

```kotlin
package com.fatec.printec.impressao

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Bluetooth clássico (SPP) sobre dispositivos JA PAREADOS.
 *
 * Nao ha descoberta nem pareamento no app, de proposito: descoberta na API 28
 * exigiria permissao de localizacao em runtime, e a impressora — que hiberna em
 * 10 minutos — nao aparece na varredura quando esta dormindo. A lista de
 * pareados a mostra mesmo desligada.
 */
class AndroidBluetoothTransport(private val context: Context) : PrinterTransport {

    private val adaptador: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    override suspend fun listarDestinos(): List<PrinterTarget> = withContext(Dispatchers.IO) {
        exigirPermissao()
        val adaptador = adaptador ?: return@withContext emptyList()
        adaptador.bondedDevices.orEmpty().map { PrinterTarget(it.address, it.name ?: it.address) }
    }

    override suspend fun imprimir(destino: PrinterTarget, bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            exigirPermissao()
            val adaptador = adaptador ?: throw ErroImpressao.FalhaAoConectar("Bluetooth indisponível")
            val dispositivo = adaptador.bondedDevices.orEmpty()
                .firstOrNull { it.address == destino.id }
                ?: throw ErroImpressao.NaoPareada

            val socket = try {
                dispositivo.createRfcommSocketToServiceRecord(UUID_SPP)
            } catch (e: Exception) {
                throw ErroImpressao.FalhaAoConectar(e.message.orEmpty())
            }

            try {
                socket.connect()
            } catch (e: Exception) {
                runCatching { socket.close() }
                throw ErroImpressao.FalhaAoConectar(e.message.orEmpty())
            }

            try {
                socket.outputStream.use { saida ->
                    saida.write(bytes)
                    saida.flush()
                }
            } catch (e: Exception) {
                throw ErroImpressao.FalhaAoEscrever(e.message.orEmpty())
            } finally {
                runCatching { socket.close() }
            }
        }

    private fun exigirPermissao() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return  // API 28: permissao de instalacao
        // Context.checkSelfPermission existe desde a API 23 e este ramo so roda
        // na API 31+, entao nao ha motivo para depender do androidx-core so por
        // causa do ContextCompat.
        val concedida = context.checkSelfPermission(
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
        if (!concedida) throw ErroImpressao.PermissaoNegada
    }

    companion object {
        val UUID_SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
```

Nenhuma dependência nova é necessária: `Context.checkSelfPermission` é API de
plataforma desde a API 23, e o `minSdk` deste projeto é 24.

- [ ] **Step 5: Rodar e confirmar que passa**

```bash
./gradlew :shared:testAndroidHostTest --tests "*AndroidBluetoothTransportTest*"
```

Esperado: PASS.

- [ ] **Step 6: Commit**

```bash
git add shared/src/androidMain/kotlin/com/fatec/printec/impressao shared/src/androidHostTest/kotlin/com/fatec/printec/impressao androidApp/src/main/AndroidManifest.xml shared/build.gradle.kts
git commit -m "feat: transporte bluetooth SPP sobre dispositivos pareados"
```

---

## Task 8: ViewModel e máquina de estados da impressão

**Files:**
- Create: `shared/src/commonMain/kotlin/com/fatec/printec/ui/EtiquetaViewModel.kt`
- Test: `shared/src/commonTest/kotlin/com/fatec/printec/ui/EtiquetaViewModelTest.kt`

**Interfaces:**
- Consumes: `LabelDocument`, `Bloco`, `LabelStore`, `Configuracoes`, `PrinterTransport`, `PrinterTarget`, `ErroImpressao`, `EscPosRenderer`
- Produces:
  - `sealed interface EstadoImpressao` com `Ocioso`, `Renderizando`, `Conectando`, `Enviando`, `Sucesso`, `Falha(erro)`
  - `class EtiquetaViewModel(store, transporte, renderizar: (LabelDocument, Int) -> ByteArray)`
  - `viewModel.estado: StateFlow<EstadoImpressao>`, `viewModel.documento: StateFlow<LabelDocument>`
  - `viewModel.imprimir()`, `atualizarDocumento(doc)`

> `renderizar` entra como parâmetro de função porque `EscPosRenderer` vive em `jvmCommonMain` e o ViewModel em `commonMain`. Isso também torna o ViewModel testável sem a biblioteca.

- [ ] **Step 1: Escrever os testes da máquina de estados**

Crie `shared/src/commonTest/kotlin/com/fatec/printec/ui/EtiquetaViewModelTest.kt`:

```kotlin
package com.fatec.printec.ui

import com.fatec.printec.dados.Configuracoes
import com.fatec.printec.dados.EtiquetaSalva
import com.fatec.printec.dados.LabelStore
import com.fatec.printec.etiqueta.Bloco
import com.fatec.printec.etiqueta.LabelDocument
import com.fatec.printec.impressao.ErroImpressao
import com.fatec.printec.impressao.PrinterTarget
import com.fatec.printec.impressao.PrinterTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class StoreFalsa(
    private val config: Configuracoes = Configuracoes("00:11:22", "KPrinter", avancoFinalMm = 3),
) : LabelStore {
    var rascunhoSalvo: LabelDocument? = null
    override fun configuracoes(): Flow<Configuracoes> = MutableStateFlow(config)
    override fun etiquetasSalvas(): Flow<List<EtiquetaSalva>> = flowOf(emptyList())
    override suspend fun salvarConfiguracoes(configuracoes: Configuracoes) = Unit
    override suspend fun salvarEtiqueta(nome: String, documento: LabelDocument): Long = 1L
    override suspend fun excluirEtiqueta(id: Long) = Unit
    override suspend fun salvarRascunho(documento: LabelDocument) { rascunhoSalvo = documento }
    override suspend fun carregarRascunho(): LabelDocument? = null
}

private class TransporteFalso(
    private val falhasAntesDeAceitar: Int = 0,
    private val erro: ErroImpressao = ErroImpressao.FalhaAoConectar("dormindo"),
) : PrinterTransport {
    var tentativas = 0
    var bytesRecebidos: ByteArray? = null
    override suspend fun listarDestinos() = listOf(PrinterTarget("00:11:22", "KPrinter"))
    override suspend fun imprimir(destino: PrinterTarget, bytes: ByteArray) {
        tentativas++
        if (tentativas <= falhasAntesDeAceitar) throw erro
        bytesRecebidos = bytes
    }
}

class EtiquetaViewModelTest {

    private val documento = LabelDocument(listOf(Bloco.Titulo("Bancada A")))
    private fun renderizadorFalso(doc: LabelDocument, avanco: Int) = byteArrayOf(1, 2, 3)

    @Test
    fun `impressao bem sucedida termina em Sucesso`() = runTest {
        val transporte = TransporteFalso()
        val vm = EtiquetaViewModel(StoreFalsa(), transporte, ::renderizadorFalso)
        vm.atualizarDocumento(documento)
        vm.imprimir()
        assertIs<EstadoImpressao.Sucesso>(vm.estado.value)
        assertEquals(listOf<Byte>(1, 2, 3), transporte.bytesRecebidos?.toList())
    }

    @Test
    fun `uma falha de conexao e superada pelo retry automatico`() = runTest {
        val transporte = TransporteFalso(falhasAntesDeAceitar = 1)
        val vm = EtiquetaViewModel(StoreFalsa(), transporte, ::renderizadorFalso)
        vm.atualizarDocumento(documento)
        vm.imprimir()
        assertEquals(2, transporte.tentativas)
        assertIs<EstadoImpressao.Sucesso>(vm.estado.value)
    }

    @Test
    fun `duas falhas seguidas viram estado de Falha e param de tentar`() = runTest {
        val transporte = TransporteFalso(falhasAntesDeAceitar = 5)
        val vm = EtiquetaViewModel(StoreFalsa(), transporte, ::renderizadorFalso)
        vm.atualizarDocumento(documento)
        vm.imprimir()
        assertEquals(2, transporte.tentativas)
        val estado = vm.estado.value
        assertIs<EstadoImpressao.Falha>(estado)
        assertIs<ErroImpressao.FalhaAoConectar>(estado.erro)
    }

    @Test
    fun `sem impressora configurada nao chega a tentar conectar`() = runTest {
        val transporte = TransporteFalso()
        val vm = EtiquetaViewModel(
            StoreFalsa(Configuracoes(impressoraId = null)), transporte, ::renderizadorFalso,
        )
        vm.atualizarDocumento(documento)
        vm.imprimir()
        assertEquals(0, transporte.tentativas)
        val estado = vm.estado.value
        assertIs<EstadoImpressao.Falha>(estado)
        assertIs<ErroImpressao.NenhumaImpressoraSelecionada>(estado.erro)
    }

    @Test
    fun `erro nao repetivel falha na primeira tentativa sem gastar a segunda`() = runTest {
        val transporte = TransporteFalso(
            falhasAntesDeAceitar = 5,
            erro = ErroImpressao.PermissaoNegada,
        )
        val vm = EtiquetaViewModel(StoreFalsa(), transporte, ::renderizadorFalso)
        vm.atualizarDocumento(documento)
        vm.imprimir()
        assertEquals(1, transporte.tentativas)   // NAO gastou a segunda
        val estado = vm.estado.value
        assertIs<EstadoImpressao.Falha>(estado)
        assertIs<ErroImpressao.PermissaoNegada>(estado.erro)
    }

    @Test
    fun `falha ao renderizar vira Falha em vez de escapar`() = runTest {
        val vm = EtiquetaViewModel(StoreFalsa(), TransporteFalso()) { _, _ ->
            throw IllegalStateException("documento invalido")
        }
        vm.atualizarDocumento(documento)
        vm.imprimir()
        val estado = vm.estado.value
        assertIs<EstadoImpressao.Falha>(estado)
        assertIs<ErroImpressao.FalhaAoPreparar>(estado.erro)
    }

    @Test
    fun `imprimir salva o rascunho`() = runTest {
        val store = StoreFalsa()
        val vm = EtiquetaViewModel(store, TransporteFalso(), ::renderizadorFalso)
        vm.atualizarDocumento(documento)
        vm.imprimir()
        assertEquals(documento, store.rascunhoSalvo)
    }

    @Test
    fun `as copias resultam em um envio por copia numa conexao logica`() = runTest {
        val transporte = TransporteFalso()
        val vm = EtiquetaViewModel(StoreFalsa(), transporte, ::renderizadorFalso)
        vm.atualizarDocumento(documento.copy(copias = 3))
        vm.imprimir()
        assertEquals(1, transporte.tentativas)
        assertEquals(9, transporte.bytesRecebidos?.size)  // 3 bytes x 3 copias
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

```bash
./gradlew :shared:jvmTest --tests "*EtiquetaViewModelTest*"
```

Esperado: FAIL na compilação — `Unresolved reference: EtiquetaViewModel`.

- [ ] **Step 3: Implementar o ViewModel**

Crie `shared/src/commonMain/kotlin/com/fatec/printec/ui/EtiquetaViewModel.kt`:

```kotlin
package com.fatec.printec.ui

import com.fatec.printec.dados.LabelStore
import com.fatec.printec.etiqueta.LabelDocument
import com.fatec.printec.impressao.ErroImpressao
import com.fatec.printec.impressao.PrinterTarget
import com.fatec.printec.impressao.PrinterTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

sealed interface EstadoImpressao {
    data object Ocioso : EstadoImpressao
    data object Renderizando : EstadoImpressao
    data object Conectando : EstadoImpressao
    data object Enviando : EstadoImpressao
    data object Sucesso : EstadoImpressao
    data class Falha(val erro: ErroImpressao) : EstadoImpressao
}

class EtiquetaViewModel(
    private val store: LabelStore,
    private val transporte: PrinterTransport,
    private val renderizar: (LabelDocument, Int) -> ByteArray,
) {
    private val _documento = MutableStateFlow(LabelDocument())
    val documento: StateFlow<LabelDocument> = _documento.asStateFlow()

    private val _estado = MutableStateFlow<EstadoImpressao>(EstadoImpressao.Ocioso)
    val estado: StateFlow<EstadoImpressao> = _estado.asStateFlow()

    fun atualizarDocumento(novo: LabelDocument) {
        _documento.value = novo
    }

    /**
     * Chamado ao trocar de tela. Sem isto, o estado e global ao ViewModel e a
     * mensagem de uma reimpressao feita na aba "Salvas" reaparece na tela de
     * composicao, atribuida a um documento que nao tem nada a ver com ela.
     * So limpa estados terminais — nunca interrompe uma impressao em andamento.
     */
    fun limparEstadoSeConcluido() {
        val atual = _estado.value
        if (atual is EstadoImpressao.Sucesso || atual is EstadoImpressao.Falha) {
            _estado.value = EstadoImpressao.Ocioso
        }
    }

    suspend fun imprimir() {
        // Guarda de reentrancia. A UI desabilita o botao IMPRIMIR, mas ha mais
        // de um ponto de entrada — compor, reimprimir pela lista de salvas, e a
        // etiqueta de teste nas configuracoes — e nem todos tem botao para
        // desabilitar. Duas impressoes concorrentes gastam papel de verdade.
        val emAndamento = _estado.value
        if (emAndamento is EstadoImpressao.Renderizando ||
            emAndamento is EstadoImpressao.Conectando ||
            emAndamento is EstadoImpressao.Enviando
        ) return

        val doc = _documento.value
        val config = store.configuracoes().first()

        val id = config.impressoraId
        if (id.isNullOrBlank()) {
            _estado.value = EstadoImpressao.Falha(ErroImpressao.NenhumaImpressoraSelecionada)
            return
        }

        _estado.value = EstadoImpressao.Renderizando
        val umaCopia = try {
            // O rascunho e salvo na impressao, nao a cada tecla.
            store.salvarRascunho(doc)
            renderizar(doc, config.avancoFinalMm)
        } catch (e: CancellationException) {
            throw e   // cancelamento nao e erro de impressao
        } catch (e: ErroImpressao) {
            _estado.value = EstadoImpressao.Falha(e)
            return
        } catch (e: Exception) {
            // Nada pode escapar daqui sem virar estado. Uma excecao crua
            // deixaria a UI presa em "Renderizando" para sempre, sem caminho
            // para Falha e sem o usuario poder tentar de novo.
            _estado.value = EstadoImpressao.Falha(
                ErroImpressao.FalhaAoPreparar(e.message ?: e::class.simpleName.orEmpty()),
            )
            return
        }
        val payload = ByteArray(umaCopia.size * doc.copias.coerceAtLeast(1))
        repeat(doc.copias.coerceAtLeast(1)) { i ->
            umaCopia.copyInto(payload, destinationOffset = i * umaCopia.size)
        }

        val destino = PrinterTarget(id, config.impressoraNome ?: id)

        // Retry unico: a LT-8359 costuma acordar na primeira tentativa e
        // aceitar a segunda. Mais que isso so faria o usuario esperar.
        repeat(2) { tentativa ->
            _estado.value = EstadoImpressao.Conectando
            try {
                _estado.value = EstadoImpressao.Enviando
                transporte.imprimir(destino, payload)
                _estado.value = EstadoImpressao.Sucesso
                return
            } catch (e: ErroImpressao) {
                val ultima = tentativa == 1
                val naoAdiantaRepetir = e !is ErroImpressao.FalhaAoConectar
                if (ultima || naoAdiantaRepetir) {
                    _estado.value = EstadoImpressao.Falha(e)
                    return
                }
            }
        }
    }
}
```

- [ ] **Step 4: Rodar e confirmar que passa**

```bash
./gradlew :shared:jvmTest --tests "*EtiquetaViewModelTest*"
```

Esperado: PASS, 6 testes.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/fatec/printec/ui shared/src/commonTest/kotlin/com/fatec/printec/ui
git commit -m "feat: maquina de estados de impressao com retry unico"
```

---

## Task 9: Pré-visualização

**Files:**
- Create: `shared/src/commonMain/kotlin/com/fatec/printec/ui/PreviewEtiqueta.kt`
- Test: `shared/src/commonTest/kotlin/com/fatec/printec/ui/LinhasDePreviewTest.kt`

**Interfaces:**
- Consumes: `LabelDocument`, `Bloco`, `QuebraDeLinha`, `Pc860`, `Bloco.normalizado()`
- Produces:
  - `data class LinhaDePreview(val texto: String, val escala: Int, val alinhamento: Alinhamento, val negrito: Boolean)`
  - `fun linhasDePreview(documento: LabelDocument): List<LinhaDePreview>`
  - `fun caracteresSubstituidos(documento: LabelDocument): Int`
  - `@Composable fun PreviewEtiqueta(documento: LabelDocument, modifier: Modifier)`

- [ ] **Step 1: Escrever os testes da lógica de preview**

Crie `shared/src/commonTest/kotlin/com/fatec/printec/ui/LinhasDePreviewTest.kt`:

```kotlin
package com.fatec.printec.ui

import com.fatec.printec.etiqueta.Alinhamento
import com.fatec.printec.etiqueta.Bloco
import com.fatec.printec.etiqueta.LabelDocument
import kotlin.test.Test
import kotlin.test.assertEquals

class LinhasDePreviewTest {

    @Test
    fun `o preview quebra igual ao renderizador`() {
        val doc = LabelDocument(listOf(Bloco.Linha("a".repeat(33))))
        val linhas = linhasDePreview(doc)
        assertEquals(2, linhas.size)
        assertEquals(32, linhas[0].texto.length)
    }

    @Test
    fun `titulo aparece em escala 2 centralizado`() {
        val linhas = linhasDePreview(LabelDocument(listOf(Bloco.Titulo("Oi"))))
        assertEquals(2, linhas.single().escala)
        assertEquals(Alinhamento.CENTRO, linhas.single().alinhamento)
    }

    @Test
    fun `titulo longo quebra em 16 colunas e nao em 32`() {
        val linhas = linhasDePreview(LabelDocument(listOf(Bloco.Titulo("a".repeat(17)))))
        assertEquals(2, linhas.size)
        assertEquals(16, linhas[0].texto.length)
    }

    @Test
    fun `caracteres nao suportados sao contados para o aviso`() {
        val doc = LabelDocument(listOf(Bloco.Linha("preço €"), Bloco.Linha("ok 🎉")))
        assertEquals(3, caracteresSubstituidos(doc))
    }

    @Test
    fun `texto totalmente suportado nao gera aviso`() {
        val doc = LabelDocument(listOf(Bloco.Linha("ação não coração")))
        assertEquals(0, caracteresSubstituidos(doc))
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

```bash
./gradlew :shared:jvmTest --tests "*LinhasDePreviewTest*"
```

Esperado: FAIL na compilação — `Unresolved reference: linhasDePreview`.

- [ ] **Step 3: Implementar o preview**

Crie `shared/src/commonMain/kotlin/com/fatec/printec/ui/PreviewEtiqueta.kt`:

```kotlin
package com.fatec.printec.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fatec.printec.etiqueta.Alinhamento
import com.fatec.printec.etiqueta.Bloco
import com.fatec.printec.etiqueta.LabelDocument
import com.fatec.printec.etiqueta.Pc860
import com.fatec.printec.etiqueta.QuebraDeLinha
import com.fatec.printec.etiqueta.normalizado

data class LinhaDePreview(
    val texto: String,
    val escala: Int,
    val alinhamento: Alinhamento,
    val negrito: Boolean,
)

/**
 * Usa a MESMA QuebraDeLinha do EscPosRenderer. Se as duas divergirem, o que o
 * usuario ve deixa de ser o que sai no papel.
 */
fun linhasDePreview(documento: LabelDocument): List<LinhaDePreview> =
    documento.blocos.map { it.normalizado() }.flatMap { bloco ->
        when (bloco) {
            is Bloco.Linha -> QuebraDeLinha
                .quebrar(bloco.texto, QuebraDeLinha.colunasPara(bloco.escala))
                .map { LinhaDePreview(it, bloco.escala, bloco.alinhamento, bloco.negrito) }
            is Bloco.Qr -> listOf(
                LinhaDePreview("[ QR: ${bloco.conteudo.take(20)} ]", 1, Alinhamento.CENTRO, false),
            )
            is Bloco.Avanco -> emptyList()
            is Bloco.Titulo -> emptyList()  // normalizado() ja converteu
        }
    }

fun caracteresSubstituidos(documento: LabelDocument): Int =
    documento.blocos.map { it.normalizado() }.sumOf { bloco ->
        when (bloco) {
            is Bloco.Linha -> Pc860.codificar(bloco.texto).substituidos
            else -> 0
        }
    }

@Composable
fun PreviewEtiqueta(documento: LabelDocument, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(8.dp),
    ) {
        linhasDePreview(documento).forEach { linha ->
            Text(
                text = linha.texto,
                color = Color.Black,
                fontFamily = FontFamily.Monospace,
                fontSize = (11 * linha.escala).sp,
                fontWeight = if (linha.negrito) FontWeight.Bold else FontWeight.Normal,
                textAlign = when (linha.alinhamento) {
                    Alinhamento.ESQUERDA -> TextAlign.Start
                    Alinhamento.CENTRO -> TextAlign.Center
                    Alinhamento.DIREITA -> TextAlign.End
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val substituidos = caracteresSubstituidos(documento)
        if (substituidos > 0) {
            Text(
                text = "$substituidos caractere(s) não suportado(s) serão substituídos por ?",
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
```

- [ ] **Step 4: Rodar e confirmar que passa**

```bash
./gradlew :shared:jvmTest --tests "*LinhasDePreviewTest*"
```

Esperado: PASS, 5 testes.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/fatec/printec/ui/PreviewEtiqueta.kt shared/src/commonTest/kotlin/com/fatec/printec/ui/LinhasDePreviewTest.kt
git commit -m "feat: pre-visualizacao compartilhando a quebra de linha do renderizador"
```

---

## Task 10: Telas e navegação

**Files:**
- Create: `shared/src/commonMain/kotlin/com/fatec/printec/ui/Navegacao.kt`
- Create: `shared/src/commonMain/kotlin/com/fatec/printec/ui/TelaCompor.kt`
- Create: `shared/src/commonMain/kotlin/com/fatec/printec/ui/TelaConfiguracoes.kt`
- Create: `shared/src/commonMain/kotlin/com/fatec/printec/ui/TelaEtiquetas.kt`
- Create: `shared/src/commonMain/kotlin/com/fatec/printec/ui/FormularioEtiqueta.kt`
- Test: `shared/src/commonTest/kotlin/com/fatec/printec/ui/FormularioEtiquetaTest.kt`

**Interfaces:**
- Consumes: `EtiquetaViewModel`, `EstadoImpressao`, `PreviewEtiqueta`, `LabelStore`, `Configuracoes`, `PerfilMidia`, `PrinterTarget`, `LabelDocument`, `Bloco`
- Produces:
  - `data class CamposDoFormulario(titulo: String, linhas: List<String>, qr: String, copias: Int)`
  - `fun CamposDoFormulario.paraDocumento(): LabelDocument`
  - `fun LabelDocument.paraCampos(): CamposDoFormulario`
  - `@Composable fun AppEtiquetas(vm, store, destinos: suspend () -> List<PrinterTarget>, aoAbrirConfigBluetooth: () -> Unit, aoImprimirTeste: () -> Unit)`
  - `@Composable fun TelaConfiguracoes(store, destinos, aoAbrirConfigBluetooth, aoImprimirTeste)` — inclui o campo de avanço final e o botão **IMPRIMIR TESTE**

- [ ] **Step 1: Escrever os testes da conversão formulário ↔ documento**

Crie `shared/src/commonTest/kotlin/com/fatec/printec/ui/FormularioEtiquetaTest.kt`:

```kotlin
package com.fatec.printec.ui

import com.fatec.printec.etiqueta.Bloco
import com.fatec.printec.etiqueta.LabelDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormularioEtiquetaTest {

    @Test
    fun `campos preenchidos viram titulo linhas e qr nesta ordem`() {
        val campos = CamposDoFormulario(
            titulo = "Bancada A",
            linhas = listOf("linha 1", "linha 2"),
            qr = "https://exemplo.com",
            copias = 2,
        )
        val doc = campos.paraDocumento()
        assertEquals(Bloco.Titulo("Bancada A"), doc.blocos[0])
        assertEquals(Bloco.Linha("linha 1"), doc.blocos[1])
        assertEquals(Bloco.Linha("linha 2"), doc.blocos[2])
        assertEquals(Bloco.Qr("https://exemplo.com"), doc.blocos[3])
        assertEquals(2, doc.copias)
    }

    @Test
    fun `campos vazios nao viram blocos`() {
        val doc = CamposDoFormulario("", listOf("", "  "), "", 1).paraDocumento()
        assertTrue(doc.blocos.isEmpty())
    }

    @Test
    fun `ida e volta preserva o conteudo`() {
        val campos = CamposDoFormulario("T", listOf("a", "b"), "q", 4)
        assertEquals(campos, campos.paraDocumento().paraCampos())
    }

    @Test
    fun `documento sem titulo volta com titulo vazio`() {
        val doc = LabelDocument(listOf(Bloco.Linha("so uma linha")))
        val campos = doc.paraCampos()
        assertEquals("", campos.titulo)
        assertEquals(listOf("so uma linha"), campos.linhas)
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

```bash
./gradlew :shared:jvmTest --tests "*FormularioEtiquetaTest*"
```

Esperado: FAIL na compilação — `Unresolved reference: CamposDoFormulario`.

- [ ] **Step 3: Implementar a conversão**

Crie `shared/src/commonMain/kotlin/com/fatec/printec/ui/FormularioEtiqueta.kt`:

```kotlin
package com.fatec.printec.ui

import com.fatec.printec.etiqueta.Bloco
import com.fatec.printec.etiqueta.LabelDocument

data class CamposDoFormulario(
    val titulo: String = "",
    val linhas: List<String> = listOf(""),
    val qr: String = "",
    val copias: Int = 1,
)

fun CamposDoFormulario.paraDocumento(): LabelDocument {
    val blocos = buildList {
        if (titulo.isNotBlank()) add(Bloco.Titulo(titulo))
        linhas.filter { it.isNotBlank() }.forEach { add(Bloco.Linha(it)) }
        if (qr.isNotBlank()) add(Bloco.Qr(qr))
    }
    return LabelDocument(blocos, copias)
}

fun LabelDocument.paraCampos(): CamposDoFormulario = CamposDoFormulario(
    titulo = blocos.filterIsInstance<Bloco.Titulo>().firstOrNull()?.texto.orEmpty(),
    linhas = blocos.filterIsInstance<Bloco.Linha>().map { it.texto }.ifEmpty { listOf("") },
    qr = blocos.filterIsInstance<Bloco.Qr>().firstOrNull()?.conteudo.orEmpty(),
    copias = copias,
)
```

- [ ] **Step 4: Rodar e confirmar que passa**

```bash
./gradlew :shared:jvmTest --tests "*FormularioEtiquetaTest*"
```

Esperado: PASS, 4 testes.

- [ ] **Step 5: Criar a navegação**

Crie `shared/src/commonMain/kotlin/com/fatec/printec/ui/Navegacao.kt`:

```kotlin
package com.fatec.printec.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fatec.printec.dados.LabelStore
import kotlinx.coroutines.launch

enum class Tela(val rotulo: String) {
    COMPOR("Compor"),
    ETIQUETAS("Salvas"),
    CONFIGURACOES("Ajustes"),
}

@Composable
fun AppEtiquetas(
    vm: EtiquetaViewModel,
    store: LabelStore,
    destinos: suspend () -> List<com.fatec.printec.impressao.PrinterTarget>,
    aoAbrirConfigBluetooth: () -> Unit,
    aoImprimirTeste: () -> Unit,
) {
    var tela by remember { mutableStateOf(Tela.COMPOR) }

    // Escopo do APP, nao da tela. `AppEtiquetas` permanece composto ao trocar de
    // aba; as telas internas nao. Lancar a impressao no escopo de uma tela faria
    // a troca de aba CANCELAR a impressao — e como o cancelamento nao escreve
    // estado terminal, o botao IMPRIMIR ficaria desabilitado para sempre e o
    // fluxo de bytes poderia ser cortado no meio do envio para a impressora.
    val escopoDoApp = rememberCoroutineScope()
    val imprimir: () -> Unit = { escopoDoApp.launch { vm.imprimir() } }

    LaunchedEffect(tela) { vm.limparEstadoSeConcluido() }

    MaterialTheme {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Tela.entries.forEach { destino ->
                        NavigationBarItem(
                            selected = tela == destino,
                            onClick = { tela = destino },
                            icon = {},
                            label = { Text(destino.rotulo) },
                        )
                    }
                }
            },
        ) { paddings ->
            Box(Modifier.fillMaxSize().padding(paddings)) {
                when (tela) {
                    Tela.COMPOR -> TelaCompor(vm, store, imprimir)
                    Tela.ETIQUETAS -> TelaEtiquetas(vm, store, imprimir)
                    Tela.CONFIGURACOES -> TelaConfiguracoes(
                        store, destinos, aoAbrirConfigBluetooth, aoImprimirTeste,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 6: Criar a tela de composição**

Crie `shared/src/commonMain/kotlin/com/fatec/printec/ui/TelaCompor.kt`:

```kotlin
package com.fatec.printec.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fatec.printec.dados.LabelStore
import kotlinx.coroutines.launch

@Composable
fun TelaCompor(vm: EtiquetaViewModel, store: LabelStore, imprimir: () -> Unit) {
    var campos by remember { mutableStateOf(CamposDoFormulario()) }
    var nomeParaSalvar by remember { mutableStateOf("") }
    val escopo = rememberCoroutineScope()
    val estado by vm.estado.collectAsState()

    // Restaura os ultimos valores digitados ao abrir.
    LaunchedEffect(Unit) {
        store.carregarRascunho()?.let { campos = it.paraCampos() }
    }

    LaunchedEffect(campos) { vm.atualizarDocumento(campos.paraDocumento()) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val largo = maxWidth > 600.dp
        val preview = @Composable {
            PreviewEtiqueta(campos.paraDocumento(), Modifier.padding(8.dp))
        }
        val formulario = @Composable {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = campos.titulo,
                    onValueChange = { campos = campos.copy(titulo = it) },
                    label = { Text("Título") },
                    modifier = Modifier.fillMaxWidth(),
                )
                campos.linhas.forEachIndexed { i, linha ->
                    OutlinedTextField(
                        value = linha,
                        onValueChange = {
                            campos = campos.copy(
                                linhas = campos.linhas.toMutableList().also { l -> l[i] = it },
                            )
                        },
                        label = { Text("Linha ${i + 1}") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedButton(
                    onClick = { campos = campos.copy(linhas = campos.linhas + "") },
                ) { Text("+ adicionar linha") }

                OutlinedTextField(
                    value = campos.qr,
                    onValueChange = { campos = campos.copy(qr = it) },
                    label = { Text("Conteúdo do QR") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = campos.copias.toString(),
                    onValueChange = {
                        campos = campos.copy(copias = it.toIntOrNull()?.coerceIn(1, 99) ?: 1)
                    },
                    label = { Text("Cópias") },
                    modifier = Modifier.width(120.dp),
                )

                // Sem esta guarda, um duplo toque dispara duas impressoes
                // concorrentes e saem duas etiquetas.
                val imprimindo = estado is EstadoImpressao.Renderizando ||
                    estado is EstadoImpressao.Conectando ||
                    estado is EstadoImpressao.Enviando
                Button(
                    onClick = imprimir,
                    enabled = !imprimindo,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (imprimindo) "IMPRIMINDO…" else "IMPRIMIR") }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nomeParaSalvar,
                        onValueChange = { nomeParaSalvar = it },
                        label = { Text("Nome") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        enabled = nomeParaSalvar.isNotBlank(),
                        onClick = {
                            escopo.launch {
                                store.salvarEtiqueta(nomeParaSalvar, campos.paraDocumento())
                                nomeParaSalvar = ""
                            }
                        },
                    ) { Text("Salvar") }
                }

                Text(
                    text = mensagemDe(estado),
                    color = corDe(estado),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (largo) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f)) { preview() }
                Column(Modifier.weight(1f)) { formulario() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                preview()
                formulario()
            }
        }
    }
}
```

Crie também, no mesmo arquivo, os auxiliares de mensagem:

```kotlin
private fun mensagemDe(estado: EstadoImpressao): String = when (estado) {
    EstadoImpressao.Ocioso -> ""
    EstadoImpressao.Renderizando -> "Preparando…"
    EstadoImpressao.Conectando -> "Conectando…"
    EstadoImpressao.Enviando -> "Enviando…"
    EstadoImpressao.Sucesso -> "Impresso"
    is EstadoImpressao.Falha -> estado.erro.message.orEmpty()
}

@Composable
private fun corDe(estado: EstadoImpressao) = when (estado) {
    is EstadoImpressao.Falha -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurface
}
```

- [ ] **Step 7: Criar a tela de configurações**

Crie `shared/src/commonMain/kotlin/com/fatec/printec/ui/TelaConfiguracoes.kt`:

```kotlin
package com.fatec.printec.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fatec.printec.dados.Configuracoes
import com.fatec.printec.dados.LabelStore
import com.fatec.printec.dados.PerfilMidia
import com.fatec.printec.impressao.PrinterTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun TelaConfiguracoes(
    store: LabelStore,
    destinos: suspend () -> List<PrinterTarget>,
    aoAbrirConfigBluetooth: () -> Unit,
    aoImprimirTeste: () -> Unit,
) {
    var config by remember { mutableStateOf(Configuracoes()) }
    var lista by remember { mutableStateOf(emptyList<PrinterTarget>()) }
    val escopo = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        config = store.configuracoes().first()
        lista = runCatching { destinos() }.getOrDefault(emptyList())
    }

    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Impressora")
        lista.forEach { alvo ->
            Row(Modifier.fillMaxWidth()) {
                RadioButton(
                    selected = config.impressoraId == alvo.id,
                    onClick = {
                        config = config.copy(impressoraId = alvo.id, impressoraNome = alvo.nome)
                        escopo.launch { store.salvarConfiguracoes(config) }
                    },
                )
                Text(alvo.nome)
            }
        }
        OutlinedButton(onClick = aoAbrirConfigBluetooth) {
            Text("Não encontrou sua impressora?")
        }

        Text("Mídia")
        PerfilMidia.entries.forEach { perfil ->
            Row(Modifier.fillMaxWidth()) {
                RadioButton(
                    selected = config.perfilMidia == perfil,
                    onClick = {
                        config = config.copy(perfilMidia = perfil)
                        escopo.launch { store.salvarConfiguracoes(config) }
                    },
                )
                Text(if (perfil == PerfilMidia.CONTINUO) "Contínuo" else "Etiqueta com gap")
            }
        }

        OutlinedTextField(
            value = config.avancoFinalMm.toString(),
            onValueChange = {
                config = config.copy(avancoFinalMm = it.toIntOrNull()?.coerceIn(0, 30) ?: 0)
                escopo.launch { store.salvarConfiguracoes(config) }
            },
            label = { Text("Avanço final (mm)") },
            modifier = Modifier.width(180.dp),
        )

        Button(onClick = aoImprimirTeste, modifier = Modifier.fillMaxWidth()) {
            Text("IMPRIMIR TESTE")
        }
        Text(
            "A etiqueta de teste traz a régua de 32 colunas, as escalas, acentos " +
                "e um QR. Se ela imprime, o problema está no conteúdo, não na conexão.",
        )
    }
}
```

- [ ] **Step 8: Criar a tela de etiquetas salvas**

Crie `shared/src/commonMain/kotlin/com/fatec/printec/ui/TelaEtiquetas.kt`:

```kotlin
package com.fatec.printec.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fatec.printec.dados.EtiquetaSalva
import com.fatec.printec.dados.LabelStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun TelaEtiquetas(vm: EtiquetaViewModel, store: LabelStore, imprimir: () -> Unit) {
    var etiquetas by remember { mutableStateOf(emptyList<EtiquetaSalva>()) }
    val escopo = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        store.etiquetasSalvas().collectLatest { etiquetas = it }
    }

    LazyColumn(Modifier.fillMaxWidth().padding(8.dp)) {
        items(etiquetas, key = { it.id }) { etiqueta ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(etiqueta.nome)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        vm.atualizarDocumento(etiqueta.documento)
                        imprimir()
                    }) { Text("Imprimir") }
                    OutlinedButton(onClick = {
                        escopo.launch { store.excluirEtiqueta(etiqueta.id) }
                    }) { Text("Excluir") }
                }
            }
        }
    }
}
```

- [ ] **Step 9: Compilar as duas plataformas**

```bash
./gradlew :shared:compileKotlinJvm :shared:assemble
```

Esperado: BUILD SUCCESSFUL. Corrija imports faltantes que o compilador apontar — os blocos acima listam os principais, mas o Compose Multiplatform é exigente e algum pode faltar.

- [ ] **Step 10: Commit**

```bash
git add shared/src/commonMain/kotlin/com/fatec/printec/ui shared/src/commonTest/kotlin/com/fatec/printec/ui
git commit -m "feat: telas de composicao, salvas e configuracoes"
```

---

## Task 11: Etiqueta de calibração e fiação dos dois apps

Esta é a tarefa que transforma código em app rodando. Termina com o checklist de hardware — os itens que nenhum teste automatizado responde.

**Files:**
- Create: `shared/src/commonMain/kotlin/com/fatec/printec/etiqueta/EtiquetaDeTeste.kt`
- Create: `androidApp/src/main/kotlin/com/fatec/printec/PrintecApp.kt`
- Modify: `androidApp/src/main/kotlin/com/fatec/printec/MainActivity.kt`
- Modify: `androidApp/src/main/AndroidManifest.xml`
- Modify: `desktopApp/src/main/kotlin/com/fatec/printec/main.kt`
- Modify: `shared/src/commonMain/kotlin/com/fatec/printec/App.kt`
- Delete: `shared/src/commonMain/kotlin/com/fatec/printec/Greeting.kt`, `GreetingUtil.kt`
- Test: `shared/src/commonTest/kotlin/com/fatec/printec/etiqueta/EtiquetaDeTesteTest.kt`

**Interfaces:**
- Consumes: tudo das tarefas anteriores
- Produces: `fun etiquetaDeCalibracao(): LabelDocument`

- [ ] **Step 1: Escrever o teste da etiqueta de calibração**

Crie `shared/src/commonTest/kotlin/com/fatec/printec/etiqueta/EtiquetaDeTesteTest.kt`:

```kotlin
package com.fatec.printec.etiqueta

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EtiquetaDeTesteTest {

    @Test
    fun `a regua tem exatamente 32 caracteres`() {
        val regua = etiquetaDeCalibracao().blocos
            .filterIsInstance<Bloco.Linha>()
            .first { it.texto.length >= 32 }
        assertEquals(32, regua.texto.length)
        assertEquals(1, regua.escala)
    }

    @Test
    fun `a calibracao exercita acentos do portugues`() {
        val textos = etiquetaDeCalibracao().blocos
            .filterIsInstance<Bloco.Linha>().map { it.texto }
        assertTrue(textos.any { it.contains("ação") })
    }

    @Test
    fun `a calibracao inclui um QR de referencia`() {
        assertTrue(etiquetaDeCalibracao().blocos.any { it is Bloco.Qr })
    }

    @Test
    fun `nenhum caractere da calibracao e substituido`() {
        val doc = etiquetaDeCalibracao()
        val fora = doc.blocos.filterIsInstance<Bloco.Linha>()
            .sumOf { Pc860.codificar(it.texto).substituidos }
        assertEquals(0, fora)
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

```bash
./gradlew :shared:jvmTest --tests "*EtiquetaDeTesteTest*"
```

Esperado: FAIL na compilação — `Unresolved reference: etiquetaDeCalibracao`.

- [ ] **Step 3: Implementar a etiqueta de calibração**

Crie `shared/src/commonMain/kotlin/com/fatec/printec/etiqueta/EtiquetaDeTeste.kt`:

```kotlin
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
```

- [ ] **Step 4: Rodar e confirmar que passa**

```bash
./gradlew :shared:jvmTest --tests "*EtiquetaDeTesteTest*"
```

Esperado: PASS, 4 testes.

- [ ] **Step 5: Ligar o app Android**

Crie `androidApp/src/main/kotlin/com/fatec/printec/PrintecApp.kt`:

```kotlin
package com.fatec.printec

import android.app.Application
import com.fatec.printec.dados.DriverAndroid
import com.fatec.printec.dados.LabelStoreSqlDelight
import com.fatec.printec.impressao.AndroidBluetoothTransport

class PrintecApp : Application() {
    lateinit var store: LabelStoreSqlDelight
        private set
    lateinit var transporte: AndroidBluetoothTransport
        private set

    override fun onCreate() {
        super.onCreate()
        store = LabelStoreSqlDelight(DriverAndroid(this).criar())
        transporte = AndroidBluetoothTransport(this)
    }
}
```

Registre-a em `androidApp/src/main/AndroidManifest.xml`, adicionando o atributo à tag `<application>`:

```xml
    <application
        android:name=".PrintecApp"
        android:allowBackup="true"
```

Substitua o conteúdo de `androidApp/src/main/kotlin/com/fatec/printec/MainActivity.kt`:

```kotlin
package com.fatec.printec

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import com.fatec.printec.etiqueta.etiquetaDeCalibracao
import com.fatec.printec.impressao.EscPosRenderer
import com.fatec.printec.ui.AppEtiquetas
import com.fatec.printec.ui.EtiquetaViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val pedirPermissao =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // API 28 concede na instalacao; so 12+ precisa pedir.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pedirPermissao.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val app = application as PrintecApp
        val vm = EtiquetaViewModel(app.store, app.transporte, EscPosRenderer::renderizar)

        setContent {
            val escopo = rememberCoroutineScope()
            AppEtiquetas(
                vm = vm,
                store = app.store,
                destinos = { app.transporte.listarDestinos() },
                aoAbrirConfigBluetooth = {
                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                },
                aoImprimirTeste = {
                    escopo.launch {
                        vm.atualizarDocumento(etiquetaDeCalibracao())
                        vm.imprimir()
                    }
                },
            )
        }
    }
}
```

- [ ] **Step 6: Ligar o app Desktop**

Substitua o conteúdo de `desktopApp/src/main/kotlin/com/fatec/printec/main.kt`:

```kotlin
package com.fatec.printec

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.fatec.printec.dados.DriverDesktop
import com.fatec.printec.dados.LabelStoreSqlDelight
import com.fatec.printec.etiqueta.etiquetaDeCalibracao
import com.fatec.printec.impressao.DesktopUsbTransport
import com.fatec.printec.impressao.EscPosRenderer
import com.fatec.printec.ui.AppEtiquetas
import com.fatec.printec.ui.EtiquetaViewModel
import kotlinx.coroutines.launch

fun main() = application {
    val store = LabelStoreSqlDelight(DriverDesktop().criar())
    val transporte = DesktopUsbTransport()
    val vm = EtiquetaViewModel(store, transporte, EscPosRenderer::renderizar)

    Window(onCloseRequest = ::exitApplication, title = "Printec") {
        val escopo = rememberCoroutineScope()
        AppEtiquetas(
            vm = vm,
            store = store,
            destinos = { transporte.listarDestinos() },
            aoAbrirConfigBluetooth = { },   // no desktop a conexao e USB
            aoImprimirTeste = {
                escopo.launch {
                    vm.atualizarDocumento(etiquetaDeCalibracao())
                    vm.imprimir()
                }
            },
        )
    }
}
```

- [ ] **Step 7: Remover o código do template**

`App.kt`, `Greeting.kt` e `GreetingUtil.kt` são do template e não são mais usados.

```bash
git rm shared/src/commonMain/kotlin/com/fatec/printec/App.kt shared/src/commonMain/kotlin/com/fatec/printec/Greeting.kt shared/src/commonMain/kotlin/com/fatec/printec/GreetingUtil.kt
```

Remova também as referências a `Greeting` nos testes do template (`SharedCommonTest.kt`, `SharedLogicDesktopTest.kt`, `SharedLogicAndroidHostTest.kt`) se houver — os arquivos atuais só testam `1 + 2`, então podem ficar.

- [ ] **Step 8: Rodar a suíte inteira e construir os dois apps**

```bash
./gradlew :shared:jvmTest :shared:testAndroidHostTest :androidApp:assembleDebug :desktopApp:build
```

Esperado: BUILD SUCCESSFUL, todos os testes passando.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat: etiqueta de calibracao e fiacao dos apps android e desktop"
```

- [ ] **Step 10: Executar o checklist de hardware**

Nenhum destes é verificável por teste automatizado. Rode o app de verdade e marque:

- [ ] **Desktop/USB** — imprimir a etiqueta de calibração. Se sair em branco ou como imagem rasterizada, o driver do Windows barrou o RAW: acione o plano B (`jSerialComm`) descrito na spec §7.
- [ ] **384 dots** — a régua `12345678901234567890123456789012` ocupa a largura inteira **sem quebrar em duas linhas**. Se quebrar, a largura real é menor que 384 e `Impressora.COLUNAS_BASE` precisa ser corrigida.
- [ ] **Acentuação** — a linha `ação não coração Ângela` sai legível, sem caracteres estranhos. Se sair errado, a code page ativa não é a PC860.
- [ ] **QR** — ler `PRINTEC-CALIBRACAO` com o leitor do celular.
- [ ] **Escalas** — `escala 2x` sai visivelmente maior que `escala 1x`.
- [ ] **Android 9 (API 28)** — instalar e imprimir. Não deve aparecer **nenhum** diálogo de permissão.
- [ ] **Android 12+** — instalar e imprimir. O diálogo de `BLUETOOTH_CONNECT` deve aparecer uma vez; negá-lo deve mostrar "Permissão de Bluetooth necessária", não travar.
- [ ] **Auto-off** — deixar a impressora parada por mais de 10 minutos e imprimir. Se o retry único não acordá-la, a política precisa mudar (dois retries, ou instruir o usuário a tocar no botão). **Reporte o resultado** — é a decisão que ficou pendente na spec.
- [ ] **Cópias** — imprimir com 3 cópias e conferir que saem três etiquetas iguais.

---

## Fora deste plano

Itens da spec §14 que **não** devem ser implementados aqui:

- Renderizador bitmap (logo, fonte customizada, QR ao lado do texto)
- Descoberta e pareamento Bluetooth dentro do app
- Exportar/sincronizar etiquetas
- Plano B `jSerialComm` no Desktop — só se o checklist de hardware provar necessário
- **Modo etiqueta com gap** — o perfil já existe no banco e na UI, mas o comportamento de alinhamento depende de a impressora ter sensor de gap, o que só se verifica com um rolo destacável em mãos
