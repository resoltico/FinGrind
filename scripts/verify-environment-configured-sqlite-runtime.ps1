$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

. (Join-Path $PSScriptRoot "gradle-wrapper-support.ps1")

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
$contractValuesReader = Join-Path $PSScriptRoot "read-contract-values.py"
$directJavaWrapper = Join-Path $PSScriptRoot "direct-java-cli.ps1"
$rootBuildDir = Get-FinGrindProjectBuildDir -RepositoryRoot $repoRoot -ProjectSegment "root"
$pythonCommand = Get-PythonCommand
$gradleWrapper = Get-GradleWrapperCommand

if (-not (Test-Path -LiteralPath $verifier -PathType Leaf)) {
    Fail "missing SQLite runtime verifier at $verifier"
}
if (-not (Test-Path -LiteralPath $contractValuesReader -PathType Leaf)) {
    Fail "missing contract-values reader at $contractValuesReader"
}
if (-not (Test-Path -LiteralPath $directJavaWrapper -PathType Leaf)) {
    Fail "missing direct Java wrapper at $directJavaWrapper"
}

$contractJson = & $pythonCommand $contractValuesReader
if ($LASTEXITCODE -ne 0) {
    Fail "failed to load canonical contract values"
}
$contract = $contractJson | ConvertFrom-Json -AsHashtable
$bundleTarget = $contract["bundleLayout"]["hostBundleTarget"]
$hostSqliteLibraryPath = Join-Path `
    (Join-Path (Join-Path $rootBuildDir "managed-sqlite") $bundleTarget["classifier"]) `
    $bundleTarget["sqliteLibraryFileName"]

Push-Location $repoRoot
try {
    & $gradleWrapper :cli:shadowJar prepareManagedSqlite --no-daemon --console=plain | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Fail "Gradle failed while preparing the managed SQLite runtime"
    }
} finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $hostSqliteLibraryPath -PathType Leaf)) {
    Fail "missing managed SQLite library for environment-configured runtime at $hostSqliteLibraryPath"
}

$hadSqliteLibrary = Test-Path Env:FINGRIND_SQLITE_LIBRARY
$previousSqliteLibrary = if ($hadSqliteLibrary) { $env:FINGRIND_SQLITE_LIBRARY } else { $null }
$hadJavaToolOptions = Test-Path Env:JAVA_TOOL_OPTIONS
$previousJavaToolOptions = if ($hadJavaToolOptions) { $env:JAVA_TOOL_OPTIONS } else { $null }

try {
    $env:FINGRIND_SQLITE_LIBRARY = $hostSqliteLibraryPath
    $env:JAVA_TOOL_OPTIONS = "-Dfingrind.sqlite.allowEnvironmentConfiguredRuntime=true"

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
                --expected-runtime-provenance environment-configured `
                --label environment-configured-runtime 2>&1 |
            Out-String)
    if ($LASTEXITCODE -ne 0) {
        Write-Host $environmentOutput
        [Console]::Error.WriteLine($verifierOutput.TrimEnd())
        exit 1
    }

    Write-Host ($verifierOutput.TrimEnd())
} finally {
    if ($hadSqliteLibrary) {
        $env:FINGRIND_SQLITE_LIBRARY = $previousSqliteLibrary
    } else {
        Remove-Item Env:FINGRIND_SQLITE_LIBRARY -ErrorAction SilentlyContinue
    }
    if ($hadJavaToolOptions) {
        $env:JAVA_TOOL_OPTIONS = $previousJavaToolOptions
    } else {
        Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
    }
}
