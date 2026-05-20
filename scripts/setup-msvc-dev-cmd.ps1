$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

param(
    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$Arch = "x64",

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$HostArch = "x64"
)

function Fail {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    throw $Message
}

function Get-VsWherePath {
    $programFilesX86 = ${env:ProgramFiles(x86)}
    if ([string]::IsNullOrWhiteSpace($programFilesX86)) {
        return $null
    }

    $candidate = Join-Path $programFilesX86 "Microsoft Visual Studio\Installer\vswhere.exe"
    if (Test-Path -LiteralPath $candidate -PathType Leaf) {
        return $candidate
    }

    return $null
}

function Get-FallbackVsDevCmdPath {
    $programFiles = $env:ProgramFiles
    if ([string]::IsNullOrWhiteSpace($programFiles)) {
        return $null
    }

    foreach ($edition in @("Enterprise", "Professional", "Community", "BuildTools")) {
        $candidate = Join-Path $programFiles "Microsoft Visual Studio\2022\$edition\Common7\Tools\VsDevCmd.bat"
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return $candidate
        }
    }

    return $null
}

function Resolve-VsDevCmdPath {
    $vsWherePath = Get-VsWherePath
    if ($null -ne $vsWherePath) {
        $installationPath = & $vsWherePath `
            -latest `
            -products * `
            -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 `
            -property installationPath
        if ($LASTEXITCODE -ne 0) {
            Fail "vswhere failed while locating a Visual Studio installation with MSVC tools"
        }
        if (-not [string]::IsNullOrWhiteSpace($installationPath)) {
            $resolved = Join-Path $installationPath "Common7\Tools\VsDevCmd.bat"
            if (Test-Path -LiteralPath $resolved -PathType Leaf) {
                return [System.IO.Path]::GetFullPath($resolved)
            }
        }
    }

    $fallback = Get-FallbackVsDevCmdPath
    if ($null -ne $fallback) {
        return [System.IO.Path]::GetFullPath($fallback)
    }

    Fail "unable to locate VsDevCmd.bat via vswhere or standard Visual Studio 2022 installation paths"
}

function Export-GitHubEnvironment {
    param(
        [Parameter(Mandatory = $true)]
        [System.Collections.Specialized.OrderedDictionary]$EnvironmentMap
    )

    if ([string]::IsNullOrWhiteSpace($env:GITHUB_ENV)) {
        Fail "missing GITHUB_ENV; this script must run inside a GitHub Actions step"
    }

    foreach ($entry in $EnvironmentMap.GetEnumerator()) {
        Add-Content -Path $env:GITHUB_ENV -Value ("{0}<<__FINGRIND_ENV__" -f $entry.Key)
        Add-Content -Path $env:GITHUB_ENV -Value ([string]$entry.Value)
        Add-Content -Path $env:GITHUB_ENV -Value "__FINGRIND_ENV__"
    }
}

if (-not $IsWindows) {
    Fail "setup-msvc-dev-cmd.ps1 can only run on Windows runners"
}

$vsDevCmdPath = Resolve-VsDevCmdPath
$commandLine = "`"$vsDevCmdPath`" -arch=$Arch -host_arch=$HostArch >nul && set"
$environmentDump = & cmd /d /s /c $commandLine
if ($LASTEXITCODE -ne 0) {
    Fail "VsDevCmd.bat failed for arch=$Arch host_arch=$HostArch"
}

$environmentMap = [System.Collections.Specialized.OrderedDictionary]::new([System.StringComparer]::OrdinalIgnoreCase)
foreach ($line in $environmentDump) {
    if ($line -match '^(?<Name>[^=]+)=(?<Value>.*)$') {
        $environmentMap[$matches.Name] = $matches.Value
    }
}

if (-not $environmentMap.Contains("VSCMD_VER")) {
    Fail "VsDevCmd.bat did not publish VSCMD_VER; refusing to continue with a partial environment"
}

Export-GitHubEnvironment -EnvironmentMap $environmentMap
Write-Host "Configured MSVC developer command environment from $vsDevCmdPath"
