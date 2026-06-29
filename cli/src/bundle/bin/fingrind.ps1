$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$scriptDirectory = $PSScriptRoot
$appHome = [System.IO.Path]::GetFullPath((Join-Path $scriptDirectory ".."))
$runtimeJava = Join-Path $appHome "runtime/bin/java.exe"
$applicationJar = Join-Path $appHome "lib/app/fingrind.jar"
$applicationModule = "dev.erst.fingrind.cli/dev.erst.fingrind.cli.App"
$internalCliArgumentsFileEnv = "FINGRIND_INTERNAL_CLI_ARGUMENTS_FILE"
$scriptInvocationArguments = @($args)

function New-StagedCliArgumentsFile {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $InvocationArguments
    )

    $argumentsFile = Join-Path ([System.IO.Path]::GetTempPath()) (
        "fingrind-cli-arguments-" + [System.Guid]::NewGuid().ToString("N") + ".json"
    )
    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText(
        $argumentsFile,
        (ConvertTo-Json -Compress $InvocationArguments),
        $utf8NoBom
    )
    return $argumentsFile
}

function Invoke-FinGrindBundleLauncher {
    $inheritedArgumentsFile = [System.Environment]::GetEnvironmentVariable($internalCliArgumentsFileEnv)
    $ownedArgumentsFile = $null
    $stagedArgumentsFile = $inheritedArgumentsFile
    if (-not (Test-Path -LiteralPath $runtimeJava -PathType Leaf)) {
        [Console]::Error.WriteLine("error: missing bundled Java runtime at $runtimeJava")
        return 1
    }

    if (-not (Test-Path -LiteralPath $applicationJar -PathType Leaf)) {
        [Console]::Error.WriteLine("error: missing FinGrind application JAR at $applicationJar")
        return 1
    }

    if ([string]::IsNullOrWhiteSpace($stagedArgumentsFile) -and $scriptInvocationArguments.Count -gt 0) {
        $ownedArgumentsFile = New-StagedCliArgumentsFile -InvocationArguments $scriptInvocationArguments
        $stagedArgumentsFile = $ownedArgumentsFile
    }

    if (-not [string]::IsNullOrWhiteSpace($stagedArgumentsFile) -and -not (Test-Path -LiteralPath $stagedArgumentsFile -PathType Leaf)) {
        [Console]::Error.WriteLine("error: missing staged CLI arguments file at $stagedArgumentsFile")
        return 1
    }

    $javaArguments = @(
        "--enable-native-access=dev.erst.fingrind.cli",
        "--add-opens=java.base/java.nio=dev.erst.fingrind.cli",
        "--add-exports=java.base/sun.nio=dev.erst.fingrind.cli",
        "-D{{sqliteBundleHomeSystemProperty}}=$appHome",
        "-Dfingrind.runtime.distribution={{bundleRuntimeDistribution}}",
        "-Dfingrind.runtime.bundle-target={{bundleClassifier}}",
        "--module-path",
        $applicationJar,
        "--module",
        $applicationModule
    )

    $javaStartInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $javaStartInfo.FileName = $runtimeJava
    $javaStartInfo.WorkingDirectory = [System.IO.Directory]::GetCurrentDirectory()
    $javaStartInfo.UseShellExecute = $false
    $javaStartInfo.RedirectStandardInput = [Console]::IsInputRedirected
    [void] $javaStartInfo.Environment.Remove("FINGRIND_SQLITE_LIBRARY")
    if ([string]::IsNullOrWhiteSpace($stagedArgumentsFile)) {
        [void] $javaStartInfo.Environment.Remove($internalCliArgumentsFileEnv)
        $javaArguments += $scriptInvocationArguments
    } else {
        $javaStartInfo.Environment[$internalCliArgumentsFileEnv] = $stagedArgumentsFile
    }

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
        if ($null -ne $ownedArgumentsFile -and (Test-Path -LiteralPath $ownedArgumentsFile -PathType Leaf)) {
            Remove-Item -LiteralPath $ownedArgumentsFile -Force -ErrorAction SilentlyContinue
        }
        $javaProcess.Dispose()
    }
}

$launcherExitCode = Invoke-FinGrindBundleLauncher
exit $launcherExitCode
