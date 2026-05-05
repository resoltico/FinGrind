$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

. (Join-Path $PSScriptRoot "bundle-smoke-common.ps1")
. (Join-Path $PSScriptRoot "bundle-smoke-acceptance.ps1")
. (Join-Path $PSScriptRoot "bundle-smoke-office-worker.ps1")
