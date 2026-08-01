$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Get-FinGrindIsWindowsHost {
    return $IsWindows
}

function Get-FinGrindWindowsEnvironmentValue {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Environment,

        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    if (-not $Environment.ContainsKey($Name)) {
        return $null
    }

    $value = $Environment[$Name]
    if ($null -eq $value -or [string]$value.Length -eq 0) {
        return $null
    }

    return [string]$value
}

function Join-FinGrindWindowsPath {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateNotNullOrEmpty()]
        [string]$ParentPath,

        [Parameter(Mandatory = $true)]
        [ValidateNotNullOrEmpty()]
        [string]$ChildPath
    )

    $trimCharacters = [char[]]@('\', '/')
    return $ParentPath.TrimEnd($trimCharacters) + '\' + $ChildPath.TrimStart($trimCharacters)
}

function Get-FinGrindWindowsGradleWrapperPlanForEnvironment {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot,

        [Parameter(Mandatory = $true)]
        [hashtable]$Environment
    )

    $projectCacheKey = Get-FinGrindWindowsEnvironmentValue `
        -Environment $Environment `
        -Name 'FINGRIND_PROJECT_CACHE_KEY'
    if ($null -eq $projectCacheKey) {
        $projectCacheKey = $RepositoryRoot
    }
    if ([string]::IsNullOrEmpty($projectCacheKey)) {
        $projectCacheKey = 'project'
    }
    $projectCacheKey = $projectCacheKey.Replace('\', '_').Replace('/', '_').Replace(':', '_').Replace(' ', '_')

    $projectCacheRoot = Get-FinGrindWindowsEnvironmentValue `
        -Environment $Environment `
        -Name 'FINGRIND_GRADLE_PROJECT_CACHE_ROOT'
    if ($null -eq $projectCacheRoot) {
        $runnerTemp = Get-FinGrindWindowsEnvironmentValue -Environment $Environment -Name 'RUNNER_TEMP'
        $tempDirectory = Get-FinGrindWindowsEnvironmentValue -Environment $Environment -Name 'TEMP'
        $localAppData = Get-FinGrindWindowsEnvironmentValue -Environment $Environment -Name 'LOCALAPPDATA'
        if ($null -ne $runnerTemp) {
            $projectCacheRoot = Join-FinGrindWindowsPath `
                -ParentPath $runnerTemp `
                -ChildPath 'fingrind-gradle-project-cache'
        } elseif ($null -ne $tempDirectory) {
            $projectCacheRoot = Join-FinGrindWindowsPath `
                -ParentPath $tempDirectory `
                -ChildPath 'fingrind-gradle-project-cache'
        } elseif ($null -ne $localAppData) {
            $projectCacheRoot = Join-FinGrindWindowsPath `
                -ParentPath $localAppData `
                -ChildPath 'FinGrind\gradle-project-cache'
        } else {
            $projectCacheRoot = Join-FinGrindWindowsPath `
                -ParentPath $RepositoryRoot `
                -ChildPath '.gradle-project-cache'
        }
    }

    $projectCacheDir = Get-FinGrindWindowsEnvironmentValue `
        -Environment $Environment `
        -Name 'FINGRIND_GRADLE_PROJECT_CACHE_DIR'
    if ($null -eq $projectCacheDir) {
        $projectCacheDir = Join-FinGrindWindowsPath `
            -ParentPath $projectCacheRoot `
            -ChildPath $projectCacheKey
    }

    $buildLogicDir = Get-FinGrindWindowsEnvironmentValue `
        -Environment $Environment `
        -Name 'FINGRIND_GRADLE_BUILD_LOGIC_DIR'
    if ($null -eq $buildLogicDir) {
        $buildLogicDir = Join-FinGrindWindowsPath `
            -ParentPath $projectCacheDir `
            -ChildPath 'build-logic'
    }

    $jacocoRoot = Get-FinGrindWindowsEnvironmentValue `
        -Environment $Environment `
        -Name 'FINGRIND_GRADLE_JACOCO_ROOT'
    if ($null -eq $jacocoRoot) {
        $jacocoRoot = Join-FinGrindWindowsPath `
            -ParentPath $projectCacheDir `
            -ChildPath 'jacoco'
    }

    $explicitProjectBuildRoot = Get-FinGrindWindowsEnvironmentValue `
        -Environment $Environment `
        -Name 'FINGRIND_GRADLE_PROJECT_BUILD_ROOT'
    $shouldExternalizeProjectBuildRoot =
        $null -ne $explicitProjectBuildRoot -or $RepositoryRoot.StartsWith('\\')
    if ($null -ne $explicitProjectBuildRoot) {
        $projectBuildRoot = $explicitProjectBuildRoot
    } else {
        $projectBuildRoot = Join-FinGrindWindowsPath `
            -ParentPath $projectCacheDir `
            -ChildPath 'project-build'
    }

    $invocationLeaseRoot = Get-FinGrindWindowsEnvironmentValue `
        -Environment $Environment `
        -Name 'FINGRIND_GRADLE_INVOCATION_LEASE_ROOT'
    if ($null -eq $invocationLeaseRoot) {
        $runnerTemp = Get-FinGrindWindowsEnvironmentValue -Environment $Environment -Name 'RUNNER_TEMP'
        $tempDirectory = Get-FinGrindWindowsEnvironmentValue -Environment $Environment -Name 'TEMP'
        $localAppData = Get-FinGrindWindowsEnvironmentValue -Environment $Environment -Name 'LOCALAPPDATA'
        if ($null -ne $runnerTemp) {
            $invocationLeaseRoot = Join-FinGrindWindowsPath `
                -ParentPath $runnerTemp `
                -ChildPath 'fingrind-gradle-invocation-leases'
        } elseif ($null -ne $tempDirectory) {
            $invocationLeaseRoot = Join-FinGrindWindowsPath `
                -ParentPath $tempDirectory `
                -ChildPath 'fingrind-gradle-invocation-leases'
        } elseif ($null -ne $localAppData) {
            $invocationLeaseRoot = Join-FinGrindWindowsPath `
                -ParentPath $localAppData `
                -ChildPath 'FinGrind\gradle-invocation-leases'
        } else {
            $invocationLeaseRoot = Join-FinGrindWindowsPath `
                -ParentPath $RepositoryRoot `
                -ChildPath '.gradle-invocation-leases'
        }
    }
    $invocationLeaseFile = Join-FinGrindWindowsPath `
        -ParentPath $invocationLeaseRoot `
        -ChildPath "$projectCacheKey.lease"

    return [pscustomobject]@{
        ProjectCacheKey = $projectCacheKey
        ProjectCacheRoot = $projectCacheRoot
        ProjectCacheDir = $projectCacheDir
        BuildLogicDir = $buildLogicDir
        JacocoRoot = $jacocoRoot
        ShouldExternalizeProjectBuildRoot = $shouldExternalizeProjectBuildRoot
        ProjectBuildRoot = $projectBuildRoot
        InvocationLeaseRoot = $invocationLeaseRoot
        InvocationLeaseFile = $invocationLeaseFile
    }
}

