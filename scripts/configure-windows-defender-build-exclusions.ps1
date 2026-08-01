param(
    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$WorkspacePath = $env:GITHUB_WORKSPACE,

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$GradleUserHome = $(if ([string]::IsNullOrWhiteSpace($env:USERPROFILE)) {
            ".gradle"
        } else {
            Join-Path $env:USERPROFILE ".gradle"
        })
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Fail {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    throw $Message
}

function Get-WindowsDefenderAddPreferenceCommand {
    return Get-Command Add-MpPreference -ErrorAction SilentlyContinue
}

function Format-HResult {
    param(
        [Parameter()]
        [System.Exception]$Exception
    )

    $current = $Exception
    while ($null -ne $current) {
        if ($null -ne $current.HResult) {
            return ("0x{0:x8}" -f ($current.HResult -band 0xffffffff))
        }
        $current = $current.InnerException
    }

    return $null
}

function Test-NonFatalWindowsDefenderExclusionFailure {
    param(
        [Parameter(Mandatory = $true)]
        [System.Management.Automation.ErrorRecord]$ErrorRecord
    )

    $hresult = Format-HResult -Exception $ErrorRecord.Exception
    if ($hresult -eq "0x800106ba") {
        return $true
    }

    $message = @(
        $ErrorRecord.FullyQualifiedErrorId
        $ErrorRecord.Exception.Message
        $ErrorRecord.ToString()
    ) -join "`n"
    return $message.Contains("0x800106ba")
}

function Write-WindowsDefenderUnavailableWarning {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [System.Management.Automation.ErrorRecord]$ErrorRecord
    )

    $message = $ErrorRecord.Exception.Message.Trim()
    Write-Information -MessageData "::warning::Windows Defender exclusions are unavailable; skipping exclusion for ${Path}. ${message}" -InformationAction Continue
}

function Add-WindowsDefenderExclusionPath {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateNotNullOrEmpty()]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [scriptblock]$AddPreferenceInvoker
    )

    try {
        & $AddPreferenceInvoker $Path
        Write-Information -MessageData "Configured Windows Defender exclusion for ${Path}" -InformationAction Continue
    } catch {
        if (Test-NonFatalWindowsDefenderExclusionFailure -ErrorRecord $_) {
            Write-WindowsDefenderUnavailableWarning -Path $Path -ErrorRecord $_
            return
        }
        throw
    }
}

function Invoke-FinGrindWindowsDefenderBuildExclusionConfiguration {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateNotNullOrEmpty()]
        [string]$WorkspacePath,

        [Parameter(Mandatory = $true)]
        [ValidateNotNullOrEmpty()]
        [string]$GradleUserHome,

        [Parameter()]
        [object]$AddPreferenceCommand = $null,

        [Parameter()]
        [scriptblock]$AddPreferenceInvoker = $null
    )

    if ($null -eq $AddPreferenceCommand) {
        $AddPreferenceCommand = Get-WindowsDefenderAddPreferenceCommand
    }

    if ($null -eq $AddPreferenceCommand) {
        Write-Information -MessageData "::warning::Windows Defender Add-MpPreference is unavailable; skipping build-directory exclusions." -InformationAction Continue
        return
    }

    if ($null -eq $AddPreferenceInvoker) {
        $AddPreferenceInvoker = {
            param([string]$CandidatePath)
            Add-MpPreference -ExclusionPath $CandidatePath -ErrorAction Stop
        }
    }

    Add-WindowsDefenderExclusionPath -Path $WorkspacePath -AddPreferenceInvoker $AddPreferenceInvoker
    Add-WindowsDefenderExclusionPath -Path $GradleUserHome -AddPreferenceInvoker $AddPreferenceInvoker
}

if ($MyInvocation.InvocationName -ne '.') {
    Invoke-FinGrindWindowsDefenderBuildExclusionConfiguration `
        -WorkspacePath $WorkspacePath `
        -GradleUserHome $GradleUserHome
}
