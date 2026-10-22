@echo off
rem ==============================================================================
rem DAMMTP2P - Rebuild JAR Script (Compiles src into dist\DAMMTP2P.jar)
rem ==============================================================================
chcp 65001 > nul
setlocal
cd /d "%~dp0"

set "JAVA_BIN=C:\Program Files\Java\jdk-21.0.11\bin"
if not exist "%JAVA_BIN%\javac.exe" (
    where javac >nul 2>nul
    if errorlevel 1 (
        echo [ERROR] Khong tim thay JDK. Vui long cai JDK 17+ hoac cau hinh PATH.
        pause
        exit /b 1
    ) else (
        for /f "delims=" %%I in ('where javac') do set "JAVA_BIN=%%~dpI"
    )
)

echo ==============================================================================
echo   Dang bien dich source code DAMMTP2P sang dist\DAMMTP2P.jar...
echo ==============================================================================

if not exist "build\classes" mkdir "build\classes"
if not exist "dist\lib" mkdir "dist\lib"

rem 1. Tao danh sach file nguon .java (duong dan tuong doi, forward slash chong loi escape)
powershell -NoProfile -Command "(Get-ChildItem -Recurse -Filter *.java src | Resolve-Path -Relative) -replace '\\','/' | Set-Content sources.txt -Encoding ASCII"

rem 2. Bien dich bang javac voi UTF-8. FlatLaf duoc load bang reflection luc runtime.
"%JAVA_BIN%\javac.exe" -encoding UTF-8 -cp "build\classes;lib\flatlaf-3.5.4.jar" -d "build\classes" @sources.txt
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Bien dich that bai! Vui long kiem tra loi tren.
    del sources.txt 2>nul
    pause
    exit /b 1
)
del sources.txt 2>nul

rem 3. Dong goi thanh file JAR
"%JAVA_BIN%\jar.exe" cfm dist\DAMMTP2P.jar manifest.mf -C build\classes .

if %ERRORLEVEL% EQU 0 (
    copy /y "lib\flatlaf-3.5.4.jar" "dist\lib\flatlaf-3.5.4.jar" >nul
    echo [THANH CONG] Da tao xong dist\DAMMTP2P.jar!
    echo [THANH CONG] Da copy thu vien FlatLaf vao dist\lib.
) else (
    echo [ERROR] Dong goi JAR that bai!
    pause
    exit /b 1
)

echo.
endlocal
