$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if (-not (Get-Command Get-FinGrindProjectBuildDir -ErrorAction SilentlyContinue)) {
    throw "source-checkout-cli-common.ps1 requires gradle-wrapper-support.ps1 to be loaded first."
}

function Get-FinGrindCliWrapperContext {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ScriptRoot
    )

    $repoRoot = [System.IO.Path]::GetFullPath((Join-Path $ScriptRoot ".."))
    $cliBuildDir = Get-FinGrindProjectBuildDir -RepositoryRoot $repoRoot -ProjectSegment "cli"
    $rootBuildDir = Get-FinGrindProjectBuildDir -RepositoryRoot $repoRoot -ProjectSegment "root"
    $lockDirectory = Join-Path $rootBuildDir "repo-locks/cli-runtime-prepare.lock"
    [pscustomobject]@{
        RepoRoot = $repoRoot
        CliBuildDir = $cliBuildDir
        RootBuildDir = $rootBuildDir
        LockDirectory = $lockDirectory
        RawJar = Join-Path $cliBuildDir "libs/fingrind.jar"
        SourceCheckoutRuntimeManifest =
            Get-FinGrindSourceCheckoutRuntimeManifestPath -RepositoryRoot $repoRoot -ProjectSegment "cli"
    }
}

function Read-FinGrindSourceCheckoutRuntimeManifest {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ManifestPath,

        [Parameter(Mandatory = $true)]
        [string]$MissingMessage,

        [Parameter(Mandatory = $true)]
        [string]$StaleMessage
    )

    if (-not (Test-Path -LiteralPath $ManifestPath -PathType Leaf)) {
        throw $MissingMessage
    }

    $javaExecutable = $null
    $applicationModule = $null
    $nativeAccessModule = $null
    $runtimeInputPaths = [System.Collections.Generic.List[string]]::new()
    $formatVersion = $null
    foreach ($line in [System.IO.File]::ReadAllLines($ManifestPath, [System.Text.Encoding]::UTF8)) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("ownerTask=")) {
            continue
        }
        if ($line.StartsWith("formatVersion=")) {
            $formatVersion = $line.Substring("formatVersion=".Length)
            continue
        }
        $parts = $line.Split("`t", 2)
        if ($parts.Length -ne 2) {
            throw $StaleMessage
        }
        switch ($parts[0]) {
            "javaExecutable" { $javaExecutable = $parts[1] }
            "javaInstallationDirectory" { }
            "applicationModule" { $applicationModule = $parts[1] }
            "nativeAccessModule" { $nativeAccessModule = $parts[1] }
            "runtimeInputPath" {
                if ([string]::IsNullOrWhiteSpace($parts[1])) {
                    throw $StaleMessage
                }
                $runtimeInputPaths.Add($parts[1])
            }
            default { throw $StaleMessage }
        }
    }

    if ($formatVersion -ne "4" -or
        [string]::IsNullOrWhiteSpace($javaExecutable) -or
        [string]::IsNullOrWhiteSpace($applicationModule) -or
        [string]::IsNullOrWhiteSpace($nativeAccessModule) -or
        $runtimeInputPaths.Count -eq 0) {
        throw $StaleMessage
    }
    if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) {
        throw $StaleMessage
    }

    [pscustomobject]@{
        JavaExecutable = $javaExecutable
        ApplicationModule = $applicationModule
        NativeAccessModule = $nativeAccessModule
        RuntimeInputPaths = $runtimeInputPaths.ToArray()
    }
}

function Test-FinGrindCliWrapperRuntimeFreshness {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ManifestPath,

        [Parameter(Mandatory = $true)]
        [string[]]$RuntimeInputPaths
    )

    if (-not (Test-Path -LiteralPath $ManifestPath -PathType Leaf)) {
        return $false
    }
    if ($RuntimeInputPaths.Count -eq 0) {
        return $false
    }
    $manifestWriteTime = (Get-Item -LiteralPath $ManifestPath).LastWriteTimeUtc
    foreach ($runtimeInputPath in $RuntimeInputPaths) {
        if (Test-Path -LiteralPath $runtimeInputPath -PathType Container) {
            $newerInput =
                Get-ChildItem -LiteralPath $runtimeInputPath -File -Recurse -ErrorAction SilentlyContinue |
                Where-Object { $_.LastWriteTimeUtc -gt $manifestWriteTime } |
                Select-Object -First 1
            if ($null -ne $newerInput) {
                return $false
            }
            continue
        }
        if (-not (Test-Path -LiteralPath $runtimeInputPath -PathType Leaf)) {
            return $false
        }
        if ((Get-Item -LiteralPath $runtimeInputPath).LastWriteTimeUtc -gt $manifestWriteTime) {
            return $false
        }
    }
    return $true
}

