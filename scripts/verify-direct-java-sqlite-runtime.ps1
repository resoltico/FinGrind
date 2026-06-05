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

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$verifier = Join-Path $PSScriptRoot "verify-sqlite-runtime-contract.py"
$directJavaWrapper = Join-Path $PSScriptRoot "direct-java-cli.ps1"
$pythonCommand = Get-PythonCommand

if (-not (Test-Path -LiteralPath $verifier -PathType Leaf)) {
    Fail "missing SQLite runtime verifier at $verifier"
}
if (-not (Test-Path -LiteralPath $directJavaWrapper -PathType Leaf)) {
    Fail "missing direct Java wrapper at $directJavaWrapper"
}

Push-Location $repoRoot
try {
    $environmentOutput = (& $directJavaWrapper environment --output json | Out-String)
    if ($LASTEXITCODE -ne 0) {
        Fail "direct Java runtime environment probe failed"
    }
} finally {
    Pop-Location
}

$verifierOutput = ($environmentOutput |
        & $pythonCommand $verifier `
            --expected-runtime-distribution-key directJavaRuntimeDistribution `
            --expected-runtime-provenance source-checkout-managed `
            --label direct-java-runtime 2>&1 |
        Out-String)
if ($LASTEXITCODE -ne 0) {
    Write-Host $environmentOutput
    [Console]::Error.WriteLine($verifierOutput.TrimEnd())
    exit 1
}

Write-Host ($verifierOutput.TrimEnd())
