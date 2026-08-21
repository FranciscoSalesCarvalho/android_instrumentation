# FridaForge

**Geração Automatizada de Scripts de Instrumentação Dinâmica para Análise de Segurança Android utilizando LLMs e
Contexto Dinâmico da Aplicação**

O FridaForge automatiza a geração de scripts Frida a partir de consultas em linguagem natural. A ferramenta coleta o
contexto da aplicação Android em execução (classes carregadas, métodos, bibliotecas, módulos nativos, armazenamento) e o
utiliza para guiar um LLM na produção de scripts funcionais e específicos para cada aplicação.

> **Artigo:** *FridaForge: Geração Automatizada de Scripts de Instrumentação Dinâmica Assistida por Modelos de Linguagem
com Contexto da Aplicação*  
> **Evento:** SBSeg
>
> **Resumo:** A instrumentação dinâmica com Frida permite inspecionar operações internas,
> interceptar dados sensíveis e contornar mecanismos de proteção em aplicações Android,
> mas exige conhecimento da API do Frida e da estrutura interna da aplicação-alvo. O
> FridaForge é uma ferramenta de código aberto que gera e executa scripts Frida a partir
> de consultas em linguagem natural, utilizando informações coletadas da aplicação em
> execução para adaptar os scripts ao alvo. Avaliado em dez aplicações Android
> intencionalmente vulneráveis (83 tarefas, 92 consultas, 460 execuções), o FridaForge
> alcançou taxa de sucesso de 95,7%.

🌐 [English version](README.md)

---

## Sumário