function Invoke-FinGrindCliWrapperRefreshLock {
    param(
        [Parameter(Mandatory = $true)]
        [string]$LockDirectory,

        [Parameter(Mandatory = $true)]
        [scriptblock]$Action
    )

    $lockParent = Split-Path -Parent $LockDirectory
    if (-not [string]::IsNullOrWhiteSpace($lockParent)) {
        [System.IO.Directory]::CreateDirectory($lockParent) | Out-Null
    }
    while ($true) {
        try {
            New-Item -ItemType Directory -Path $LockDirectory -ErrorAction Stop | Out-Null
            break
        }
        catch {
            Start-Sleep -Milliseconds 50
        }
    }
    try {
        & $Action
    }
    finally {
        Remove-Item -LiteralPath $LockDirectory -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Invoke-FinGrindEnsureCliWrapperRuntime {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$Context,

        [Parameter(Mandatory = $true)]
        [string]$ArtifactLabel,

        [Parameter(Mandatory = $true)]
        [string[]]$GradleTasks,

        [Parameter(Mandatory = $true)]
        [string]$RuntimeManifestMissingMessage,

        [Parameter(Mandatory = $true)]
        [string]$RuntimeManifestStaleMessage
    )

    $forceRerun = $false
    if ((Test-Path -LiteralPath $Context.RawJar -PathType Leaf) -and
        (Test-Path -LiteralPath $Context.SourceCheckoutRuntimeManifest -PathType Leaf)) {
        try {
            $runtimeManifest = Read-FinGrindSourceCheckoutRuntimeManifest `
                -ManifestPath $Context.SourceCheckoutRuntimeManifest `
                -MissingMessage $RuntimeManifestMissingMessage `
                -StaleMessage $RuntimeManifestStaleMessage
            if (
                Test-FinGrindCliWrapperRuntimeFreshness `
                    -ManifestPath $Context.SourceCheckoutRuntimeManifest `
                    -RuntimeInputPaths $runtimeManifest.RuntimeInputPaths
            ) {
                return $runtimeManifest
            }
            $forceRerun = $true
        }
        catch {
        }
    }

    $gradleWrapper =
        if (Get-FinGrindIsWindowsHost) {
            Join-Path $Context.RepoRoot "gradlew.bat"
        } else {
            Join-Path $Context.RepoRoot "gradlew"
        }
    Invoke-FinGrindCliWrapperRefreshLock -LockDirectory $Context.LockDirectory -Action {
        if ((Test-Path -LiteralPath $Context.RawJar -PathType Leaf) -and
            (Test-Path -LiteralPath $Context.SourceCheckoutRuntimeManifest -PathType Leaf)) {
            try {
                $runtimeManifest = Read-FinGrindSourceCheckoutRuntimeManifest `
                    -ManifestPath $Context.SourceCheckoutRuntimeManifest `
                    -MissingMessage $RuntimeManifestMissingMessage `
                    -StaleMessage $RuntimeManifestStaleMessage
                if (
                    Test-FinGrindCliWrapperRuntimeFreshness `
                        -ManifestPath $Context.SourceCheckoutRuntimeManifest `
                        -RuntimeInputPaths $runtimeManifest.RuntimeInputPaths
                ) {
                    return
                }
                $forceRerun = $true
            }
            catch {
            }
        }
        Push-Location $Context.RepoRoot
        try {
            if ($forceRerun) {
                & $gradleWrapper @GradleTasks "--rerun-tasks" "--quiet" *> $null
            } else {
                & $gradleWrapper @GradleTasks "--quiet" *> $null
            }
            if ($LASTEXITCODE -ne 0) {
                throw "failed to prepare $ArtifactLabel via $gradleWrapper $($GradleTasks -join ' ')"
            }
        } finally {
            Pop-Location
        }
    }

    if (-not (Test-Path -LiteralPath $Context.RawJar -PathType Leaf)) {
        $gradleCommandHint =
            if (Get-FinGrindIsWindowsHost) {
                ".\\gradlew.bat"
            } else {
                "./gradlew"
            }
        throw "missing $ArtifactLabel at $($Context.RawJar); run $gradleCommandHint $($GradleTasks -join ' ')"
    }

    return Read-FinGrindSourceCheckoutRuntimeManifest `
        -ManifestPath $Context.SourceCheckoutRuntimeManifest `
        -MissingMessage $RuntimeManifestMissingMessage `
        -StaleMessage $RuntimeManifestStaleMessage
}

function Invoke-FinGrindCliWrapper {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$Context,

        [Parameter(Mandatory = $true)]
        [pscustomobject]$RuntimeManifest,

        [Parameter(Mandatory = $true)]
        [string]$RuntimeDistribution,

        [Parameter()]
        [string[]]$Arguments = @()
    )

    & $RuntimeManifest.JavaExecutable `
        "--enable-native-access=$($RuntimeManifest.NativeAccessModule)" `
        "--add-opens=java.base/java.nio=$($RuntimeManifest.NativeAccessModule)" `
        "--add-exports=java.base/sun.nio=$($RuntimeManifest.NativeAccessModule)" `
        "-Dfingrind.runtime.distribution=$RuntimeDistribution" `
        "-Dfingrind.source-checkout.root=$($Context.RepoRoot)" `
        "-Dfingrind.source-checkout.build-root=$($Context.RootBuildDir)" `
        --module-path $Context.RawJar `
        --module $RuntimeManifest.ApplicationModule @Arguments
    $script:FinGrindCliWrapperExitCode = $LASTEXITCODE
}
