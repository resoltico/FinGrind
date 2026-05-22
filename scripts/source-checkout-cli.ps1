$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

. (Join-Path $PSScriptRoot "gradle-wrapper-support.ps1")

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$cliBuildDir = Get-FinGrindProjectBuildDir -RepositoryRoot $repoRoot -ProjectSegment "cli"
$rootBuildDir = Get-FinGrindProjectBuildDir -RepositoryRoot $repoRoot -ProjectSegment "root"
$rawJar = Join-Path $cliBuildDir "libs/fingrind.jar"
$sourceCheckoutArtifactManifest =
    Get-FinGrindSourceCheckoutArtifactManifestPath -RepositoryRoot $repoRoot -ProjectSegment "cli"
$applicationModule = "fingrind/dev.erst.fingrind.cli.App"

Invoke-FinGrindEnsureSourceCheckoutArtifact `
    -RepositoryRoot $repoRoot `
    -ManifestPath $sourceCheckoutArtifactManifest `
    -ArtifactPath $rawJar `
    -ArtifactLabel "source-checkout launcher JAR" `
    -GradleTasks @(":cli:writeSourceCheckoutArtifactManifest", "prepareManagedSqlite")

& java `
    --enable-native-access=fingrind `
    "-Dfingrind.runtime.distribution=source-checkout-gradle" `
    "-Dfingrind.source-checkout.root=$repoRoot" `
    "-Dfingrind.source-checkout.build-root=$rootBuildDir" `
    --module-path $rawJar `
    --module $applicationModule @args
exit $LASTEXITCODE
