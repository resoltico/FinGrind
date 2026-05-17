$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

. (Join-Path $PSScriptRoot "gradle-wrapper-support.ps1")

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$cliBuildDir = Get-FinGrindProjectBuildDir -RepositoryRoot $repoRoot -ProjectSegment "cli"
$rootBuildDir = Get-FinGrindProjectBuildDir -RepositoryRoot $repoRoot -ProjectSegment "root"
$rawJar = Join-Path $cliBuildDir "libs/fingrind.jar"
$applicationModule = "fingrind/dev.erst.fingrind.cli.App"

if (-not (Test-Path -LiteralPath $rawJar -PathType Leaf)) {
    throw "missing source-checkout launcher JAR at $rawJar; run .\\gradlew.bat :cli:shadowJar prepareManagedSqlite"
}

& java `
    --enable-native-access=fingrind `
    "-Dfingrind.runtime.distribution=source-checkout-gradle" `
    "-Dfingrind.source-checkout.root=$repoRoot" `
    "-Dfingrind.source-checkout.build-root=$rootBuildDir" `
    --module-path $rawJar `
    --module $applicationModule @args
exit $LASTEXITCODE
