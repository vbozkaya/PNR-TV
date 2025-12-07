@echo off
chcp 65001 >nul
echo ========================================
echo PlayerControlView Logları Kaydediliyor
echo ========================================
echo.

REM ADB yolunu otomatik bul
set ADB_PATH=
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
) else if exist "%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe" (
    set ADB_PATH=%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe
) else if exist "%ANDROID_HOME%\platform-tools\adb.exe" (
    set ADB_PATH=%ANDROID_HOME%\platform-tools\adb.exe
) else (
    where adb >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        set ADB_PATH=adb
    ) else (
        echo [HATA] ADB bulunamadı!
        echo Lütfen Android SDK'yı yükleyin veya ADB'yi PATH'e ekleyin.
        pause
        exit /b 1
    )
)

echo ADB yolu: %ADB_PATH%
echo.

REM Cihaz bağlantısını kontrol et
%ADB_PATH% devices | findstr /C:"device" >nul
if %ERRORLEVEL% NEQ 0 (
    echo [HATA] Cihaz bulunamadı!
    echo Lütfen cihazınızın USB ile bağlı olduğundan ve USB hata ayıklama modunun açık olduğundan emin olun.
    pause
    exit /b 1
)

echo Cihaz bağlantısı başarılı!
echo.

REM Dosya adını zaman damgası ile oluştur
for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value') do set datetime=%%I
set filename=player_control_view_logs_%datetime:~0,8%_%datetime:~8,6%.txt

echo Loglar kaydediliyor: %filename%
echo Durdurmak için Ctrl+C tuşlarına basın...
echo.

REM PlayerControlView ve PlayerActivity loglarını filtrele ve kaydet
%ADB_PATH% logcat -c
%ADB_PATH% logcat | findstr /C:"PlayerControlView" /C:"PlayerActivity" /C:"🔍" /C:"📊" /C:"⏩" /C:"⏪" /C:"🔘" /C:"▶️" /C:"🎯" /C:"⚠️" /C:"✅" /C:"❌" > %filename%

pause

