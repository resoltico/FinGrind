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
$stdinFile = $null
$priorReturnMode = $env:FINGRIND_BUNDLE_RETURN_EXIT_CODE
$priorStdinFile = $env:FINGRIND_BUNDLE_STDIN_FILE

try {
    if ($null -ne $request.stdinText) {
        $stdinFile = Join-Path ([System.IO.Path]::GetTempPath()) (
            "fingrind-bundle-stdin-" + [System.Guid]::NewGuid().ToString("N") + ".txt"
        )
        $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
        [System.IO.File]::WriteAllText($stdinFile, [string] $request.stdinText, $utf8NoBom)
        $env:FINGRIND_BUNDLE_STDIN_FILE = $stdinFile
    }
    $env:FINGRIND_BUNDLE_RETURN_EXIT_CODE = "true"

    $launcherResult = & $LauncherPath @arguments
    if (-not $?) {
        exit 1
    }
    exit ([int] $launcherResult)
}
finally {
    if ($null -ne $stdinFile -and (Test-Path -LiteralPath $stdinFile -PathType Leaf)) {
        Remove-Item -LiteralPath $stdinFile -Force -ErrorAction SilentlyContinue
    }
    if ($null -ne $priorStdinFile) {
        $env:FINGRIND_BUNDLE_STDIN_FILE = $priorStdinFile
    } else {
        Remove-Item Env:FINGRIND_BUNDLE_STDIN_FILE -ErrorAction SilentlyContinue
    }
    if ($null -ne $priorReturnMode) {
        $env:FINGRIND_BUNDLE_RETURN_EXIT_CODE = $priorReturnMode
    } else {
        Remove-Item Env:FINGRIND_BUNDLE_RETURN_EXIT_CODE -ErrorAction SilentlyContinue
    }
}
