$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

. (Join-Path $PSScriptRoot "gradle-wrapper-support.ps1")

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$cliBuildDir = Get-FinGrindProjectBuildDir -RepositoryRoot $repoRoot -ProjectSegment "cli"
$rawJar = Join-Path $cliBuildDir "libs/fingrind.jar"

if (-not (Test-Path -LiteralPath $rawJar -PathType Leaf)) {
    throw "missing developer raw JAR at $rawJar; run .\\gradlew.bat :cli:shadowJar prepareManagedSqlite"
}

& java -jar $rawJar @args
exit $LASTEXITCODE
