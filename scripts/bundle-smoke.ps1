$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Fail([string] $Message) {
    throw $Message
}

function Require-Match {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Text,
        [Parameter(Mandatory = $true)]
        [string] $Pattern,
        [Parameter(Mandatory = $true)]
        [string] $Message
    )

    if ($Text -notmatch $Pattern) {
        Fail $Message
    }
}

function Require-NoMatch {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Text,
        [Parameter(Mandatory = $true)]
        [string] $Pattern,
        [Parameter(Mandatory = $true)]
        [string] $Message
    )

    if ($Text -match $Pattern) {
        Fail $Message
    }
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
        $outputLines = & $script:BundleLauncher @Arguments 2>&1 | ForEach-Object { $_.ToString() }
        $output = ($outputLines | Where-Object { -not [string]::IsNullOrEmpty($_) }) -join "`n"
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

if (-not (Test-Path -LiteralPath $bundleArchivePath -PathType Leaf)) {
    Fail "missing bundle archive at $bundleArchivePath"
}
if (-not (Test-Path -LiteralPath $bundleChecksumPath -PathType Leaf)) {
    Fail "missing bundle checksum file at $bundleChecksumPath"
}

$checksumLine = Get-Content -LiteralPath $bundleChecksumPath | Select-Object -First 1
$checksumTokens = $checksumLine -split '\s+', 3
if ($checksumTokens.Count -lt 2) {
    Fail "invalid bundle checksum file at $bundleChecksumPath"
}
$expectedArchiveSha256 = $checksumTokens[0]
$expectedArchiveNameFromChecksum = $checksumTokens[1].TrimStart('*')
if ($expectedArchiveNameFromChecksum -ne [System.IO.Path]::GetFileName($bundleArchivePath)) {
    Fail "bundle checksum file $bundleChecksumPath does not match archive $([System.IO.Path]::GetFileName($bundleArchivePath))"
}

$actualArchiveSha256 = (Get-FileHash -LiteralPath $bundleArchivePath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualArchiveSha256 -ne $expectedArchiveSha256.ToLowerInvariant()) {
    Fail "bundle archive checksum mismatch for $bundleArchivePath"
}

$smokeRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("fingrind-bundle-acceptance.{0}" -f [guid]::NewGuid().ToString("N"))
$extractRoot = Join-Path $smokeRoot "extract"
$workRoot = Join-Path $smokeRoot "workspace odd/Rīga büro/2026 Q2 close"
$script:BundleLauncher = $null

