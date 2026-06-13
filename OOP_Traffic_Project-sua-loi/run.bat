@echo off
title Traffic Simulation

echo ==========================================
echo   Traffic Simulation - Build ^& Run
echo ==========================================


set "JAVA_HOME=D:\jdk-26.0.1"
set "JAVAFX_PATH=%~dp0lib"

if exist "%JAVA_HOME%\bin\javac.exe" (
    set "JAVAC=%JAVA_HOME%\bin\javac.exe"
    set "JAVA=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVAC=javac"
    set "JAVA=java"
)

echo.
echo [1/2] Compiling...
if not exist bin mkdir bin
if not exist bin\images mkdir bin\images
if not exist bin\sounds mkdir bin\sounds
"%JAVAC%" --module-path "%JAVAFX_PATH%" --add-modules javafx.controls,javafx.graphics -d bin src/com/traffic/core/*.java src/com/traffic/drivers/*.java src/com/traffic/map/*.java src/com/traffic/maps/*.java src/com/traffic/ui/*.java src/com/traffic/vehicles/*.java 2>&1

if %ERRORLEVEL% neq 0 (
    echo [ERROR] Compile failed!
    pause
    exit /b 1
)

echo [OK] Compile successful.
echo [*] Copying resources...
copy /Y src\style.css bin\style.css >nul 2>&1
xcopy /E /Y /I assets\images bin\images >nul 2>&1
copy /Y assets\sounds\*.wav bin\sounds\ >nul 2>&1
echo [OK] Resources copied.

echo.
echo [2/2] Launching...
"%JAVA%" --module-path "%JAVAFX_PATH%" --add-modules javafx.controls,javafx.graphics --enable-native-access=javafx.graphics -cp "bin;src" com.traffic.ui.MainApp 2>&1

pause