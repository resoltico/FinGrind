@echo off
setlocal enableextensions

set "POWERSHELL_LAUNCHER=%~dp0fingrind.ps1"

if not exist "%POWERSHELL_LAUNCHER%" (
  >&2 echo error: missing FinGrind PowerShell launcher at %POWERSHELL_LAUNCHER%
  exit /b 1
)

pwsh -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%POWERSHELL_LAUNCHER%" %*
