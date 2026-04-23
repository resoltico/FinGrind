@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem
@rem SPDX-License-Identifier: Apache-2.0
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal EnableExtensions

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

call :scanFinGrindArguments %*
if /I not "%FINGRIND_HAS_PROJECT_CACHE%"=="true" (
    call :resolveFinGrindProjectCacheDir
    if not exist "%FINGRIND_GRADLE_PROJECT_CACHE_DIR%" mkdir "%FINGRIND_GRADLE_PROJECT_CACHE_DIR%" >NUL 2>&1
    if not exist "%FINGRIND_GRADLE_PROJECT_CACHE_DIR%" (
        echo. 1>&2
        echo ERROR: Unable to create FinGrind Gradle project cache at %FINGRIND_GRADLE_PROJECT_CACHE_DIR% 1>&2
        echo. 1>&2
        goto fail
    )
    set "FINGRIND_GRADLE_PROJECT_CACHE_ARG=--project-cache-dir=%FINGRIND_GRADLE_PROJECT_CACHE_DIR%"
)
if /I not "%FINGRIND_HAS_BUILD_LOGIC_DIR%"=="true" (
    call :resolveFinGrindBuildLogicDir
    if not exist "%FINGRIND_GRADLE_BUILD_LOGIC_DIR%" mkdir "%FINGRIND_GRADLE_BUILD_LOGIC_DIR%" >NUL 2>&1
    if not exist "%FINGRIND_GRADLE_BUILD_LOGIC_DIR%" (
        echo. 1>&2
        echo ERROR: Unable to create FinGrind Gradle build-logic directory at %FINGRIND_GRADLE_BUILD_LOGIC_DIR% 1>&2
        echo. 1>&2
        goto fail
    )
    set "FINGRIND_GRADLE_BUILD_LOGIC_ARG=-Dfingrind.gradle.build-logic-dir=%FINGRIND_GRADLE_BUILD_LOGIC_DIR%"
)
if /I not "%FINGRIND_HAS_JACOCO_ROOT%"=="true" (
    call :resolveFinGrindJacocoRoot
    if not exist "%FINGRIND_GRADLE_JACOCO_ROOT%" mkdir "%FINGRIND_GRADLE_JACOCO_ROOT%" >NUL 2>&1
    if not exist "%FINGRIND_GRADLE_JACOCO_ROOT%" (
        echo. 1>&2
        echo ERROR: Unable to create FinGrind JaCoCo directory at %FINGRIND_GRADLE_JACOCO_ROOT% 1>&2
        echo. 1>&2
        goto fail
    )
    set "FINGRIND_GRADLE_JACOCO_ARG=-Dfingrind.gradle.jacoco-root=%FINGRIND_GRADLE_JACOCO_ROOT%"
)
call :resolveFinGrindProjectBuildExternalization
if /I not "%FINGRIND_HAS_PROJECT_BUILD_ROOT%"=="true" (
    if /I "%FINGRIND_SHOULD_EXTERNALIZE_PROJECT_BUILD_ROOT%"=="true" (
        call :resolveFinGrindProjectBuildRoot
        if not exist "%FINGRIND_GRADLE_PROJECT_BUILD_ROOT%" mkdir "%FINGRIND_GRADLE_PROJECT_BUILD_ROOT%" >NUL 2>&1
        if not exist "%FINGRIND_GRADLE_PROJECT_BUILD_ROOT%" (
            echo. 1>&2
            echo ERROR: Unable to create FinGrind project build root at %FINGRIND_GRADLE_PROJECT_BUILD_ROOT% 1>&2
            echo. 1>&2
            goto fail
        )
        set "FINGRIND_GRADLE_PROJECT_BUILD_ROOT_ARG=-Dfingrind.gradle.project-build-root=%FINGRIND_GRADLE_PROJECT_BUILD_ROOT%"
    )
)

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line



