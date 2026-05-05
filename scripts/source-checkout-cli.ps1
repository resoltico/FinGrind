$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

. (Join-Path $PSScriptRoot "gradle-wrapper-support.ps1")

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$cliBuildDir = Get-FinGrindProjectBuildDir -RepositoryRoot $repoRoot -ProjectSegment "cli"
$launcher = Join-Path $cliBuildDir "install/cli-shadow/bin/cli.bat"

if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) {
    throw "missing generated source-checkout launcher at $launcher; run .\\gradlew.bat :cli:installShadowDist prepareManagedSqlite"
}

& $launcher @args
exit $LASTEXITCODE
