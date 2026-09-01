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
@rem Modified by FinGrind contributors in 2026 to delegate Windows wrapper
@rem execution to the repository-owned PowerShell launcher.
@rem

@if "%DEBUG%"=="" @echo off
setlocal EnableExtensions DisableDelayedExpansion

set "APP_HOME=%~dp0"
if not defined APP_HOME set "APP_HOME=."
for %%I in ("%APP_HOME%") do set "APP_HOME=%%~fI"

set "PWSH_EXE=%FINGRIND_PWSH_EXECUTABLE%"
if defined PWSH_EXE if not exist "%PWSH_EXE%" (
    echo ERROR: FINGRIND_PWSH_EXECUTABLE does not name an existing PowerShell executable: %PWSH_EXE% 1>&2
    set "EXIT_CODE=1"
    goto complete
)
if not defined PWSH_EXE for /f "usebackq delims=" %%I in (`where.exe pwsh.exe 2^>NUL`) do if not defined PWSH_EXE set "PWSH_EXE=%%~fI"
if not defined PWSH_EXE (
    echo ERROR: FinGrind's Windows Gradle wrapper requires PowerShell 7 or later as pwsh.exe on PATH. 1>&2
    set "EXIT_CODE=1"
    goto complete
)

"%PWSH_EXE%" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%APP_HOME%\scripts\gradlew.ps1" %*
set "EXIT_CODE=%ERRORLEVEL%"

:complete
if "%GRADLE_EXIT_CONSOLE%"=="" goto return
endlocal & exit %EXIT_CODE%

:return
endlocal & exit /b %EXIT_CODE%
