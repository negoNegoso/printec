# Printec — Impressão de etiquetas via ESC/POS (Android + Desktop)

**Data:** 2026-08-24
**Status:** Design aprovado, aguardando plano de implementação
**Repositório:** projeto Kotlin Multiplatform existente (`androidApp`, `desktopApp`, `shared`)

---

## 1. Objetivo

Um aplicativo Android e Desktop onde o usuário preenche um formulário de campos
fixos, vê uma pré-visualização fiel da etiqueta e a imprime numa impressora
térmica Lintian LT-8359 — por Bluetooth no Android, por USB no Desktop.

---

## 2. Fatos apurados sobre o hardware

Obtidos do autoteste da impressora (FEED + ligar), não de suposição:

| Propriedade | Valor |
|---|---|
| Modelo / firmware | LT-8359, V2.8F (Jun 18 2025) |
| Linguagem | **ESC/POS** (lista de code pages, "Ascii print mode", QR nativo) |
| Code page padrão | **3 = PC860** (página portuguesa) |
| Largura de impressão | **384 dots** ≈ 48 mm úteis, papel 58 mm |
| Resolução | 203 dpi → **8 dots/mm** |
| Colunas (Font A, 12×24) | **32 caracteres por linha** |
| Interfaces | Bluetooth + USB Print |
| Nome Bluetooth | `KPrinter_aa9c` (padrão de SPP clássico) |
| Alimentação | Bateria 7.29 V, **auto-desliga em 10 minutos** |
| QR | Comando nativo, exercitado no autoteste |

A largura de 384 dots é **inferência** (32 caracteres × 12 dots) e deve ser
confirmada pelo "Imprimir teste" antes de ser tratada como fato.

**Consequência-chave:** o risco de a impressora falar TSPL em vez de ESC/POS
não existe. O `escpos-coffee` é a biblioteca adequada.

---

## 3. Escopo

### Dentro

- Formulário de campos fixos: título, N linhas de texto, conteúdo de QR
- Pré-visualização fiel na quebra de linha e proporção
- Impressão com quantidade de cópias
- Etiquetas salvas com nome, para reimpressão
- Últimos valores digitados restaurados ao reabrir
- Configurações: impressora, perfil de mídia, avanço final
- Etiqueta de teste/calibração
- Bluetooth (Android, dispositivos já pareados) e USB (Desktop/Windows)
- Suporte a Android 9 (API 28) até Android 15+; `minSdk` permanece 24

### Fora

- Editor livre com posicionamento de elementos (decidido: formulário fixo)
- Código de barras 1D
- Logo / imagem na etiqueta — **porta deixada aberta**, ver §5 e §14
- Descoberta e pareamento Bluetooth dentro do app — ver §14
- Sincronização entre dispositivos — ver §9

---

## 4. Arquitetura

```
commonMain
  LabelDocument         modelo puro (sem Compose, sem plataforma)
  LabelStore            interface de persistência
  PrinterTransport      interface de transporte
  PreviewRenderer       Composable de pré-visualização
  UI (3 telas) + ViewModel

jvmCommonMain           (source set intermediário: android + jvm)
  EscPosRenderer        LabelDocument -> ByteArray, via escpos-coffee

androidMain                          jvmMain
  AndroidBluetoothTransport          DesktopUsbTransport
  AndroidSqliteDriver                JdbcSqliteDriver
```

O KMP não cria automaticamente um source set compartilhado entre os alvos
`android` e `jvm`. É preciso declará-lo:

```kotlin
sourceSets {
    val jvmCommonMain by creating { dependsOn(commonMain.get()) }
    jvmMain.get().dependsOn(jvmCommonMain)
    androidMain.get().dependsOn(jvmCommonMain)
}
```

O `escpos-coffee` e o `EscPosRenderer` vivem em `jvmCommonMain` — escritos uma
vez, usados nas duas plataformas. O `PrinterOutputStream` do escpos-coffee usa
`javax.print` e portanto fica restrito a `jvmMain`.

---

## 5. Modelo de documento

