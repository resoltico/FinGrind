$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$scriptDirectory = $PSScriptRoot
$appHome = [System.IO.Path]::GetFullPath((Join-Path $scriptDirectory ".."))
$runtimeJava = Join-Path $appHome "runtime/bin/java.exe"
$applicationJar = Join-Path $appHome "lib/app/fingrind.jar"
$scriptInvocationArguments = @($args)

function Resolve-FinGrindBundleLauncherArguments {
    $argumentsFile = $env:FINGRIND_BUNDLE_ARGUMENTS_FILE
    if ([string]::IsNullOrWhiteSpace($argumentsFile)) {
        return $scriptInvocationArguments
    }
    if (-not (Test-Path -LiteralPath $argumentsFile -PathType Leaf)) {
        [Console]::Error.WriteLine("error: missing staged bundle arguments file at $argumentsFile")
        return $null
    }
    $parsedArguments = Get-Content -LiteralPath $argumentsFile -Raw -Encoding UTF8 | ConvertFrom-Json
    $resolvedArguments = @()
    foreach ($argument in @($parsedArguments)) {
        $resolvedArguments += [string] $argument
    }
    return $resolvedArguments
}

function Invoke-FinGrindBundleLauncher {
    $stdinFile = $env:FINGRIND_BUNDLE_STDIN_FILE
    $launcherArguments = Resolve-FinGrindBundleLauncherArguments

    if (-not (Test-Path -LiteralPath $runtimeJava -PathType Leaf)) {
        [Console]::Error.WriteLine("error: missing bundled Java runtime at $runtimeJava")
        return 1
    }

    if (-not (Test-Path -LiteralPath $applicationJar -PathType Leaf)) {
        [Console]::Error.WriteLine("error: missing FinGrind application JAR at $applicationJar")
        return 1
    }

    if ($stdinFile -and -not (Test-Path -LiteralPath $stdinFile -PathType Leaf)) {
        [Console]::Error.WriteLine("error: missing staged bundle stdin file at $stdinFile")
        return 1
    }
    if ($null -eq $launcherArguments) {
        return 1
    }

    $javaArguments = @(
        "--enable-native-access=ALL-UNNAMED",
        "-D{{bundleHomeSystemProperty}}=$appHome",
        "-Dfingrind.runtime.distribution={{bundleRuntimeDistribution}}",
        "-jar",
        $applicationJar
    ) + $launcherArguments

    $javaStartInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $javaStartInfo.FileName = $runtimeJava
    $javaStartInfo.WorkingDirectory = [System.IO.Directory]::GetCurrentDirectory()
    $javaStartInfo.UseShellExecute = $false
    $javaStartInfo.RedirectStandardInput = [string]::IsNullOrEmpty($stdinFile) -eq $false
    [void] $javaStartInfo.Environment.Remove("FINGRIND_BUNDLE_RETURN_EXIT_CODE")
    [void] $javaStartInfo.Environment.Remove("FINGRIND_BUNDLE_STDIN_FILE")
    [void] $javaStartInfo.Environment.Remove("FINGRIND_BUNDLE_ARGUMENTS_FILE")

    foreach ($javaArgument in $javaArguments) {
        [void] $javaStartInfo.ArgumentList.Add([string] $javaArgument)
    }

    $javaProcess = [System.Diagnostics.Process]::new()
    $javaProcess.StartInfo = $javaStartInfo

    try {
        if (-not $javaProcess.Start()) {
            [Console]::Error.WriteLine("error: failed to start bundled Java runtime at $runtimeJava")
            return 1
        }

        if ($javaStartInfo.RedirectStandardInput) {
            $stdinText = Get-Content -LiteralPath $stdinFile -Raw -Encoding UTF8
            $javaProcess.StandardInput.Write($stdinText)
            $javaProcess.StandardInput.Close()
        }

        $javaProcess.WaitForExit()
        return $javaProcess.ExitCode
    }
    finally {
        $javaProcess.Dispose()
    }
}

$launcherExitCode = Invoke-FinGrindBundleLauncher
if ($env:FINGRIND_BUNDLE_RETURN_EXIT_CODE -eq "true") {
    return $launcherExitCode
}
exit $launcherExitCode
