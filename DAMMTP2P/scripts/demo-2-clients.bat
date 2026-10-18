@echo off
rem ==============================================================================
rem DEMO 2 NGUOI DUNG CUNG LUC (ALICE HOST & BOB CLIENT)
rem Phuc vu trinh dien truc tiep cho giang vien tren cung 1 may tinh
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

echo Đang khoi dong Cua so 1: Alice (Dong vai tro Host Server)...
start "DAMMTP2P - Alice (Host)" javaw "-Dfile.encoding=UTF-8" -jar "dist\DAMMTP2P.jar" --user alice --host

timeout /t 1 /nobreak > nul

echo Đang khoi dong Cua so 2: Bob (Dong vai tro Client)...
start "DAMMTP2P - Bob (Client)" javaw "-Dfile.encoding=UTF-8" -jar "dist\DAMMTP2P.jar" --user bob --ip 127.0.0.1

echo.
echo ==============================================================================
echo [THANH CONG] Da mo 2 cua so Alice va Bob!
echo - Alice: Mat khau: alice123 (Server nhung dang chay tren cong 8888/8889)
echo - Bob  : Mat khau: bob123
echo -> 2 ben se thay nhau truc tuyen, chat 1-1, tao group chat va truyen tep tin!
echo ==============================================================================
endlocal
