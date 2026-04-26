$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

. (Join-Path $PSScriptRoot "bundle-smoke-support.ps1")

Invoke-BundleSmoke -Arguments $args
exit 0
