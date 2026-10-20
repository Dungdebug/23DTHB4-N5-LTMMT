@echo off
chcp 65001 >nul
title DAMMTP2P - Rebuild and Package Release
cd /d "%~dp0"

echo ==============================================================================
echo   DAMMTP2P - REBUILD, TEST AND PACKAGE
echo ==============================================================================

set "JAVA_BIN=C:\Program Files\Java\jdk-26.0.2.1\bin"
if not exist "%JAVA_BIN%\javac.exe" (
    where javac >nul 2>nul
    if %ERRORLEVEL% equ 0 (
        for /f "delims=" %%I in ('where javac') do set "JAVA_BIN=%%~dpI"
    ) else (
        echo [LOI] Khong tim thay JDK. Vui long cai JDK 17+ hoac cau hinh PATH.
        pause
        exit /b 1
    )
)

echo [1/4] Bien dich ma nguon Java...
if not exist "build\classes" mkdir build\classes
if not exist "dist\lib" mkdir dist\lib
powershell -NoProfile -Command "(Get-ChildItem -Path src -Filter *.java -Recurse).FullName | Set-Content sources.txt; & '%JAVA_BIN%\javac.exe' -encoding UTF-8 -d build/classes '@sources.txt'"
if %ERRORLEVEL% neq 0 (
    echo [LOI] Bien dich that bai.
    del sources.txt 2>nul
    pause
    exit /b 1
)
del sources.txt 2>nul

echo [2/4] Dong goi dist\DAMMTP2P.jar...
"%JAVA_BIN%\jar.exe" cfm dist\DAMMTP2P.jar manifest.mf -C build/classes .
if %ERRORLEVEL% neq 0 (
    echo [LOI] Dong goi JAR that bai.
    pause
    exit /b 1
)
copy /y "lib\flatlaf-3.5.4.jar" "dist\lib\flatlaf-3.5.4.jar" >nul

echo [3/4] Chay bo kiem thu tu dong...
"%JAVA_BIN%\java.exe" -Dfile.encoding=UTF-8 -jar dist\DAMMTP2P.jar --test
if %ERRORLEVEL% neq 0 (
    echo [LOI] Mot hoac nhieu test khong dat.
    pause
    exit /b 1
)

echo [4/4] Dong goi app-image bang jpackage neu co...
if exist "%JAVA_BIN%\jpackage.exe" (
    if not exist "release" mkdir release
    "%JAVA_BIN%\jpackage.exe" --type app-image --name "DAMMTP2P-Client" --input dist --main-jar DAMMTP2P.jar --main-class dammtp2p.DAMMTP2P --arguments "--client" --java-options "-Dfile.encoding=UTF-8" --dest release
    "%JAVA_BIN%\jpackage.exe" --type app-image --name "DAMMTP2P-Server" --input dist --main-jar DAMMTP2P.jar --main-class dammtp2p.DAMMTP2P --arguments "--server" --java-options "-Dfile.encoding=UTF-8" --dest release
    echo Da dong goi vao release\.
) else (
    echo [CANH BAO] Khong tim thay jpackage.exe, bo qua buoc tao .exe.
)

echo ==============================================================================
echo   HOAN TAT
echo ==============================================================================
pause
