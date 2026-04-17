$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Fail([string] $Message) {
    throw $Message
}

function ProjectVersion {
    $versionLine = Get-Content -Path (Join-Path $script:RepoRoot "gradle.properties") |
        Where-Object { $_ -match '^version=' } |
        Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($versionLine)) {
        Fail "could not determine project version from gradle.properties"
    }
    return $versionLine.Split('=', 2)[1].Trim()
}

function CurrentUtcDate {
    return [DateTime]::UtcNow.ToString("yyyy-MM-dd")
}

function Write-Utf8NoBomFile {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Path,
        [Parameter(Mandatory = $true, ValueFromPipeline = $true)]
        [AllowEmptyString()]
        [string] $Content
    )

    process {
        $encoding = New-Object -TypeName System.Text.UTF8Encoding -ArgumentList $false
        [System.IO.File]::WriteAllText($Path, $Content, $encoding)
    }
}

function Invoke-BundleCommand {
    param(
        [string[]] $Arguments,
        [switch] $AllowFailure
    )

    $originalSqliteLibrary = $env:FINGRIND_SQLITE_LIBRARY
    $originalJavaHome = $env:JAVA_HOME
    try {
        Remove-Item Env:FINGRIND_SQLITE_LIBRARY -ErrorAction SilentlyContinue
        Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue

        $output = & $script:BundleLauncher @Arguments 2>&1 | Out-String
        $exitCode = $LASTEXITCODE
    } finally {
        if ($null -ne $originalSqliteLibrary) {
            $env:FINGRIND_SQLITE_LIBRARY = $originalSqliteLibrary
        } else {
            Remove-Item Env:FINGRIND_SQLITE_LIBRARY -ErrorAction SilentlyContinue
        }
        if ($null -ne $originalJavaHome) {
            $env:JAVA_HOME = $originalJavaHome
        } else {
            Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
        }
    }

    if (-not $AllowFailure -and $exitCode -ne 0) {
        Fail "bundle command failed with exit code $exitCode`n$output"
    }

    return [pscustomobject]@{
        Output   = $output -replace "`r", ""
        ExitCode = $exitCode
    }
}

$script:RepoRoot = Split-Path -Path $PSScriptRoot -Parent
$expectedArchiveName = "fingrind-$(ProjectVersion)-windows-x86_64.zip"
$bundleArchivePath = if ($args.Count -gt 0) { $args[0] } else { Join-Path $script:RepoRoot "cli/build/distributions/$expectedArchiveName" }
$bundleArchivePath = [System.IO.Path]::GetFullPath($bundleArchivePath)
$bundleChecksumPath = "$bundleArchivePath.sha256"

if (-not (Test-Path -Path $bundleArchivePath -PathType Leaf)) {
    Fail "missing bundle archive at $bundleArchivePath"
}
if (-not (Test-Path -Path $bundleChecksumPath -PathType Leaf)) {
    Fail "missing bundle checksum file at $bundleChecksumPath"
}

$checksumLine = Get-Content -Path $bundleChecksumPath | Select-Object -First 1
$checksumTokens = $checksumLine -split '\s+', 3
if ($checksumTokens.Count -lt 2) {
    Fail "invalid bundle checksum file at $bundleChecksumPath"
}
$expectedArchiveSha256 = $checksumTokens[0]
$expectedArchiveNameFromChecksum = $checksumTokens[1].TrimStart('*')
if ($expectedArchiveNameFromChecksum -ne [System.IO.Path]::GetFileName($bundleArchivePath)) {
    Fail "bundle checksum file $bundleChecksumPath does not match archive $(Split-Path -Leaf $bundleArchivePath)"
}

$actualArchiveSha256 = (Get-FileHash -Path $bundleArchivePath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualArchiveSha256 -ne $expectedArchiveSha256.ToLowerInvariant()) {
    Fail "bundle archive checksum mismatch for $bundleArchivePath"
}

$smokeRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("fingrind-bundle-smoke.{0}" -f [guid]::NewGuid().ToString("N"))
$extractRoot = Join-Path $smokeRoot "extract"
$workRoot = Join-Path $smokeRoot "workspace odd"
$script:BundleLauncher = $null

