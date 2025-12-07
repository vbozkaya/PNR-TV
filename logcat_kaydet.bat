@echo off
echo ========================================
echo Logcat Kaydedici - Tum Loglar
echo ========================================
echo.

REM ADB'yi otomatik bul
set ADB_PATH=

REM 1. PATH'te ara
where adb >nul 2>&1
if %errorlevel% equ 0 (
    set ADB_PATH=adb
    goto :adb_found
)

REM 2. Standart Android SDK konumlarını kontrol et
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
    goto :adb_found
)

if exist "%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe" (
    set ADB_PATH=%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe
    goto :adb_found
)

if exist "%ANDROID_HOME%\platform-tools\adb.exe" (
    set ADB_PATH=%ANDROID_HOME%\platform-tools\adb.exe
    goto :adb_found
)

if exist "C:\Users\%USERNAME%\AppData\Local\Android\Sdk\platform-tools\adb.exe" (
    set ADB_PATH=C:\Users\%USERNAME%\AppData\Local\Android\Sdk\platform-tools\adb.exe
    goto :adb_found
)

echo [HATA] ADB bulunamadi!
echo.
echo Lutfen Android Studio'yu acin ve SDK Manager'dan Platform Tools'u yukleyin.
echo Veya ADB yolunu manuel girin.
pause
exit /b 1

:adb_found
echo [OK] ADB bulundu: %ADB_PATH%
echo.

REM Cihaz bağlı mı kontrol et
"%ADB_PATH%" devices | findstr /C:"device" >nul 2>&1
if %errorlevel% neq 0 (
    echo [HATA] Cihaz bulunamadi!
    echo Lutfen cihazinizin bagli oldugundan ve USB debugging'in acik oldugundan emin olun.
    echo.
    pause
    exit /b 1
)

echo [OK] Cihaz bulundu
echo.
echo Logcat kaydediliyor...
echo Dosya: player_logs.txt
echo.
echo Durdurmak icin Ctrl+C basin ve 'Y' yazin
echo ========================================
echo.

REM Logcat'i temizle
"%ADB_PATH%" logcat -c

REM Logları kaydet
"%ADB_PATH%" logcat > player_logs.txt

REM Eğer buraya geldiyse, bir hata oluşmuş demektir
echo.
echo [HATA] Logcat kaydi durdu!
pause

