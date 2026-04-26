param(
    [Parameter(Mandatory = $true)]
    [string] $LauncherPath,
    [Parameter(Mandatory = $true)]
    [string] $RequestPath
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if (-not (Test-Path -LiteralPath $LauncherPath -PathType Leaf)) {
    throw "missing bundle launcher at $LauncherPath"
}
if (-not (Test-Path -LiteralPath $RequestPath -PathType Leaf)) {
    throw "missing bridge request file at $RequestPath"
}

$request = Get-Content -LiteralPath $RequestPath -Raw -Encoding UTF8 | ConvertFrom-Json
$arguments = @()
foreach ($argument in @($request.arguments)) {
    $arguments += [string] $argument
}
$stdinText = $null
if ($null -ne $request.stdinText) {
    $stdinText = [string] $request.stdinText
}

$launcherStartInfo = [System.Diagnostics.ProcessStartInfo]::new()
$launcherStartInfo.UseShellExecute = $false
$launcherStartInfo.WorkingDirectory = (Get-Location).Path
$launcherStartInfo.RedirectStandardInput = $null -ne $stdinText

if ([string]::Equals(
        [System.IO.Path]::GetExtension($LauncherPath),
        ".ps1",
        [System.StringComparison]::OrdinalIgnoreCase)) {
    $launcherHost =
        Join-Path $PSHOME $(if ($IsWindows) { "pwsh.exe" } else { "pwsh" })
    $launcherStartInfo.FileName = $launcherHost
    foreach ($prefixArgument in @(
            "-NoLogo",
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-File", $LauncherPath
        )) {
        [void] $launcherStartInfo.ArgumentList.Add([string] $prefixArgument)
    }
} else {
    $launcherStartInfo.FileName = $LauncherPath
}

foreach ($argument in $arguments) {
    [void] $launcherStartInfo.ArgumentList.Add([string] $argument)
}

$launcherProcess = [System.Diagnostics.Process]::new()
$launcherProcess.StartInfo = $launcherStartInfo

try {
    if (-not $launcherProcess.Start()) {
        throw "failed to start bridged launcher at $LauncherPath"
    }

    if ($launcherStartInfo.RedirectStandardInput) {
        $launcherProcess.StandardInput.Write($stdinText)
        $launcherProcess.StandardInput.Close()
    }

    $launcherProcess.WaitForExit()
    exit $launcherProcess.ExitCode
}
finally {
    $launcherProcess.Dispose()
}
