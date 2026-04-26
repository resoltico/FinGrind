$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Fail([string] $Message) {
    throw $Message
}

function Require-Match {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Text,
        [Parameter(Mandatory = $true)]
        [string] $Pattern,
        [Parameter(Mandatory = $true)]
        [string] $Message
    )

    if (-not [System.Text.RegularExpressions.Regex]::IsMatch(
            $Text,
            $Pattern,
            [System.Text.RegularExpressions.RegexOptions]::Multiline)) {
        Fail $Message
    }
}

function Require-NoMatch {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Text,
        [Parameter(Mandatory = $true)]
        [string] $Pattern,
        [Parameter(Mandatory = $true)]
        [string] $Message
    )

    if ([System.Text.RegularExpressions.Regex]::IsMatch(
            $Text,
            $Pattern,
            [System.Text.RegularExpressions.RegexOptions]::Multiline)) {
        Fail $Message
    }
}

function Require-Java26 {
    param(
        [Parameter(Mandatory = $true)]
        [string] $JavaCommand
    )

    $versionOutput = (& $JavaCommand --version 2>&1 | Out-String) -replace "`r", ""
    $versionLines = @($versionOutput -split "`n" | Where-Object { $_ -ne "" })
    $versionTokens = @()
    if ($versionLines.Count -gt 0) {
        $versionTokens = @($versionLines[0] -split '\s+' | Where-Object { $_ -ne "" })
    }
    if ($versionTokens.Count -lt 2 -or ($versionTokens[1] -ne "26" -and -not $versionTokens[1].StartsWith("26."))) {
        Write-Host $versionOutput
        Fail "bundled Java runtime did not report Java 26"
    }
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

function Read-ContractValues {
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

    return @(
        Compare-Object -ReferenceObject $Reference -DifferenceObject $Actual
    ).Count -eq 0
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

    $originalSqliteLibrary = $env:FINGRIND_SQLITE_LIBRARY
    $originalJavaHome = $env:JAVA_HOME
    try {
        Remove-Item Env:FINGRIND_SQLITE_LIBRARY -ErrorAction SilentlyContinue
        Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
        $outputLines = & $script:BundleLauncher @Arguments 2>&1 | ForEach-Object { $_.ToString() }
        $output = ($outputLines | Where-Object { -not [string]::IsNullOrEmpty($_) }) -join "`n"
        $exitCode = $LASTEXITCODE
    } finally {
        if ($null -ne $originalSqliteLibrary) {
            $env:FINGRIND_SQLITE_LIBRARY = $originalSqliteLibrary
        } else {
            Remove-Item Env:FINGRIND_SQLITE_LIBRARY -ErrorAction SilentlyContinue
        }
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
