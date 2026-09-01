param(
    [Parameter(Mandatory = $true)]
    [string] $LauncherPath,
    [Parameter(Mandatory = $true)]
    [string] $RequestPath
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

. (Join-Path $PSScriptRoot "bundle-smoke-common.ps1")

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = $utf8NoBom
$OutputEncoding = $utf8NoBom

if (-not (Test-Path -LiteralPath $LauncherPath -PathType Leaf)) {
    throw "missing bundle launcher at $LauncherPath"
}
if (-not (Test-Path -LiteralPath $RequestPath -PathType Leaf)) {
    throw "missing bridge request file at $RequestPath"
}

$request = Get-Content -LiteralPath $RequestPath -Raw -Encoding UTF8 | ConvertFrom-Json
$argumentsFile = [string] $request.argumentsFile
if ([string]::IsNullOrWhiteSpace($argumentsFile)) {
    throw "bridge request must name one staged CLI arguments file"
}
if (-not (Test-Path -LiteralPath $argumentsFile -PathType Leaf)) {
    throw "bridge request staged CLI arguments file does not exist: $argumentsFile"
}
$stdinFile = $null
if ($null -ne $request.stdinFile) {
    $stdinFile = [string] $request.stdinFile
    if ([string]::IsNullOrWhiteSpace($stdinFile)) {
        throw "bridge request stdin file must be null or name one file"
    }
    if (-not (Test-Path -LiteralPath $stdinFile -PathType Leaf)) {
        throw "bridge request stdin file does not exist: $stdinFile"
    }
}
$internalCliArgumentsFileEnv = "FINGRIND_INTERNAL_CLI_ARGUMENTS_FILE"
$pwshExecutable = Get-FinGrindPowerShellExecutable

function Invoke-LauncherBridgeProcess {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $InvocationArguments,
        [Parameter(Mandatory = $true)]
        [string] $ArgumentsFile,
        [Parameter()]
        [AllowNull()]
        [string] $StdinFile
    )

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $pwshExecutable
    $startInfo.WorkingDirectory = [System.IO.Directory]::GetCurrentDirectory()
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = -not [string]::IsNullOrWhiteSpace($StdinFile)
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    if ($startInfo.RedirectStandardInput) {
        $startInfo.StandardInputEncoding = $utf8NoBom
    }
    $startInfo.StandardOutputEncoding = $utf8NoBom
    $startInfo.StandardErrorEncoding = $utf8NoBom
    $startInfo.Environment[$internalCliArgumentsFileEnv] = $ArgumentsFile
    foreach ($invocationArgument in $InvocationArguments) {
        [void] $startInfo.ArgumentList.Add([string] $invocationArgument)
    }

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) {
            throw "failed to start bundle bridge subprocess"
        }
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        if ($startInfo.RedirectStandardInput) {
            $inputStream = [System.IO.File]::OpenRead($StdinFile)
            try {
                $inputStream.CopyTo($process.StandardInput.BaseStream)
                $process.StandardInput.BaseStream.Flush()
            }
            finally {
                $inputStream.Dispose()
                $process.StandardInput.Close()
            }
        }
        $process.WaitForExit()
        [Console]::Out.Write($stdoutTask.GetAwaiter().GetResult())
        [Console]::Error.Write($stderrTask.GetAwaiter().GetResult())
        return $process.ExitCode
    }
    finally {
        $process.Dispose()
    }
}

$bridgeArguments = @("-NoLogo", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $LauncherPath)
exit (Invoke-LauncherBridgeProcess -InvocationArguments $bridgeArguments -ArgumentsFile $argumentsFile -StdinFile $stdinFile)
