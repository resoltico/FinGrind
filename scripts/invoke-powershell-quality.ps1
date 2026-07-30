[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$ProductionScriptPathsJson,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$PesterTestPathsJson,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$PesterManifest,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$PesterVersion,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$ScriptAnalyzerManifest,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$ScriptAnalyzerVersion
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function ConvertFrom-FinGrindPowerShellQualityPathCollection {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Json,

        [Parameter(Mandatory = $true)]
        [string]$Label
    )

    try {
        $decoded = $Json | ConvertFrom-Json -NoEnumerate -ErrorAction Stop
    } catch {
        throw "$Label is not valid JSON: $($_.Exception.Message)"
    }
    if ($decoded -is [string] -or $null -eq $decoded) {
        throw "$Label must be a nonempty JSON array of paths"
    }
    $paths = [System.Collections.Generic.List[string]]::new()
    $seen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($candidate in @($decoded)) {
        if ($candidate -isnot [string] -or [string]::IsNullOrWhiteSpace($candidate)) {
            throw "$Label contains a blank or non-string path"
        }
        $resolved = [System.IO.Path]::GetFullPath($candidate)
        if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
            throw "$Label contains a missing file: $resolved"
        }
        if (-not $seen.Add($resolved)) {
            throw "$Label contains a duplicate path: $resolved"
        }
        $paths.Add($resolved)
    }
    if ($paths.Count -eq 0) {
        throw "$Label is empty"
    }
    return $paths.ToArray()
}

function Import-FinGrindExactPowerShellQualityModule {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Manifest,

        [Parameter(Mandatory = $true)]
        [string]$ModuleName,

        [Parameter(Mandatory = $true)]
        [string]$ExpectedVersion
    )

    $resolvedManifest = [System.IO.Path]::GetFullPath($Manifest)
    if (-not (Test-Path -LiteralPath $resolvedManifest -PathType Leaf)) {
        throw "pinned $ModuleName module manifest does not exist: $resolvedManifest"
    }
    if ([System.IO.Path]::GetExtension($resolvedManifest) -ne ".psd1") {
        throw "pinned $ModuleName module manifest must be a .psd1 file: $resolvedManifest"
    }
    $expectedModuleBase = [System.IO.Path]::GetDirectoryName($resolvedManifest)
    Remove-Module -Name $ModuleName -Force -ErrorAction SilentlyContinue
    Import-Module -Name $resolvedManifest -Force -ErrorAction Stop
    $modules = @(
        Get-Module -Name $ModuleName | Where-Object {
            [System.IO.Path]::GetFullPath($_.ModuleBase) -eq $expectedModuleBase
        }
    )
    if ($modules.Count -ne 1) {
        throw "pinned $ModuleName module import did not resolve exactly its provisioned module directory"
    }
    if ($modules[0].Version.ToString() -ne $ExpectedVersion) {
        throw "pinned $ModuleName module version mismatch: expected $ExpectedVersion, observed $($modules[0].Version)"
    }
}

function Get-FinGrindPesterResultCount {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Result,

        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $property = $Result.PSObject.Properties[$Name]
    if ($null -eq $property -or $null -eq $property.Value) {
        return 0
    }
    return [int]$property.Value
}

$productionScriptPaths = ConvertFrom-FinGrindPowerShellQualityPathCollection `
    -Json $ProductionScriptPathsJson `
    -Label "PowerShell production-script inventory"
$pesterTestPaths = ConvertFrom-FinGrindPowerShellQualityPathCollection `
    -Json $PesterTestPathsJson `
    -Label "PowerShell Pester-test inventory"

foreach ($pesterTestPath in $pesterTestPaths) {
    if (-not $pesterTestPath.EndsWith(".Tests.ps1", [System.StringComparison]::Ordinal)) {
        throw "PowerShell Pester-test inventory contains a non-test file: $pesterTestPath"
    }
}

$PSModuleAutoloadingPreference = "None"
Import-FinGrindExactPowerShellQualityModule `
    -Manifest $PesterManifest `
    -ModuleName "Pester" `
    -ExpectedVersion $PesterVersion
Import-FinGrindExactPowerShellQualityModule `
    -Manifest $ScriptAnalyzerManifest `
    -ModuleName "PSScriptAnalyzer" `
    -ExpectedVersion $ScriptAnalyzerVersion

$analyzerRules = @(
    "PSAvoidUsingBrokenHashAlgorithms",
    "PSAvoidUsingConvertToSecureStringWithPlainText",
    "PSAvoidUsingEmptyCatchBlock",
    "PSAvoidUsingInvokeExpression",
    "PSAvoidUsingPlainTextForPassword",
    "PSAvoidUsingUsernameAndPasswordParams",
    "PSAvoidUsingWMICmdlet",
    "PSAvoidUsingWriteHost",
    "PSUseApprovedVerbs",
    "PSUseBOMForUnicodeEncodedFile",
    "PSUseCmdletCorrectly",
    "PSUseShouldProcessForStateChangingFunctions",
    "PSUseSingularNouns"
)
$analyzerFindings = @(
    foreach ($scriptPath in $productionScriptPaths) {
        Invoke-ScriptAnalyzer `
            -Path $scriptPath `
            -IncludeRule $analyzerRules `
            -Severity Error, Warning `
            -ErrorAction Stop
    }
)
if ($analyzerFindings.Count -gt 0) {
    $renderedFindings = $analyzerFindings |
        Sort-Object ScriptName, Line, Column, RuleName |
        ForEach-Object {
            "$($_.ScriptName):$($_.Line):$($_.Column):$($_.RuleName):$($_.Message)"
        }
    throw "PSScriptAnalyzer found $($analyzerFindings.Count) quality finding(s):`n$($renderedFindings -join [System.Environment]::NewLine)"
}

$pesterConfiguration = New-PesterConfiguration
$pesterConfiguration.Run.Path = $pesterTestPaths
$pesterConfiguration.Run.PassThru = $true
$pesterConfiguration.Output.Verbosity = "Detailed"
$pesterResult = Invoke-Pester -Configuration $pesterConfiguration
$failedCount = Get-FinGrindPesterResultCount -Result $pesterResult -Name "FailedCount"
$skippedCount = Get-FinGrindPesterResultCount -Result $pesterResult -Name "SkippedCount"
$notRunCount = Get-FinGrindPesterResultCount -Result $pesterResult -Name "NotRunCount"
$inconclusiveCount = Get-FinGrindPesterResultCount -Result $pesterResult -Name "InconclusiveCount"
$errorCount = Get-FinGrindPesterResultCount -Result $pesterResult -Name "ErrorCount"
$totalCount = Get-FinGrindPesterResultCount -Result $pesterResult -Name "TotalCount"
if ($totalCount -eq 0) {
    throw "Pester discovered no tests from the owned nonempty test inventory"
}
if (
    $failedCount -ne 0 -or
    $skippedCount -ne 0 -or
    $notRunCount -ne 0 -or
    $inconclusiveCount -ne 0 -or
    $errorCount -ne 0
) {
    throw (
        "Pester quality tests must have no failures, errors, skips, not-run, or inconclusive cases: " +
        "failed=$failedCount errors=$errorCount skipped=$skippedCount " +
        "notRun=$notRunCount inconclusive=$inconclusiveCount"
    )
}

Write-Output (
    "PowerShell quality: PSScriptAnalyzer $ScriptAnalyzerVersion analyzed " +
    "$($productionScriptPaths.Count) production script(s); Pester $PesterVersion passed $totalCount test(s)."
)
