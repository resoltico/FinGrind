$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Invoke-SharedBundleOfficeWorkerWorkflow {
    param(
        [Parameter(Mandatory = $true)]
        [string] $WorkRoot
    )

    $workflowScript = Join-Path $script:RepoRoot "scripts/release-smoke-workflow.py"
    if (-not (Test-Path -LiteralPath $workflowScript -PathType Leaf)) {
        Fail "missing shared release smoke workflow runner at $workflowScript"
    }

    $commandPrefixJson = ConvertTo-Json -Compress @(
        "pwsh",
        "-NoLogo",
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-File", $script:BundleLauncher
    )
    $releaseSmokeEnv = [ordered]@{
        FINGRIND_RELEASE_SMOKE_LABEL                        = "Bundle acceptance"
        FINGRIND_RELEASE_SMOKE_REPO_ROOT                    = $script:RepoRoot
        FINGRIND_RELEASE_SMOKE_COMMAND_PREFIX_JSON          = $commandPrefixJson
        FINGRIND_RELEASE_SMOKE_COMMAND_ENV_DROP_JSON        = (ConvertTo-Json -Compress @("FINGRIND_SQLITE_LIBRARY", "JAVA_HOME"))
        FINGRIND_RELEASE_SMOKE_RUNTIME_DISTRIBUTION_KEY     = "bundleRuntimeDistribution"
        FINGRIND_RELEASE_SMOKE_EXPECT_LOADED_SQLITE_DETAILS = "true"
        FINGRIND_RELEASE_SMOKE_EXPECT_BUNDLE_HOME_PROPERTY  = "true"
        FINGRIND_RELEASE_SMOKE_WORK_ROOT                    = $WorkRoot
        FINGRIND_RELEASE_SMOKE_ARGUMENT_PATH_MODE           = "absolute"
        FINGRIND_RELEASE_SMOKE_SCENARIO_ID                  = "bundle-acceptance"
        FINGRIND_RELEASE_SMOKE_BOOK_KEY_OUTPUT_PERMISSIONS  = "owner-only-acl"
        FINGRIND_RELEASE_SMOKE_OPEN_BOOK_MODE               = "book-key-file"
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

        & python3 $workflowScript
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
