$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Get-RepoUvExecutable {
    $propertiesPath = Join-Path $script:RepoRoot "gradle/fingrind-build.properties"
    $versionLine = Get-Content -LiteralPath $propertiesPath |
        Where-Object { $_ -match '^fingrindUvVersion=' } |
        Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($versionLine)) {
        Fail "missing fingrindUvVersion in $propertiesPath"
    }
    $requiredVersion = $versionLine.Split('=', 2)[1].Trim()
    $candidatePaths = [System.Collections.Generic.List[string]]::new()
    if (-not [string]::IsNullOrWhiteSpace($env:ORG_GRADLE_PROJECT_fingrindUvExecutable)) {
        $candidatePaths.Add($env:ORG_GRADLE_PROJECT_fingrindUvExecutable)
    }
    $pathUv = Get-Command uv -ErrorAction SilentlyContinue
    if ($null -ne $pathUv) {
        $candidatePaths.Add($pathUv.Source)
    }
    $userScripts = & python3 -c "import sysconfig; print(sysconfig.get_path('scripts', scheme=sysconfig.get_preferred_scheme('user')))"
    if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($userScripts)) {
        $candidatePaths.Add((Join-Path $userScripts.Trim() "uv.exe"))
    }
    foreach ($candidate in $candidatePaths | Select-Object -Unique) {
        if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            continue
        }
        $versionOutput = (& $candidate --version 2>&1 | Out-String).Trim()
        if ($versionOutput -eq "uv $requiredVersion" -or $versionOutput.StartsWith("uv $requiredVersion ")) {
            return $candidate
        }
    }
    Fail "the repo-owned Python tools require pinned uv $requiredVersion. Install it with: python -m pip install --user uv==$requiredVersion"
}

function Invoke-SharedBundleOfficeWorkerWorkflow {
    param(
        [Parameter(Mandatory = $true)]
        [string] $WorkRoot
    )

    $workflowScript = Join-Path $script:RepoRoot "scripts/release-smoke-workflow.py"
    $bridgeScript = Join-Path $script:RepoRoot "scripts/bundle-smoke-command-bridge.ps1"
    $requirementsFile = Join-Path $script:RepoRoot "requirements-release-smoke-workflow.txt"
    if (-not (Test-Path -LiteralPath $workflowScript -PathType Leaf)) {
        Fail "missing shared release smoke workflow runner at $workflowScript"
    }
    if (-not (Test-Path -LiteralPath $bridgeScript -PathType Leaf)) {
        Fail "missing bundle smoke command bridge at $bridgeScript"
    }
    if (-not (Test-Path -LiteralPath $requirementsFile -PathType Leaf)) {
        Fail "missing repo-owned Python tool requirements at $requirementsFile"
    }

    $powerShellExecutable = Get-FinGrindPowerShellExecutable
    $commandPrefixJson = ConvertTo-Json -Compress -Depth 4 @(
        $powerShellExecutable,
        "-NoLogo",
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-File", $script:BundleLauncher
    )
    $commandBridgePrefixJson = ConvertTo-Json -Compress -Depth 4 @(
        $powerShellExecutable,
        "-NoLogo",
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-File", $bridgeScript,
        $script:BundleLauncher
    )
    $releaseSmokeEnv = [ordered]@{
        FINGRIND_RELEASE_SMOKE_LABEL                        = "Bundle acceptance"
        FINGRIND_RELEASE_SMOKE_REPO_ROOT                    = $script:RepoRoot
        FINGRIND_RELEASE_SMOKE_COMMAND_PREFIX_JSON          = $commandPrefixJson
        FINGRIND_RELEASE_SMOKE_COMMAND_BRIDGE_PREFIX_JSON   = $commandBridgePrefixJson
        FINGRIND_RELEASE_SMOKE_COMMAND_ENV_DROP_JSON        = (ConvertTo-Json -Compress -Depth 4 @("JAVA_HOME"))
        FINGRIND_RELEASE_SMOKE_POWERSHELL_EXECUTABLE        = $powerShellExecutable
        FINGRIND_RELEASE_SMOKE_RUNTIME_DISTRIBUTION_KEY     = "bundleRuntimeDistribution"
        FINGRIND_RELEASE_SMOKE_EXPECT_LOADED_SQLITE_DETAILS = "true"
        FINGRIND_RELEASE_SMOKE_EXPECT_BUNDLE_HOME_PROPERTY  = "true"
        FINGRIND_RELEASE_SMOKE_WORK_ROOT                    = $WorkRoot
        FINGRIND_RELEASE_SMOKE_ARGUMENT_PATH_MODE           = "absolute"
        FINGRIND_RELEASE_SMOKE_SCENARIO_ID                  = "bundle-acceptance"
        FINGRIND_RELEASE_SMOKE_BOOK_KEY_OUTPUT_PERMISSIONS  = "owner-only-acl"
        FINGRIND_RELEASE_SMOKE_OPEN_BOOK_MODE               = "book-key-file"
        FINGRIND_RELEASE_SMOKE_NATIVE_SQLITE_PROBE_CLASSPATH = (Join-Path $script:BundleRoot "lib/release-smoke/native-sqlite-format-boundary-probe.jar")
    }
    $priorValues = @{}

    try {
        foreach ($entry in $releaseSmokeEnv.GetEnumerator()) {
            $envPath = "Env:$($entry.Key)"
            if (Test-Path -Path $envPath) {
                $priorValues[$entry.Key] = (Get-Item -Path $envPath).Value
            }
            Set-Item -Path $envPath -Value ([string] $entry.Value)
        }

        $uvExecutable = Get-RepoUvExecutable
        & $uvExecutable run --no-project --python python3 --with-requirements $requirementsFile python $workflowScript
        if ($LASTEXITCODE -ne 0) {
            Fail "shared release smoke workflow failed"
        }
    }
    finally {
        foreach ($name in $releaseSmokeEnv.Keys) {
            $envPath = "Env:$name"
            if ($priorValues.ContainsKey($name)) {
                Set-Item -Path $envPath -Value $priorValues[$name]
            } else {
                Remove-Item -Path $envPath -ErrorAction SilentlyContinue
            }
        }
    }
}
