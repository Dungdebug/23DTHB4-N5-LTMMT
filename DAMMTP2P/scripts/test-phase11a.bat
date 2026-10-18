@echo off
chcp 65001 > nul
setlocal
cd /d "%~dp0.."

if not exist "dist\DAMMTP2P.jar" (
    echo [ERROR] Không tìm thấy file dist\DAMMTP2P.jar.
    echo Vui lòng biên dịch trước bằng Apache Ant.
    exit /b 1
)

java -Dfile.encoding=UTF-8 -jar "dist\DAMMTP2P.jar" --test-11a
endlocal
