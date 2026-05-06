$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

. (Join-Path $PSScriptRoot "gradle-wrapper-support.ps1")

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$cliBuildDir = Get-FinGrindProjectBuildDir -RepositoryRoot $repoRoot -ProjectSegment "cli"
$rootBuildDir = Get-FinGrindProjectBuildDir -RepositoryRoot $repoRoot -ProjectSegment "root"
$rawJar = Join-Path $cliBuildDir "libs/fingrind.jar"

if (-not (Test-Path -LiteralPath $rawJar -PathType Leaf)) {
    throw "missing developer raw JAR at $rawJar; run .\\gradlew.bat :cli:shadowJar prepareManagedSqlite"
}

& java `
    "-Dfingrind.source-checkout.root=$repoRoot" `
    "-Dfingrind.source-checkout.build-root=$rootBuildDir" `
    -jar $rawJar @args
exit $LASTEXITCODE
