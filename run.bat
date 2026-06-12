@echo off
title Traffic Simulation

echo.
echo   ======================================
echo       Traffic Simulation - Build/Run    
echo   ======================================
echo.

set MODULES=--module-path lib --add-modules javafx.controls,javafx.graphics

echo [1/2] Compiling...
javac %MODULES% -d bin src/com/traffic/core/*.java src/com/traffic/drivers/*.java src/com/traffic/map/*.java src/com/traffic/maps/*.java src/com/traffic/ui/*.java src/com/traffic/vehicles/*.java 2>&1

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Compile failed!
    pause
    exit /b 1
)
echo [OK] Compile successful.

echo.
echo [2/2] Launching...
java %MODULES% --enable-native-access=javafx.graphics -Djava.library.path=lib -cp "bin;src" com.traffic.ui.MainApp

pause
