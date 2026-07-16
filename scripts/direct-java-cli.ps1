$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

. (Join-Path $PSScriptRoot "gradle-wrapper-support.ps1")
. (Join-Path $PSScriptRoot "source-checkout-cli-common.ps1")

$context = Get-FinGrindCliWrapperContext -ScriptRoot $PSScriptRoot
$gradlePrepareCommand =
    if (Get-FinGrindIsWindowsHost) {
        ".\\gradlew.bat :cli:prepareSourceCheckoutCliRuntime"
    } else {
        "./gradlew :cli:prepareSourceCheckoutCliRuntime"
    }

$runtimeManifest =
    Invoke-FinGrindEnsureCliWrapperRuntime `
        -Context $context `
        -ArtifactLabel "developer raw JAR" `
        -GradleTasks @(":cli:prepareSourceCheckoutCliRuntime") `
        -RuntimeManifestMissingMessage "missing source-checkout runtime manifest at $($context.SourceCheckoutRuntimeManifest); run $gradlePrepareCommand" `
        -RuntimeManifestStaleMessage "source-checkout runtime manifest at $($context.SourceCheckoutRuntimeManifest) is not synchronized with the prepared runtime; rerun $gradlePrepareCommand"

Invoke-FinGrindCliWrapper `
    -Context $context `
    -RuntimeManifest $runtimeManifest `
    -RuntimeDistribution "direct-java-invocation" `
    -InvocationLabel $MyInvocation.InvocationName `
    -Arguments $args

exit $script:FinGrindCliWrapperExitCode
