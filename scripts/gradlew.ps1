$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if ($PSVersionTable.PSVersion.Major -lt 7) {
    [Console]::Error.WriteLine("ERROR: FinGrind's Windows Gradle wrapper requires PowerShell 7 or later.")
    exit 1
}

. (Join-Path $PSScriptRoot "gradle-wrapper-owner.ps1")

$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
exit (Invoke-FinGrindGradleWrapper -RepositoryRoot $repositoryRoot -GradleArguments @($args))
