$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$scriptDirectory = $PSScriptRoot
$appHome = [System.IO.Path]::GetFullPath((Join-Path $scriptDirectory ".."))
$runtimeJava = Join-Path $appHome "runtime/bin/java.exe"
$applicationJar = Join-Path $appHome "lib/app/fingrind.jar"
$applicationModule = "dev.erst.fingrind.cli/dev.erst.fingrind.cli.App"
$scriptInvocationArguments = @($args)

function Invoke-FinGrindBundleLauncher {
    if (-not (Test-Path -LiteralPath $runtimeJava -PathType Leaf)) {
        [Console]::Error.WriteLine("error: missing bundled Java runtime at $runtimeJava")
        return 1
    }

    if (-not (Test-Path -LiteralPath $applicationJar -PathType Leaf)) {
        [Console]::Error.WriteLine("error: missing FinGrind application JAR at $applicationJar")
        return 1
    }

    $javaArguments = @(
        "--enable-native-access=dev.erst.fingrind.cli",
        "-D{{sqliteBundleHomeSystemProperty}}=$appHome",
        "-Dfingrind.runtime.distribution={{bundleRuntimeDistribution}}",
        "-Dfingrind.runtime.bundle-target={{bundleClassifier}}",
        "--module-path",
        $applicationJar,
        "--module",
        $applicationModule
    ) + $scriptInvocationArguments

    $javaStartInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $javaStartInfo.FileName = $runtimeJava
    $javaStartInfo.WorkingDirectory = [System.IO.Directory]::GetCurrentDirectory()
    $javaStartInfo.UseShellExecute = $false
    $javaStartInfo.RedirectStandardInput = [Console]::IsInputRedirected
    [void] $javaStartInfo.Environment.Remove("FINGRIND_BUNDLE_RETURN_EXIT_CODE")
    [void] $javaStartInfo.Environment.Remove("FINGRIND_BUNDLE_STDIN_FILE")
    [void] $javaStartInfo.Environment.Remove("FINGRIND_BUNDLE_ARGUMENTS_FILE")
    [void] $javaStartInfo.Environment.Remove("FINGRIND_LAUNCHER_ARGUMENTS_FILE")
    [void] $javaStartInfo.Environment.Remove("FINGRIND_SQLITE_LIBRARY")

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
            try {
                [Console]::OpenStandardInput().CopyTo($javaProcess.StandardInput.BaseStream)
                $javaProcess.StandardInput.BaseStream.Flush()
            }
            finally {
                $javaProcess.StandardInput.Close()
            }
        }

        $javaProcess.WaitForExit()
        return $javaProcess.ExitCode
    }
    finally {
        $javaProcess.Dispose()
    }
}

$launcherExitCode = Invoke-FinGrindBundleLauncher
exit $launcherExitCode
