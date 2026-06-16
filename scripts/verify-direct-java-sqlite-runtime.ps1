$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$verifier = Join-Path $PSScriptRoot "verify-sqlite-runtime-contract.py"
$directJavaWrapper = Join-Path $PSScriptRoot "direct-java-cli.ps1"
$commonSupport = Join-Path $PSScriptRoot "sqlite-runtime-verifier-common.ps1"

if (-not (Test-Path -LiteralPath $commonSupport -PathType Leaf)) {
    throw "missing SQLite runtime PowerShell verifier support at $commonSupport"
}
. $commonSupport
$pythonCommand = Get-PythonCommandPath

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

$verifierResult = Invoke-PythonVerifier `
    -PythonCommand $pythonCommand `
    -Verifier $verifier `
    -EnvironmentOutput $environmentOutput `
    -ExpectedRuntimeDistributionKey directJavaRuntimeDistribution `
    -ExpectedRuntimeProvenance source-checkout-managed `
    -Label direct-java-runtime
if ($verifierResult.ExitCode -ne 0) {
    Write-Host $environmentOutput
    [Console]::Error.WriteLine($verifierResult.Output.TrimEnd())
    exit 1
}

Write-Host ($verifierResult.Output.TrimEnd())
