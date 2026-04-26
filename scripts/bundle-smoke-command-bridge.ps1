$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

param(
    [Parameter(Mandatory = $true)]
    [string] $LauncherPath,
    [Parameter(Mandatory = $true)]
    [string] $RequestPath
)

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

if ($null -ne $request.stdinText) {
    $request.stdinText | & $LauncherPath @arguments
} else {
    & $LauncherPath @arguments
}

exit $LASTEXITCODE