function Get-FinGrindWindowsGradleWrapperPlan {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    $environment = @{}
    foreach ($name in @(
            'FINGRIND_PROJECT_CACHE_KEY',
            'FINGRIND_GRADLE_PROJECT_CACHE_ROOT',
            'FINGRIND_GRADLE_PROJECT_CACHE_DIR',
            'FINGRIND_GRADLE_BUILD_LOGIC_DIR',
            'FINGRIND_GRADLE_JACOCO_ROOT',
            'FINGRIND_GRADLE_PROJECT_BUILD_ROOT',
            'FINGRIND_GRADLE_INVOCATION_LEASE_ROOT',
            'RUNNER_TEMP',
            'TEMP',
            'LOCALAPPDATA'
        )) {
        $environment[$name] = [System.Environment]::GetEnvironmentVariable($name)
    }

    return Get-FinGrindWindowsGradleWrapperPlanForEnvironment `
        -RepositoryRoot $RepositoryRoot `
        -Environment $environment
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
        return (Get-FinGrindWindowsGradleWrapperPlan -RepositoryRoot $RepositoryRoot).ProjectCacheKey
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

    if (Get-FinGrindIsWindowsHost) {
        return (Get-FinGrindWindowsGradleWrapperPlan -RepositoryRoot $RepositoryRoot).ProjectCacheRoot
    }

    if ($env:FINGRIND_GRADLE_PROJECT_CACHE_ROOT) {
        return $env:FINGRIND_GRADLE_PROJECT_CACHE_ROOT
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

    if (Get-FinGrindIsWindowsHost) {
        return (Get-FinGrindWindowsGradleWrapperPlan -RepositoryRoot $RepositoryRoot).ProjectCacheDir
    }

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

    if (Get-FinGrindIsWindowsHost) {
        return (Get-FinGrindWindowsGradleWrapperPlan -RepositoryRoot $RepositoryRoot).ProjectBuildRoot
    }

    if ($env:FINGRIND_GRADLE_PROJECT_BUILD_ROOT) {
        return $env:FINGRIND_GRADLE_PROJECT_BUILD_ROOT
    }

    if ($env:FINGRIND_GRADLE_PROJECT_CACHE_DIR) {
        return (Join-Path $env:FINGRIND_GRADLE_PROJECT_CACHE_DIR "project-build")
    }

    return (Join-Path (Get-FinGrindProjectCacheDir -RepositoryRoot $RepositoryRoot) "project-build")
}

function Test-FinGrindProjectBuildExternalization {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    if (Get-FinGrindIsWindowsHost) {
        return (Get-FinGrindWindowsGradleWrapperPlan -RepositoryRoot $RepositoryRoot).ShouldExternalizeProjectBuildRoot
    }

    return $true
}

function Get-FinGrindProjectBuildDir {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot,

        [Parameter(Mandatory = $true)]
        [string]$ProjectSegment
    )

    if (Get-FinGrindIsWindowsHost) {
        $windowsPlan = Get-FinGrindWindowsGradleWrapperPlan -RepositoryRoot $RepositoryRoot
        if ($windowsPlan.ShouldExternalizeProjectBuildRoot) {
            return Join-FinGrindWindowsPath -ParentPath $windowsPlan.ProjectBuildRoot -ChildPath $ProjectSegment
        }
        if ($ProjectSegment -eq "root") {
            return Join-FinGrindWindowsPath -ParentPath $RepositoryRoot -ChildPath "build"
        }
        return Join-FinGrindWindowsPath `
            -ParentPath (Join-FinGrindWindowsPath -ParentPath $RepositoryRoot -ChildPath $ProjectSegment) `
            -ChildPath "build"
    }

    if (Test-FinGrindProjectBuildExternalization -RepositoryRoot $RepositoryRoot) {
        return (Join-Path (Get-FinGrindProjectBuildRoot -RepositoryRoot $RepositoryRoot) $ProjectSegment)
    }

    if ($ProjectSegment -eq "root") {
        return (Join-Path $RepositoryRoot "build")
    }

    return (Join-Path (Join-Path $RepositoryRoot $ProjectSegment) "build")
}

function Get-FinGrindSourceCheckoutRuntimeManifestPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot,

        [Parameter(Mandatory = $true)]
        [string]$ProjectSegment
    )

    $buildDir = Get-FinGrindProjectBuildDir -RepositoryRoot $RepositoryRoot -ProjectSegment $ProjectSegment
    return (Join-Path $buildDir "generated/source-checkout/source-checkout-runtime-manifest.tsv")
}

function Get-FinGrindBundleArchiveManifestPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot,

        [Parameter(Mandatory = $true)]
        [string]$ProjectSegment
    )

    $buildDir = Get-FinGrindProjectBuildDir -RepositoryRoot $RepositoryRoot -ProjectSegment $ProjectSegment
    return (Join-Path $buildDir "generated/bundle/bundle-archive-manifest.json")
}

function Get-FinGrindDockerContextDir {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot,

        [Parameter(Mandatory = $true)]
        [string]$ProjectSegment
    )

    $buildDir = Get-FinGrindProjectBuildDir -RepositoryRoot $RepositoryRoot -ProjectSegment $ProjectSegment
    return (Join-Path $buildDir "docker-context")
}