try {
    New-Item -ItemType Directory -Path $extractRoot -Force | Out-Null
    New-Item -ItemType Directory -Path $workRoot -Force | Out-Null

    Expand-Archive -LiteralPath $bundleArchivePath -DestinationPath $extractRoot -Force
    $extractedRoots = @(Get-ChildItem -LiteralPath $extractRoot -Directory)
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
        (Join-Path $bundleRoot "LICENSE-SIL-OFL-1.1"),
        (Join-Path $bundleRoot "LICENSE-SQLITE3MULTIPLECIPHERS"),
        (Join-Path $bundleRoot "NOTICE"),
        (Join-Path $bundleRoot "PATENTS.md"),
        (Join-Path $bundleRoot "README.md"),
        (Join-Path $bundleRoot "bundle-manifest.json")
    )) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            Fail "missing bundle file at $path"
        }
    }

    $bundleReadme = Get-Content -LiteralPath (Join-Path $bundleRoot "README.md") -Raw
    if (-not $bundleReadme.StartsWith("# FinGrind ")) {
        Fail "bundle README did not start with the FinGrind title"
    }
    if ($bundleReadme -notmatch 'bundle-manifest\.json') {
        Fail "bundle README did not mention the machine-readable bundle manifest"
    }

    $bundleManifest = Get-Content -LiteralPath (Join-Path $bundleRoot "bundle-manifest.json") -Raw | ConvertFrom-Json
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
    if (@($bundleManifest.unsupportedPublicCliOperatingSystems).Count -ne 0) {
        Fail "bundle manifest still reported unsupported public operating systems"
    }

    $runtimeVersionOutput = (& $runtimeJava --version | Out-String) -replace "`r", ""
    if ($runtimeVersionOutput -notmatch '^openjdk 26 ') {
        Fail "bundled Java runtime did not report Java 26"
    }
    $runtimeModulesOutput = (& $runtimeJava --list-modules | Out-String) -replace "`r", ""
    foreach ($forbiddenModule in @('jdk.jlink@', 'jdk.jpackage@', 'jdk.jdeps@')) {
        if ($runtimeModulesOutput -match ('^' + [regex]::Escape($forbiddenModule))) {
            Fail "bundled Java runtime still contains $forbiddenModule"
        }
    }

    $requestSalePath = Join-Path $workRoot "requests odd\-sale bundle acceptance.json"
    $requestAdjustmentPath = Join-Path $workRoot "requests odd\adjustment bundle acceptance.json"
    $invalidRequestPath = Join-Path $workRoot "requests odd\bad fields bundle acceptance.json"
    $declareCashPath = Join-Path $workRoot "requests odd\declare account cash bundle acceptance.json"
    $declareRevenuePath = Join-Path $workRoot "requests odd\declare account revenue bundle acceptance.json"
    $bookPath = Join-Path $workRoot "books odd\nested\-entity bundle acceptance.sqlite"
    $bookKeyPath = Join-Path $workRoot "keys odd\nested\-entity bundle acceptance.key"
    $replacementBookKeyPath = Join-Path $workRoot "keys odd\nested\-entity bundle acceptance replacement.key"
    $wrongBookKeyPath = Join-Path $workRoot "keys odd\nested\-entity bundle acceptance wrong.key"
    $promptFailureBookPath = Join-Path $workRoot "books odd\nested\prompt unavailable bundle acceptance.sqlite"
    $trialBalancePdfPath = Join-Path $workRoot "reports odd\trial balance bundle acceptance.pdf"

    foreach ($path in @(
        [System.IO.Path]::GetDirectoryName($requestSalePath),
        [System.IO.Path]::GetDirectoryName($bookPath),
        [System.IO.Path]::GetDirectoryName($bookKeyPath),
        [System.IO.Path]::GetDirectoryName($trialBalancePdfPath)
    )) {
        New-Item -ItemType Directory -Path $path -Force | Out-Null
    }

    @"
{
  "effectiveDate": "2026-04-07",
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
    "actorId": "bundle-acceptance",
    "actorType": "AGENT",
    "commandId": "bundle-acceptance-sale",
    "idempotencyKey": "bundle-acceptance-idem-1",
    "causationId": "bundle-acceptance-cause-1"
  }
}
"@ | Write-Utf8NoBomFile -Path $requestSalePath

    @"
{
  "effectiveDate": "2026-04-08",
  "lines": [
    {
      "accountCode": "1000",
      "side": "CREDIT",
      "currencyCode": "EUR",
      "amount": "4.00"
    },
    {
      "accountCode": "2000",
      "side": "DEBIT",
      "currencyCode": "EUR",
      "amount": "4.00"
    }
  ],
  "provenance": {
    "actorId": "bundle-acceptance",
    "actorType": "AGENT",
    "commandId": "bundle-acceptance-adjustment",
    "idempotencyKey": "bundle-acceptance-idem-2",
    "causationId": "bundle-acceptance-cause-2"
  }
}
"@ | Write-Utf8NoBomFile -Path $requestAdjustmentPath

    @"
{
  "accountCode": "1000",
  "accountName": "Cash",
  "normalBalance": "DEBIT",
  "nonsenseOne": "unexpected",
  "nonsenseTwo": "unexpected"
}
"@ | Write-Utf8NoBomFile -Path $invalidRequestPath

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

    "definitely-wrong-bundle-acceptance-passphrase`n" | Write-Utf8NoBomFile -Path $wrongBookKeyPath

    Write-Host "Bundle acceptance: verifying version command"
    $versionPayload = (Invoke-BundleCommand -Arguments @("version", "--output", "json")).Output | ConvertFrom-Json
    if ($versionPayload.status -ne "ok") {
        Fail "version output did not report ok status"
    }
    if ($versionPayload.payload.application -ne "FinGrind") {
        Fail "version output did not include application name"
    }
    if ($versionPayload.payload.version -ne (ProjectVersion)) {
        Fail "version output did not report the expected version"
    }

    Write-Host "Bundle acceptance: verifying self-contained runtime contract"
    $capabilitiesPayload = (Invoke-BundleCommand -Arguments @("capabilities", "--output", "json")).Output | ConvertFrom-Json
    if ($capabilitiesPayload.payload.environment.runtimeDistribution -ne "self-contained-bundle") {
        Fail "capabilities output did not report the self-contained runtime distribution"
    }
    if ($capabilitiesPayload.payload.environment.publicCliDistribution -ne "self-contained-bundle") {
        Fail "capabilities output did not report the public bundle distribution contract"
    }
    if ($capabilitiesPayload.payload.environment.supportedPublicCliBundleTargets -notcontains "windows-x86_64") {
        Fail "capabilities output did not report the supported public bundle targets"
    }
    if (@($capabilitiesPayload.payload.environment.unsupportedPublicCliOperatingSystems).Count -ne 0) {
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
    $queryCommands = @($capabilitiesPayload.payload.queryCommands)
    foreach ($command in @("trial-balance", "account-ledger", "period-summary")) {
        if ($queryCommands -notcontains $command) {
            Fail "capabilities output did not report query command $command"
        }
    }
    $queryOutputModes = @($capabilitiesPayload.payload.requestInput.queryOutputModes)
    if (($queryOutputModes -join ",") -ne "json,human,csv") {
        Fail "capabilities output did not report json,human,csv query output modes"
    }
    $errorCodes = @($capabilitiesPayload.payload.responseModel.errorDescriptors | ForEach-Object { $_.code })
    foreach ($code in @("invalid-page-cursor", "interactive-prompt-unavailable", "book-authentication-failed")) {
        if ($errorCodes -notcontains $code) {
            Fail "capabilities output did not report error descriptor $code"
        }
    }

    Write-Host "Bundle acceptance: generating a dedicated book key file"
    $generateKeyPayload =
        (Invoke-BundleCommand -Arguments @("generate-book-key-file", "--book-key-file", $bookKeyPath)).Output |
            ConvertFrom-Json
    if ($generateKeyPayload.status -ne "ok") {
        Fail "generate-book-key-file did not report ok status"
    }
    if ($generateKeyPayload.payload.permissions -ne "owner-only-acl") {
        Fail "generate-book-key-file did not report Windows owner-only ACL protection"
    }
    if (-not (Test-Path -LiteralPath $bookKeyPath -PathType Leaf)) {
        Fail "generate-book-key-file did not create the expected key file"
    }

    Write-Host "Bundle acceptance: verifying explicit book initialization"
    $openBookPayload =
        (Invoke-BundleCommand -Arguments @("open-book", "--book-file", $bookPath, "--book-key-file", $bookKeyPath)).Output |
            ConvertFrom-Json
    if ($openBookPayload.status -ne "ok") {
        Fail "open-book did not report ok status"
    }

    Write-Host "Bundle acceptance: verifying account declaration and registry listing"
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

    Write-Host "Bundle acceptance: verifying preflight and commit"
    $preflightPayload =
        (Invoke-BundleCommand -Arguments @(
                "preflight-entry",
                "--book-file", $bookPath,
                "--book-key-file", $bookKeyPath,
                "--request-file", $requestSalePath
            )).Output | ConvertFrom-Json
    if ($preflightPayload.status -ne "preflight-accepted") {
        Fail "preflight-entry did not report preflight-accepted"
    }
    $commitSalePayload =
        (Invoke-BundleCommand -Arguments @(
                "post-entry",
                "--book-file", $bookPath,
                "--book-key-file", $bookKeyPath,
                "--request-file", $requestSalePath
            )).Output | ConvertFrom-Json
    if ($commitSalePayload.status -ne "committed") {
        Fail "post-entry did not report committed for the sale"
    }
    $commitAdjustmentPayload =
        (Invoke-BundleCommand -Arguments @(
                "post-entry",
                "--book-file", $bookPath,
                "--book-key-file", $bookKeyPath,
                "--request-file", $requestAdjustmentPath
            )).Output | ConvertFrom-Json
    if ($commitAdjustmentPayload.status -ne "committed") {
        Fail "post-entry did not report committed for the adjustment"
    }

    Write-Host "Bundle acceptance: verifying operator query and report surfaces"
    $listPostingsPayload =
        (Invoke-BundleCommand -Arguments @(
                "list-postings",
                "--book-file", $bookPath,
                "--book-key-file", $bookKeyPath,
                "--limit", "1"
            )).Output | ConvertFrom-Json
    if ([string]::IsNullOrWhiteSpace($listPostingsPayload.payload.nextCursor)) {
        Fail "list-postings did not report nextCursor for the first page"
    }
    $nextCursor = $listPostingsPayload.payload.nextCursor
    $secondPostingPagePayload =
        (Invoke-BundleCommand -Arguments @(
                "list-postings",
                "--book-file", $bookPath,
                "--book-key-file", $bookKeyPath,
                "--cursor", $nextCursor,
                "--limit", "25"
            )).Output | ConvertFrom-Json
    $secondPageCommandIds = @($secondPostingPagePayload.payload.postings | ForEach-Object { $_.provenance.commandId })
    if ($secondPageCommandIds -notcontains "bundle-acceptance-sale") {
        Fail "list-postings did not round-trip the opaque nextCursor"
    }

    $listPostingsHumanOutput =
        (Invoke-BundleCommand -Arguments @(
                "list-postings",
                "--book-file", $bookPath,
                "--book-key-file", $bookKeyPath,
                "--limit", "25",
                "--output", "human"
            )).Output
    Require-Match $listPostingsHumanOutput 'Effective date\s+\|\s+Recorded at' "human posting register did not render a table header"
    Require-Match $listPostingsHumanOutput '10\.00' "human posting register did not render accounting-scale amounts"

    $accountBalanceHumanOutput =
        (Invoke-BundleCommand -Arguments @(
                "account-balance",
                "--book-file", $bookPath,
                "--book-key-file", $bookKeyPath,
                "--account-code", "1000",
                "--output", "human"
            )).Output
    Require-Match $accountBalanceHumanOutput '^Account Balance$' "human account-balance output did not render the report title"
    Require-Match $accountBalanceHumanOutput 'Account\s+:\s+1000' "human account-balance output did not render the selected account"
    Require-Match $accountBalanceHumanOutput '6\.00' "human account-balance output did not render the expected net balance"

    $trialBalanceHumanOutput =
        (Invoke-BundleCommand -Arguments @(
                "trial-balance",
                "--book-file", $bookPath,
                "--book-key-file", $bookKeyPath,
                "--effective-date-to", "2026-04-08",
                "--output", "human"
            )).Output
    Require-Match $trialBalanceHumanOutput '^Trial Balance$' "trial-balance output did not render the report title"
    Require-Match $trialBalanceHumanOutput 'Effective date to\s+:\s+2026-04-08' "trial-balance output did not render the effective date"
    Require-Match $trialBalanceHumanOutput '1000' "trial-balance output did not render account 1000"
    Require-Match $trialBalanceHumanOutput '6\.00' "trial-balance output did not render the expected net amount"
    [void](Invoke-BundleCommand -Arguments @(
            "trial-balance",
            "--book-file", $bookPath,
            "--book-key-file", $bookKeyPath,
            "--effective-date-to", "2026-04-08",
            "--output", "human",
            "--pdf-out", $trialBalancePdfPath
        ))
    if (-not (Test-Path -LiteralPath $trialBalancePdfPath -PathType Leaf)) {
        Fail "trial-balance did not write the requested PDF artifact"
    }
    $pdfHeader = [System.Text.Encoding]::ASCII.GetString((Get-Content -LiteralPath $trialBalancePdfPath -Encoding Byte -TotalCount 5))
    if ($pdfHeader -ne "%PDF-") {
        Fail "trial-balance PDF artifact did not start with %PDF-"
    }

    $accountLedgerCsvOutput =
        (Invoke-BundleCommand -Arguments @(
                "account-ledger",
                "--book-file", $bookPath,
                "--book-key-file", $bookKeyPath,
                "--account-code", "1000",
                "--effective-date-from", "2026-04-07",
                "--effective-date-to", "2026-04-08",
                "--output", "csv"
            )).Output
    Require-Match $accountLedgerCsvOutput '^accountCode,accountName,effectiveDateFrom,effectiveDateTo,postingId,effectiveDate,recordedAt,currencyCode,debitAmount,creditAmount,runningBalance,runningBalanceSide,counterpartAccounts$' "account-ledger CSV output did not render the expected header"
    Require-Match $accountLedgerCsvOutput '^1000,Cash,2026-04-07,2026-04-08,[^,]+,2026-04-07,[^,]+,EUR,10\.00,0\.00,10\.00,DEBIT,2000$' "account-ledger CSV output did not render the opening ledger movement row"
    Require-Match $accountLedgerCsvOutput '^1000,Cash,2026-04-07,2026-04-08,[^,]+,2026-04-08,[^,]+,EUR,0\.00,4\.00,6\.00,DEBIT,2000$' "account-ledger CSV output did not render the running-balance adjustment row"

    $periodSummaryHumanOutput =
        (Invoke-BundleCommand -Arguments @(
                "period-summary",
                "--book-file", $bookPath,
                "--book-key-file", $bookKeyPath,
                "--effective-date-from", "2026-04-07",
                "--effective-date-to", "2026-04-08",
                "--output", "human"
            )).Output
    Require-Match $periodSummaryHumanOutput '^Period Summary$' "period-summary output did not render the report title"
    Require-Match $periodSummaryHumanOutput 'Posting count' "period-summary output did not render posting-count metadata"
    Require-Match $periodSummaryHumanOutput '2' "period-summary output did not render the expected posting count"

    Write-Host "Bundle acceptance: verifying rekey and wrong-key semantics"
    $replacementKeyPayload =
        (Invoke-BundleCommand -Arguments @("generate-book-key-file", "--book-key-file", $replacementBookKeyPath)).Output |
            ConvertFrom-Json
    if ($replacementKeyPayload.status -ne "ok") {
        Fail "replacement key generation did not report ok status"
    }
    $rekeyPayload =
        (Invoke-BundleCommand -Arguments @(
                "rekey-book",
                "--book-file", $bookPath,
                "--book-key-file", $bookKeyPath,
                "--new-book-key-file", $replacementBookKeyPath
            )).Output | ConvertFrom-Json
    if ($rekeyPayload.status -ne "ok") {
        Fail "rekey-book did not report ok status"
    }

    $wrongKeyResult = Invoke-BundleCommand -Arguments @(
        "list-accounts",
        "--book-file", $bookPath,
        "--book-key-file", $bookKeyPath
    ) -AllowFailure
    if ($wrongKeyResult.ExitCode -ne 2) {
        Fail "wrong-key listing exited with $($wrongKeyResult.ExitCode) instead of 2"
    }
    $wrongKeyPayload = $wrongKeyResult.Output | ConvertFrom-Json
    if ($wrongKeyPayload.code -ne "book-authentication-failed") {
        Fail "wrong-key listing did not report book-authentication-failed"
    }
    if ($wrongKeyPayload.message -match 'SQLITE_NOTADB') {
        Fail "wrong-key listing leaked the SQLite NOTADB storage symptom"
    }

    $replacementTrialBalanceOutput =
        (Invoke-BundleCommand -Arguments @(
                "trial-balance",
                "--book-file", $bookPath,
                "--book-key-file", $replacementBookKeyPath,
                "--effective-date-to", "2026-04-08",
                "--output", "human"
            )).Output
    Require-Match $replacementTrialBalanceOutput '1000' "trial-balance did not remain readable after rekey"

    Write-Host "Bundle acceptance: verifying deterministic nonsense workflows"
    $invalidCursorResult = Invoke-BundleCommand -Arguments @(
        "list-postings",
        "--book-file", $bookPath,
        "--book-key-file", $replacementBookKeyPath,
        "--cursor", "definitely-not-a-valid-cursor"
    ) -AllowFailure
    if ($invalidCursorResult.ExitCode -ne 2) {
        Fail "invalid cursor exited with $($invalidCursorResult.ExitCode) instead of 2"
    }
    $invalidCursorPayload = $invalidCursorResult.Output | ConvertFrom-Json
    if ($invalidCursorPayload.code -ne "invalid-page-cursor") {
        Fail "invalid cursor did not report invalid-page-cursor"
    }

    $promptFailureResult = Invoke-BundleCommand -Arguments @(
        "open-book",
        "--book-file", $promptFailureBookPath,
        "--book-passphrase-prompt"
    ) -AllowFailure
    if ($promptFailureResult.ExitCode -ne 2) {
        Fail "prompt-unavailable exited with $($promptFailureResult.ExitCode) instead of 2"
    }
    $promptFailurePayload = $promptFailureResult.Output | ConvertFrom-Json
    if ($promptFailurePayload.code -ne "interactive-prompt-unavailable") {
        Fail "prompt-unavailable did not report interactive-prompt-unavailable"
    }
    if ($promptFailurePayload.hint -notmatch '--book-passphrase-stdin') {
        Fail "prompt-unavailable did not report a repair hint"
    }

    $invalidRequestResult = Invoke-BundleCommand -Arguments @(
        "declare-account",
        "--book-file", $bookPath,
        "--book-key-file", $replacementBookKeyPath,
        "--request-file", $invalidRequestPath
    ) -AllowFailure
    if ($invalidRequestResult.ExitCode -ne 2) {
        Fail "invalid request exited with $($invalidRequestResult.ExitCode) instead of 2"
    }
    $invalidRequestPayload = $invalidRequestResult.Output | ConvertFrom-Json
    if ($invalidRequestPayload.code -ne "invalid-request") {
        Fail "invalid request did not report invalid-request"
    }
    if ($invalidRequestPayload.message -notmatch 'Unexpected fields: nonsenseOne, nonsenseTwo') {
        Fail "invalid request did not report all unexpected fields together"
    }

    Write-Host "Bundle acceptance: success"
} finally {
    if (Test-Path -LiteralPath $smokeRoot) {
        Remove-Item -LiteralPath $smokeRoot -Recurse -Force
    }
}
