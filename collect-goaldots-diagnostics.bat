@echo off
setlocal enabledelayedexpansion

REM ==============================================================================
REM Goal Dots Automated Diagnostic Collector for Xiaomi HyperOS / POCO X8 Pro
REM Package: com.aistudio.goaldots.wkqn
REM Service: com.aistudio.goaldots.wkqn/com.example.service.GoalWallpaperService
REM ==============================================================================

set APP_ID=com.aistudio.goaldots.wkqn
set WALLPAPER_SERVICE=%APP_ID%/com.example.service.GoalWallpaperService

echo [1/4] Checking ADB connection...
where adb >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] 'adb' not found in PATH!
    echo Please make sure Android SDK platform-tools is installed and in PATH,
    echo or place this script in your platform-tools folder.
    pause
    exit /b 1
)

for /f "tokens=1,2" %%A in ('adb devices ^| findstr /v "List of devices attached" ^| findstr /r "[a-zA-Z0-9]"') do (
    if "%%B"=="device" (
        set DEVICE_SERIAL=%%A
        goto :device_found
    )
)

echo [ERROR] No connected device in 'device' state found!
echo Please make sure:
echo  1. Phone is connected via USB.
echo  2. USB debugging is enabled in Developer Options.
echo  3. You accepted the computer authorization on phone screen.
pause
exit /b 1

:device_found
echo Device detected: %DEVICE_SERIAL%

set TIMESTAMP=%DATE:~6,4%%DATE:~3,2%%DATE:~0,2%_%TIME:~0,2%%TIME:~3,2%%TIME:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%
set OUTDIR=diagnostics_%TIMESTAMP%
mkdir "%OUTDIR%"

echo.
echo ==============================================================================
echo Target Application: %APP_ID%
echo Saving outputs to:  %OUTDIR%
echo ==============================================================================
echo.

echo [2/4] Starting logcat capture in background...
adb logcat -c
start "GoalDots Logcat" /B cmd /c "adb logcat -v time > %OUTDIR%\logcat_stream.txt"

echo [3/4] Diagnostic session is RUNNING.
echo.
echo ==============================================================================
echo INSTRUCTIONS:
echo 1. Open Goal Dots on your phone and ensure goal is displayed.
echo 2. Reproduce the scenario:
echo    - State A: Normal working state.
echo    - State B: Swipe Goal Dots from Recent Apps (screen on).
echo    - State C: Lock screen (Power button) - wait 3s - unlock.
echo    - State D: Open app again - swipe from Recents - lock - unlock (Failure).
echo.
echo The script will record continuous snapshots (PID, dumpsys wallpaper, services)
echo automatically every 2 seconds into %OUTDIR%.
echo.
echo Press Ctrl+C in this window or press ANY KEY to STOP recording and pack results.
echo ==============================================================================
echo.

set SNAPSHOT_NUM=0

:loop
set /a SNAPSHOT_NUM+=1
set PADDED_NUM=000%SNAPSHOT_NUM%
set PADDED_NUM=%PADDED_NUM:~-3%

for /f "delims=" %%P in ('adb shell pidof %APP_ID% 2^>nul') do set CURRENT_PID=%%P
if "%CURRENT_PID%"=="" set CURRENT_PID=DEAD_OR_NOT_RUNNING

set SNAP_PREFIX=%OUTDIR%\snap_%PADDED_NUM%_PID_%CURRENT_PID%

echo [%TIME%] Snapshot #%PADDED_NUM% - PID: %CURRENT_PID%
echo PID: %CURRENT_PID% > "%SNAP_PREFIX%_summary.txt"
echo TIME: %DATE% %TIME% >> "%SNAP_PREFIX%_summary.txt"

adb shell dumpsys wallpaper > "%SNAP_PREFIX%_dumpsys_wallpaper.txt" 2>nul
adb shell dumpsys activity services %APP_ID% > "%SNAP_PREFIX%_dumpsys_services.txt" 2>nul

choice /T 2 /D Y /N >nul 2>nul
if %errorlevel% neq 1 goto :loop

:finish
echo.
echo [4/4] Stopping logcat and collecting in-app diagnostic log...
taskkill /FI "WINDOWTITLE eq GoalDots Logcat*" /F >nul 2>nul

echo Pulling internal app diagnostic log from /data/data/%APP_ID%/files/...
adb shell "run-as %APP_ID% cat files/goal_dots_diagnostics.log" > "%OUTDIR%\internal_app_diagnostic.log" 2>nul

echo.
echo ==============================================================================
echo DIAGNOSTIC COLLECTION FINISHED!
echo Folder created: %OUTDIR%
echo Contents:
echo  - logcat_stream.txt (Full real-time logcat with millisecond timeline)
echo  - internal_app_diagnostic.log (In-app diagnostic recorder log)
echo  - snap_XXX_PID_* (Continuous system wallpaper & service state snapshots)
echo ==============================================================================
echo.
echo Please ZIP the folder '%OUTDIR%' or send its contents.
pause
