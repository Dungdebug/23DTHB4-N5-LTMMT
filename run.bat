@echo off
rem ==============================================================================
rem DAMMTP2P - 1-to-1 P2P Chat & File Transfer over ZeroTier
rem Windows Launcher Script
rem ==============================================================================

chcp 65001 > nul
setlocal

cd /d "%~dp0"

if not exist "dist\DAMMTP2P.jar" (
    echo [ERROR] Không tìm thấy file dist\DAMMTP2P.jar.
    echo Vui lòng biên dịch dự án bằng lệnh: ant compile jar
    echo.
    pause
    exit /b 1
)

echo ==============================================================================
echo   Đang khởi động DAMMTP2P (P2P Chat & File Transfer over ZeroTier)...
echo ==============================================================================
echo.

java -Dfile.encoding=UTF-8 -jar "dist\DAMMTP2P.jar"

endlocal
