# A1 — DIVA
# Package: jakhar.aseem.diva

## T1 — Interceptar Log
- Específica: hook jakhar.aseem.diva.LogActivity.checkout(View) log calls
- Semi-Específica: intercept the checkout method from LogActivity
- Genérica: capture sensitive data being logged

## T2 — Bypass Hardcoded Credentials
- Específica: replace implementation of jakhar.aseem.diva.HardcodeActivity.access(...) to show Toast "Access granted"
- Semi-Específica: replace the access(android.view.View) method in HardcodeActivity to show Toast "Access granted"
- Genérica: bypass access to show Toast "Access granted"

## T3 — Interceptar SharedPreferences
- Genérica: intercept data being saved to SharedPreferences and capture the values

## T4 — Interceptar SQL
- Genérica: monitor local database access and capture executed queries

## T5 — Interceptar SDCard
- Genérica: intercept data being written to SD card or external storage and capture the content in plain text
