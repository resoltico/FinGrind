$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Get-FinGrindProjectCacheKey {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
        return "project"
    }

    return $RepositoryRoot.Replace("\", "_").Replace("/", "_").Replace(":", "_").Replace(" ", "_")
}

function Get-FinGrindProjectCacheDir {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    if ($env:FINGRIND_GRADLE_PROJECT_CACHE_DIR) {
        return $env:FINGRIND_GRADLE_PROJECT_CACHE_DIR
    }

    if ($env:FINGRIND_GRADLE_PROJECT_CACHE_ROOT) {
        $cacheRoot = $env:FINGRIND_GRADLE_PROJECT_CACHE_ROOT
    } elseif ($env:RUNNER_TEMP) {
        $cacheRoot = Join-Path $env:RUNNER_TEMP "fingrind-gradle-project-cache"
    } elseif ($env:TEMP) {
        $cacheRoot = Join-Path $env:TEMP "fingrind-gradle-project-cache"
    } elseif ($env:LOCALAPPDATA) {
        $cacheRoot = Join-Path $env:LOCALAPPDATA "FinGrind/gradle-project-cache"
    } else {
        $cacheRoot = Join-Path $RepositoryRoot ".gradle-project-cache"
    }

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
