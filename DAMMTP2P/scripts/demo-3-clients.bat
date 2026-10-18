@echo off
rem ==============================================================================
rem DEMO 3 NGUOI DUNG CUNG LUC (ALICE HOST, BOB & CHARLIE CLIENTS)
rem Phuc vu trinh dien Group Chat va truyen file da khach
rem ==============================================================================
chcp 65001 > nul
setlocal
cd /d "%~dp0.."

if not exist "dist\DAMMTP2P.jar" (
    echo [ERROR] Khong tim thay dist\DAMMTP2P.jar.
    echo Vui long chay build.bat truoc.
    pause
    exit /b 1
)

echo Đang khoi dong Cua so 1: Alice (Host Server)...
start "DAMMTP2P - Alice" javaw "-Dfile.encoding=UTF-8" -jar "dist\DAMMTP2P.jar" --user alice --host

timeout /t 1 /nobreak > nul

echo Đang khoi dong Cua so 2: Bob (Client)...
start "DAMMTP2P - Bob" javaw "-Dfile.encoding=UTF-8" -jar "dist\DAMMTP2P.jar" --user bob --ip 127.0.0.1

timeout /t 1 /nobreak > nul

echo Đang khoi dong Cua so 3: Charlie (Client)...
start "DAMMTP2P - Charlie" javaw "-Dfile.encoding=UTF-8" -jar "dist\DAMMTP2P.jar" --user charlie --ip 127.0.0.1

echo.
echo ==============================================================================
echo [THANH CONG] Da mo 3 cua so: Alice, Bob, Charlie!
echo - Mat khau: alice123, bob123, charlie123
echo -> Test tao Group Chat da thanh vien va truyen tep tin dong thoi!
echo ==============================================================================
endlocal