```kotlin
data class LabelDocument(val blocos: List<Bloco>)

sealed interface Bloco {
    data class Titulo(val texto: String) : Bloco
    data class Linha(val texto: String, val escala: Int = 1,
                     val alinhamento: Alinhamento = Alinhamento.ESQUERDA,
                     val negrito: Boolean = false) : Bloco
    data class Qr(val conteudo: String, val tamanhoModulo: Int = 6) : Bloco
    data class Avanco(val milimetros: Int) : Bloco
}
```

`Titulo` é açúcar sintático para `Linha(escala = 2, alinhamento = CENTRO,
negrito = true)` — existe como tipo próprio porque o formulário o trata como
campo distinto, mas não introduz caso novo no renderizador.

Um único modelo é consumido por **dois renderizadores**: o `PreviewRenderer`
(tela) e o `EscPosRenderer` (bytes). A UI conhece apenas o modelo; nenhuma tela
sabe o que é ESC/POS.

Se um dia for preciso logo, fonte customizada ou QR ao lado do texto, entra um
terceiro renderizador (bitmap) sem alterar modelo, UI ou transporte.

---

## 6. Renderização: comandos nativos ESC/POS

### Decisão

Renderizar por **comandos nativos**, não por bitmap.

Esta decisão reverte uma recomendação anterior de bitmap, que se apoiava em dois
riscos que o autoteste eliminou: "pode ser TSPL" (é ESC/POS) e "acentuação vai
doer" (a code page padrão já é a portuguesa PC860).

**A favor dos comandos nativos:**

- Payload de ~200 bytes contra ~19 KB do bitmap. Numa impressora **a bateria**
  ligada por Bluetooth, isso é menos tempo de rádio e impressão instantânea.
- Fonte nativa a 203 dpi sai mais nítida que texto rasterizado e binarizado.
- Acentuação correta de fábrica.

**O que custa:** layout estritamente **linear** (bloco sob bloco, sem
posicionamento livre) e tamanhos de fonte em múltiplos inteiros de 1× a 8×.
Cobre o conteúdo definido no escopo.

### Comandos utilizados

| Função | Bytes |
|---|---|
| Inicializar | `1B 40` (`ESC @`) |
| Selecionar code page 3 (PC860) | `1B 74 03` (`ESC t 3`) |
| Alinhamento (0/1/2) | `1B 61 n` (`ESC a n`) |
| Tamanho (nibble alto = largura, baixo = altura) | `1D 21 n` (`GS ! n`) |
| Negrito | `1B 45 n` (`ESC E n`) |
| Avanço em dots | `1B 4A n` (`ESC J n`), 1 mm = 8 dots |
| QR — modelo | `1D 28 6B 04 00 31 41 n 00` |
| QR — tamanho do módulo | `1D 28 6B 03 00 31 43 n` |
| QR — correção de erro | `1D 28 6B 03 00 31 45 n` |
| QR — armazenar dados | `1D 28 6B pL pH 31 50 30 <dados>` |
| QR — imprimir | `1D 28 6B 03 00 31 51 30` |

O `ESC t 3` é enviado explicitamente mesmo sendo o padrão de fábrica: depender
de estado não declarado da impressora é fonte de bug intermitente.

### Codificação e caracteres não suportados

A PC860 cobre o português, mas não cobre emoji, `€`, ou caracteres de outros
alfabetos. Política **explícita**:

- Caracteres não mapeáveis são substituídos por `?`
- O preview **avisa** ("2 caracteres não suportados serão substituídos")

Substituição silenciosa é o pior resultado possível — o usuário só descobre no
papel.

### Fidelidade do preview

O preview renderiza numa grade de **32 colunas** com a mesma regra de quebra do
renderizador ESC/POS. Texto em escala 2× ocupa **16** colunas.

Limite honesto: o preview é fiel **na quebra e na proporção, não pixel-a-pixel**
— a fonte da tela não é a fonte interna da impressora.

A lógica de quebra é **compartilhada** entre os dois renderizadores. Se
divergirem, o WYSIWYG vira mentira.

---

## 7. Transporte

```kotlin
interface PrinterTransport {
    suspend fun listarDestinos(): List<PrinterTarget>   // id + nome legível
    suspend fun imprimir(destino: PrinterTarget, bytes: ByteArray)
}
```

### Android — Bluetooth SPP

