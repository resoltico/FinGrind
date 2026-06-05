$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

. (Join-Path $PSScriptRoot "gradle-wrapper-support.ps1")
. (Join-Path $PSScriptRoot "source-checkout-cli-common.ps1")

$context = Get-FinGrindCliWrapperContext -ScriptRoot $PSScriptRoot

$runtimeManifest =
    Invoke-FinGrindEnsureCliWrapperRuntime `
        -Context $context `
        -ArtifactLabel "source-checkout wrapper JAR" `
        -GradleTasks @(":cli:writeSourceCheckoutRuntimeManifest", "prepareManagedSqlite") `
        -RuntimeManifestMissingMessage "missing source-checkout runtime manifest at $($context.SourceCheckoutRuntimeManifest); run .\\gradlew.bat :cli:writeSourceCheckoutRuntimeManifest" `
        -RuntimeManifestStaleMessage "source-checkout runtime manifest at $($context.SourceCheckoutRuntimeManifest) is not synchronized with the current checkout; rerun .\\gradlew.bat :cli:writeSourceCheckoutRuntimeManifest"

Invoke-FinGrindCliWrapper `
    -Context $context `
    -RuntimeManifest $runtimeManifest `
    -RuntimeDistribution "source-checkout-gradle" `
    -Arguments $args

exit $script:FinGrindCliWrapperExitCode
