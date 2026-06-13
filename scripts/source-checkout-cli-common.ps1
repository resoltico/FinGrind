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
            default { throw $StaleMessage }
        }
    }

    if ($formatVersion -ne "3" -or
        [string]::IsNullOrWhiteSpace($javaExecutable) -or
        [string]::IsNullOrWhiteSpace($applicationModule) -or
        [string]::IsNullOrWhiteSpace($nativeAccessModule)) {
        throw $StaleMessage
    }
    if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) {
        throw $StaleMessage
    }

    [pscustomobject]@{
        JavaExecutable = $javaExecutable
        ApplicationModule = $applicationModule
        NativeAccessModule = $nativeAccessModule
    }
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

    if ((Test-Path -LiteralPath $Context.RawJar -PathType Leaf) -and
        (Test-Path -LiteralPath $Context.SourceCheckoutRuntimeManifest -PathType Leaf)) {
        try {
            return Read-FinGrindSourceCheckoutRuntimeManifest `
                -ManifestPath $Context.SourceCheckoutRuntimeManifest `
                -MissingMessage $RuntimeManifestMissingMessage `
                -StaleMessage $RuntimeManifestStaleMessage
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
                Read-FinGrindSourceCheckoutRuntimeManifest `
                    -ManifestPath $Context.SourceCheckoutRuntimeManifest `
                    -MissingMessage $RuntimeManifestMissingMessage `
                    -StaleMessage $RuntimeManifestStaleMessage | Out-Null
                return
            }
            catch {
            }
        }
        Push-Location $Context.RepoRoot
        try {
            & $gradleWrapper @GradleTasks "--quiet" *> $null
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
        "-Dfingrind.runtime.distribution=$RuntimeDistribution" `
        "-Dfingrind.source-checkout.root=$($Context.RepoRoot)" `
        "-Dfingrind.source-checkout.build-root=$($Context.RootBuildDir)" `
        --module-path $Context.RawJar `
        --module $RuntimeManifest.ApplicationModule @Arguments
    $script:FinGrindCliWrapperExitCode = $LASTEXITCODE
}
