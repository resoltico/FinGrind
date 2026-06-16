$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Fail {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    throw $Message
}

function Get-PythonCommandPath {
    foreach ($candidate in @(
            $env:FINGRIND_PYTHON_EXECUTABLE,
            $env:ORG_GRADLE_PROJECT_fingrindPythonExecutable
        )) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and
            (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            return [System.IO.Path]::GetFullPath($candidate)
        }
    }

    foreach ($candidate in @("python3", "python")) {
        $command = @(Get-Command $candidate -CommandType Application -ErrorAction SilentlyContinue) |
            Select-Object -First 1
        if ($null -ne $command) {
            return $command.Source
        }
    }

    Fail "missing Python interpreter; expected python3 or python on PATH"
}

function Invoke-PythonVerifier {
    param(
        [Parameter(Mandatory = $true)]
        [string]$PythonCommand,
        [Parameter(Mandatory = $true)]
        [string]$Verifier,
        [Parameter(Mandatory = $true)]
        [string]$EnvironmentOutput,
        [Parameter(Mandatory = $true)]
        [string]$ExpectedRuntimeDistributionKey,
        [Parameter(Mandatory = $true)]
        [string]$ExpectedRuntimeProvenance,
        [Parameter(Mandatory = $true)]
        [string]$Label
    )

    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    $processStartInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $processStartInfo.FileName = $PythonCommand
    $processStartInfo.UseShellExecute = $false
    $processStartInfo.RedirectStandardInput = $true
    $processStartInfo.RedirectStandardOutput = $true
    $processStartInfo.RedirectStandardError = $true
    $processStartInfo.StandardInputEncoding = $utf8NoBom
    $processStartInfo.StandardOutputEncoding = $utf8NoBom
    $processStartInfo.StandardErrorEncoding = $utf8NoBom
    $null = $processStartInfo.ArgumentList.Add($Verifier)
    $null = $processStartInfo.ArgumentList.Add("--expected-runtime-distribution-key")
    $null = $processStartInfo.ArgumentList.Add($ExpectedRuntimeDistributionKey)
    $null = $processStartInfo.ArgumentList.Add("--expected-runtime-provenance")
    $null = $processStartInfo.ArgumentList.Add($ExpectedRuntimeProvenance)
    $null = $processStartInfo.ArgumentList.Add("--label")
    $null = $processStartInfo.ArgumentList.Add($Label)

    $normalizedEnvironmentOutput = $EnvironmentOutput.TrimStart([char]0xFEFF)

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $processStartInfo
    $null = $process.Start()
    try {
        $process.StandardInput.Write($normalizedEnvironmentOutput)
    } finally {
        $process.StandardInput.Close()
    }

    $standardOutput = $process.StandardOutput.ReadToEnd()
    $standardError = $process.StandardError.ReadToEnd()
    $process.WaitForExit()

    [pscustomobject]@{
        ExitCode = $process.ExitCode
        Output = $standardOutput + $standardError
    }
}
