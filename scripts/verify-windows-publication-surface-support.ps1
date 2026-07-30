$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Resolve-FinGrindWindowsPublicationDirectory {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$Label
    )

    $resolvedPath = [System.IO.Path]::GetFullPath($Path)
    if (-not (Test-Path -LiteralPath $resolvedPath -PathType Container)) {
        throw "$Label directory does not exist: $resolvedPath"
    }
    return $resolvedPath
}

function Resolve-FinGrindWindowsPublicationFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$Label
    )

    $resolvedPath = [System.IO.Path]::GetFullPath($Path)
    if (-not (Test-Path -LiteralPath $resolvedPath -PathType Leaf)) {
        throw "$Label file does not exist: $resolvedPath"
    }
    return $resolvedPath
}

function Resolve-FinGrindWindowsPublicationRepositoryFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot,

        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$Label
    )

    $resolvedRepositoryRoot = Resolve-FinGrindWindowsPublicationDirectory `
        -Path $RepositoryRoot `
        -Label "target repository"
    $resolvedPath = [System.IO.Path]::GetFullPath($Path)
    $repositoryPrefix = $resolvedRepositoryRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) +
        [System.IO.Path]::DirectorySeparatorChar
    if (-not $resolvedPath.StartsWith($repositoryPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "$Label must stay under the target repository: $resolvedPath"
    }

    $relativePath = [System.IO.Path]::GetRelativePath($resolvedRepositoryRoot, $resolvedPath)
    $pathSegments = $relativePath.Split(
        [char[]]@([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar),
        [System.StringSplitOptions]::RemoveEmptyEntries
    )
    $candidatePaths = [System.Collections.Generic.List[string]]::new()
    $candidatePaths.Add($resolvedRepositoryRoot)
    $currentPath = $resolvedRepositoryRoot
    foreach ($pathSegment in $pathSegments) {
        $currentPath = Join-Path $currentPath $pathSegment
        $candidatePaths.Add($currentPath)
    }
    foreach ($candidatePath in $candidatePaths) {
        if (-not (Test-Path -LiteralPath $candidatePath)) {
            throw "$Label file does not exist: $candidatePath"
        }
        $item = Get-Item -LiteralPath $candidatePath -Force
        if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "$Label must not traverse a reparse point: $candidatePath"
        }
    }

    if (-not (Test-Path -LiteralPath $resolvedPath -PathType Leaf)) {
        throw "$Label is not a file: $resolvedPath"
    }
    return $resolvedPath
}

