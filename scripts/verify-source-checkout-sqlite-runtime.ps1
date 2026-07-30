$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$verifier = Join-Path $PSScriptRoot "verify-sqlite-runtime-contract.py"
$launcherWrapper = Join-Path $PSScriptRoot "source-checkout-cli.ps1"
$commonSupport = Join-Path $PSScriptRoot "sqlite-runtime-verifier-common.ps1"

if (-not (Test-Path -LiteralPath $commonSupport -PathType Leaf)) {
    throw "missing SQLite runtime PowerShell verifier support at $commonSupport"
}
. $commonSupport
$pythonCommand = Get-PythonCommandPath

if (-not (Test-Path -LiteralPath $verifier -PathType Leaf)) {
    Fail "missing SQLite runtime verifier at $verifier"
}
if (-not (Test-Path -LiteralPath $launcherWrapper -PathType Leaf)) {
    Fail "missing source-checkout launcher wrapper at $launcherWrapper"
}

Push-Location $repoRoot
try {
    $environmentOutput = (& $launcherWrapper environment --output json | Out-String)
    if ($LASTEXITCODE -ne 0) {
        Fail "source-checkout launcher environment probe failed"
    }
} finally {
    Pop-Location
}

$verifierResult = Invoke-PythonVerifier `
    -PythonCommand $pythonCommand `
    -Verifier $verifier `
    -EnvironmentOutput $environmentOutput `
    -ExpectedRuntimeDistributionKey sourceCheckoutRuntimeDistribution `
    -ExpectedRuntimeProvenance source-checkout-managed `
    -Label source-checkout-managed-runtime
if ($verifierResult.ExitCode -ne 0) {
    Write-Information -MessageData $environmentOutput -InformationAction Continue
    [Console]::Error.WriteLine($verifierResult.Output.TrimEnd())
    exit 1
}

Write-Information -MessageData ($verifierResult.Output.TrimEnd()) -InformationAction Continue
