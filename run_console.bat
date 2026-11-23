@echo off
setlocal enabledelayedexpansion

REM ==============================================================================
REM CloudSim 4.0 Isolation & Density Study - Windows Console Launcher
REM Package: isolationstudy
REM Owned by Person 3
REM ==============================================================================

cd /d "%~dp0"

REM 1. Detect Java Compiler and Runtime
set "JAVAC_CMD=javac"
set "JAVA_CMD=java"

if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javac.exe" set "JAVAC_CMD=%JAVA_HOME%\bin\javac.exe"
    if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
)

REM Fallback check for JDK in Program Files
if not exist "%JAVAC_CMD%" (
    if exist "C:\Program Files\Java\jdk-24\bin\javac.exe" (
        set "JAVAC_CMD=C:\Program Files\Java\jdk-24\bin\javac.exe"
        set "JAVA_CMD=C:\Program Files\Java\jdk-24\bin\java.exe"
    )
)

REM 2. Verify Library Dependencies
set "CP=cloud-iso-study\lib\cloudsim-4.0.jar;cloud-iso-study\lib\commons-math3-3.6.1.jar"

if not exist "cloud-iso-study\lib\cloudsim-4.0.jar" (
    echo [ERROR] cloud-iso-study\lib\cloudsim-4.0.jar not found!
    echo Please ensure the CloudSim 4.0 JAR is present in cloud-iso-study\lib.
    exit /b 1
)

if not exist "cloud-iso-study\lib\commons-math3-3.6.1.jar" (
    echo [ERROR] cloud-iso-study\lib\commons-math3-3.6.1.jar not found!
    echo Please ensure the commons-math3-3.6.1 JAR is present in cloud-iso-study\lib.
    exit /b 1
)

REM 3. Create target/classes directory
if not exist "target\classes" (
    mkdir "target\classes"
)

REM 4. Compile Java sources
echo [INFO] Compiling isolationstudy sources...
dir /b /s src\isolationstudy\*.java > sources_win.tmp
"%JAVAC_CMD%" -cp "%CP%" -d "target\classes" @sources_win.tmp
set "COMPILE_STATUS=%ERRORLEVEL%"
del sources_win.tmp

if %COMPILE_STATUS% neq 0 (
    echo [ERROR] Compilation failed with status %COMPILE_STATUS%
    exit /b %COMPILE_STATUS%
)

REM 5. Execute Main entry point with forwarded arguments
"%JAVA_CMD%" -cp "target\classes;%CP%" isolationstudy.Main %*
exit /b %ERRORLEVEL%