- [Visão Geral](#visão-geral)
- [Requisitos](#requisitos)
- [Instalação](#instalação)
- [Início Rápido](#início-rápido)
- [Uso](#uso)
- [Exemplos](#exemplos)
- [Estrutura do Projeto](#estrutura-do-projeto)
- [Reprodução dos Experimentos](#reprodução-dos-experimentos)
- [Vídeo de Demonstração](#vídeo-de-demonstração)
- [Licença](#licença)
- [Citação](#citação)

---

## Visão Geral

A instrumentação dinâmica com Frida é essencial para análise de segurança Android, porém a escrita de scripts eficazes
exige expertise na API do Frida e conhecimento da estrutura interna da aplicação-alvo. O FridaForge aborda esse problema
por meio de quatro etapas:

1. **Coleta de contexto em tempo de execução** — classes, métodos, bibliotecas, módulos nativos, bancos de dados e
   SharedPreferences da aplicação
2. **Construção de prompts com contexto** — estruturação das informações coletadas junto à consulta em linguagem natural
   do analista
3. **Geração de scripts executáveis** — uso de um LLM para produzir scripts Frida em JavaScript que referenciam
   elementos reais da aplicação
4. **Validação e execução** — extração, validação sintática e injeção automática do script na aplicação-alvo
5. **Correção iterativa** — quando um script falha ou produz resultados inesperados, o analista pode acionar um ciclo de
   correção que alimenta os logs de execução e feedback de volta ao LLM para refinamento

As consultas podem ser formuladas em três níveis de especificidade:

| Nível               | Descrição                                     | Exemplo                                                |
|---------------------|-----------------------------------------------|--------------------------------------------------------|
| **Específica**      | Caminho completo da classe e método           | `hook com.app.SecurityCheck.isEmulator() return false` |
| **Semi-específica** | Nome da classe ou método sem caminho completo | `bypass isEmulator from SecurityCheck`                 |
| **Genérica**        | Intenção em alto nível                        | `bypass emulator detection`                            |

---

## Requisitos

- **SO:** macOS ou Linux
- **Java:** JDK 17+
- **Android:** Dispositivo ou emulador com acesso root
- **Frida:** v17.4.1 instalado no host (`pip install frida-tools`) e Frida Server no dispositivo
- **ADB:** Android Debug Bridge configurado e dispositivo conectado
- **Chave de API:** Chave da API da Anthropic para acesso ao Claude
- **Nota sobre custo de reprodução:** a geração de scripts depende de chamadas à API da Anthropic, um serviço pago.
  Reproduzir o protocolo experimental completo (460 execuções em 10 aplicações) gera custo não trivial de API e exige
  configurar um emulador Android com root e Frida Server. Para fins de verificação, recomendamos
  a [reprodução mínima](#reprodução-mínima-uma-única-aplicação) abaixo, ou a inspeção das saídas já registradas em
  `artifacts/` e `examples/scripts/`.

---

## Instalação

### 1. Clonar o repositório

```bash
git clone https://github.com/FranciscoSalesCarvalho/android_instrumentation.git
cd android_instrumentation
```

### 2. Instalar o Frida no host

```bash
pip install frida-tools
frida --version
```

### 3. Instalar o Frida Server no dispositivo

```bash
# Check device architecture
adb shell getprop ro.product.cpu.abi

# Download the matching Frida Server release (replace <version> and <arch>,
# e.g. 17.4.1 and arm64) from https://github.com/frida/frida/releases
wget https://github.com/frida/frida/releases/download/<version>/frida-server-<version>-android-<arch>.xz
unxz frida-server-<version>-android-<arch>.xz
mv frida-server-<version>-android-<arch> frida-server

# Push to device
adb push frida-server /data/local/tmp/
adb shell "chmod 755 /data/local/tmp/frida-server"
adb shell "/data/local/tmp/frida-server &"
```

### 4. Compilar o FridaForge

```bash
# Opção A: Usar jar pré-compilado (sem necessidade de compilação)
java -jar release/fridaforge.jar --help
 
# Opção B: Compilar a partir do código-fonte
./gradlew build
```

### 5. Configurar a chave de API

```bash
export ANTHROPIC_API_KEY="sua-chave-aqui"
```

---

## Início Rápido

```bash
# 1. Verificar conexão com o dispositivo e Frida Server
adb devices
frida-ps -Ua

# 2. Executar o FridaForge em modo interativo
./gradlew run --args="-p com.example.app -k $ANTHROPIC_API_KEY -i"

# 3. Digitar uma consulta
fridaforge> bypass emulator detection

# 4. Se o resultado não for o esperado, corrigir
fridaforge> /retry hookou o método errado, deveria ser isEmulator em EmulatorDetectionActivity
```

---

## Uso

### Modo Interativo (Recomendado)

```bash
./gradlew run --args="-p <nome_do_pacote> -k <chave_api> -i"
```

Comandos disponíveis no modo interativo:

| Comando               | Descrição                                             |
|-----------------------|-------------------------------------------------------|
| `<qualquer consulta>` | Gerar e executar um script Frida                      |
| `/retry`              | Corrigir último script (LLM analisa logs de execução) |
| `/retry <feedback>`   | Corrigir último script com feedback do analista       |
| `classes`             | Listar classes coletadas da aplicação                 |
| `frameworks`          | Mostrar bibliotecas detectadas                        |
| `stats`               | Exibir estatísticas da coleta de contexto             |
| `help`                | Mostrar comandos disponíveis                          |
| `exit`                | Sair do modo interativo                               |

### Correção Iterativa de Scripts

Quando um script gerado não produz o resultado esperado — seja por erro técnico (crash, exceção) ou por falha semântica
(método errado hookado, output irrelevante) — o analista pode acionar um ciclo de correção usando `/retry`:

```bash
# Sem feedback: LLM analisa output do Frida e logcat automaticamente
fridaforge> /retry
 
# Com feedback: analista fornece diagnóstico para guiar a correção
fridaforge> /retry o script interceptou checkPassword em vez de validatePin
fridaforge> /retry script crashou, tentar hookar ContextWrapper em vez de Context
```

O ciclo de correção combina três fontes de informação:

- **Logs de execução** — stdout/stderr do Frida e logcat filtrado do Android, capturados automaticamente durante a
  execução
- **Feedback do analista** — diagnóstico textual opcional fornecido via `/retry`
- **Contexto da aplicação** — o mesmo contexto coletado em tempo de execução (classes, métodos, bibliotecas) utilizado
  na consulta original O analista pode invocar `/retry` quantas vezes forem necessárias. Cada correção se baseia na
  tentativa imediatamente anterior.

### Modo de Consulta Única

```bash
./gradlew run --args="-p <nome_do_pacote> -k <chave_api> -q '<consulta>'"
```

### Opções Adicionais

| Flag           | Descrição                                                          |
|----------------|--------------------------------------------------------------------|
| `-p`           | Nome do pacote da aplicação-alvo                                   |
| `-k`           | Chave de API da Anthropic (ou variável `ANTHROPIC_API_KEY`)        |
| `-q`           | Consulta única para execução                                       |
| `-i`           | Modo interativo                                                    |
| `-c`           | Nível de contexto: `MINIMAL`, `BASIC` (padrão), `FULL`             |
| `-s`           | Salvar script gerado em arquivo                                    |
| `-o`           | Salvar contexto coletado em JSON                                   |
| `--dry-run`    | Gerar script sem executar                                          |
| `--stacktrace` | Caminho para arquivo de stack trace para contexto adicional ao LLM |

---

## Exemplos

### Bypass de Detecção de Emulador

```bash
# Específica
./gradlew run --args="-p owasp.sat.agoat -q 'hook owasp.sat.agoat.EmulatorDetectionActivity.isEmulator() return false'"

# Genérica
./gradlew run --args="-p owasp.sat.agoat -q 'bypass emulator detection'"
```

### Interceptar Credenciais de Login

```bash
./gradlew run --args="-p com.android.insecurebankv2 -q 'intercept credentials from LoginActivity when user performs login'"
```

### Bypass de SSL Pinning

```bash
# Genérico
./gradlew run --args="-p owasp.sat.agoat -q 'bypass SSL pinning'"

# Com stack trace (maior taxa de sucesso)
./gradlew run --args="-p owasp.sat.agoat -q 'bypass SSL pinning' -e /path/to/stacktrace.txt"
```

> **Dica:** Quando um bypass de SSL pinning falha, capture o stack trace do `logcat` e forneça-o via `-e`/
> `--stacktrace`. O LLM utiliza essa informação para identificar a classe e o método exatos que realizam a validação do
> certificado, gerando scripts mais precisos.

### Interceptar Operações Criptográficas

```bash
./gradlew run --args="-p sg.vantagepoint.uncrackable1 -q 'intercept cryptographic operations and convert the result to legible text'"
```

### Múltiplos Hooks

```bash
# Usando separador AND
./gradlew run --args="-p com.example.app -q 'hook Class1.method1() return false AND hook Class2.method2() return false'"
```

### Correção Iterativa

```bash
# Iniciar modo interativo
./gradlew run --args="-p com.android.insecurebankv2 -k $ANTHROPIC_API_KEY -i"
 
# Gerar script inicial
fridaforge> intercept credentials from LoginActivity
 
# Script hookou método errado — corrigir
fridaforge> /retry deveria hookar doLogin e não onCreate
 
# Ainda não está certo — refinar
fridaforge> /retry interceptar o body da requisição HTTP, credenciais são enviadas via POST
```

---

## Estrutura do Projeto

```
fridaforge/
├── README.md                          # Documentação (inglês)
├── README.pt-br.md                    # Documentação (português)
├── LICENSE                            # Licença MIT
├── build.gradle.kts
├── settings.gradle.kts
├── examples/                          # Consultas e saídas de exemplo
│   ├── queries/                       # Consultas por aplicação
│   └── scripts/                       # Scripts gerados de exemplo
├── artifacts/                         # Dados e resultados experimentais
│   ├── FridaForge_Resultados_N5.xlsx  # Resultados completos N=5
│   └── context_samples/               # Exemplos de contexto coletado
├── demo/                              # Materiais de demonstração
│   └── video_link.md                  # Link para vídeo demonstrativo
└── src/main/kotlin/
├── Main.kt                        # Ponto de entrada CLI
├── core/
│   ├── FridaConnector.kt          # Comunicação com o Frida
│   ├── ContextCollector.kt        # Coleta dinâmica de contexto
│   ├── QueryRouter.kt             # Classificação de consultas
│   ├── QueryParser.kt             # Parsing de consultas específicas
│   ├── ScriptExecutor.kt          # Validação e execução de scripts
│   └── RetryManager.kt            # Orquestração da correção iterativa
├── collectors/
│   ├── AppInfoCollector.kt        # Metadados da aplicação
│   ├── ClassCollector.kt          # Enumeração de classes
│   ├── MethodCollector.kt         # Enumeração de métodos
│   ├── FrameworkDetector.kt       # Detecção de bibliotecas
│   ├── ManifestCollector.kt       # Parsing do AndroidManifest
│   ├── StorageCollector.kt        # Detecção de BD e SharedPrefs
│   └── NativeLibraryCollector.kt  # Enumeração de módulos nativos
├── llm/
│   ├── LLMClient.kt              # Cliente da API Claude
│   ├── PromptBuilder.kt          # Construção de prompts com contexto
│   └── CorrectionPromptBuilder.kt # Construção de prompts de correção
├── models/
│   ├── AppContext.kt              # Modelo de contexto da aplicação
│   ├── ClassInfo.kt               # Dados de classes/métodos
│   ├── ExecutionRecord.kt         # Estado de execução para retry
│   └── GeneratedScript.kt         # Modelo de resultado do script
└── utils/
└── LogcatCapture.kt           # Captura e filtragem de logcat
```

**Mapeamento para as reivindicações do artigo:** a abordagem de geração guiada por contexto dinâmico descrita no artigo
corresponde diretamente a esta estrutura. A classificação do nível de especificidade da consulta (Seção 3.2) é
implementada em `QueryRouter.kt` e `QueryParser.kt`. A coleta dinâmica de contexto (Seção 3.3) é implementada no pacote 
`collectors/`, com `NativeLibraryCollector.kt` responsável pela enumeração de módulos nativos. A injeção progressiva de 
contexto (Seção 3.4) é implementada em `PromptBuilder.kt`. A validação e execução do script (Seção 3.6) é implementada em
`ScriptExecutor.kt`. O mecanismo de refinamento iterativo, é implementado em `RetryManager.kt` e `CorrectionPromptBuilder.kt`.

---

## Reprodução dos Experimentos

A avaliação descrita no artigo pode ser reproduzida conforme os passos abaixo.

### 1. Aplicações Benchmark

| ID  | Application          | MASTG ID       | Source                                                                |
|-----|----------------------|----------------|-----------------------------------------------------------------------|
| A1  | AndroGoat            | MASTG-APP-0001 | [GitHub](https://github.com/satishpatnayak/AndroGoat)                 | |
| A2  | DIVA                 | MASTG-APP-0007 | [GitHub](https://github.com/payatu/diva-android)                      |
| A3  | DodoVulnerableBank   | MASTG-APP-0008 | [GitHub](https://github.com/CSPF-Founder/DodoVulnerableBank)          |
| A4  | InsecureBankv2       | MASTG-APP-0010 | [GitHub](https://github.com/dineshshetty/Android-InsecureBankv2)      |
| A5  | OVAA                 | MASTG-APP-0013 | [GitHub](https://github.com/oversecured/ovaa)                         |
| A6  | Finstergram          | MASTG-APP-0016 | [GitHub](https://github.com/netlight/finstergram)                     |
| A7  | MASTestApp-NETWORK   | MASTG-APP-0018 | [OWASP MASTG](https://github.com/sydseter/MASTestApp-Android-NETWORK) |
| A8  | BugBazaar            | MASTG-APP-0029 | [GitHub](https://github.com/payatu/BugBazaar)                         |
| A9 | VulnForum            | MASTG-APP-0031 | [GitHub](https://github.com/macik09/Vulnforum)                        |
| A10 | Damn Vulnerable Bank | —              | [GitHub](https://github.com/rewanthtammana/Damn-Vulnerable-Bank)      |

### 2. Configuração do Ambiente

```bash
# Emulador Android com API 36, root habilitado
adb root

# Baixar o Frida Server v17.4.1 para arm64
wget https://github.com/frida/frida/releases/download/17.4.1/frida-server-17.4.1-android-arm64.xz
unxz frida-server-17.4.1-android-arm64.xz
adb push frida-server-17.4.1-android-arm64 /data/local/tmp/frida-server
adb shell "chmod 755 /data/local/tmp/frida-server"
adb shell "/data/local/tmp/frida-server &"
```

### 3. Execução dos Testes

```bash
# Instalar aplicação-alvo
adb install <app.apk>

# Executar FridaForge
./gradlew run --args="-p <nome_do_pacote> -k $ANTHROPIC_API_KEY -i"

# Reiniciar entre execuções
adb shell am force-stop <nome_do_pacote>
adb shell pm clear <nome_do_pacote>
```

### Reprodução Mínima (Uma Única Aplicação)

Para revisores que desejam verificar a funcionalidade sem configurar o benchmark completo, os passos abaixo reproduzem
uma tarefa representativa em poucos minutos:

```bash
# 1. Instalar o DIVA (100% de sucesso na avaliação completa)
adb install diva-beta.apk

# 2. Executar uma consulta genérica para capturar logs sensíveis
./gradlew run --args="-p jakhar.aseem.diva -q 'capture sensitive logs being logged' -k $ANTHROPIC_API_KEY"

# 3. Comparar a saída com o resultado esperado documentado em
#    examples/scripts/diva/script1.js
```

Isso não reproduz o protocolo completo de 460 execuções, mas demonstra todo o fluxo — coleta de contexto, construção do
prompt, geração do script e execução — de ponta a ponta.

### 4. Resultados dos Experimentos

Os resultados completos (N=5, 475 execuções) estão disponíveis em `artifacts/FridaForge_Resultados_Final.xlsx`.

---

## Vídeo de Demonstração

Um vídeo demonstrativo com a instalação, configuração e uso do FridaForge está disponível em:

**[Vídeo de Demonstração](https://drive.google.com/drive/folders/1adbSMc6c9pAS0T3duMDPOnGjah7tJcJK?usp=drive_link)**

O vídeo cobre dois cenários:

1. Bypass de detecção de emulador na aplicação AndroGoat
2. Interceptação de operações criptográficas na aplicação UnCrackable L1

---

## Licença

Este projeto está licenciado sob a Licença MIT. Consulte [LICENSE](LICENSE) para detalhes.

---
