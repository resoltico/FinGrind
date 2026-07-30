$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$script:FinGrindMsvcSetupPolicyPath = Join-Path $PSScriptRoot "msvc_setup_policy.py"

function Assert-FinGrindMsvcSetupFailure {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    throw $Message
}

function Get-FinGrindMsvcSetupPolicyPython {
    foreach ($candidate in @(
            $env:FINGRIND_PYTHON_EXECUTABLE,
            $env:ORG_GRADLE_PROJECT_fingrindPythonExecutable
        )) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and
            (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            return [System.IO.Path]::GetFullPath($candidate)
        }
    }

    foreach ($commandName in @("python", "python3")) {
        $command = @(Get-Command $commandName -CommandType Application -ErrorAction SilentlyContinue) |
            Select-Object -First 1
        if ($null -ne $command) {
            return $command.Source
        }
    }

    Assert-FinGrindMsvcSetupFailure "missing Python interpreter; expected python or python3 on PATH"
}

function Get-FinGrindMsvcSetupPolicyPath {
    if (-not (Test-Path -LiteralPath $script:FinGrindMsvcSetupPolicyPath -PathType Leaf)) {
        Assert-FinGrindMsvcSetupFailure (
            "missing MSVC setup policy owner at $script:FinGrindMsvcSetupPolicyPath"
        )
    }

    return [System.IO.Path]::GetFullPath($script:FinGrindMsvcSetupPolicyPath)
}

function Invoke-FinGrindNativeProcess {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateNotNullOrEmpty()]
        [string]$ExecutablePath,

        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,

        [Parameter()]
        [System.Text.Encoding]$StandardOutputEncoding
    )

    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    if ($null -eq $StandardOutputEncoding) {
        $StandardOutputEncoding = $utf8NoBom
    }
    $processStartInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $processStartInfo.FileName = $ExecutablePath
    $processStartInfo.UseShellExecute = $false
    $processStartInfo.RedirectStandardOutput = $true
    $processStartInfo.RedirectStandardError = $true
    $processStartInfo.StandardOutputEncoding = $StandardOutputEncoding
    $processStartInfo.StandardErrorEncoding = $utf8NoBom
    foreach ($argument in $Arguments) {
        $null = $processStartInfo.ArgumentList.Add($argument)
    }

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $processStartInfo
    try {
        $null = $process.Start()
        $standardOutputTask = $process.StandardOutput.ReadToEndAsync()
        $standardErrorTask = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        $standardOutput = $standardOutputTask.GetAwaiter().GetResult()
        $standardError = $standardErrorTask.GetAwaiter().GetResult()
        return [pscustomobject]@{
            ExitCode = $process.ExitCode
            StandardOutput = $standardOutput
            StandardError = $standardError
        }
    } finally {
        $process.Dispose()
    }
}

function Invoke-FinGrindMsvcSetupPolicy {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateNotNullOrEmpty()]
        [string]$Operation,

        [Parameter(Mandatory = $true)]
        [System.Collections.IDictionary]$Payload
    )

    $python = Get-FinGrindMsvcSetupPolicyPython
    $policyPath = Get-FinGrindMsvcSetupPolicyPath
    $request = [ordered]@{
        operation = $Operation
        payload = $Payload
    }
    $requestJson = $request | ConvertTo-Json -Compress -Depth 20
    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    $processStartInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $processStartInfo.FileName = $python
    $processStartInfo.UseShellExecute = $false
    $processStartInfo.RedirectStandardInput = $true
    $processStartInfo.RedirectStandardOutput = $true
    $processStartInfo.RedirectStandardError = $true
    $processStartInfo.StandardInputEncoding = $utf8NoBom
    $processStartInfo.StandardOutputEncoding = $utf8NoBom
    $processStartInfo.StandardErrorEncoding = $utf8NoBom
    # The policy is trusted code; ambient Python state is excluded while its JSON wire remains UTF-8.
    $null = $processStartInfo.ArgumentList.Add("-B")
    $null = $processStartInfo.ArgumentList.Add("-I")
    $null = $processStartInfo.ArgumentList.Add("-X")
    $null = $processStartInfo.ArgumentList.Add("utf8")
    $null = $processStartInfo.ArgumentList.Add($policyPath)

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $processStartInfo
    try {
        $null = $process.Start()
        $standardOutputTask = $process.StandardOutput.ReadToEndAsync()
        $standardErrorTask = $process.StandardError.ReadToEndAsync()
        try {
            $process.StandardInput.Write($requestJson)
        } finally {
            $process.StandardInput.Close()
        }
        $process.WaitForExit()
        $standardOutput = $standardOutputTask.GetAwaiter().GetResult()
        $standardError = $standardErrorTask.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) {
            $failure = $standardError.Trim()
            if ([string]::IsNullOrWhiteSpace($failure)) {
                $failure = "MSVC setup policy failed without a diagnostic"
            }
            Assert-FinGrindMsvcSetupFailure $failure
        }

        $policyOutput = $standardOutput.TrimStart([char]0xFEFF)
        if ([string]::IsNullOrWhiteSpace($policyOutput)) {
            Assert-FinGrindMsvcSetupFailure "MSVC setup policy did not return a JSON response"
        }
        try {
            $response = $policyOutput | ConvertFrom-Json -AsHashtable -Depth 20
        } catch {
            Assert-FinGrindMsvcSetupFailure "MSVC setup policy returned invalid JSON"
        }
        if ($response -isnot [System.Collections.IDictionary]) {
            Assert-FinGrindMsvcSetupFailure "MSVC setup policy did not return a JSON object"
        }
        return $response
    } finally {
        $process.Dispose()
    }
}