@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% %FINGRIND_GRADLE_PROJECT_CACHE_ARG% %FINGRIND_GRADLE_BUILD_LOGIC_ARG% %FINGRIND_GRADLE_JACOCO_ARG% %FINGRIND_GRADLE_PROJECT_BUILD_ROOT_ARG% "-Dorg.gradle.appname=%APP_BASE_NAME%" -jar "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega

:scanFinGrindArguments
set "FINGRIND_HAS_PROJECT_CACHE=false"
set "FINGRIND_HAS_BUILD_LOGIC_DIR=false"
set "FINGRIND_HAS_JACOCO_ROOT=false"
set "FINGRIND_HAS_PROJECT_BUILD_ROOT=false"
:scanFinGrindArgumentsLoop
if "%~1"=="" exit /b 0
if /I "%~1"=="--project-cache-dir" (
    set "FINGRIND_HAS_PROJECT_CACHE=true"
    shift
    if "%~1"=="" exit /b 0
    shift
    goto scanFinGrindArgumentsLoop
)
set "FINGRIND_SCAN_ARG=%~1"
if /I "%FINGRIND_SCAN_ARG:~0,20%"=="--project-cache-dir=" set "FINGRIND_HAS_PROJECT_CACHE=true"
if /I "%FINGRIND_SCAN_ARG:~0,34%"=="-Dfingrind.gradle.build-logic-dir=" set "FINGRIND_HAS_BUILD_LOGIC_DIR=true"
if /I "%FINGRIND_SCAN_ARG:~0,30%"=="-Dfingrind.gradle.jacoco-root=" set "FINGRIND_HAS_JACOCO_ROOT=true"
if /I "%FINGRIND_SCAN_ARG:~0,37%"=="-Dfingrind.gradle.project-build-root=" set "FINGRIND_HAS_PROJECT_BUILD_ROOT=true"
shift
goto scanFinGrindArgumentsLoop

:resolveFinGrindProjectCacheDir
if defined FINGRIND_GRADLE_PROJECT_CACHE_DIR goto projectCacheDirResolved
if defined FINGRIND_GRADLE_PROJECT_CACHE_ROOT (
    set "FINGRIND_PROJECT_CACHE_ROOT=%FINGRIND_GRADLE_PROJECT_CACHE_ROOT%"
) else if defined LOCALAPPDATA (
    set "FINGRIND_PROJECT_CACHE_ROOT=%LOCALAPPDATA%\FinGrind\gradle-project-cache"
) else if defined TEMP (
    set "FINGRIND_PROJECT_CACHE_ROOT=%TEMP%\fingrind-gradle-project-cache"
) else (
    set "FINGRIND_PROJECT_CACHE_ROOT=%APP_HOME%\.gradle-project-cache"
)
call :resolveFinGrindProjectCacheKey
set "FINGRIND_GRADLE_PROJECT_CACHE_DIR=%FINGRIND_PROJECT_CACHE_ROOT%\%FINGRIND_PROJECT_CACHE_KEY%"
:projectCacheDirResolved
exit /b 0

