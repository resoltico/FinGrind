$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Fail([string] $Message) {
    throw $Message
}

function Get-FinGrindPowerShellExecutable {
    $configuredExecutableVariable = Get-Item -LiteralPath 'Env:FINGRIND_PWSH_EXECUTABLE' -ErrorAction SilentlyContinue
    if ($null -ne $configuredExecutableVariable) {
        $configuredExecutableValue = [string]$configuredExecutableVariable.Value
        if ([string]::IsNullOrWhiteSpace($configuredExecutableValue)) {
            Fail "FINGRIND_PWSH_EXECUTABLE must name one non-empty absolute PowerShell executable path"
        }
        if (-not [System.IO.Path]::IsPathFullyQualified($configuredExecutableValue)) {
            Fail "FINGRIND_PWSH_EXECUTABLE must be an absolute path"
        }
        $configuredExecutable = [System.IO.Path]::GetFullPath($configuredExecutableValue)
        if (-not (Test-Path -LiteralPath $configuredExecutable -PathType Leaf)) {
            Fail "FINGRIND_PWSH_EXECUTABLE does not name an existing PowerShell executable: $configuredExecutable"
        }
        return $configuredExecutable
    }

    $pathExecutable = (Get-Command pwsh -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty Source -First 1)
    if ([string]::IsNullOrWhiteSpace($pathExecutable)) {
        Fail "missing pwsh executable for bundle smoke"
    }
    return $pathExecutable
}

function Read-AsciiPrefix {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Path,
        [Parameter(Mandatory = $true)]
        [int] $Length
    )

    $stream = [System.IO.File]::OpenRead($Path)
    try {
        $buffer = [byte[]]::new($Length)
        $bytesRead = $stream.Read($buffer, 0, $Length)
        return [System.Text.Encoding]::ASCII.GetString($buffer, 0, $bytesRead)
    }
    finally {
        $stream.Dispose()
    }
}

function ProjectVersion {
    $versionLine = Get-Content -Path (Join-Path $script:RepoRoot "gradle.properties") |
        Where-Object { $_ -match '^version=' } |
        Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($versionLine)) {
        Fail "could not determine project version from gradle.properties"
    }
    return $versionLine.Split('=', 2)[1].Trim()
}

function Read-FinGrindContractValueSet {
    $reader = Join-Path $script:RepoRoot "scripts/read-contract-values.py"
    if (-not (Test-Path -LiteralPath $reader -PathType Leaf)) {
        Fail "missing contract-values reader at $reader"
    }
    $json = & python3 $reader
    if ($LASTEXITCODE -ne 0) {
        Fail "contract-values reader failed"
    }
    return $json | ConvertFrom-Json
}

function Test-SameSequence {
    param(
        [Parameter(Mandatory = $true)]
        [object[]] $Reference,
        [Parameter(Mandatory = $true)]
        [object[]] $Actual
    )

    if ($Reference.Count -ne $Actual.Count) {
        return $false
    }
    for ($index = 0; $index -lt $Reference.Count; $index++) {
        if (-not [object]::Equals($Reference[$index], $Actual[$index])) {
            return $false
        }
    }
    return $true
}

function Write-Utf8NoBomFile {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Path,
        [Parameter(Mandatory = $true, ValueFromPipeline = $true)]
        [AllowEmptyString()]
        [string] $Content
    )

    process {
        $encoding = New-Object -TypeName System.Text.UTF8Encoding -ArgumentList $false
        [System.IO.File]::WriteAllText($Path, $Content, $encoding)
    }
}

function Invoke-BundleCommand {
    param(
        [string[]] $Arguments,
        [switch] $AllowFailure
    )

    $originalJavaHome = $env:JAVA_HOME
    try {
        Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
        $outputLines = & $script:BundleLauncher @Arguments 2>&1 | ForEach-Object { $_.ToString() }
        $output = ($outputLines | Where-Object { -not [string]::IsNullOrEmpty($_) }) -join "`n"
        $exitCode = $LASTEXITCODE
    } finally {
        if ($null -ne $originalJavaHome) {
            $env:JAVA_HOME = $originalJavaHome
        } else {
            Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
        }
    }

    if (-not $AllowFailure -and $exitCode -ne 0) {
        Fail "bundle command failed with exit code $exitCode`n$output"
    }

    return [pscustomobject]@{
        Output   = $output -replace "`r", ""
        ExitCode = $exitCode
    }
}
