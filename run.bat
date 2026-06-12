@echo off
title Traffic Simulation

echo ==========================================
echo   Traffic Simulation - Build ^& Run
echo ==========================================


set JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot
set JAVAFX_PATH=%USERPROFILE%\javafx-sdk\javafx-sdk-21.0.5\lib
set JAVAC=%JAVA_HOME%\bin\javac.exe
set JAVA=%JAVA_HOME%\bin\java.exe

echo.
echo [1/2] Compiling...
if not exist bin mkdir bin
"%JAVAC%" --module-path "%JAVAFX_PATH%" --add-modules javafx.controls,javafx.graphics,javafx.fxml -d bin src/com/traffic/core/*.java src/com/traffic/drivers/*.java src/com/traffic/map/*.java src/com/traffic/maps/*.java src/com/traffic/ui/*.java src/com/traffic/vehicles/*.java 2>&1

if %ERRORLEVEL% neq 0 (
    echo [ERROR] Compile failed!
    pause
    exit /b 1
)
echo [OK] Compile successful.

echo.
echo [2/2] Launching...
"%JAVA%" -Djava.library.path=lib --module-path "%JAVAFX_PATH%" --add-modules javafx.controls,javafx.graphics,javafx.fxml --enable-native-access=javafx.graphics -cp "bin;src" com.traffic.ui.MainApp

pause