:resolveFinGrindProjectCacheKey
if defined FINGRIND_PROJECT_CACHE_KEY exit /b 0
set "FINGRIND_GRADLE_HASH_SHELL="
where pwsh.exe >NUL 2>&1
if "%ERRORLEVEL%"=="0" set "FINGRIND_GRADLE_HASH_SHELL=pwsh.exe"
if defined FINGRIND_GRADLE_HASH_SHELL goto hashShellResolved
where powershell.exe >NUL 2>&1
if "%ERRORLEVEL%"=="0" set "FINGRIND_GRADLE_HASH_SHELL=powershell.exe"
:hashShellResolved
if not defined FINGRIND_GRADLE_HASH_SHELL goto sanitizeProjectCacheKey
set "FINGRIND_GRADLE_HASH_INPUT=%APP_HOME%"
for /f "usebackq delims=" %%i in (`"%FINGRIND_GRADLE_HASH_SHELL%" -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "$bytes = [System.Text.Encoding]::UTF8.GetBytes($env:FINGRIND_GRADLE_HASH_INPUT); $sha = [System.Security.Cryptography.SHA256]::Create(); try { ($sha.ComputeHash($bytes) | ForEach-Object { $_.ToString('x2') }) -join '' } finally { $sha.Dispose() }"` ) do set "FINGRIND_PROJECT_CACHE_KEY=%%i"
if defined FINGRIND_PROJECT_CACHE_KEY exit /b 0
:sanitizeProjectCacheKey
set "FINGRIND_PROJECT_CACHE_KEY=%APP_HOME%"
set "FINGRIND_PROJECT_CACHE_KEY=%FINGRIND_PROJECT_CACHE_KEY:\=_%"
set "FINGRIND_PROJECT_CACHE_KEY=%FINGRIND_PROJECT_CACHE_KEY:/=_%"
set "FINGRIND_PROJECT_CACHE_KEY=%FINGRIND_PROJECT_CACHE_KEY::=_%"
set "FINGRIND_PROJECT_CACHE_KEY=%FINGRIND_PROJECT_CACHE_KEY: =_%"
exit /b 0

:resolveFinGrindBuildLogicDir
if defined FINGRIND_GRADLE_BUILD_LOGIC_DIR goto buildLogicDirResolved
if defined FINGRIND_GRADLE_PROJECT_CACHE_DIR (
    set "FINGRIND_GRADLE_BUILD_LOGIC_DIR=%FINGRIND_GRADLE_PROJECT_CACHE_DIR%\build-logic"
    goto buildLogicDirResolved
)
call :resolveFinGrindProjectCacheDir
set "FINGRIND_GRADLE_BUILD_LOGIC_DIR=%FINGRIND_GRADLE_PROJECT_CACHE_DIR%\build-logic"
:buildLogicDirResolved
exit /b 0

:resolveFinGrindJacocoRoot
if defined FINGRIND_GRADLE_JACOCO_ROOT goto jacocoRootResolved
if defined FINGRIND_GRADLE_PROJECT_CACHE_DIR (
    set "FINGRIND_GRADLE_JACOCO_ROOT=%FINGRIND_GRADLE_PROJECT_CACHE_DIR%\jacoco"
    goto jacocoRootResolved
)
call :resolveFinGrindProjectCacheDir
set "FINGRIND_GRADLE_JACOCO_ROOT=%FINGRIND_GRADLE_PROJECT_CACHE_DIR%\jacoco"
:jacocoRootResolved
exit /b 0

:resolveFinGrindProjectBuildExternalization
set "FINGRIND_SHOULD_EXTERNALIZE_PROJECT_BUILD_ROOT=false"
if defined FINGRIND_GRADLE_PROJECT_BUILD_ROOT set "FINGRIND_SHOULD_EXTERNALIZE_PROJECT_BUILD_ROOT=true"
if "%APP_HOME:~0,2%"=="\\" set "FINGRIND_SHOULD_EXTERNALIZE_PROJECT_BUILD_ROOT=true"
exit /b 0

:resolveFinGrindProjectBuildRoot
if defined FINGRIND_GRADLE_PROJECT_BUILD_ROOT goto projectBuildRootResolved
if defined FINGRIND_GRADLE_PROJECT_CACHE_DIR (
    set "FINGRIND_GRADLE_PROJECT_BUILD_ROOT=%FINGRIND_GRADLE_PROJECT_CACHE_DIR%\project-build"
    goto projectBuildRootResolved
)
call :resolveFinGrindProjectCacheDir
set "FINGRIND_GRADLE_PROJECT_BUILD_ROOT=%FINGRIND_GRADLE_PROJECT_CACHE_DIR%\project-build"
:projectBuildRootResolved
exit /b 0
