$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Assert-BundleArchiveContract {
    param(
        [Parameter(Mandatory = $true)]
        [string] $BundleRoot,
        [Parameter(Mandatory = $true)]
        [pscustomobject] $HostBundleTarget
    )

    $runtimeJava = Join-Path $BundleRoot "runtime/bin/java.exe"
    $applicationJar = Join-Path $BundleRoot "lib/app/fingrind.jar"
    $nativeLibrary = Join-Path $BundleRoot "lib/native/$($HostBundleTarget.sqliteLibraryFileName)"

    foreach ($path in @(
        $script:BundleLauncher,
        $runtimeJava,
        $applicationJar,
        $nativeLibrary,
        (Join-Path $BundleRoot "LICENSE"),
        (Join-Path $BundleRoot "LICENSE-APACHE-2.0"),
        (Join-Path $BundleRoot "LICENSE-SIL-OFL-1.1"),
        (Join-Path $BundleRoot "LICENSE-SQLITE3MULTIPLECIPHERS"),
        (Join-Path $BundleRoot "NOTICE"),
        (Join-Path $BundleRoot "PATENTS.md"),
        (Join-Path $BundleRoot "README.md"),
        (Join-Path $BundleRoot "bundle-manifest.json")
    )) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            Fail "missing bundle file at $path"
        }
    }

    $bundleReadme = Get-Content -LiteralPath (Join-Path $BundleRoot "README.md") -Raw
    if (-not $bundleReadme.StartsWith("# FinGrind ")) {
        Fail "bundle README did not start with the FinGrind title"
    }
    if ($bundleReadme -notmatch 'bundle-manifest\.json') {
        Fail "bundle README did not mention the machine-readable bundle manifest"
    }

    $bundleManifest = Get-Content -LiteralPath (Join-Path $BundleRoot "bundle-manifest.json") -Raw | ConvertFrom-Json
    if ($bundleManifest.runtimeDistribution -ne $script:ContractValues.runtimeSurface.bundleRuntimeDistribution) {
        Fail "bundle manifest did not report the self-contained runtime distribution"
    }
    if ($bundleManifest.archiveFormat -ne $HostBundleTarget.archiveFormat) {
        Fail "bundle manifest did not report the platform-native archive format"
    }
    if ($bundleManifest.bundleTarget.classifier -ne $HostBundleTarget.classifier) {
        Fail "bundle manifest did not report the current host classifier"
    }
    if ($bundleManifest.launcher -ne $HostBundleTarget.launcherPath) {
        Fail "bundle manifest did not report the canonical launcher path"
    }
    if (-not (Test-SameSequence `
            -Reference @($script:ContractValues.publicDistribution.supportedPublicCliBundleTargets) `
            -Actual @($bundleManifest.supportedPublicCliBundleTargets))) {
        Fail "bundle manifest did not report the supported public bundle targets"
    }
    if (-not (Test-SameSequence `
            -Reference @($script:ContractValues.publicDistribution.unsupportedPublicCliBundleTargets) `
            -Actual @($bundleManifest.unsupportedPublicCliBundleTargets))) {
        Fail "bundle manifest still reported unsupported public bundle targets"
    }
    if ($bundleManifest.publicCliDistribution -ne $script:ContractValues.runtimeSurface.publicCliDistribution) {
        Fail "bundle manifest did not report the public bundle distribution contract"
    }
    if ($bundleManifest.managedSqlite.storageDriver -ne $script:ContractValues.runtimeSurface.storageDriver) {
        Fail "bundle manifest did not report the canonical storage driver"
    }
    if ($bundleManifest.managedSqlite.storageEngine -ne $script:ContractValues.runtimeSurface.storageEngine) {
        Fail "bundle manifest did not report the canonical storage engine"
    }
    if ($bundleManifest.managedSqlite.bookProtectionMode -ne $script:ContractValues.runtimeSurface.bookProtectionMode) {
        Fail "bundle manifest did not report the canonical book protection mode"
    }
    if ($bundleManifest.managedSqlite.defaultBookCipher -ne $script:ContractValues.runtimeSurface.defaultBookCipher) {
        Fail "bundle manifest did not report the canonical default book cipher"
    }
    if ($bundleManifest.managedSqlite.libraryMode -ne $script:ContractValues.runtimeSurface.sqliteLibraryMode) {
        Fail "bundle manifest did not report the canonical SQLite library mode"
    }
    if (
        $bundleManifest.managedSqlite.requiredMinimumSqliteVersion -ne
        $script:ContractValues.managedSqlite.requiredMinimumSqliteVersion
    ) {
        Fail "bundle manifest did not report the canonical minimum SQLite version"
    }
    if (
        $bundleManifest.managedSqlite.requiredSqlite3mcVersion -ne
        $script:ContractValues.managedSqlite.requiredSqlite3mcVersion
    ) {
        Fail "bundle manifest did not report the canonical SQLite3 Multiple Ciphers version"
    }
    if (
        $bundleManifest.managedSqlite.requiredSqliteSourceId -ne
        $script:ContractValues.managedSqlite.requiredSqliteSourceId
    ) {
        Fail "bundle manifest did not report the canonical SQLite source id"
    }
    if (-not (Test-SameSequence `
            -Reference @($script:ContractValues.managedSqlite.requiredCompileOptions) `
            -Actual @($bundleManifest.managedSqlite.requiredCompileOptions))) {
        Fail "bundle manifest did not report the canonical SQLite compile options"
    }
    if ($bundleManifest.bootstrap.recommendedFirstCommand[-1] -ne $script:ContractValues.operationIds.help) {
        Fail "bundle manifest did not publish the canonical bootstrap help command"
    }
    if ($bundleManifest.bootstrap.machineReadableContractCommand[-1] -ne $script:ContractValues.operationIds.capabilities) {
        Fail "bundle manifest did not publish the canonical machine-readable contract command"
    }
    if ($bundleManifest.bootstrap.requestTemplateCommand[-1] -ne $script:ContractValues.operationIds.printRequestTemplate) {
        Fail "bundle manifest did not publish the canonical request-template bootstrap command"
    }
    if ($bundleManifest.bootstrap.planTemplateCommand[-1] -ne $script:ContractValues.operationIds.printPlanTemplate) {
        Fail "bundle manifest did not publish the canonical plan-template bootstrap command"
    }

    Require-JavaRuntimeVersion $runtimeJava $script:ContractValues.runtimeEnvironment.sourceCheckoutJava
    $runtimeModulesOutput = (& $runtimeJava --list-modules | Out-String) -replace "`r", ""
    foreach ($forbiddenModule in @('jdk.jlink@', 'jdk.jpackage@', 'jdk.jdeps@')) {
        if ($runtimeModulesOutput -match ('^' + [regex]::Escape($forbiddenModule))) {
            Fail "bundled Java runtime still contains $forbiddenModule"
        }
    }
}

