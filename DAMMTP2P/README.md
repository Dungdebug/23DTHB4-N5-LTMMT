# DAMMTP2P

Do an mon Lap trinh mang may tinh: ung dung chat nhom, chat rieng, truyen tep va voice qua ZeroTier/LAN ao.

## Chay nhanh

1. Cai JDK/JRE 17 tro len.
2. Chay server: mo `CHAY_SERVER.bat`.
3. Chay client: mo `CHAY_CLIENT.bat`.
4. Neu chua co `dist/DAMMTP2P.jar`, hai file tren se tu goi `build.bat` de bien dich truoc khi chay.

Co the chay truc tiep bang lenh:

```bat
build.bat
run-server.bat
run-client.bat
```

## Chuc nang chinh

- Dang ky/dang nhap tai khoan, mat khau bam PBKDF2.
- Chat rieng 1-1 va chat nhom qua TCP port `8888`.
- Truyen tep qua kenh rieng TCP port `8889`, co SHA-256 va ho tro tep lon.
- Voice chat qua UDP port `8890`.
- Giao dien Java Swing/FlatLaf cho client va dashboard server.

## Cau truc du an

```text
src/                  Ma nguon Java
lib/                  Thu vien can thiet
dist/                 JAR sau khi build
downloads/            Noi client luu file nhan duoc
server_data/          Du lieu tai khoan sinh ra khi chay server
build.bat             Bien dich va dong goi JAR
CHAY_SERVER.bat       Chay server
CHAY_CLIENT.bat       Chay client
package-release.bat   Dong goi .exe bang jpackage neu can
```

## Kiem thu

```bat
java -Dfile.encoding=UTF-8 -jar dist\DAMMTP2P.jar --test
```

## Ghi chu

Thu muc `build/`, `dist/`, `release/`, `server_data/` va cac file trong `downloads/` la du lieu sinh ra khi build/chay. Neu bi xoa, co the tao lai bang `build.bat` hoac khi ung dung chay.
