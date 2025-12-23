## Análise por Tipo de Consulta:

| Tipo            | Total | Sucess | Parcial | Taxa | 
|-----------------|-------|--------|---------|------|
| Específica      | 10    | 9      | 0       | 100% |
| Semi-específica | 10    | 9      | 0       | 100% |
| Genérica        | 8     | 7      | 0       | 100% |

A1 - DIVA: Análise por Categoria

| Tarefa                              | Categoria     | Testes |
|-------------------------------------|---------------|--------|
| A1-T1 Interceptar Log               | Monitoramento | 3      |
| A1-T2 Bypass Hardcoded              | Bypass        | 2      |
| A1-T3 Interceptar SharedPreferences | Monitoramento | 2      |
| A1-T4 Interceptar SQL               | Monitoramento | 2      |
| A1-T5 Interceptar SDCard            | Monitoramento | 1      |

| Categoria     | Tarefas | Testes | Sucesso | Taxa |
|---------------|---------|--------|---------|------|
| Monitoramento | 4       | 8      | 8       | 100% |
| Bypass        | 1       | 2      | 2       | 100% |
| Extração      | 0       | 0      | 0       | N/A  |

A2 - InsecureBankv2: Análise por Categoria

| Tarefa                              | Categoria     | Testes |
|-------------------------------------|---------------|--------|
| A2-T1 Ativar Root Detection         | Bypass        | 7      |
| A2-T2 Interceptar Login Credentials | Monitoramento | 1      |
| A2-T3 Bypass Login                  | Bypass        | 1      |
| A2-T4 Interceptar Content Provider  | Monitoramento | 1      |

| Categoria     | Tarefas | Testes | Sucesso | Taxa |
|---------------|---------|--------|---------|------|
| Monitoramento | 2       | 2      | 2       | 100% |
| Bypass        | 2       | 8      | 8       | 100% |
| Extração      | 0       | 0      | 0       | N/A  |

A3 - UnCrackable L1: Análise por Categoria

| Tarefa                          | Categoria     | Testes |
|---------------------------------|---------------|--------|
| A3-T1 Interceptar verificação   | Monitoramento | 2      |
| A3-T2 Bypass da verificação     | Bypass        | 3      |
| A3-T3 Extrair secret decriptado | Extração      | 3      |

| Categoria     | Tarefas | Testes | Sucesso | Taxa |
|---------------|---------|--------|---------|------|
| Monitoramento | 1       | 2      | 2       | 100% |
| Bypass        | 1       | 3      | 3       | 100% |
| Extração      | 1       | 3      | 2.5     | 83%  |