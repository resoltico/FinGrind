$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if (-not (Get-Command Get-FinGrindWindowsGradleWrapperPlan -ErrorAction SilentlyContinue)) {
    . (Join-Path $PSScriptRoot "gradle-wrapper-support.ps1")
}

function Test-FinGrindGradleProjectCacheArgument {
    param(
        [Parameter()]
        [AllowEmptyString()]
        [string[]]$GradleArguments = @()
    )

    foreach ($gradleArgument in $GradleArguments) {
        if (
            $gradleArgument -eq "--project-cache-dir" -or
            $gradleArgument.StartsWith("--project-cache-dir=", [System.StringComparison]::OrdinalIgnoreCase)
        ) {
            return $true
        }
    }
    return $false
}

function Test-FinGrindGradleSystemPropertyArgument {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string[]]$GradleArguments,

        [Parameter(Mandatory = $true)]
        [string]$PropertyName
    )

    $prefix = "-D$PropertyName="
    foreach ($gradleArgument in $GradleArguments) {
        if ($gradleArgument.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            return $true
        }
    }
    return $false
}

function Initialize-FinGrindGradleWrapperDirectory {
    [CmdletBinding(SupportsShouldProcess)]
    param(
        [Parameter(Mandatory = $true)]
        [string]$Directory,

        [Parameter(Mandatory = $true)]
        [string]$Label
    )

    if (-not $PSCmdlet.ShouldProcess($Directory, "create FinGrind Gradle $Label")) {
        throw "creation of FinGrind Gradle $Label was declined for $Directory"
    }

    try {
        [System.IO.Directory]::CreateDirectory($Directory) | Out-Null
    }
    catch {
        throw "Unable to create FinGrind Gradle $Label at $Directory"
    }

    if (-not (Test-Path -LiteralPath $Directory -PathType Container)) {
        throw "Unable to create FinGrind Gradle $Label at $Directory"
    }
}

function ConvertFrom-FinGrindWindowsCommandLine {
    param(
        [Parameter()]
        [AllowEmptyString()]
        [string]$CommandLine = ""
    )

    $arguments = [System.Collections.Generic.List[string]]::new()
    $index = 0
    while ($index -lt $CommandLine.Length) {
        while ($index -lt $CommandLine.Length -and [char]::IsWhiteSpace($CommandLine[$index])) {
            $index++
        }
        if ($index -ge $CommandLine.Length) {
            break
        }

        $argument = [System.Text.StringBuilder]::new()
        $insideQuotes = $false
        while ($index -lt $CommandLine.Length) {
            $backslashCount = 0
            while ($index -lt $CommandLine.Length -and $CommandLine[$index] -eq '\') {
                $backslashCount++
                $index++
            }

            if ($index -lt $CommandLine.Length -and $CommandLine[$index] -eq '"') {
                $null = $argument.Append('\', [int]($backslashCount / 2))
                if (($backslashCount % 2) -eq 0) {
                    $insideQuotes = -not $insideQuotes
                }
                else {
                    $null = $argument.Append('"')
                }
                $index++
                continue
            }

            if ($backslashCount -gt 0) {
                $null = $argument.Append('\', $backslashCount)
            }
            if ($index -ge $CommandLine.Length) {
                break
            }
            if (-not $insideQuotes -and [char]::IsWhiteSpace($CommandLine[$index])) {
                break
            }
            $null = $argument.Append($CommandLine[$index])
            $index++
        }

        $arguments.Add($argument.ToString())
    }
    return $arguments.ToArray()
}

function Get-FinGrindGradleJavaExecutable {
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $javaHome = $env:JAVA_HOME.Trim('"')
        $javaName = if (Get-FinGrindIsWindowsHost) { "java.exe" } else { "java" }
        $javaExecutable = Join-Path (Join-Path $javaHome "bin") $javaName
        if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) {
            throw @"
ERROR: JAVA_HOME is set to an invalid directory: $javaHome

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation.
"@
        }
        return $javaExecutable
    }

    $javaCommand = @(Get-Command "java" -CommandType Application -ErrorAction SilentlyContinue) |
        Select-Object -First 1
    if ($null -eq $javaCommand) {
        throw @"
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation.
"@
    }
    return $javaCommand.Source
}

