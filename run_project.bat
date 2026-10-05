@echo off
title GreenCorridor - Smart Traffic Junction Simulator
echo ===================================================
echo Starting GreenCorridor Java Course End Project...
echo ===================================================
cd /d "%~dp0app"
java -jar "target\GreenCorridor.jar"
if %errorlevel% neq 0 (
    echo.
    echo An error occurred while launching the application.
    pause
)