function Resolve-FinGrindWindowsPublicationWorkflowOutputFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not [System.IO.Path]::IsPathFullyQualified($Path)) {
        throw "GitHub workflow output path must be absolute: $Path"
    }
    $resolvedPath = [System.IO.Path]::GetFullPath($Path)
    $parentPath = Split-Path -Path $resolvedPath -Parent
    if ([string]::IsNullOrWhiteSpace($parentPath) -or
        -not (Test-Path -LiteralPath $parentPath -PathType Container)) {
        throw "GitHub workflow output parent directory does not exist: $parentPath"
    }
    $outputAncestors = [System.Collections.Generic.List[string]]::new()
    $currentAncestor = $parentPath
    while ($true) {
        $outputAncestors.Add($currentAncestor)
        $nextAncestor = Split-Path -Path $currentAncestor -Parent
        if ([string]::IsNullOrWhiteSpace($nextAncestor) -or
            [string]::Equals(
                $nextAncestor,
                $currentAncestor,
                [System.StringComparison]::OrdinalIgnoreCase
            )) {
            break
        }
        $currentAncestor = $nextAncestor
    }
    foreach ($outputAncestor in $outputAncestors) {
        $ancestorItem = Get-Item -LiteralPath $outputAncestor -Force
        if (($ancestorItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "GitHub workflow output path must not traverse a reparse point: $outputAncestor"
        }
    }
    if (Test-Path -LiteralPath $resolvedPath -PathType Container) {
        throw "GitHub workflow output path is a directory: $resolvedPath"
    }
    if (-not (Test-Path -LiteralPath $resolvedPath -PathType Leaf)) {
        [System.IO.File]::Open(
            $resolvedPath,
            [System.IO.FileMode]::OpenOrCreate,
            [System.IO.FileAccess]::Write
        ).Dispose()
    }
    $outputItem = Get-Item -LiteralPath $resolvedPath -Force
    if (($outputItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "GitHub workflow output path must not be a reparse point: $resolvedPath"
    }
    return $resolvedPath
}

function Invoke-FinGrindWindowsPublicationPolicy {
    param(
        [Parameter(Mandatory = $true)]
        [string]$PythonExecutable,

        [Parameter(Mandatory = $true)]
        [string]$PolicyScriptPath,

        [Parameter(Mandatory = $true)]
        [hashtable]$Request
    )

    $requestJson = $Request | ConvertTo-Json -Compress -Depth 8
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $PythonExecutable
    # The helper-root policy is release control, not an extension point for ambient Python state.
    $null = $startInfo.ArgumentList.Add("-I")
    $null = $startInfo.ArgumentList.Add("-B")
    $null = $startInfo.ArgumentList.Add("-X")
    $null = $startInfo.ArgumentList.Add("utf8")
    $null = $startInfo.ArgumentList.Add($PolicyScriptPath)
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    $startInfo.StandardInputEncoding = $utf8NoBom
    $startInfo.StandardOutputEncoding = $utf8NoBom
    $startInfo.StandardErrorEncoding = $utf8NoBom
    $policyProcess = [System.Diagnostics.Process]::new()
    $policyProcess.StartInfo = $startInfo
    try {
        if (-not $policyProcess.Start()) {
            throw "Windows publication policy process did not start"
        }
        $policyOutputTask = $policyProcess.StandardOutput.ReadToEndAsync()
        $policyErrorTask = $policyProcess.StandardError.ReadToEndAsync()
        $policyProcess.StandardInput.Write($requestJson)
        $policyProcess.StandardInput.Close()
        $policyProcess.WaitForExit()
        $responseJson = $policyOutputTask.GetAwaiter().GetResult()
        $policyError = $policyErrorTask.GetAwaiter().GetResult().Trim()
        if ($policyProcess.ExitCode -ne 0) {
            if ([string]::IsNullOrWhiteSpace($policyError)) {
                throw "Windows publication policy failed with exit code $($policyProcess.ExitCode)"
            }
            throw "Windows publication policy failed with exit code $($policyProcess.ExitCode): $policyError"
        }
    } finally {
        $policyProcess.Dispose()
    }
    if ([string]::IsNullOrWhiteSpace($responseJson)) {
        throw "Windows publication policy returned no JSON response"
    }
    try {
        $response = $responseJson | ConvertFrom-Json -AsHashtable
    } catch {
        throw "Windows publication policy returned invalid JSON: $($_.Exception.Message)"
    }
    if ($null -eq $response -or -not ($response -is [System.Collections.IDictionary])) {
        throw "Windows publication policy response must be one JSON object"
    }
    return $response
}

function Get-FinGrindWindowsPublicationPlan {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot,

        [Parameter(Mandatory = $true)]
        [string]$ExpectedOperatingSystemId,

        [Parameter(Mandatory = $true)]
        [string]$ExpectedArchitectureId,

        [Parameter(Mandatory = $true)]
        [string]$BundleClassifier,

        [Parameter(Mandatory = $true)]
        [string]$PythonExecutable,

        [Parameter(Mandatory = $true)]
        [string]$PolicyScriptPath
    )

    $resolvedRepositoryRoot = Resolve-FinGrindWindowsPublicationDirectory `
        -Path $RepositoryRoot `
        -Label "target repository"
    $gradleProperties = Resolve-FinGrindWindowsPublicationRepositoryFile `
        -RepositoryRoot $resolvedRepositoryRoot `
        -Path (Join-Path $resolvedRepositoryRoot "gradle.properties") `
        -Label "target Gradle properties"
    $bundleLayoutContract = Resolve-FinGrindWindowsPublicationRepositoryFile `
        -RepositoryRoot $resolvedRepositoryRoot `
        -Path (Join-Path `
            $resolvedRepositoryRoot `
            "contract/src/main/resources/dev/erst/fingrind/contract/protocol/bundle-layout-contract.json") `
        -Label "target bundle layout contract"

    $response = Invoke-FinGrindWindowsPublicationPolicy `
        -PythonExecutable $PythonExecutable `
        -PolicyScriptPath $PolicyScriptPath `
        -Request @{
            operation = "publication-plan"
            repositoryRoot = $resolvedRepositoryRoot
            gradleProperties = [System.IO.File]::ReadAllText(
                $gradleProperties,
                [System.Text.Encoding]::UTF8
            )
            bundleLayoutContract = [System.IO.File]::ReadAllText(
                $bundleLayoutContract,
                [System.Text.Encoding]::UTF8
            )
            expectedOperatingSystemId = $ExpectedOperatingSystemId
            expectedArchitectureId = $ExpectedArchitectureId
            bundleClassifier = $BundleClassifier
        }
    return [pscustomobject]$response
}

function Resolve-FinGrindWindowsPublicationArtifactSet {
    param(
        [Parameter(Mandatory = $true)]
        [psobject]$Plan,

        [Parameter(Mandatory = $true)]
        [string]$PythonExecutable,

        [Parameter(Mandatory = $true)]
        [string]$PolicyScriptPath
    )

    $manifestPath = Resolve-FinGrindWindowsPublicationRepositoryFile `
        -RepositoryRoot $Plan.RepositoryRoot `
        -Path $Plan.ManifestPath `
        -Label "bundle archive manifest"
    $response = Invoke-FinGrindWindowsPublicationPolicy `
        -PythonExecutable $PythonExecutable `
        -PolicyScriptPath $PolicyScriptPath `
        -Request @{
            operation = "manifest-artifacts"
            plan = @{
                repositoryRoot = [string]$Plan.RepositoryRoot
                cliBuildDirectory = [string]$Plan.CliBuildDirectory
                manifestPath = [string]$Plan.ManifestPath
                archivePath = [string]$Plan.ArchivePath
                checksumPath = [string]$Plan.ChecksumPath
                projectVersion = [string]$Plan.ProjectVersion
                bundleClassifier = [string]$Plan.BundleClassifier
            }
            bundleArchiveManifest = [System.IO.File]::ReadAllText(
                $manifestPath,
                [System.Text.Encoding]::UTF8
            )
        }
    return [pscustomobject]@{
        ArchivePath = Resolve-FinGrindWindowsPublicationRepositoryFile `
            -RepositoryRoot $Plan.RepositoryRoot `
            -Path ([string]$response["archivePath"]) `
            -Label "bundle archive manifest archivePath"
        ChecksumPath = Resolve-FinGrindWindowsPublicationRepositoryFile `
            -RepositoryRoot $Plan.RepositoryRoot `
            -Path ([string]$response["checksumPath"]) `
            -Label "bundle archive manifest checksumPath"
    }
}

function Write-FinGrindWindowsPublicationWorkflowOutput {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Value,

        [Parameter(Mandatory = $true)]
        [string]$PythonExecutable,

        [Parameter(Mandatory = $true)]
        [string]$PolicyScriptPath
    )

    $response = Invoke-FinGrindWindowsPublicationPolicy `
        -PythonExecutable $PythonExecutable `
        -PolicyScriptPath $PolicyScriptPath `
        -Request @{
            operation = "workflow-output-line"
            name = $Name
            value = $Value
        }
    $line = $response["line"]
    if (-not ($line -is [string])) {
        throw "Windows publication policy response must declare workflow output line as one string"
    }
    [System.IO.File]::AppendAllText(
        $Path,
        $line,
        [System.Text.UTF8Encoding]::new($false)
    )
}