function Assert-BundleCapabilitiesContract {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject] $CapabilitiesPayload
    )

    $environment = $CapabilitiesPayload.payload.environment
    $distribution = $environment.distribution
    $storage = $environment.storage
    $sqlite = $environment.sqlite
    if ($distribution.runtimeDistribution -ne $script:ContractValues.runtimeSurface.bundleRuntimeDistribution) {
        Fail "capabilities output did not report the self-contained runtime distribution"
    }
    if ($distribution.publicCliDistribution -ne $script:ContractValues.runtimeSurface.publicCliDistribution) {
        Fail "capabilities output did not report the public bundle distribution contract"
    }
    if (-not (Test-SameSequence `
            -Reference @($script:ContractValues.publicDistribution.supportedPublicCliBundleTargets) `
            -Actual @($distribution.supportedPublicCliBundleTargets))) {
        Fail "capabilities output did not report the supported public bundle targets"
    }
    if (-not (Test-SameSequence `
            -Reference @($script:ContractValues.publicDistribution.unsupportedPublicCliBundleTargets) `
            -Actual @($distribution.unsupportedPublicCliBundleTargets))) {
        Fail "capabilities output still reported unsupported public bundle targets"
    }
    if ($sqlite.libraryMode -ne $script:ContractValues.runtimeSurface.sqliteLibraryMode) {
        Fail "capabilities output did not report the managed-only SQLite runtime mode"
    }
    if ($storage.storageDriver -ne $script:ContractValues.runtimeSurface.storageDriver) {
        Fail "capabilities output did not report the SQLite3 Multiple Ciphers storage driver"
    }
    if ($storage.bookProtectionMode -ne $script:ContractValues.runtimeSurface.bookProtectionMode) {
        Fail "capabilities output did not report required book protection"
    }
    if ($storage.defaultProtectedBookFormat.cipher -ne $script:ContractValues.protectedBookFormat.cipher) {
        Fail "capabilities output did not report the canonical default book cipher"
    }
    if ($storage.defaultProtectedBookFormat.legacyMode -ne $script:ContractValues.protectedBookFormat.legacyMode) {
        Fail "capabilities output did not report the canonical legacy-mode flag"
    }
    if ($storage.defaultProtectedBookFormat.pageSize -ne $script:ContractValues.protectedBookFormat.pageSize) {
        Fail "capabilities output did not report the canonical protected-book page size"
    }
    if ($storage.defaultProtectedBookFormat.reservedBytes -ne $script:ContractValues.protectedBookFormat.reservedBytes) {
        Fail "capabilities output did not report the canonical protected-book reserved bytes"
    }
    if ($sqlite.requiredMinimumSqliteVersion -ne $script:ContractValues.managedSqlite.requiredMinimumSqliteVersion) {
        Fail "capabilities output did not report the canonical minimum SQLite version"
    }
    if ($sqlite.requiredSqlite3mcVersion -ne $script:ContractValues.managedSqlite.requiredSqlite3mcVersion) {
        Fail "capabilities output did not report the canonical SQLite3 Multiple Ciphers version"
    }
    if ($sqlite.requiredSqliteSourceId -ne $script:ContractValues.managedSqlite.requiredSqliteSourceId) {
        Fail "capabilities output did not report the canonical SQLite source id requirement"
    }
    if ($sqlite.runtimeStatus -ne "ready") {
        Fail "capabilities output did not report a ready SQLite runtime"
    }
    if ($sqlite.runtimeProvenance -ne "bundle-managed") {
        Fail "capabilities output did not report bundle-managed SQLite provenance"
    }
    if ([string]::IsNullOrWhiteSpace($sqlite.loadedLibraryPath)) {
        Fail "capabilities output did not report the loaded SQLite library path"
    }
    if ($sqlite.loadedSqliteSourceId -ne $script:ContractValues.managedSqlite.requiredSqliteSourceId) {
        Fail "capabilities output did not report the canonical loaded SQLite source id"
    }
    if (-not (Test-SameSequence `
            -Reference @($script:ContractValues.managedSqlite.requiredCompileOptions) `
            -Actual @($sqlite.requiredCompileOptions))) {
        Fail "capabilities output did not report the canonical SQLite compile options"
    }
    if ($sqlite.compileOptionsVerification -ne "verified") {
        Fail "capabilities output did not report verified SQLite compile-option enforcement"
    }
    $queryCommands = @($CapabilitiesPayload.payload.commands.query)
    $queryCommandsByName = @{}
    foreach ($commandDescriptor in $queryCommands) {
        $queryCommandsByName[$commandDescriptor.name] = $commandDescriptor
    }
    foreach ($command in @("trial-balance", "account-ledger", "period-summary")) {
        if (-not $queryCommandsByName.ContainsKey($command)) {
            Fail "capabilities output did not report query command $command"
        }
    }
    if ($CapabilitiesPayload.payload.requestInput.outputOption -ne "--output") {
        Fail "capabilities output did not report the canonical --output selector"
    }
    foreach ($command in @("trial-balance", "account-ledger", "period-summary")) {
        $outputModes = @($queryCommandsByName[$command].outputModes)
        if (($outputModes -join ",") -ne "json,human,csv") {
            Fail "$command did not report json,human,csv stdout modes"
        }
    }
    $trialBalanceArtifactOutputs = @($queryCommandsByName["trial-balance"].artifactOutputs)
    if ($trialBalanceArtifactOutputs.Count -ne 1 `
        -or $trialBalanceArtifactOutputs[0].format -ne "pdf" `
        -or $trialBalanceArtifactOutputs[0].option -ne "--pdf-out <path>") {
        Fail "trial-balance did not report the canonical PDF artifact contract"
    }
    $errorCodes = @($CapabilitiesPayload.payload.responseModel.errorDescriptors | ForEach-Object { $_.code })
    foreach ($code in @("invalid-page-cursor", "interactive-prompt-unavailable", "book-authentication-failed")) {
        if ($errorCodes -notcontains $code) {
            Fail "capabilities output did not report error descriptor $code"
        }
    }
}
