$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Get-FinGrindIsWindowsHost {
    return $IsWindows
}

function Get-FinGrindProjectCacheKey {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
        return "project"
    }

    if (Get-FinGrindIsWindowsHost) {
        return $RepositoryRoot.Replace("\", "_").Replace("/", "_").Replace(":", "_").Replace(" ", "_")
    }

    $cksumOutput = ($RepositoryRoot | & cksum | Out-String).Trim()
    if ([string]::IsNullOrWhiteSpace($cksumOutput)) {
        throw "failed to compute FinGrind Gradle project cache key for $RepositoryRoot"
    }

    return $cksumOutput.Split([char[]]" `t", [System.StringSplitOptions]::RemoveEmptyEntries)[0]
}

function Get-FinGrindProjectCacheRoot {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    if ($env:FINGRIND_GRADLE_PROJECT_CACHE_ROOT) {
        return $env:FINGRIND_GRADLE_PROJECT_CACHE_ROOT
    }

    if (Get-FinGrindIsWindowsHost) {
        if ($env:RUNNER_TEMP) {
            return (Join-Path $env:RUNNER_TEMP "fingrind-gradle-project-cache")
        }
        if ($env:TEMP) {
            return (Join-Path $env:TEMP "fingrind-gradle-project-cache")
        }
        if ($env:LOCALAPPDATA) {
            return (Join-Path $env:LOCALAPPDATA "FinGrind/gradle-project-cache")
        }
        return (Join-Path $RepositoryRoot ".gradle-project-cache")
    }

    if ($IsMacOS -and $env:HOME) {
        return (Join-Path $env:HOME "Library/Caches/FinGrind/gradle-project-cache")
    }
    if ($env:XDG_CACHE_HOME) {
        return (Join-Path $env:XDG_CACHE_HOME "fingrind/gradle-project-cache")
    }
    if ($env:HOME) {
        return (Join-Path $env:HOME ".cache/fingrind/gradle-project-cache")
    }
    if ($env:TMPDIR) {
        return (Join-Path ($env:TMPDIR.TrimEnd('/')) "fingrind-gradle-project-cache")
    }

    return "/tmp/fingrind-gradle-project-cache"
}

function Get-FinGrindProjectCacheDir {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    if ($env:FINGRIND_GRADLE_PROJECT_CACHE_DIR) {
        return $env:FINGRIND_GRADLE_PROJECT_CACHE_DIR
    }

    $cacheRoot = Get-FinGrindProjectCacheRoot -RepositoryRoot $RepositoryRoot
    return Join-Path $cacheRoot (Get-FinGrindProjectCacheKey -RepositoryRoot $RepositoryRoot)
}

function Get-FinGrindProjectBuildRoot {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    if ($env:FINGRIND_GRADLE_PROJECT_BUILD_ROOT) {
        return $env:FINGRIND_GRADLE_PROJECT_BUILD_ROOT
    }

    if ($env:FINGRIND_GRADLE_PROJECT_CACHE_DIR) {
        return (Join-Path $env:FINGRIND_GRADLE_PROJECT_CACHE_DIR "project-build")
    }

    return (Join-Path (Get-FinGrindProjectCacheDir -RepositoryRoot $RepositoryRoot) "project-build")
}

function Test-FinGrindShouldExternalizeProjectBuilds {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    if (-not (Get-FinGrindIsWindowsHost)) {
        return $true
    }

    if ($env:FINGRIND_GRADLE_PROJECT_BUILD_ROOT) {
        return $true
    }

    return $RepositoryRoot.StartsWith("\\")
}

function Get-FinGrindProjectBuildDir {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot,

        [Parameter(Mandatory = $true)]
        [string]$ProjectSegment
    )

    if (Test-FinGrindShouldExternalizeProjectBuilds -RepositoryRoot $RepositoryRoot) {
        return (Join-Path (Get-FinGrindProjectBuildRoot -RepositoryRoot $RepositoryRoot) $ProjectSegment)
    }

    if ($ProjectSegment -eq "root") {
        return (Join-Path $RepositoryRoot "build")
    }

    return (Join-Path (Join-Path $RepositoryRoot $ProjectSegment) "build")
}