try {
    New-Item -ItemType Directory -Path $extractRoot -Force | Out-Null
    New-Item -ItemType Directory -Path $workRoot -Force | Out-Null

    Expand-Archive -Path $bundleArchivePath -DestinationPath $extractRoot -Force
    $extractedRoots = @(Get-ChildItem -Path $extractRoot -Directory)
    if ($extractedRoots.Count -ne 1) {
        Fail "expected exactly one extracted bundle root under $extractRoot"
    }

    $bundleRoot = $extractedRoots[0].FullName
    $script:BundleLauncher = Join-Path $bundleRoot "bin/fingrind.cmd"
    $runtimeJava = Join-Path $bundleRoot "runtime/bin/java.exe"
    $applicationJar = Join-Path $bundleRoot "lib/app/fingrind.jar"
    $nativeLibrary = Join-Path $bundleRoot "lib/native/sqlite3.dll"

    foreach ($path in @(
        $script:BundleLauncher,
        $runtimeJava,
        $applicationJar,
        $nativeLibrary,
        (Join-Path $bundleRoot "LICENSE"),
        (Join-Path $bundleRoot "LICENSE-APACHE-2.0"),
        (Join-Path $bundleRoot "LICENSE-SQLITE3MULTIPLECIPHERS"),
        (Join-Path $bundleRoot "NOTICE"),
        (Join-Path $bundleRoot "PATENTS.md"),
        (Join-Path $bundleRoot "README.md"),
        (Join-Path $bundleRoot "bundle-manifest.json")
    )) {
        if (-not (Test-Path -Path $path -PathType Leaf)) {
            Fail "missing bundle file at $path"
        }
    }

    $bundleReadme = Get-Content -Path (Join-Path $bundleRoot "README.md") -Raw
    if (-not $bundleReadme.StartsWith("# FinGrind ")) {
        Fail "bundle README did not start with the FinGrind title"
    }
    if ($bundleReadme -notmatch 'bundle-manifest\.json') {
        Fail "bundle README did not mention the machine-readable bundle manifest"
    }

    $bundleManifest = Get-Content -Path (Join-Path $bundleRoot "bundle-manifest.json") -Raw | ConvertFrom-Json
    if ($bundleManifest.runtimeDistribution -ne "self-contained-bundle") {
        Fail "bundle manifest did not report the self-contained runtime distribution"
    }
    if ($bundleManifest.archiveFormat -ne "zip") {
        Fail "bundle manifest did not report zip as the Windows archive format"
    }
    if ($bundleManifest.bundleTarget.classifier -ne "windows-x86_64") {
        Fail "bundle manifest did not report the current host classifier"
    }
    if ($bundleManifest.launcher -ne "bin/fingrind.cmd") {
        Fail "bundle manifest did not report the Windows launcher path"
    }
    if ($bundleManifest.supportedPublicCliBundleTargets -notcontains "windows-x86_64") {
        Fail "bundle manifest did not report the supported public bundle targets"
    }
    $unsupportedBundleOperatingSystems = @($bundleManifest.unsupportedPublicCliOperatingSystems)
    if ($unsupportedBundleOperatingSystems.Count -ne 0) {
        Fail "bundle manifest still reported unsupported public operating systems"
    }

    $runtimeVersionOutput = & $runtimeJava --version | Out-String
    if ($runtimeVersionOutput -notmatch '^openjdk 26 ') {
        Fail "bundled Java runtime did not report Java 26"
    }
    $runtimeModulesOutput = (& $runtimeJava --list-modules | Out-String) -replace "`r", ""
    foreach ($forbiddenModule in @('jdk.jlink@', 'jdk.jpackage@', 'jdk.jdeps@')) {
        if ($runtimeModulesOutput -match ('^' + [regex]::Escape($forbiddenModule))) {
            Fail "bundled Java runtime still contains $forbiddenModule"
        }
    }

    $requestPath = Join-Path $workRoot "requests odd/request [bundle #smoke].json"
    $declareCashPath = Join-Path $workRoot "requests odd/declare account cash [bundle #smoke].json"
    $declareRevenuePath = Join-Path $workRoot "requests odd/declare account revenue [bundle #smoke].json"
    $bookPath = Join-Path $workRoot "books odd/nested/entity [bundle #smoke].sqlite"
    $bookKeyPath = Join-Path $workRoot "books odd/nested/entity [bundle #smoke].key"
    $wrongBookKeyPath = Join-Path $workRoot "books odd/nested/entity [bundle #smoke]-wrong.key"

    New-Item -ItemType Directory -Path (Split-Path $requestPath -Parent) -Force | Out-Null
    New-Item -ItemType Directory -Path (Split-Path $bookPath -Parent) -Force | Out-Null

    @"
{
  "effectiveDate": "$(CurrentUtcDate)",
  "lines": [
    {
      "accountCode": "1000",
      "side": "DEBIT",
      "currencyCode": "EUR",
      "amount": "10.00"
    },
    {
      "accountCode": "2000",
      "side": "CREDIT",
      "currencyCode": "EUR",
      "amount": "10.00"
    }
  ],
  "provenance": {
    "actorId": "bundle-smoke",
    "actorType": "AGENT",
    "commandId": "bundle-smoke-command",
    "idempotencyKey": "bundle-smoke-idem",
    "causationId": "bundle-smoke-cause"
  }
}
"@ | Write-Utf8NoBomFile -Path $requestPath

    @"
{
  "accountCode": "1000",
  "accountName": "Cash",
  "normalBalance": "DEBIT"
}
"@ | Write-Utf8NoBomFile -Path $declareCashPath

    @"
{
  "accountCode": "2000",
  "accountName": "Revenue",
  "normalBalance": "CREDIT"
}
"@ | Write-Utf8NoBomFile -Path $declareRevenuePath

    Write-Host "Bundle smoke: verifying version command"
    $versionPayload = (Invoke-BundleCommand -Arguments @("version")).Output | ConvertFrom-Json
    if ($versionPayload.status -ne "ok") {
        Fail "version output did not report ok status"
    }
    if ($versionPayload.payload.application -ne "FinGrind") {
        Fail "version output did not include application name"
    }

    Write-Host "Bundle smoke: verifying self-contained runtime contract"
    $capabilitiesPayload = (Invoke-BundleCommand -Arguments @("capabilities")).Output | ConvertFrom-Json
    if ($capabilitiesPayload.payload.environment.runtimeDistribution -ne "self-contained-bundle") {
        Fail "capabilities output did not report the self-contained runtime distribution"
    }
    if ($capabilitiesPayload.payload.environment.publicCliDistribution -ne "self-contained-bundle") {
        Fail "capabilities output did not report the public bundle distribution contract"
    }
    if ($capabilitiesPayload.payload.environment.supportedPublicCliBundleTargets -notcontains "windows-x86_64") {
        Fail "capabilities output did not report the supported public bundle targets"
    }
    $unsupportedCapabilityOperatingSystems =
        @($capabilitiesPayload.payload.environment.unsupportedPublicCliOperatingSystems)
    if ($unsupportedCapabilityOperatingSystems.Count -ne 0) {
        Fail "capabilities output still reported unsupported public operating systems"
    }
    if ($capabilitiesPayload.payload.environment.sqliteLibraryMode -ne "managed-only") {
        Fail "capabilities output did not report the managed-only SQLite runtime mode"
    }
    if ($capabilitiesPayload.payload.environment.storageDriver -ne "sqlite-ffm-sqlite3mc") {
        Fail "capabilities output did not report the SQLite3 Multiple Ciphers storage driver"
    }
    if ($capabilitiesPayload.payload.environment.bookProtectionMode -ne "required") {
        Fail "capabilities output did not report required book protection"
    }
    if ($capabilitiesPayload.payload.environment.defaultBookCipher -ne "chacha20") {
        Fail "capabilities output did not report the default chacha20 cipher"
    }
    if ($capabilitiesPayload.payload.environment.requiredMinimumSqliteVersion -ne "3.53.0") {
        Fail "capabilities output did not report the required SQLite 3.53.0 minimum"
    }
    if ($capabilitiesPayload.payload.environment.requiredSqlite3mcVersion -ne "2.3.3") {
        Fail "capabilities output did not report the required SQLite3 Multiple Ciphers 2.3.3 version"
    }
    if ($capabilitiesPayload.payload.environment.sqliteRuntimeStatus -ne "ready") {
        Fail "capabilities output did not report a ready SQLite runtime"
    }

    Write-Host "Bundle smoke: generating a dedicated book key file"
    $generateKeyPayload =
        (Invoke-BundleCommand -Arguments @("generate-book-key-file", "--book-key-file", $bookKeyPath)).Output |
            ConvertFrom-Json
    if ($generateKeyPayload.payload.permissions -ne "owner-only-acl") {
        Fail "generate-book-key-file did not report Windows owner-only ACL protection"
    }
    if (-not (Test-Path -Path $bookKeyPath -PathType Leaf)) {
        Fail "generate-book-key-file did not create the expected key file"
    }

    Write-Host "Bundle smoke: verifying explicit book initialization"
    $openBookPayload =
        (Invoke-BundleCommand -Arguments @("open-book", "--book-file", $bookPath, "--book-key-file", $bookKeyPath)).Output |
            ConvertFrom-Json
    if ($openBookPayload.status -ne "ok") {
        Fail "open-book did not report ok status"
    }

    Write-Host "Bundle smoke: verifying account declaration and registry listing"
    foreach ($declarePath in @($declareCashPath, $declareRevenuePath)) {
        $declarePayload =
            (Invoke-BundleCommand -Arguments @(
                    "declare-account",
                    "--book-file", $bookPath,
                    "--book-key-file", $bookKeyPath,
                    "--request-file", $declarePath
                )).Output | ConvertFrom-Json
        if ($declarePayload.status -ne "ok") {
            Fail "declare-account did not report ok status"
        }
    }

    $listAccountsPayload =
        (Invoke-BundleCommand -Arguments @("list-accounts", "--book-file", $bookPath, "--book-key-file", $bookKeyPath)).Output |
            ConvertFrom-Json
    if ($listAccountsPayload.status -ne "ok") {
        Fail "list-accounts did not report ok status"
    }
    $listedAccounts = @($listAccountsPayload.payload.accounts)
    if ($listedAccounts.Count -ne 2) {
        Fail "list-accounts did not report the declared account set"
    }

    Write-Host "Bundle smoke: verifying wrong key is rejected deterministically"
    Invoke-BundleCommand -Arguments @("generate-book-key-file", "--book-key-file", $wrongBookKeyPath) | Out-Null
    "definitely-wrong-bundle-smoke-passphrase" | Write-Utf8NoBomFile -Path $wrongBookKeyPath
    $wrongKeyResult =
        Invoke-BundleCommand -Arguments @("list-accounts", "--book-file", $bookPath, "--book-key-file", $wrongBookKeyPath) -AllowFailure
    if ($wrongKeyResult.ExitCode -eq 0) {
        Fail "wrong key unexpectedly succeeded"
    }
    $wrongKeyPayload = $wrongKeyResult.Output | ConvertFrom-Json
    if ($wrongKeyPayload.error.code -ne "runtime-failure") {
        Fail "wrong key failure did not map to runtime-failure"
    }

    Write-Host "Bundle smoke: verifying preflight and commit"
    $preflightPayload =
        (Invoke-BundleCommand -Arguments @(
                "preflight-entry",
                "--book-file", $bookPath,
                "--book-key-file", $bookKeyPath,
                "--request-file", $requestPath
            )).Output | ConvertFrom-Json
    if ($preflightPayload.status -ne "preflight-accepted") {
        Fail "preflight-entry did not report preflight-accepted"
    }

    $postPayload =
        (Invoke-BundleCommand -Arguments @(
                "post-entry",
                "--book-file", $bookPath,
                "--book-key-file", $bookKeyPath,
                "--request-file", $requestPath
            )).Output | ConvertFrom-Json
    if ($postPayload.status -ne "committed") {
        Fail "post-entry did not report committed"
    }

    Write-Host "Bundle smoke: success"
} finally {
    if (Test-Path -Path $smokeRoot) {
        Remove-Item -Path $smokeRoot -Recurse -Force
    }
}
