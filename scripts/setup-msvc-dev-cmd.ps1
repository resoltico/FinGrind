param(
    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$Arch = "x64",

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$HostArch = "x64"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$supportPath = Join-Path $PSScriptRoot "setup-msvc-dev-cmd-support.ps1"
if (-not (Test-Path -LiteralPath $supportPath -PathType Leaf)) {
    throw "missing MSVC setup support script at $supportPath"
}
. $supportPath

if (-not $IsWindows) {
    Assert-FinGrindMsvcSetupFailure "setup-msvc-dev-cmd.ps1 can only run on Windows runners"
}

$vsDevCmdPath = Resolve-FinGrindVsDevCmdPath
$commandLine = Get-FinGrindVsDevCmdCommandLine -VsDevCmdPath $vsDevCmdPath -Arch $Arch -HostArch $HostArch
$environmentDump = Invoke-FinGrindVsDevCmdEnvironmentDump `
    -CommandLine $commandLine `
    -Arch $Arch `
    -HostArch $HostArch
$environmentText = ConvertTo-FinGrindGitHubEnvironmentText -EnvironmentDump $environmentDump
Export-FinGrindGitHubEnvironmentText -EnvironmentText $environmentText
Write-Information -MessageData "Configured MSVC developer command environment from $vsDevCmdPath" -InformationAction Continue
