@echo off
rem ==============================================================================
rem DAMMTP2P - Central Multi-Client Server Launcher (Ports 8888 & 8889)
rem ==============================================================================
chcp 65001 > nul
setlocal
cd /d "%~dp0"

if not exist "dist\DAMMTP2P.jar" (
    call "%~dp0build.bat"
    if %ERRORLEVEL% NEQ 0 exit /b 1
)

echo ==============================================================================
echo   Dang khoi dong DAMMTP2P Central Server (Swing Dashboard)...
echo   Cong dieu khien & Chat: 8888
echo   Cong tiep suc truyen tep: 8889
echo ==============================================================================
echo.

java "-Dfile.encoding=UTF-8" -jar "dist\DAMMTP2P.jar" --server %*

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [THONG BAO] Server da dung voi ma loi %ERRORLEVEL%.
    pause
)

endlocal
