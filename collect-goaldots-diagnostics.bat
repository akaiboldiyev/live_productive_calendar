@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM ==============================================================================
REM Goal Dots Automated Diagnostic Collector for Xiaomi HyperOS / POCO X8 Pro
REM Package: com.aistudio.goaldots.wkqn
REM ==============================================================================

set "APP_ID=com.aistudio.goaldots.wkqn"
set "SCRIPT_DIR=%~dp0"

REM Prefer the private platform-tools folder shipped beside this script. This means
REM the user never needs to add adb to PATH or open a terminal in a special folder.
set "ADB=%SCRIPT_DIR%platform-tools\adb.exe"
echo [1/3] Checking ADB connection...
if exist "%ADB%" goto :adb_found

REM Retain a PATH fallback for developers who run the script from the repository.
set "ADB=adb.exe"

where adb.exe >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] adb.exe was not found.
    echo Keep the platform-tools folder beside this script, then run START_GOALDOTS_DIAGNOSTICS.bat.
    pause
    exit /b 1
)

:adb_found

for /f "tokens=1,2" %%A in ('"%ADB%" devices ^| findstr /v "List of devices attached" ^| findstr /r "[a-zA-Z0-9]"') do (
    if "%%B"=="device" (
        set "DEVICE_SERIAL=%%A"
        goto :device_found
    )
)

echo [ERROR] No connected Android device found!
echo Ensure USB debugging is ON and device is authorized.
pause
exit /b 1

:device_found
echo Device connected: %DEVICE_SERIAL%

REM Safe timestamp generation for Windows CMD without localized date slash errors
for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value 2^>nul') do set "DATETIME_RAW=%%I"
if not defined DATETIME_RAW (
    set "SAFE_STAMP=%RANDOM%"
) else (
    set "SAFE_STAMP=%DATETIME_RAW:~0,8%_%DATETIME_RAW:~8,6%"
)

set "OUTDIR=%SCRIPT_DIR%diagnostics_%SAFE_STAMP%"
mkdir "%OUTDIR%" 2>nul

echo Target Application: %APP_ID%
echo Output Directory:  %OUTDIR%
echo.

echo [2/3] Starting continuous logcat capture to %OUTDIR%\logcat_stream.txt...
"%ADB%" logcat -c
start "GoalDots_Logcat_Session" /B "%ADB%" logcat -v time > "%OUTDIR%\logcat_stream.txt"

echo.
echo ==============================================================================
echo [3/3] DIAGNOSTIC SESSION IS NOW ACTIVE AND RECORDING!
echo.
echo The script will record state snapshots (PID, dumpsys wallpaper, services)
echo continuously every 2 seconds into '%OUTDIR%'.
echo.
echo REPRODUCE YOUR SCENARIO NOW:
echo  1. STATE A: Check working live wallpaper.
echo  2. STATE B: Swipe Goal Dots card from Recent Apps.
echo  3. STATE C: Lock screen (Power button) -> wait 3s -> unlock.
echo  4. STATE D: Open app -> swipe from Recents -> lock/unlock (Complete failure).
echo.
echo *** WHEN FINISHED: PRESS Ctrl+C IN THIS WINDOW TO STOP ***
echo ==============================================================================
echo.

set /a SNAPSHOT_COUNT=0

:loop
set /a SNAPSHOT_COUNT+=1
set "PADDED_INDEX=0000%SNAPSHOT_COUNT%"
set "PADDED_INDEX=!PADDED_INDEX:~-4!"

set "MAIN_PID="
for /f "usebackq delims=" %%P in (`"%ADB%" shell "pidof %APP_ID%" 2^>nul`) do (
    set "MAIN_PID=%%P"
)
if not defined MAIN_PID set "MAIN_PID=NOT_RUNNING"

set "WALLPAPER_PID="
for /f "usebackq delims=" %%P in (`"%ADB%" shell "pidof %APP_ID%:wallpaper" 2^>nul`) do (
    set "WALLPAPER_PID=%%P"
)
if not defined WALLPAPER_PID set "WALLPAPER_PID=NOT_RUNNING"

set "PREFIX=%OUTDIR%\snap_!PADDED_INDEX!_MAIN_!MAIN_PID!_WALLPAPER_!WALLPAPER_PID!"

echo [!TIME!] Snapshot #!PADDED_INDEX! -- Main PID: !MAIN_PID! | Wallpaper PID: !WALLPAPER_PID!

(
    echo SNAPSHOT: !PADDED_INDEX!
    echo TIMESTAMP: !DATE! !TIME!
    echo MAIN_PROCESS_PID: !MAIN_PID!
    echo WALLPAPER_PROCESS_PID: !WALLPAPER_PID!
) > "!PREFIX!_info.txt" 2>nul

"%ADB%" shell dumpsys wallpaper > "!PREFIX!_dumpsys_wallpaper.txt" 2>nul
"%ADB%" shell "dumpsys activity services %APP_ID%" > "!PREFIX!_dumpsys_services.txt" 2>nul

REM Reliable 2-second sleep in Windows CMD
ping 127.0.0.1 -n 3 >nul 2>&1

goto :loop
