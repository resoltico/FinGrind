$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if ([string]::IsNullOrWhiteSpace($env:FINGRIND_REPOSITORY_ROOT)) {
    throw "FINGRIND_REPOSITORY_ROOT is required for PowerShell quality tests"
}
if ([string]::IsNullOrWhiteSpace($env:FINGRIND_PYTHON_EXECUTABLE)) {
    throw "FINGRIND_PYTHON_EXECUTABLE is required for PowerShell quality tests"
}

Describe "MSVC setup policy adapter" {
    BeforeAll {
        . (Join-Path $env:FINGRIND_REPOSITORY_ROOT "scripts/setup-msvc-dev-cmd-support.ps1")
    }

    BeforeEach {
        $script:NativeProcessCalls = [System.Collections.Generic.List[object]]::new()
        Mock Invoke-FinGrindNativeProcess {
            param(
                [string]$ExecutablePath,
                [string[]]$Arguments,
                [System.Text.Encoding]$StandardOutputEncoding,
                [System.Text.Encoding]$StandardErrorEncoding,
                [string]$RawArgumentLine
            )

            $null = $script:NativeProcessCalls.Add([pscustomobject]@{
                    ExecutablePath = $ExecutablePath
                    Arguments = @($Arguments)
                    RawArgumentLine = $RawArgumentLine
                    Encoding = $StandardOutputEncoding
                })
            if ($ExecutablePath -eq "cmd.exe") {
                return [pscustomobject]@{
                    ExitCode = 0
                    StandardOutput = "VSCMD_VER=17.12.3`nFINGRIND_VALUE=left=right`n"
                    StandardError = ""
                }
            }
            return [pscustomobject]@{
                ExitCode = 0
                StandardOutput = "C:\Rīga Visual Studio\2022\BuildTools`n"
                StandardError = ""
            }
        }
    }

    It "uses the real isolated policy for VSWhere arguments and mocks only the terminal native process" {
        $installation = Invoke-FinGrindVsWhere -VsWherePath "C:\Visual Studio Installer\vswhere.exe"

        $installation | Should -Be "C:\Rīga Visual Studio\2022\BuildTools"
        $script:NativeProcessCalls.Count | Should -Be 1
        $script:NativeProcessCalls[0].ExecutablePath | Should -Be "C:\Visual Studio Installer\vswhere.exe"
        $script:NativeProcessCalls[0].Arguments | Should -Contain "-latest"
        $script:NativeProcessCalls[0].Arguments | Should -Contain "-products"
        Should -Invoke Invoke-FinGrindNativeProcess -Times 1 -Exactly
    }

    It "uses the policy-generated raw cmd line and preserves UTF-16 environment output through the final native adapter" {
        $commandLine = Get-FinGrindVsDevCmdCommandLine `
            -VsDevCmdPath "C:\Rīga Visual Studio\2022\BuildTools\Common7\Tools\VsDevCmd.bat" `
            -Arch "x64" `
            -HostArch "arm64"
        $environmentDump = @(Invoke-FinGrindVsDevCmdEnvironmentDump `
            -CommandLine $commandLine `
            -Arch "x64" `
            -HostArch "arm64")
        $environmentText = ConvertTo-FinGrindGitHubEnvironmentText -EnvironmentDump $environmentDump

        $commandLine | Should -Be 'call "C:\Rīga Visual Studio\2022\BuildTools\Common7\Tools\VsDevCmd.bat" -arch=x64 -host_arch=arm64 >nul && set'
        $environmentDump | Should -Contain "VSCMD_VER=17.12.3"
        $environmentText | Should -Match "VSCMD_VER<<__FINGRIND_ENV__"
        $environmentText | Should -Match "FINGRIND_VALUE<<__FINGRIND_ENV__"
        $script:NativeProcessCalls.Count | Should -Be 1
        $script:NativeProcessCalls[0].ExecutablePath | Should -Be "cmd.exe"
        $script:NativeProcessCalls[0].RawArgumentLine | Should -Be "/d /u /v:off /s /c $commandLine"
        $script:NativeProcessCalls[0].Encoding.CodePage | Should -Be ([System.Text.Encoding]::Unicode.CodePage)
        Should -Invoke Invoke-FinGrindNativeProcess -Times 1 -Exactly
    }
}
