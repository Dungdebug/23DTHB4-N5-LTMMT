@echo off
chcp 65001 >nul
cd /d "%~dp0"

if not exist "%~dp0dist\DAMMTP2P.jar" (
    call "%~dp0build.bat"
    if %ERRORLEVEL% neq 0 exit /b 1
)

:: Chay bang javaw neu co de khong hien cua so console.
where javaw >nul 2>nul
if %ERRORLEVEL% equ 0 (
    start "" javaw "-Dfile.encoding=UTF-8" -jar "%~dp0dist\DAMMTP2P.jar" --server
    exit /b 0
)

:: Du phong: chay bang java.
where java >nul 2>nul
if %ERRORLEVEL% equ 0 (
    start "" java "-Dfile.encoding=UTF-8" -jar "%~dp0dist\DAMMTP2P.jar" --server
    exit /b 0
)

echo [LOI] Khong tim thay Java Runtime hoac file Server.exe.
pause