function Get-FinGrindMsvcSetupPolicyValue {
    param(
        [Parameter(Mandatory = $true)]
        [System.Collections.IDictionary]$Response,

        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    if (-not $Response.Contains($Name)) {
        Assert-FinGrindMsvcSetupFailure "MSVC setup policy response omitted required field $Name"
    }
    return $Response[$Name]
}

function Get-FinGrindVsWherePath {
    $programFilesX86 = ${env:ProgramFiles(x86)}
    if ([string]::IsNullOrWhiteSpace($programFilesX86)) {
        return $null
    }

    $candidate = Join-Path $programFilesX86 "Microsoft Visual Studio\Installer\vswhere.exe"
    if (Test-Path -LiteralPath $candidate -PathType Leaf) {
        return [System.IO.Path]::GetFullPath($candidate)
    }

    return $null
}

function Invoke-FinGrindVsWhere {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateNotNullOrEmpty()]
        [string]$VsWherePath
    )

    $argumentResponse = Invoke-FinGrindMsvcSetupPolicy -Operation "vswhere-arguments" -Payload @{}
    $argumentsValue = Get-FinGrindMsvcSetupPolicyValue -Response $argumentResponse -Name "arguments"
    if ($argumentsValue -is [string] -or $argumentsValue -isnot [System.Collections.IEnumerable]) {
        Assert-FinGrindMsvcSetupFailure "MSVC setup policy returned invalid vswhere arguments"
    }
    $arguments = @($argumentsValue | ForEach-Object { [string]$_ })
    $result = Invoke-FinGrindNativeProcess -ExecutablePath $VsWherePath -Arguments $arguments
    $selectionResponse = Invoke-FinGrindMsvcSetupPolicy -Operation "select-vswhere-installation" -Payload @{
        exitCode = $result.ExitCode
        output = @($result.StandardOutput -split '\r?\n')
    }
    return Get-FinGrindMsvcSetupPolicyValue -Response $selectionResponse -Name "installationPath"
}

function Resolve-FinGrindVsDevCmdPath {
    $installationPath = $null
    $vsWherePath = Get-FinGrindVsWherePath
    if ($null -ne $vsWherePath) {
        $installationPath = Invoke-FinGrindVsWhere -VsWherePath $vsWherePath
    }

    $candidateResponse = Invoke-FinGrindMsvcSetupPolicy -Operation "vsdevcmd-candidates" -Payload @{
        installationPath = $installationPath
        programFiles = $env:ProgramFiles
    }
    $candidatePaths = Get-FinGrindMsvcSetupPolicyValue -Response $candidateResponse -Name "candidatePaths"
    $notFoundMessage = Get-FinGrindMsvcSetupPolicyValue -Response $candidateResponse -Name "notFoundMessage"
    if ($candidatePaths -is [string] -or $candidatePaths -isnot [System.Collections.IEnumerable]) {
        Assert-FinGrindMsvcSetupFailure "MSVC setup policy returned invalid VsDevCmd candidates"
    }
    foreach ($candidate in $candidatePaths) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return [System.IO.Path]::GetFullPath($candidate)
        }
    }

    Assert-FinGrindMsvcSetupFailure ([string]$notFoundMessage)
}

function Get-FinGrindVsDevCmdCommandLine {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$VsDevCmdPath,

        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Arch,

        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$HostArch
    )

    $response = Invoke-FinGrindMsvcSetupPolicy -Operation "command-line" -Payload @{
        vsdevcmdPath = $VsDevCmdPath
        arch = $Arch
        hostArch = $HostArch
    }
    return [string](Get-FinGrindMsvcSetupPolicyValue -Response $response -Name "commandLine")
}

function Invoke-FinGrindVsDevCmdEnvironmentDump {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateNotNullOrEmpty()]
        [string]$CommandLine,

        [Parameter(Mandatory = $true)]
        [string]$Arch,

        [Parameter(Mandatory = $true)]
        [string]$HostArch
    )

    # cmd /u makes the internal set command emit UTF-16 on the redirected output stream.
    $result = Invoke-FinGrindNativeProcess -ExecutablePath "cmd.exe" -Arguments @(
        "/d",
        "/u",
        "/v:off",
        "/s",
        "/c",
        $CommandLine
    ) -StandardOutputEncoding ([System.Text.Encoding]::Unicode)
    if ($result.ExitCode -ne 0) {
        Assert-FinGrindMsvcSetupFailure "VsDevCmd.bat failed for arch=$Arch host_arch=$HostArch"
    }
    # `cmd /c set` terminates its output with a newline.  Do not pass that
    # structural terminator to the environment policy as an empty variable.
    return @(
        $result.StandardOutput -split '\r?\n' |
            Where-Object { -not [string]::IsNullOrEmpty($_) }
    )
}

function ConvertTo-FinGrindGitHubEnvironmentText {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$EnvironmentDump
    )

    $response = Invoke-FinGrindMsvcSetupPolicy -Operation "github-environment" -Payload @{
        environmentDump = @($EnvironmentDump)
    }
    return [string](Get-FinGrindMsvcSetupPolicyValue -Response $response -Name "githubEnvironment")
}

function Export-FinGrindGitHubEnvironmentText {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$EnvironmentText,

        [Parameter()]
        [AllowEmptyString()]
        [string]$GitHubEnvironmentPath = $env:GITHUB_ENV
    )

    if ([string]::IsNullOrWhiteSpace($GitHubEnvironmentPath)) {
        Assert-FinGrindMsvcSetupFailure "missing GITHUB_ENV; this script must run inside a GitHub Actions step"
    }

    [System.IO.File]::AppendAllText(
        $GitHubEnvironmentPath,
        $EnvironmentText,
        [System.Text.UTF8Encoding]::new($false)
    )
}
