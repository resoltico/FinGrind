$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if ([string]::IsNullOrWhiteSpace($env:FINGRIND_REPOSITORY_ROOT)) {
    throw "FINGRIND_REPOSITORY_ROOT is required for PowerShell quality tests"
}

Describe "Bundle smoke common policy" {
    BeforeAll {
        . (Join-Path $env:FINGRIND_REPOSITORY_ROOT "scripts/bundle-smoke-common.ps1")
    }

    It "preserves order and detects sequence differences" {
        (Test-SameSequence -Reference @("one", "two") -Actual @("one", "two")) | Should -BeTrue
        (Test-SameSequence -Reference @("one", "two") -Actual @("two", "one")) | Should -BeFalse
    }

    It "requires a configured explicit PowerShell executable to be absolute and existing" {
        $original = Get-Item -LiteralPath "Env:FINGRIND_PWSH_EXECUTABLE" -ErrorAction SilentlyContinue
        try {
            $env:FINGRIND_PWSH_EXECUTABLE = $PSHOME | Join-Path -ChildPath "pwsh"
            (Get-FinGrindPowerShellExecutable) | Should -Be ([System.IO.Path]::GetFullPath($env:FINGRIND_PWSH_EXECUTABLE))
            $env:FINGRIND_PWSH_EXECUTABLE = "relative-pwsh"
            { Get-FinGrindPowerShellExecutable } | Should -Throw "*must be an absolute path*"
        } finally {
            if ($null -eq $original) {
                Remove-Item -LiteralPath "Env:FINGRIND_PWSH_EXECUTABLE" -ErrorAction SilentlyContinue
            } else {
                $env:FINGRIND_PWSH_EXECUTABLE = [string]$original.Value
            }
        }
    }
}
