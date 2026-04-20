1. Pentester baixa APK
   ↓
2. Abre no JADX/JEB
   ↓
3. Analisa código (engenharia reversa)
   ↓
4. Identifica: "Aha! Esse método verifica emulador!"
   ↓
5. Precisa de script Frida para hookar
   ↓
6. Usa sua ferramenta LLM! ✨
```

---

## 💬 **Tipos de Requests Reais**

### **Nível 1: Genérico (Iniciante)**
```
User: "bypass emulator detection"
```
→ Ferramenta precisa descobrir tudo

### **Nível 2: Semi-específico (Intermediário)**
```
User: "bypass no método checkDevice da classe SecurityManager"
```
→ Ferramenta precisa encontrar classe exata

### **Nível 3: Específico (Expert)** ⭐ **MAIS COMUM**
```
User: "hook com.pentestmobile.appemulator.SecurityCheck.isEmulator()Z 
       e retornar false"
```
→ Usuário JÁ SABE tudo!

### **Nível 4: Muito Específico (Profissional)**
```
User: "hook com.example.security.DeviceValidator.checkEnvironment(I)Z,
       quando parâmetro for 2, retornar false, 
       caso contrário manter comportamento original"
```
→ Lógica complexa, usuário conhece internals

---

## 🎯 **Seu LLM Precisa Suportar AMBOS Cenários**

### **Cenário A: Sem Informação (Descoberta Automática)**
```
Input: "bypass emulator detection"

Pipeline:
1. Scan classes
2. Filtro inteligente
3. Coleta métodos
4. LLM gera script
```

### **Cenário B: Com Informação (Geração Direta)** ⭐
```
Input: "hook com.example.SecurityCheck.isEmulator() retornando false"

Pipeline:
1. Parse do input (extrai classe + método)
2. Verifica se classe existe (opcional)
3. LLM gera script DIRETO

ESPECIFIC: hooking
--package
com.francisco.appprotegido
--query
"hook com.francisco.appprotegido.MainActivity.isEmulator() return false AND hook com.francisco.appprotegido.MainActivity.isRooted() return false"
--save-script
"/Users/thoughtworks/Documents/personal/mestrado/FridaGPT/scripts/multi.js"

--package
com.pentestmobile.appwebtest
--query
"ssl pinning"
--save-script
"/Users/thoughtworks/Documents/personal/mestrado/FridaGPT/scripts/pinning.js"
--stacktrace "/Users/thoughtworks/Documents/personal/mestrado/resultados/androgoat/t3_ssl_pinning/stacktrace.txt"

--package
jakhar.aseem.diva
--device
emulator-5554
--save-script
/Users/thoughtworks/Documents/personal/mestrado/FridaGPT/docs/experiments/results/scripts/temp.js
--interactive

SEMI-SPECIFIC:
--package
com.pentestmobile.appemulator
--query "bypass isEmulator method from MainActivity"
--save-script
/Users/thoughtworks/Documents/personal/mestrado/FridaGPT/scripts/semi.js

--package
com.francisco.appprotegido
--query "bypass isEmulator and isRooted methods from MainActivity"
--save-script
/Users/thoughtworks/Documents/personal/mestrado/FridaGPT/scripts/semi_multi.js

GENERIC:
--package
com.pentestmobile.appemulator
--query "bypass emulator detection"
--save-script
/Users/thoughtworks/Documents/personal/mestrado/FridaGPT/scripts/generic.js

--package
com.francisco.appprotegido
--device
emulator-5554
--query "bypass emulator and root detection"
--save-script
/Users/thoughtworks/Documents/personal/mestrado/FridaGPT/scripts/generic_double.js
