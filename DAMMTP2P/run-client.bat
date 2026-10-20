@echo off
rem ==============================================================================
rem DAMMTP2P - Client Application Launcher (Connects to Server over ZeroTier)
rem ==============================================================================
chcp 65001 > nul
setlocal
cd /d "%~dp0"

if not exist "dist\DAMMTP2P.jar" (
    call "%~dp0build.bat"
    if %ERRORLEVEL% NEQ 0 exit /b 1
)

echo ==============================================================================
echo   Dang khoi dong DAMMTP2P Client (Giao dien Swing FlatLaf)...
echo ==============================================================================
echo.

java "-Dfile.encoding=UTF-8" -jar "dist\DAMMTP2P.jar" --client %*

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [THONG BAO] Ung dung da thoat voi ma loi %ERRORLEVEL%.
    pause
)

endlocal