`BluetoothSocket` no UUID SPP `00001101-0000-1000-8000-00805F9B34FB`, sobre
dispositivos **já pareados** (`bondedDevices`).

**O app não faz descoberta nem pareamento.** Em vez disso, um botão
*"Não encontrou sua impressora?"* dispara `Settings.ACTION_BLUETOOTH_SETTINGS`.

Justificativa (avaliada explicitamente, ver §13):

- Descoberta na API 28 exige `ACCESS_COARSE_LOCATION` **em runtime** — pedir
  localização para achar impressora é confuso e é fonte clássica de negativa
- A impressora **hiberna em 10 minutos** e, dormindo, **não aparece na
  varredura** — a lista de pareados a mostra mesmo desligada
- `createBond()` não pode ser silencioso: o diálogo do sistema aparece de todo
  jeito, então o ganho de UX é menor do que parece
- ~30 linhas contra ~400

### Permissões Android

```xml
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

| | Android 9 (API 28) | Android 12+ (API 31+) |
|---|---|---|
| Permissão | `BLUETOOTH` + `BLUETOOTH_ADMIN` | `BLUETOOTH_CONNECT` |
| Tipo | Instalação — sem diálogo | Runtime — pedir e tratar negativa |
| Localização | Não necessária | Não necessária |

Nenhuma permissão de localização, em nenhuma versão.

`coreLibraryDesugaring` habilitado no `androidApp` como seguro contra o
`escpos-coffee` ser uma biblioteca Java 8.

### Desktop — USB via spooler do Windows

`javax.print`: lista os `PrintService` instalados, envia bytes crus com
`DocFlavor.BYTE_ARRAY.AUTOSENSE` — o que o `PrinterOutputStream` do
escpos-coffee faz.

**Plano B**, só se o A falhar: escrita direta na porta COM/USB via
`jSerialComm`, caso o driver do Windows converta a impressão em imagem em vez
de repassar os bytes.

### Modelo de conexão: abrir, escrever, fechar

**Nenhuma conexão persistente.** Cada trabalho de impressão abre, escreve e
fecha. As N cópias vão numa conexão só.

O auto-desliga de 10 minutos torna a hibernação da impressora o caso **normal**,
não excepcional. Manter conexão aberta produziria a classe de bug "o app diz
conectado mas a impressora está desligada". O custo é ~1–2 s de handshake
Bluetooth por impressão.

**Retry único e automático** na falha de conexão — a impressora costuma acordar
na primeira tentativa e aceitar a segunda. Se o teste em hardware mostrar que um
retry não basta, a política é revista.

---

## 8. UI e fluxo

Três telas. Barra inferior no celular, rail lateral no desktop. Navegação por
`sealed class Tela` com estado — três destinos não justificam uma biblioteca de
navegação.

### Compor etiqueta (tela principal)

Preview + formulário (título, linhas com adicionar/remover, QR), campo de
cópias, botões **Imprimir** e **Salvar…**.

Layout adaptativo pela largura (`> 600.dp`): duas colunas no desktop, coluna
única com preview no topo no celular. Um `if` no layout, não duas UIs.

### Configurações

Impressora selecionada + botão de configurações Bluetooth; perfil de mídia;
avanço final em mm; **Imprimir teste**.

A etiqueta de teste imprime régua de 32 colunas, os multiplicadores de 1× a 8×,
um QR de referência e o avanço configurado. É o instrumento que confirma os 384
dots e a primeira ferramenta de diagnóstico: "imprimiu o teste? então o problema
é o conteúdo, não a conexão".

### Etiquetas salvas

Lista com reimpressão direta e carregamento no formulário.

### Perfis de mídia

| Perfil | Comportamento | Situação |
|---|---|---|
| Contínuo | Imprime e avança N mm; destaque manual | Implementar primeiro — é o papel em mãos |
| Adesivo contínuo | Idêntico ao contínuo | Sem código adicional |
| Etiqueta com gap | Requer alinhamento por sensor ou avanço fixo | **Adiado**: o autoteste não indicou sensor de gap, e testar sem rolo destacável seria adivinhação |

---

## 9. Persistência — SQLDelight

Banco local nas duas plataformas (decisão do usuário). Ganhos: transacional por
natureza, salvar uma etiqueta não reescreve tudo, `CASCADE` automático, busca
por conteúdo vira consulta. Custo: build mais complexo e disciplina de migração.

SQLDelight em vez de Room: não usa KSP, portanto sem risco de conflito de versão
com o Kotlin 2.4.10 deste projeto.

### Esquema

```sql
CREATE TABLE etiqueta (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  nome          TEXT,                        -- NULL quando é rascunho
  eh_rascunho   INTEGER NOT NULL DEFAULT 0,
  copias        INTEGER NOT NULL DEFAULT 1,
  criada_em     INTEGER NOT NULL,
  atualizada_em INTEGER NOT NULL
);