function New-FinGrindGradleWrapperInvocation {
    [CmdletBinding(SupportsShouldProcess)]
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot,

        [Parameter()]
        [AllowEmptyString()]
        [string[]]$GradleArguments = @(),

        [Parameter()]
        [string]$JavaExecutable = (Get-FinGrindGradleJavaExecutable),

        [Parameter()]
        [pscustomobject]$WrapperPlan = (Get-FinGrindWindowsGradleWrapperPlan -RepositoryRoot $RepositoryRoot)
    )

    if (-not $PSCmdlet.ShouldProcess($RepositoryRoot, "prepare FinGrind Gradle wrapper invocation")) {
        throw "preparation of the FinGrind Gradle wrapper invocation was declined"
    }

    $wrapperJar = Join-Path $RepositoryRoot "gradle/wrapper/gradle-wrapper.jar"
    if (-not (Test-Path -LiteralPath $wrapperJar -PathType Leaf)) {
        throw "missing Gradle wrapper JAR at $wrapperJar"
    }
    $invocationLeaseSource = Join-Path $RepositoryRoot "scripts/GradleInvocationLease.java"
    if (-not (Test-Path -LiteralPath $invocationLeaseSource -PathType Leaf)) {
        throw "missing FinGrind Gradle invocation lease source at $invocationLeaseSource"
    }
    $invocationLeaseDirectory = Split-Path -Path $WrapperPlan.InvocationLeaseFile -Parent
    if ([string]::IsNullOrWhiteSpace($invocationLeaseDirectory)) {
        throw "missing parent directory for FinGrind Gradle invocation lease $($WrapperPlan.InvocationLeaseFile)"
    }
    Initialize-FinGrindGradleWrapperDirectory `
        -Directory $invocationLeaseDirectory `
        -Label "invocation lease directory"

    $injectedJavaArguments = [System.Collections.Generic.List[string]]::new()
    $injectedGradleArguments = [System.Collections.Generic.List[string]]::new()

    if (-not (Test-FinGrindGradleProjectCacheArgument -GradleArguments $GradleArguments)) {
        Initialize-FinGrindGradleWrapperDirectory -Directory $WrapperPlan.ProjectCacheDir -Label "project cache"
        $injectedGradleArguments.Add("--project-cache-dir=$($WrapperPlan.ProjectCacheDir)")
    }
    if (-not (Test-FinGrindGradleSystemPropertyArgument `
            -GradleArguments $GradleArguments `
            -PropertyName "fingrind.gradle.build-logic-dir")) {
        Initialize-FinGrindGradleWrapperDirectory -Directory $WrapperPlan.BuildLogicDir -Label "build-logic directory"
        $injectedJavaArguments.Add("-Dfingrind.gradle.build-logic-dir=$($WrapperPlan.BuildLogicDir)")
    }
    if (-not (Test-FinGrindGradleSystemPropertyArgument `
            -GradleArguments $GradleArguments `
            -PropertyName "fingrind.gradle.jacoco-root")) {
        Initialize-FinGrindGradleWrapperDirectory -Directory $WrapperPlan.JacocoRoot -Label "JaCoCo directory"
        $injectedJavaArguments.Add("-Dfingrind.gradle.jacoco-root=$($WrapperPlan.JacocoRoot)")
    }
    if (
        $WrapperPlan.ShouldExternalizeProjectBuildRoot -and
        -not (Test-FinGrindGradleSystemPropertyArgument `
            -GradleArguments $GradleArguments `
            -PropertyName "fingrind.gradle.project-build-root")
    ) {
        Initialize-FinGrindGradleWrapperDirectory -Directory $WrapperPlan.ProjectBuildRoot -Label "project build root"
        $injectedJavaArguments.Add("-Dfingrind.gradle.project-build-root=$($WrapperPlan.ProjectBuildRoot)")
    }

    $javaArguments = [System.Collections.Generic.List[string]]::new()
    foreach ($defaultJavaArgument in @("-Xmx64m", "-Xms64m")) {
        $javaArguments.Add($defaultJavaArgument)
    }
    foreach ($javaOptionArgument in @(ConvertFrom-FinGrindWindowsCommandLine -CommandLine $env:JAVA_OPTS)) {
        $javaArguments.Add([string]$javaOptionArgument)
    }
    foreach ($gradleOptionArgument in @(ConvertFrom-FinGrindWindowsCommandLine -CommandLine $env:GRADLE_OPTS)) {
        $javaArguments.Add([string]$gradleOptionArgument)
    }
    foreach ($injectedJavaArgument in $injectedJavaArguments) {
        $javaArguments.Add($injectedJavaArgument)
    }
    foreach ($fixedJavaArgument in @("-Dorg.gradle.appname=gradlew", "-jar", $wrapperJar)) {
        $javaArguments.Add($fixedJavaArgument)
    }
    foreach ($injectedGradleArgument in $injectedGradleArguments) {
        $javaArguments.Add($injectedGradleArgument)
    }
    foreach ($gradleArgument in $GradleArguments) {
        $javaArguments.Add([string]$gradleArgument)
    }

    return [pscustomobject]@{
        JavaExecutable = $JavaExecutable
        JavaArguments = $javaArguments.ToArray()
        InvocationLeaseSource = $invocationLeaseSource
        InvocationLeaseFile = $WrapperPlan.InvocationLeaseFile
        WorkingDirectory = [System.IO.Directory]::GetCurrentDirectory()
    }
}

function Invoke-FinGrindGradleWrapper {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot,

        [Parameter()]
        [AllowEmptyString()]
        [string[]]$GradleArguments = @()
    )

    $invocation = New-FinGrindGradleWrapperInvocation `
        -RepositoryRoot $RepositoryRoot `
        -GradleArguments $GradleArguments
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $invocation.JavaExecutable
    $startInfo.WorkingDirectory = $invocation.WorkingDirectory
    $startInfo.UseShellExecute = $false
    # A redirected parent pipe remains open while PowerShell waits for Java. Transfer it explicitly
    # and close Java's copy before waiting; an interactive console must remain inherited instead.
    $startInfo.RedirectStandardInput = [Console]::IsInputRedirected
    $startInfo.RedirectStandardOutput = $false
    $startInfo.RedirectStandardError = $false
    foreach ($leaseArgument in @(
            $invocation.InvocationLeaseSource,
            $invocation.InvocationLeaseFile,
            "--",
            $invocation.JavaExecutable
        )) {
        $null = $startInfo.ArgumentList.Add([string]$leaseArgument)
    }
    foreach ($javaArgument in $invocation.JavaArguments) {
        $null = $startInfo.ArgumentList.Add([string]$javaArgument)
    }

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) {
            throw "failed to start Gradle Java runtime at $($invocation.JavaExecutable)"
        }
        if ($startInfo.RedirectStandardInput) {
            try {
                [Console]::OpenStandardInput().CopyTo($process.StandardInput.BaseStream)
                $process.StandardInput.BaseStream.Flush()
            }
            finally {
                $process.StandardInput.Close()
            }
        }
        $process.WaitForExit()
        return $process.ExitCode
    }
    finally {
        $process.Dispose()
    }
}
