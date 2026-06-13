@echo off
set "JAVAFX_PATH=%~dp0lib"
set "JAVA=java"

echo [DEBUG] Launching with error capture...
"%JAVA%" -Djava.library.path=lib --module-path "%JAVAFX_PATH%" --add-modules javafx.controls,javafx.graphics,javafx.fxml --enable-native-access=javafx.graphics -cp "bin;src" com.traffic.ui.MainApp
echo [EXIT CODE] %ERRORLEVEL%
pause
