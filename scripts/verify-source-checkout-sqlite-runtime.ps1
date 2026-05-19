$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Fail {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    throw $Message
}

function Get-PythonCommand {
    foreach ($candidate in @("python3", "python")) {
        if ($null -ne (Get-Command $candidate -ErrorAction SilentlyContinue)) {
            return $candidate
        }
    }

    Fail "missing Python interpreter; expected python3 or python on PATH"
}

function Get-GradleWrapperCommand {
    if ($IsWindows) {
        return (Join-Path $repoRoot "gradlew.bat")
    }

    return (Join-Path $repoRoot "gradlew")
}

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$verifier = Join-Path $PSScriptRoot "verify-sqlite-runtime-contract.py"
$launcherWrapper = Join-Path $PSScriptRoot "source-checkout-cli.ps1"
$pythonCommand = Get-PythonCommand
$gradleWrapper = Get-GradleWrapperCommand

if (-not (Test-Path -LiteralPath $verifier -PathType Leaf)) {
    Fail "missing SQLite runtime verifier at $verifier"
}
if (-not (Test-Path -LiteralPath $launcherWrapper -PathType Leaf)) {
    Fail "missing source-checkout launcher wrapper at $launcherWrapper"
}

Push-Location $repoRoot
try {
    & $gradleWrapper :cli:installShadowDist prepareManagedSqlite --no-daemon --console=plain | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Fail "Gradle failed while preparing the source-checkout managed runtime"
    }

    $environmentOutput = (& $launcherWrapper environment --output json | Out-String)
    if ($LASTEXITCODE -ne 0) {
        Fail "source-checkout launcher environment probe failed"
    }
} finally {
    Pop-Location
}

$verifierOutput = ($environmentOutput |
        & $pythonCommand $verifier `
            --expected-runtime-distribution-key sourceCheckoutRuntimeDistribution `
            --expected-runtime-provenance source-checkout-managed `
            --label source-checkout-managed-runtime 2>&1 |
        Out-String)
if ($LASTEXITCODE -ne 0) {
    Write-Host $environmentOutput
    [Console]::Error.WriteLine($verifierOutput.TrimEnd())
    exit 1
}

Write-Host ($verifierOutput.TrimEnd())