CREATE TABLE bloco (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  etiqueta_id INTEGER NOT NULL REFERENCES etiqueta(id) ON DELETE CASCADE,
  ordem       INTEGER NOT NULL,
  tipo        TEXT    NOT NULL,              -- TITULO | LINHA | QR | AVANCO
  conteudo    TEXT,
  escala      INTEGER NOT NULL DEFAULT 1,
  alinhamento TEXT    NOT NULL DEFAULT 'ESQUERDA',
  negrito     INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_bloco_etiqueta ON bloco(etiqueta_id, ordem);

CREATE TABLE configuracao (
  id              INTEGER PRIMARY KEY CHECK (id = 0),   -- linha única
  impressora_id   TEXT,
  impressora_nome TEXT,
  perfil_midia    TEXT    NOT NULL DEFAULT 'CONTINUO',
  avanco_final_mm INTEGER NOT NULL DEFAULT 3
);
```

A quantidade de cópias é coluna de `etiqueta`, não parâmetro solto de impressão:
uma etiqueta salva que sempre sai em 5 vias guarda esse 5, e o rascunho guarda a
última quantidade usada.

Os "últimos valores digitados" **não ganham tabela própria**: são uma etiqueta
com `eh_rascunho = 1`, reaproveitando estrutura, leitura e escrita de blocos.

**`PRAGMA foreign_keys = ON` na criação do driver, nas duas plataformas.** O
SQLite traz essa checagem **desligada por padrão, por conexão**; sem ela o
`ON DELETE CASCADE` é ignorado e blocos órfãos acumulam silenciosamente.

Rascunho é salvo **na impressão** e quando o app vai a segundo plano — não a
cada tecla.

### Driver por plataforma

| Plataforma | Driver | Local |
|---|---|---|
| Android | `AndroidSqliteDriver` | banco interno do app |
| Desktop | `JdbcSqliteDriver` | `%APPDATA%\Printec\printec.db` |

`configuracao.impressora_id` guarda um MAC no Android e um nome de
`PrintService` no Desktop — significados intransferíveis. Já `etiqueta`/`bloco`
são conteúdo puro. A separação mantém aberta a possibilidade de exportar ou
sincronizar **apenas** as etiquetas no futuro, sem desemaranhar configuração de
máquina.

---

## 10. Tratamento de erros

Falha de impressora é **fluxo normal** neste app, não exceção.

```
Ocioso → Renderizando → Conectando → Enviando → ✓ Sucesso
                            └──────────────────→ ✗ Erro (tipado + ação)
```

Erros são tipados, nunca strings soltas:

| Erro | Mensagem | Ação oferecida |
|---|---|---|
| `NaoPareada` | "Impressora não pareada" | Abrir configurações Bluetooth |
| `PermissaoNegada` | "Permissão de Bluetooth necessária" | Conceder |
| `FalhaAoConectar` | "A impressora pode estar desligada — ela hiberna após 10 minutos" | Tentar de novo |
| `FalhaAoEscrever` | "A conexão caiu durante a impressão" | Tentar de novo |
| `NenhumaImpressoraSelecionada` | "Escolha uma impressora" | Abrir configurações |

`FalhaAoConectar` será a mensagem mais vista do app, por causa do auto-off.

---

## 11. Testes

Princípio: **erro de ESC/POS não estoura exceção**. Um byte errado não quebra o
app — sai papel torto ou em branco, sem pista. O teste mora onde o byte nasce.

### Sem hardware

| Alvo | Como |
|---|---|
| `EscPosRenderer` | **Asserção de bytes exatos** (`1B 40`, `1B 61 01`, `1D 21 11`, `1D 28 6B …`). Candidato a TDD: a sequência esperada vem da especificação ESC/POS **antes** da implementação |
| Quebra em 32 colunas | 31/32/33 caracteres; palavra maior que a linha; acentos; escala 2× ocupando 16 colunas |
| Codificação PC860 | Caracteres fora da página viram `?` e produzem aviso |
| `LabelStore` | SQLDelight com driver JDBC **em memória** no `jvmTest`: CRUD, `CASCADE` apagando blocos de fato, rascunho indo e voltando |
| Máquina de estados | ViewModel + `PrinterTransport` falso: cada caminho de erro, o retry único, e "falhou duas vezes" |

O projeto já tem `commonTest`, `jvmTest` e `androidHostTest` com `kotlin-test`.
Nenhuma infraestrutura nova.

### Somente com hardware (checklist manual)

- [ ] 384 dots confirmados pela régua do "Imprimir teste"
- [ ] Acentuação: imprimir `ação não coração Ângela`
- [ ] QR nativo legível por leitor de celular nos tamanhos usados
- [ ] Bluetooth em Android 9 (sem diálogo) **e** Android 12+ (com diálogo)
- [ ] USB RAW no Windows sem o driver converter em imagem
- [ ] Auto-off: deixar 10+ min parado, imprimir, verificar se o retry único acorda
- [ ] Modo gap — apenas quando houver rolo destacável

---

## 12. Riscos

Em ordem de impacto:

1. **USB RAW barrado pelo driver do Windows.** Mitigação conhecida:
   `jSerialComm` direto na porta. Descoberto em minutos.
2. **Ausência de sensor de gap na LT-8359.** Sem sensor, etiqueta destacável só
   alinha por avanço fixo, acumulando desvio ao longo do rolo. Mitigação: perfil
   de mídia com avanço configurável; alternativa é adesivo contínuo.
   **Não resolvível sem o rolo em mãos.**
3. **`escpos-coffee` no Android.** As classes de núcleo são `java.io` puro; as
   de imagem usam `java.awt` e ficam de fora. Mitigação: desugaring e um teste
   instrumentado **cedo**, não no fim.
4. **Bluetooth BLE em vez de SPP.** `KPrinter_aa9c` sugere SPP clássico, mas é
   inferência. Se for BLE, muda apenas `AndroidBluetoothTransport` (GATT,
   fragmentação em pacotes de ~20 bytes) — o resto do app não muda uma linha.
   É precisamente para isso que `PrinterTransport` é uma interface.

---

## 13. Decisões registradas

| # | Decisão | Motivo |
|---|---|---|
| 1 | Formulário de campos fixos, sem editor livre | Escolha do usuário; corta escopo drasticamente |
| 2 | Comandos ESC/POS nativos, não bitmap | Autoteste eliminou os riscos de TSPL e de acentuação que justificavam bitmap |
| 3 | Modelo único + renderizadores plugáveis | Mantém o bitmap como opção futura a custo ~zero |
| 4 | Somente dispositivos pareados no Android | Evita permissão de localização na API 28; a impressora dormindo não apareceria na varredura |
| 5 | Conexão por trabalho, sem persistência | Auto-off de 10 min torna hibernação o caso normal |
| 6 | SQLDelight | Banco local pedido pelo usuário; sem KSP, sem conflito com Kotlin 2.4.10 |
| 7 | Rascunho como `eh_rascunho = 1` | Evita duplicar toda a estrutura de blocos |
| 8 | Modo gap adiado | Testar sem rolo destacável seria adivinhação |
| 9 | Desktop por USB, não Bluetooth | Escolha do usuário; elimina dependência nativa de Bluetooth na JVM |

---

## 14. Portas deixadas abertas (não construir agora)

- **Renderizador bitmap** — para logo, fonte customizada ou QR ao lado do texto
- **Descoberta e pareamento no app** — se o atalho para as configurações se
  mostrar insuficiente no uso real
- **Exportar/sincronizar etiquetas** — o esquema já separa conteúdo de
  configuração de máquina
- **Plano B de transporte no Desktop** (`jSerialComm`) — só se o RAW falhar
