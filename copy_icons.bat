@echo off
chcp 65001 > nul
echo ========================================================
echo Copying new Android app icons to app\src\main\res...
echo ========================================================

xcopy /E /Y /I "ios code\new icon\android\res\*" "app\src\main\res\"
copy /Y "ios code\new icon\android\play_store_512.png" "app\play_store_512.png" > nul

if not exist "app\src\main\res\drawable-night" mkdir "app\src\main\res\drawable-night"
copy /Y "ios code\logo find light.png" "app\src\main\res\drawable\ic_app_logo_full.png" > nul
copy /Y "ios code\logo find dark.png" "app\src\main\res\drawable-night\ic_app_logo_full.png" > nul

echo.
echo ========================================================
echo App icons and logos updated successfully!
echo ========================================================
pause
