# A5 — AndroGoat
# Package: owasp.sat.agoat

## T1 — Insecure Logging
- Genérica: capture sensitive data being logged

## T2 — Bypass Emulator Detection
- Específica: hook owasp.sat.agoat.EmulatorDetectionActivity.isEmulator() return false
- Semi-Específica: bypass isEmulator() from EmulatorDetectionActivity
- Genérica: bypass emulator detection

## T3 — Bypass SSL Pinning
- Genérica: bypass SSL pinning

## T5 — Temp Files
- Genérica: intercept data being saved to temp files and print in plain text

## T6 — SharedPreferences
- Genérica: intercept data being saved to SharedPreferences and capture the values

## T7 — Bypass Score
- Específica: hook owasp.sat.agoat.InsecureStorageSharedPrefs1Activity.CheckScore(int) return true
- Semi-Específica: hook CheckScore(int) from InsecureStorageSharedPrefs1Activity return true

## T8 — SQLite
- Genérica: monitor local database access and capture executed queries

## T9 — SDCard
- Genérica: intercept data being written to SD card or external storage and capture the content in plain text

## T10 — SQL Injection
- Genérica: intercept SQL queries and detect SQL injection patterns

## T11 — Clipboard
- Genérica: intercept clipboard operations and capture copied data
