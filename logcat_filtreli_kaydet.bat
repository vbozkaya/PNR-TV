@echo off
echo ========================================
echo Logcat Kaydedici - PNR TV
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

REM 3. Android Studio'nun varsayılan konumunu kontrol et
if exist "C:\Users\%USERNAME%\AppData\Local\Android\Sdk\platform-tools\adb.exe" (
    set ADB_PATH=C:\Users\%USERNAME%\AppData\Local\Android\Sdk\platform-tools\adb.exe
    goto :adb_found
)

REM ADB bulunamadı
echo [HATA] ADB bulunamadi!
echo.
echo Lutfen asagidaki adimlari takip edin:
echo 1. Android Studio'yu acin
echo 2. Tools -^> SDK Manager -^> SDK Tools
echo 3. "Android SDK Platform-Tools" secili oldugundan emin olun
echo 4. Veya ADB'nin tam yolunu buraya girin
echo.
echo ADB yolunu manuel girmek ister misiniz? (E/H)
set /p MANUAL_INPUT=
if /i "%MANUAL_INPUT%"=="E" (
    echo ADB'nin tam yolunu girin (ornek: C:\Users\Kullanici\AppData\Local\Android\Sdk\platform-tools\adb.exe):
    set /p ADB_PATH=
    if not exist "%ADB_PATH%" (
        echo [HATA] Belirtilen yol bulunamadi!
        pause
        exit /b 1
    )
    goto :adb_found
)
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
echo Dosya: player_logs_filtered.txt
echo.
echo Durdurmak icin Ctrl+C basin ve 'Y' yazin
echo ========================================
echo.

REM Logcat'i temizle
"%ADB_PATH%" logcat -c

REM Logları kaydet
"%ADB_PATH%" logcat com.pnr.tv:D PlayerActivity:D PlayerViewModel:D PlayerControlView:D *:S > player_logs_filtered.txt

REM Eğer buraya geldiyse, bir hata oluşmuş demektir
echo.
echo [HATA] Logcat kaydi durdu!
pause

