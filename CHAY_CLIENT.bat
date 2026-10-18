@echo off
cd /d "%~dp0"

if not exist "%~dp0downloads" mkdir "%~dp0downloads"

:: Tu dong dong bo JAR moi nhat vao release neu co
if exist "%~dp0dist\DAMMTP2P.jar" (
    if exist "%~dp0release\DAMMTP2P-Client\app" copy /y "%~dp0dist\DAMMTP2P.jar" "%~dp0release\DAMMTP2P-Client\app\DAMMTP2P.jar" >nul 2>nul
    if exist "%~dp0release\DAMMTP2P-Client" copy /y "%~dp0dist\DAMMTP2P.jar" "%~dp0release\DAMMTP2P-Client\DAMMTP2P.jar" >nul 2>nul
)

:: 1. Uu tien chay ban native Windows Executable (.exe)
if exist "%~dp0release\DAMMTP2P-Client\DAMMTP2P-Client.exe" (
    start "" "%~dp0release\DAMMTP2P-Client\DAMMTP2P-Client.exe"
    exit /b 0
)

:: 2. Chay em qua javaw
where javaw >nul 2>nul
if %ERRORLEVEL% equ 0 (
    start "" javaw "-Dfile.encoding=UTF-8" -jar "%~dp0dist\DAMMTP2P.jar" --client
    exit /b 0
)

:: 3. Chay qua java
where java >nul 2>nul
if %ERRORLEVEL% equ 0 (
    start "" java "-Dfile.encoding=UTF-8" -jar "%~dp0dist\DAMMTP2P.jar" --client
    exit /b 0
)

echo [LOI] Khong tim thay Java Runtime hoac file Client.exe.
pause
