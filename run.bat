@echo off
title Traffic Simulation

echo ══════════════════════════════════════════
echo   Traffic Simulation - Build ^& Run
echo ══════════════════════════════════════════

echo.
echo [1/2] Compiling...
javac --module-path lib --add-modules javafx.controls,javafx.graphics -d bin src/com/traffic/core/*.java src/com/traffic/drivers/*.java src/com/traffic/map/*.java src/com/traffic/maps/*.java src/com/traffic/ui/*.java src/com/traffic/vehicles/*.java 2>&1

if %ERRORLEVEL% neq 0 (
    echo [ERROR] Compile failed!
    pause
    exit /b 1
)
echo [OK] Compile successful.

echo.
echo [2/2] Launching...
java -Djava.library.path=lib --module-path lib --add-modules javafx.controls,javafx.graphics --enable-native-access=javafx.graphics -cp "bin;src" com.traffic.ui.MainApp

pause
