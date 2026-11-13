@echo off
rem Start the interview trainer PC server (real AI mode). Close this window or press Ctrl+C to stop.
rem NOTE: keep this file ASCII only. Korean text breaks cmd.exe on Korean Windows (cp949).
cd /d "%~dp0"

echo [1/3] Checking Python packages...
python -m pip install -q -r requirements.txt
if errorlevel 1 (
    echo Package install failed. Check that Python 3.10 or newer is installed.
    pause
    exit /b 1
)

echo [2/3] Checking claude CLI...
where claude >nul 2>nul
if errorlevel 1 (
    echo WARNING: claude command not found. Run: npm install -g @anthropic-ai/claude-code , then run claude once to log in.
)

echo [3/3] Starting server on port 8787.
echo Emulator address: 10.0.2.2:8787
echo Real phone address: use the IPv4 address below with :8787
ipconfig | findstr /i "IPv4"
echo.
python -m uvicorn main:app --host 0.0.0.0 --port 8787
pause
