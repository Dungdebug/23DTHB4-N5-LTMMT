@echo off
chcp 65001 >nul
title DAMMTP2P - 20 GiB Boundary Test Suite
echo [DAMMTP2P] Running 20 GiB File Transfer Boundary Verification Suite...
cd /d "%~dp0\.."
java "-Dfile.encoding=UTF-8" -jar dist\DAMMTP2P.jar --test-20gb
pause
