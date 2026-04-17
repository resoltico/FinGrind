@echo off
setlocal enableextensions

for %%I in ("%~dp0..") do set "APP_HOME=%%~fI"
set "RUNTIME_JAVA=%APP_HOME%\runtime\bin\java.exe"
set "APPLICATION_JAR=%APP_HOME%\lib\app\fingrind.jar"

if not exist "%RUNTIME_JAVA%" (
  >&2 echo error: missing bundled Java runtime at %RUNTIME_JAVA%
  exit /b 1
)

if not exist "%APPLICATION_JAR%" (
  >&2 echo error: missing FinGrind application JAR at %APPLICATION_JAR%
  exit /b 1
)

"%RUNTIME_JAVA%" ^
  --enable-native-access=ALL-UNNAMED ^
  "-Dfingrind.bundle.home=%APP_HOME%" ^
  -Dfingrind.runtime.distribution=self-contained-bundle ^
  -jar "%APPLICATION_JAR%" ^
  %*
