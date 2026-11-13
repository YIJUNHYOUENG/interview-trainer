@echo off
rem Start the server in FAKE mode (no claude CLI, no speech model). For testing app screens and connection only.
rem NOTE: keep this file ASCII only. Korean text breaks cmd.exe on Korean Windows (cp949).
cd /d "%~dp0"
set TRAINER_FAKE=1
echo Starting FAKE mode server on port 8787.
echo Emulator address: 10.0.2.2:8787
echo Real phone address: use the IPv4 address below with :8787
ipconfig | findstr /i "IPv4"
echo.
python -m uvicorn main:app --host 0.0.0.0 --port 8787
pause
