$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$appHome = [System.IO.Path]::GetFullPath((Join-Path $scriptDirectory ".."))
$runtimeJava = Join-Path $appHome "runtime/bin/java.exe"
$applicationJar = Join-Path $appHome "lib/app/fingrind.jar"

if (-not (Test-Path -LiteralPath $runtimeJava -PathType Leaf)) {
    [Console]::Error.WriteLine("error: missing bundled Java runtime at $runtimeJava")
    exit 1
}

if (-not (Test-Path -LiteralPath $applicationJar -PathType Leaf)) {
    [Console]::Error.WriteLine("error: missing FinGrind application JAR at $applicationJar")
    exit 1
}

$javaArguments = @(
    "--enable-native-access=ALL-UNNAMED",
    "-Dfingrind.bundle.home=$appHome",
    "-Dfingrind.runtime.distribution=self-contained-bundle",
    "-jar",
    $applicationJar
) + $args

& $runtimeJava @javaArguments
exit $LASTEXITCODE
