@echo off
rem ==============================================================================
rem DAMMTP2P - Rebuild JAR Script (Compiles src into dist\DAMMTP2P.jar)
rem ==============================================================================
chcp 65001 > nul
setlocal
cd /d "%~dp0"

echo ==============================================================================
echo   Dang bien dich source code DAMMTP2P sang dist\DAMMTP2P.jar...
echo ==============================================================================

if not exist "build\classes" mkdir "build\classes"
if not exist "dist\lib" mkdir "dist\lib"
if exist "lib\flatlaf-3.5.4.jar" copy /y "lib\flatlaf-3.5.4.jar" "dist\lib\flatlaf-3.5.4.jar" >nul

rem 1. Tao danh sach file nguon .java
dir /s /b src\*.java > sources.txt

rem 2. Bien dich bang javac voi UTF-8 va flatlaf library
javac -encoding UTF-8 -cp "lib\flatlaf-3.5.4.jar;dist\lib\flatlaf-3.5.4.jar" -d "build\classes" @sources.txt
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Bien dich that bai! Vui long kiem tra loi tren.
    del sources.txt 2>nul
    pause
    exit /b 1
)
del sources.txt 2>nul

rem 3. Dong goi thanh file JAR
set "JAR_EXE=jar"
if exist "%JAVA_HOME%\bin\jar.exe" set "JAR_EXE=%JAVA_HOME%\bin\jar.exe"
if exist "C:\Program Files\Java\latest\bin\jar.exe" set "JAR_EXE=C:\Program Files\Java\latest\bin\jar.exe"
if exist "C:\Program Files\Java\jdk-21.0.11\bin\jar.exe" set "JAR_EXE=C:\Program Files\Java\jdk-21.0.11\bin\jar.exe"
if exist "C:\Program Files\Java\jdk-26.0.2.1\bin\jar.exe" set "JAR_EXE=C:\Program Files\Java\jdk-26.0.2.1\bin\jar.exe"

"%JAR_EXE%" cfm dist\DAMMTP2P.jar manifest.mf -C build\classes .

if %ERRORLEVEL% EQU 0 (
    echo [THANH CONG] Da tao xong dist\DAMMTP2P.jar!
    if exist "release\DAMMTP2P-Client\app" copy /y "dist\DAMMTP2P.jar" "release\DAMMTP2P-Client\app\DAMMTP2P.jar" >nul
    if exist "release\DAMMTP2P-Client" copy /y "dist\DAMMTP2P.jar" "release\DAMMTP2P-Client\DAMMTP2P.jar" >nul
    if exist "release\DAMMTP2P-Server\app" copy /y "dist\DAMMTP2P.jar" "release\DAMMTP2P-Server\app\DAMMTP2P.jar" >nul
    if exist "release\DAMMTP2P-Server" copy /y "dist\DAMMTP2P.jar" "release\DAMMTP2P-Server\DAMMTP2P.jar" >nul
    echo [DONG BO] Da cap nhat DAMMTP2P.jar vao thu muc release!
) else (
    echo [ERROR] Dong goi JAR that bai!
    pause
    exit /b 1
)

echo.
endlocal
