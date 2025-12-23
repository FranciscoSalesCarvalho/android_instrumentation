# Dataset

| ID | Aplicação                        | Fonte        | Justificativa                                          | Proteções                  |
|----|----------------------------------|--------------|--------------------------------------------------------|----------------------------|
| A1 | DIVA (Damn Insure Vulnerable App | Github       | Vulnerabilidades didáticas, sem proteções anti-análise | Nenhuma                    |
| A2 | InsureceBankv2                   | Github/OWASP | Cenário bancário realista, múltiplas vulnerabilidades  | SSL Pinning básico         |
| A3 | OWASP MSTG UnCrackable L1        | Github       | Benchmark padrão, root detection simples               | Root Detection             |
| A4 | OAWSP MSTG UnCrackable L2        | Github       | Dificuldade intermediária                              | Root + anti-tampering      |
| A5 | OWASP MSTG UnCrackable L3        | Github       | Avançado, múltiplas camadas                            | Root + anti-debug + native |
| A6 | AndrodGoat                       | OWASP        | Vulnerabilidades OWASP Top 10 Mobile                   | Mínimas                    |
| A7 | Frida Detection Lab              | Github       | Específico para anti-Frida                             | Anti-Frida                 |

## Critérios de seleção da Dataset

| Critério                           | Descrição                                 |
|------------------------------------|-------------------------------------------|
| Código aberto ou benchmark público | Reprodutibilidade dos experimentos        |
| Mecanismos de segurança conhecidos | Ground truth para validação               |
| Variedade de frameworks            | Testar detecção de OkHttp, Retrofit, etc. |
| Níveis de dificuldade progressivos | Avaliar limites do sistema                |

## A1 - Diva (5 tarefas)

| ID    | Tarefa                                                  | Tipo de Query   | Nível         |
|-------|---------------------------------------------------------|-----------------|---------------|
| A1-T1 | Hook ```HardcodedActivity.access``` e logar credenciais | Específico      | Trivial       |
| A1-T2 | Interceptar acesso ao SharedPreferences                 | Semi-específico | Básico        |
| A1-T3 | Logar queries SQL executadas                            | Semi-específico | Básico        |
| A1-T4 | Interceptar input de usuário em campos de texto         | Genérico        | Intermediário |
| A1-T5 | Monitorar todas as chamadas de API do app               | Genérico        | Intermediário |

## A2 - UnCrackable L1 (2 tarefas)

| ID      | Tarefa                                                                             | Tipo de Query   | Nível |
|---------|------------------------------------------------------------------------------------|-----------------|-------|
| A2-T1.1 | Hook ```sg.vantagepoint.uncrackable1.a.a(String)``` and log the verification logic | Específico      | Médio |
| A2-T1.2 | Intercept verification in ```sg.vantagepoint.uncrackable1```                       | Semi-Específico | Médio |
| A2-T1.3 | Find the secret password                                                           | Genérica        | Médio |
| A2-T2.1 | Hook ```sg.vantagepoint.a.a.a(byte[], byte[])``` and log result as string          | Específico      | Alto  |
| A2-T1.2 | Intercept AES decryption in  ```sg.vantagepoint.a.a```                             | Semi-Específico | Alto  |
| A2-T1.3 | Extract hidden secret string                                                       | Genérica        | Alto  |

## A3 - InsecureBankv2 (6 tarefas)

| ID    | Tarefa                                       | Tipo de Query   | Nível         |
|-------|----------------------------------------------|-----------------|---------------|
| A3-T1 | Hook método de login retornando true         | Específico      | Trivial       |
| A3-T2 | Bypass SSL Pinning (OkHttp)                  | Semi-específico | Básico        |
| A3-T3 | Interceptar e logar transferências bancárias | Semi-específico | Básico        |
| A3-T4 | Modificar valor de transferência em runtime  | Semi-específico | Intermediário |
| A3-T5 | Logar todas as credenciais em trânsito       | Genérico        | Intermediário |
| A3-T6 | Bypass validação de certificado              | Gnérico         | Básico        |

## A4 - UnCrackable L2 (5 tarefas)

| ID    | Tarefa                            | Tipo de Query   | Nível         |
|-------|-----------------------------------|-----------------|---------------|
| A4-T1 | Bypass root detection             | Semi-Específico | Básico        |
| A4-T2 | Bypass verificação de integridade | Semi-específico | Intermediário |
| A4-T3 | Hook função nativa de verificação | Específico      | Avançado      |
| A4-T4 | Bypass todas proteções combinadas | Genérico        | Avançado      |
| A4-T5 | Extrair secret protegido          | Genérico        | Avançado      |

## A5 - UnCrackable L3 (4 tarefas)

| ID    | Tarefa                                    | Tipo de Query   | Nível         |
|-------|-------------------------------------------|-----------------|---------------|
| A5-T1 | Bypass verificação de debugger            | Semi-específico | Intermediário |
| A5-T2 | Bypass anti-tampering                     | Genérico        | Avançado      |
| A5-T3 | Hook múltiplas verificações nativas       | Genérico        | Avançado      |
| A5-T4 | Extrair secret com todas proteções ativas | Genérico        | Expert        |

## A6 - AndroGoat (5 tarefas)

| ID    | Tarefa                                        | Tipo de Query   | Nível         |
|-------|-----------------------------------------------|-----------------|---------------|
| A6-T1 | Interceptar dados de login                    | Específico      | Trivial       |
| A6-T2 | Logar acesso a banco de dados local           | Semi-específico | Básico        |
| A6-T3 | Monitorar broadcat receivers                  | Semi-específico | Intermediário |
| A6-T4 | Interceptar comunicação entre componentes     | Genérico        | Intermediário |
| A6-T5 | Detectar vazamento de dados sensíveis em logs | Genérico        | Básico        |

## A7 - Frida Detection Lab (4 tarefas)

| ID    | Tarefa                                        | Tipo de Query   | Nível         |
|-------|-----------------------------------------------|-----------------|---------------|
| A7-T1 | Bypass detecção de Frida por nome de processo | Específico      | Intermediário |
| A7-T2 | Bypass verificação de porta do Frida          | Semi-específico | Intermediário |
| A7-T3 | Bypass todas técnicas anti-Frida              | Genérico        | Avançado      |
| A7-T4 | Manter Frida injetado sem detecção            | Genérico        | Expert        |

## Resumo do Dataset

| Nível         | Qtd Tarefas | %    |
|---------------|-------------|------|
| Trivial       | 5           | 15%  |
| Básico        | 11          | 33%  |
| Intermediário | 10          | 30%  |
| Avançado      | 6           | 18%  |
| Expert        | 2           | 6%   |
| Total         | 34          | 100% |

| Tipo de Query   | Qtd Tarefas | %    |
|-----------------|-------------|------|
| Específico      | 9           | 26%  |
| Semi-específico | 13          | 38%  |
| Genérico        | 12          | 35%  |
| Total           | 34          | 100% |

## Dataset - Status Atualizado:

| ID | Aplicação           | Status        | Taxa         |
|----|---------------------|---------------|--------------|
| A1 | DIVA                | ✅             | 10/10 (100%) |
| A2 | InsecureBankv2      | ❌ Não testado | -            |
| A3 | UnCrackable L1      | ✅             | 7.5/8 (94%)  |
| A4 | UnCrackable L2      | ❌ Não testado | -            |
| A5 | UnCrackable L3      | ❌ Não testado | -            |
| A6 | AndroGoat           | ✅             | 6/6 (100%)   |
| A7 | Frida Detection Lab | ❌ -           |              |

## Apps Vulneráveis Mais Realistas (Recomendado)

| App                  | Descrição                              | Diferencial                                   |
|----------------------|----------------------------------------|-----------------------------------------------|
| Damn Vulnerable Bank | App bancário vulnerável completo       | Backend funcional, múltiplas vulnerabilidades |
| WaTF-Bank            | Banking app com backend Python/Flask   | Simula serviços web reais                     |
| UnSAFE Bank          | Suite bancária completa (Web + Mobile) | Vulnerabilidades de severidade variada        |
| Vuln-Bank-Mobile     | App bancário com API vulnerável        | APK pronto para download                      |
| DodoVulnerableBank   | App bancário inseguro                  | Foco em hacking e securing                    |

## Apps Open Source Reais

| App              | Descrição                       | Diferencial |
|------------------|---------------------------------|-------------|
| Bitwarden        | Password Manager                |             |
| Signal           | Mensagens                       |             |
| Nextcloud        | Cloud storage                   |             |
| Vuln-Bank-Mobile | App bancário com API vulnerável |             |
| WordPress App    | CMS                             |             |

## Apps com Bug Bounty (Testes Autorizados)

| App        | Descrição                   | Diferencial |
|------------|-----------------------------|-------------|
| Google VRP | Android apps                |             |
| Meta       | Facebook/Instagram/WhatsApp |             |
| Uber       | App Móvel                   |             |