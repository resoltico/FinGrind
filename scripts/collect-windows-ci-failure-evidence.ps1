[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$RepositoryRoot,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$EvidenceDirectory,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$TrustedEvidenceRoot,

    [Parameter()]
    [switch]$EvidenceDirectoryPrepared,

    [Parameter()]
    [string]$CommitSha = $env:GITHUB_SHA,

    [Parameter()]
    [string]$RunId = $env:GITHUB_RUN_ID
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

# This collector intentionally writes one synthesized JSON document. It never uploads logs,
# source trees, test XML, generated bundles, books, keys, environment dumps, or raw tool output.
# Inputs are fixed repository locations and only normalized metadata escapes the process.

$outputPolicyPath = Join-Path $PSScriptRoot "windows-failure-evidence-output.ps1"
if (-not (Test-Path -LiteralPath $outputPolicyPath -PathType Leaf)) {
    throw "missing Windows failure-evidence output policy at $outputPolicyPath"
}
. $outputPolicyPath

function Fail {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    throw $Message
}

function Test-AllowlistedFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $false
    }
    if (-not (Test-EvidencePathDescendsFrom -CandidatePath $Path -RootPath $RepositoryRoot)) {
        return $false
    }
    return Test-EvidencePathTreeWithoutReparsePoint -CandidatePath $Path -RootPath $RepositoryRoot
}

function Test-AllowlistedDirectory {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        return $false
    }
    if (-not (Test-EvidencePathDescendsFrom -CandidatePath $Path -RootPath $RepositoryRoot)) {
        return $false
    }
    return Test-EvidencePathTreeWithoutReparsePoint -CandidatePath $Path -RootPath $RepositoryRoot
}

function Get-AllowlistedFileRecord {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot,

        [Parameter()]
        [int64]$MaximumBytes = 1048576
    )

    if (-not (Test-AllowlistedFile -Path $Path -RepositoryRoot $RepositoryRoot)) {
        return [ordered]@{
            present = $false
            bytes = $null
            withinByteLimit = $false
        }
    }

    $item = Get-Item -LiteralPath $Path -Force
    return [ordered]@{
        present = $true
        bytes = [int64]$item.Length
        withinByteLimit = ([int64]$item.Length -le $MaximumBytes)
    }
}

function Read-AllowlistedJsonObject {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    if (-not (Test-AllowlistedFile -Path $Path -RepositoryRoot $RepositoryRoot)) {
        return $null
    }

    $item = Get-Item -LiteralPath $Path -Force
    if ($item.Length -gt 1048576) {
        return $null
    }

    try {
        $content = [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
        return ,($content | ConvertFrom-Json -AsHashtable -Depth 20)
    } catch {
        return $null
    }
}

function Get-ObjectProperty {
    param(
        [Parameter()]
        [object]$Object,

        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    if ($Object -is [System.Collections.IDictionary] -and $Object.Contains($Name)) {
        return $Object[$Name]
    }
    return $null
}

function Get-SafeVersion {
    param(
        [Parameter()]
        [object]$Value
    )

    if ($null -eq $Value) {
        return $null
    }

    $match = [regex]::Match(
        [string]$Value,
        '(?<![0-9])(?<version>[0-9]+(?:\.[0-9]+){1,4}(?:[+_-][A-Za-z0-9._-]+)?)'
    )
    if (-not $match.Success) {
        return $null
    }
    return $match.Groups['version'].Value
}

function Get-SafeIdentifier {
    param(
        [Parameter()]
        [object]$Value
    )

    if ($null -eq $Value) {
        return $null
    }

    $candidate = [string]$Value
    if ($candidate -match '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$') {
        return $candidate
    }
    return $null
}

function Get-SafeCount {
    param(
        [Parameter()]
        [object]$Value
    )

    if ($null -eq $Value) {
        return 0
    }

    $candidate = [string]$Value
    if ($candidate -match '^\d{1,9}$') {
        return [int]$candidate
    }
    return 0
}

function Get-SafeCollectionCount {
    param(
        [Parameter()]
        [object]$Value
    )

    if ($null -eq $Value -or $Value -is [string] -or -not ($Value -is [System.Collections.IEnumerable])) {
        return $null
    }

    $count = 0
    foreach ($ignored in $Value) {
        $count += 1
        if ($count -gt 10000) {
            return $null
        }
    }
    return $count
}

function Get-SafeBoolean {
    param(
        [Parameter()]
        [object]$Value
    )

    if ($Value -is [bool]) {
        return [bool]$Value
    }
    if ($Value -is [string] -and $Value -eq 'true') {
        return $true
    }
    if ($Value -is [string] -and $Value -eq 'false') {
        return $false
    }
    return $null
}

function Get-ExpectedOperatingSystemId {
    param(
        [Parameter()]
        [object]$Value
    )

    if ($Value -is [string] -and $Value -eq 'windows') {
        return 'windows'
    }
    return $null
}

function Get-ExpectedArchitectureId {
    param(
        [Parameter()]
        [object]$Value
    )

    if ($Value -is [string] -and $Value -eq 'x86_64') {
        return 'x86_64'
    }
    return $null
}

function Get-ExpectedTargetTriple {
    param(
        [Parameter()]
        [object]$Value
    )

    if ($Value -is [string] -and $Value -eq 'x86_64-pc-windows-msvc') {
        return 'x86_64-pc-windows-msvc'
    }
    return $null
}

function Get-DeclaredToolchainVersion {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot,

        [Parameter(Mandatory = $true)]
        [ValidateSet('fingrindJavaVersion', 'fingrindPythonVersion')]
        [string]$PropertyName
    )

    # Failure collection runs after an arbitrary build failure. Do not execute java, python, cl.exe,
    # or any PATH-selected binary here: a wedged or replaced tool must not turn one failed Windows
    # job into an unbounded diagnostic hang. The checked-in toolchain declaration is enough to make
    # the evidence actionable, while actual MSVC provenance is read from the bounded build output.
    $propertiesPath = Join-Path $RepositoryRoot 'gradle/fingrind-build.properties'
    if (-not (Test-AllowlistedFile -Path $propertiesPath -RepositoryRoot $RepositoryRoot)) {
        return $null
    }

    try {
        $item = Get-Item -LiteralPath $propertiesPath -Force
        if ($item.Length -gt 1048576) {
            return $null
        }
        $content = [System.IO.File]::ReadAllText($propertiesPath, [System.Text.Encoding]::UTF8)
        $match = [regex]::Match(
            $content,
            "(?m)^$([regex]::Escape($PropertyName))=(?<version>[0-9]+(?:\.[0-9]+){0,4}(?:[+_-][A-Za-z0-9._-]+)?)$"
        )
        if ($match.Success) {
            return $match.Groups['version'].Value
        }
    } catch {
        # A malformed or inaccessible checked-in declaration is represented by null, never raw text.
        Write-Verbose "Could not normalize the declared toolchain version."
    }
    return $null
}

function Get-GradleWrapperVersion {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    $propertiesPath = Join-Path $RepositoryRoot 'gradle/wrapper/gradle-wrapper.properties'
    if (-not (Test-AllowlistedFile -Path $propertiesPath -RepositoryRoot $RepositoryRoot)) {
        return $null
    }

    try {
        $content = [System.IO.File]::ReadAllText($propertiesPath, [System.Text.Encoding]::UTF8)
        $match = [regex]::Match($content, 'gradle-(?<version>[0-9]+(?:\.[0-9]+)+)-')
        if ($match.Success) {
            return $match.Groups['version'].Value
        }
    } catch {
        return $null
    }
    return $null
}

function Get-ReleaseVersion {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    $propertiesPath = Join-Path $RepositoryRoot 'gradle.properties'
    if (-not (Test-AllowlistedFile -Path $propertiesPath -RepositoryRoot $RepositoryRoot)) {
        return $null
    }

    try {
        $content = [System.IO.File]::ReadAllText($propertiesPath, [System.Text.Encoding]::UTF8)
        $match = [regex]::Match(
            $content,
            '(?m)^version=(?<version>[0-9]+(?:\.[0-9]+){2}(?:-[0-9A-Za-z.-]+)?)$'
        )
        if ($match.Success) {
            return $match.Groups['version'].Value
        }
    } catch {
        return $null
    }
    return $null
}

function Get-KnownGradleScopeSet {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    return @(
        [pscustomobject]@{ name = 'root'; path = $RepositoryRoot },
        [pscustomobject]@{ name = 'core'; path = (Join-Path $RepositoryRoot 'core') },
        [pscustomobject]@{ name = 'contract'; path = (Join-Path $RepositoryRoot 'contract') },
        [pscustomobject]@{ name = 'executor'; path = (Join-Path $RepositoryRoot 'executor') },
        [pscustomobject]@{ name = 'sqlite'; path = (Join-Path $RepositoryRoot 'sqlite') },
        [pscustomobject]@{ name = 'report-pdf'; path = (Join-Path $RepositoryRoot 'report-pdf') },
        [pscustomobject]@{ name = 'cli'; path = (Join-Path $RepositoryRoot 'cli') },
        [pscustomobject]@{ name = 'architecture'; path = (Join-Path $RepositoryRoot 'architecture') },
        [pscustomobject]@{ name = 'gradle-build-logic'; path = (Join-Path $RepositoryRoot 'gradle/build-logic') }
    )
}

function Get-CombinedDigest {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Values
    )

    $bytes = [System.Text.Encoding]::UTF8.GetBytes(($Values -join "`n"))
    $algorithm = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ([System.BitConverter]::ToString($algorithm.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant()
    } finally {
        $algorithm.Dispose()
    }
}

function Read-JunitSummary {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $summary = [ordered]@{
        valid = $false
        tests = 0
        failures = 0
        errors = 0
        skipped = 0
        aclMutationPermissions = @()
        aclMutationPrincipalKinds = @()
        aclMutationScopes = @()
        aclMutationAncestryDepths = @()
    }
    $aclMutationPermissions = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $allowedAclMutationPermissions = @(
        'DELETE', 'DELETE_CHILD', 'WRITE_ACL', 'WRITE_ATTRIBUTES', 'WRITE_NAMED_ATTRS', 'WRITE_OWNER'
    )
    $aclMutationPrincipalKinds = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $allowedAclMutationPrincipalKinds = @(
        'AUTHENTICATED_USERS', 'BUILTIN_USERS', 'CREATOR_OWNER', 'EVERYONE', 'OTHER'
    )
    $aclMutationScopes = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $allowedAclMutationScopes = @('CREATION', 'PROTECTED')
    $aclMutationAncestryDepths = [System.Collections.Generic.HashSet[int]]::new()
    $settings = [System.Xml.XmlReaderSettings]::new()
    $settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
    $settings.XmlResolver = $null
    $settings.IgnoreComments = $true
    $settings.IgnoreWhitespace = $true
    $settings.MaxCharactersInDocument = 8388608
    $stream = $null
    $reader = $null

    try {
        $stream = [System.IO.File]::OpenRead($Path)
        $reader = [System.Xml.XmlReader]::Create($stream, $settings)
        while ($reader.Read()) {
            if ($reader.NodeType -ne [System.Xml.XmlNodeType]::Element) {
                continue
            }
            if ($reader.LocalName -eq 'testsuite') {
                $summary.tests += Get-SafeCount -Value $reader.GetAttribute('tests')
                $summary.failures += Get-SafeCount -Value $reader.GetAttribute('failures')
                $summary.errors += Get-SafeCount -Value $reader.GetAttribute('errors')
                $summary.skipped += Get-SafeCount -Value $reader.GetAttribute('skipped')
                continue
            }
            if ($reader.LocalName -notin @('failure', 'error')) {
                continue
            }
            $message = $reader.GetAttribute('message')
            if ($null -eq $message) {
                continue
            }
            foreach ($match in [regex]::Matches(
                $message,
                '\[FINGRIND_ACL_MUTATION_PERMISSIONS=(?<permissions>[A-Z_,]+)\]'
            )) {
                foreach ($permission in $match.Groups['permissions'].Value.Split(',')) {
                    if ($allowedAclMutationPermissions.Contains($permission)) {
                        [void]$aclMutationPermissions.Add($permission)
                    }
                }
            }
            foreach ($match in [regex]::Matches(
                $message,
                '\[FINGRIND_ACL_MUTATION_PRINCIPAL=(?<kind>[A-Z_]+)\]'
            )) {
                $kind = $match.Groups['kind'].Value
                if ($allowedAclMutationPrincipalKinds.Contains($kind)) {
                    [void]$aclMutationPrincipalKinds.Add($kind)
                }
            }
            foreach ($match in [regex]::Matches(
                $message,
                '\[FINGRIND_ACL_MUTATION_SCOPE=(?<scope>[A-Z_]+)\]'
            )) {
                $mutationScope = $match.Groups['scope'].Value
                if ($allowedAclMutationScopes.Contains($mutationScope)) {
                    [void]$aclMutationScopes.Add($mutationScope)
                }
            }
            foreach ($match in [regex]::Matches(
                $message,
                '\[FINGRIND_ACL_ANCESTRY_DEPTH=(?<depth>[0-9]{1,3})\]'
            )) {
                [void]$aclMutationAncestryDepths.Add([int]$match.Groups['depth'].Value)
            }
        }
        $summary.valid = $true
    } catch {
        # Invalid result XML is itself represented as a count; its raw content is never retained.
        Write-Verbose "Could not normalize an allowlisted JUnit result file."
    } finally {
        if ($null -ne $reader) {
            $reader.Dispose()
        }
        if ($null -ne $stream) {
            $stream.Dispose()
        }
    }
    $summary.aclMutationPermissions = @($aclMutationPermissions | Sort-Object)
    $summary.aclMutationPrincipalKinds = @($aclMutationPrincipalKinds | Sort-Object)
    $summary.aclMutationScopes = @($aclMutationScopes | Sort-Object)
    $summary.aclMutationAncestryDepths = @($aclMutationAncestryDepths | Sort-Object)
    return $summary
}

function Get-TestResultSummarySet {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    $summaries = [System.Collections.Generic.List[object]]::new()
    foreach ($scope in (Get-KnownGradleScopeSet -RepositoryRoot $RepositoryRoot)) {
        $resultDirectory = Join-Path $scope.path 'build/test-results/test'
        $fileCount = 0
        $invalidFileCount = 0
        $tests = 0
        $failures = 0
        $errors = 0
        $skipped = 0
        $aclMutationPermissions = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        $aclMutationPrincipalKinds = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        $aclMutationScopes = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        $aclMutationAncestryDepths = [System.Collections.Generic.HashSet[int]]::new()

        if (Test-AllowlistedDirectory -Path $resultDirectory -RepositoryRoot $RepositoryRoot) {
            foreach ($resultFile in (Get-ChildItem -LiteralPath $resultDirectory -Filter 'TEST-*.xml' -File -ErrorAction SilentlyContinue)) {
                if (-not (Test-AllowlistedFile -Path $resultFile.FullName -RepositoryRoot $RepositoryRoot)) {
                    continue
                }
                $metadata = Get-AllowlistedFileRecord `
                    -Path $resultFile.FullName `
                    -RepositoryRoot $RepositoryRoot `
                    -MaximumBytes 8388608
                if (-not $metadata.present -or -not $metadata.withinByteLimit) {
                    $invalidFileCount += 1
                    continue
                }
                $fileCount += 1
                $fileSummary = Read-JunitSummary -Path $resultFile.FullName
                if (-not $fileSummary.valid) {
                    $invalidFileCount += 1
                    continue
                }
                $tests += $fileSummary.tests
                $failures += $fileSummary.failures
                $errors += $fileSummary.errors
                $skipped += $fileSummary.skipped
                foreach ($permission in $fileSummary.aclMutationPermissions) {
                    [void]$aclMutationPermissions.Add($permission)
                }
                foreach ($kind in $fileSummary.aclMutationPrincipalKinds) {
                    [void]$aclMutationPrincipalKinds.Add($kind)
                }
                foreach ($mutationScope in $fileSummary.aclMutationScopes) {
                    [void]$aclMutationScopes.Add($mutationScope)
                }
                foreach ($depth in $fileSummary.aclMutationAncestryDepths) {
                    [void]$aclMutationAncestryDepths.Add($depth)
                }
            }
        }

        $summaries.Add([ordered]@{
                scope = $scope.name
                resultFileCount = $fileCount
                invalidResultFileCount = $invalidFileCount
                tests = $tests
                failures = $failures
                errors = $errors
                skipped = $skipped
                aclMutationPermissions = @($aclMutationPermissions | Sort-Object)
                aclMutationPrincipalKinds = @($aclMutationPrincipalKinds | Sort-Object)
                aclMutationScopes = @($aclMutationScopes | Sort-Object)
                aclMutationAncestryDepths = @($aclMutationAncestryDepths | Sort-Object)
            })
    }
    return @($summaries)
}

function Get-GradleProblemReportSummarySet {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    $summaries = [System.Collections.Generic.List[object]]::new()
    foreach ($scope in (Get-KnownGradleScopeSet -RepositoryRoot $RepositoryRoot)) {
        $reportPath = Join-Path $scope.path 'build/reports/problems/problems-report.html'
        $metadata = Get-AllowlistedFileRecord `
            -Path $reportPath `
            -RepositoryRoot $RepositoryRoot `
            -MaximumBytes 8388608
        if ($metadata.present) {
            $summaries.Add([ordered]@{
                    scope = $scope.name
                    report = $metadata
                })
        }
    }
    return @($summaries)
}

function Get-ManagedSqliteProvenance {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    $provisioningDirectory = Join-Path $RepositoryRoot 'sqlite/build/managed-sqlite/windows-x86_64'
    $fingerprintPath = Join-Path $provisioningDirectory 'toolchain-fingerprint.json'
    $buildContractPath = Join-Path $provisioningDirectory 'build-contract.json'
    $fingerprintFile = Get-AllowlistedFileRecord -Path $fingerprintPath -RepositoryRoot $RepositoryRoot
    $buildContractFile = Get-AllowlistedFileRecord -Path $buildContractPath -RepositoryRoot $RepositoryRoot
    $fingerprintJson = Read-AllowlistedJsonObject -Path $fingerprintPath -RepositoryRoot $RepositoryRoot
    $buildContractJson = Read-AllowlistedJsonObject -Path $buildContractPath -RepositoryRoot $RepositoryRoot
    $toolchain = [ordered]@{
        file = $fingerprintFile
        compilerVersion = Get-SafeVersion -Value (Get-ObjectProperty -Object $fingerprintJson -Name 'compilerVersion')
        linkerVersion = Get-SafeVersion -Value (Get-ObjectProperty -Object $fingerprintJson -Name 'linkerVersion')
        targetTriple = Get-ExpectedTargetTriple -Value (Get-ObjectProperty -Object $fingerprintJson -Name 'targetTriple')
        operatingSystemId = Get-ExpectedOperatingSystemId -Value (Get-ObjectProperty -Object $fingerprintJson -Name 'operatingSystemId')
        architectureId = Get-ExpectedArchitectureId -Value (Get-ObjectProperty -Object $fingerprintJson -Name 'architectureId')
        sanitizedFingerprint = $null
    }
    $toolchain.sanitizedFingerprint = if ($fingerprintFile.present) {
        Get-CombinedDigest -Values @(
            "compilerVersion=$($toolchain.compilerVersion)",
            "linkerVersion=$($toolchain.linkerVersion)",
            "targetTriple=$($toolchain.targetTriple)",
            "operatingSystemId=$($toolchain.operatingSystemId)",
            "architectureId=$($toolchain.architectureId)"
        )
    } else {
        $null
    }
    $contract = [ordered]@{
        file = $buildContractFile
        sqliteVersion = Get-SafeVersion -Value (Get-ObjectProperty -Object $buildContractJson -Name 'sqliteVersion')
        operatingSystemId = Get-ExpectedOperatingSystemId -Value (Get-ObjectProperty -Object $buildContractJson -Name 'operatingSystemId')
        requiredCompileOptionCount = Get-SafeCollectionCount -Value (Get-ObjectProperty -Object $buildContractJson -Name 'requiredCompileOptions')
        forbiddenCompileOptionCount = Get-SafeCollectionCount -Value (Get-ObjectProperty -Object $buildContractJson -Name 'forbiddenCompileOptions')
        requiresSecureMemorySupport = Get-SafeBoolean -Value (Get-ObjectProperty -Object $buildContractJson -Name 'requiresSecureMemorySupport')
        sanitizedFingerprint = $null
    }
    $contract.sanitizedFingerprint = if ($buildContractFile.present) {
        Get-CombinedDigest -Values @(
            "sqliteVersion=$($contract.sqliteVersion)",
            "operatingSystemId=$($contract.operatingSystemId)",
            "requiredCompileOptionCount=$($contract.requiredCompileOptionCount)",
            "forbiddenCompileOptionCount=$($contract.forbiddenCompileOptionCount)",
            "requiresSecureMemorySupport=$($contract.requiresSecureMemorySupport)"
        )
    } else {
        $null
    }

    return [ordered]@{
        toolchainFingerprint = $toolchain
        buildContract = $contract
    }
}

function Get-BundleProvenance {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    $manifestPath = Join-Path $RepositoryRoot 'cli/build/generated/bundle/bundle-archive-manifest.json'
    $manifest = Get-AllowlistedFileRecord -Path $manifestPath -RepositoryRoot $RepositoryRoot
    $checksumMetadata = [ordered]@{
        present = $false
        bytes = $null
        withinByteLimit = $false
    }
    $archiveSha256 = $null

    # The manifest records absolute paths, so it is evidence of task completion only. Never use a
    # manifest-selected path for collection: derive the one expected Windows bundle checksum from
    # the release version and fixed classifier instead.
    $releaseVersion = Get-ReleaseVersion -RepositoryRoot $RepositoryRoot
    if ($null -ne $releaseVersion) {
        $distributionDirectory = Join-Path $RepositoryRoot 'cli/build/distributions'
        $checksumPath = Join-Path $distributionDirectory "fingrind-${releaseVersion}-windows-x86_64.zip.sha256"
        $checksumMetadata = Get-AllowlistedFileRecord `
            -Path $checksumPath `
            -RepositoryRoot $RepositoryRoot `
            -MaximumBytes 65536
        if ($checksumMetadata.present -and $checksumMetadata.withinByteLimit) {
            try {
                $checksumContent = [System.IO.File]::ReadAllText($checksumPath, [System.Text.Encoding]::UTF8)
                $match = [regex]::Match($checksumContent, '(?m)^\s*(?<digest>[A-Fa-f0-9]{64})\s{2,}[^\r\n]+$')
                if ($match.Success) {
                    $archiveSha256 = $match.Groups['digest'].Value.ToLowerInvariant()
                }
            } catch {
                # The fingerprint remains useful when a partial checksum cannot be normalized.
                Write-Verbose "Could not normalize the bundle checksum."
            }
        }
    }

    return [ordered]@{
        archiveManifest = $manifest
        checksumFile = $checksumMetadata
        archiveSha256 = $archiveSha256
    }
}

function Get-CommitSha {
    param(
        [Parameter()]
        [string]$Value
    )

    if ($Value -match '^[A-Fa-f0-9]{40}$') {
        return $Value.ToLowerInvariant()
    }
    return $null
}

function Get-RunId {
    param(
        [Parameter()]
        [string]$Value
    )

    if ($Value -match '^\d{1,20}$') {
        return $Value
    }
    return $null
}

$repositoryRootPath = Get-NormalizedAbsolutePath -Path $RepositoryRoot
if (-not (Test-Path -LiteralPath $repositoryRootPath -PathType Container)) {
    Fail "Repository root does not exist: ${repositoryRootPath}"
}

$evidenceDirectoryPath = if ($EvidenceDirectoryPrepared) {
    Assert-PreparedTrustedEvidenceDirectory `
        -EvidenceDirectory $EvidenceDirectory `
        -TrustedEvidenceRoot $TrustedEvidenceRoot
} else {
    New-TrustedEvidenceDirectory `
        -EvidenceDirectory $EvidenceDirectory `
        -TrustedEvidenceRoot $TrustedEvidenceRoot
}

$evidence = [ordered]@{
    schemaVersion = 1
    collectionStatus = 'collected'
    privacy = [ordered]@{
        collectionMode = 'allowlisted-normalized'
        rawLogsIncluded = $false
        workspaceFilesCopied = $false
        bookOrKeyFilesIncluded = $false
        environmentDumpIncluded = $false
    }
    provenance = [ordered]@{
        commitSha = Get-CommitSha -Value $CommitSha
        runId = Get-RunId -Value $RunId
    }
    runner = [ordered]@{
        osArchitecture = Get-SafeIdentifier -Value ([System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString())
        osVersion = Get-SafeVersion -Value ([System.Environment]::OSVersion.Version.ToString())
        powerShellVersion = Get-SafeVersion -Value $PSVersionTable.PSVersion.ToString()
        declaredJavaVersion = Get-DeclaredToolchainVersion `
            -RepositoryRoot $repositoryRootPath `
            -PropertyName 'fingrindJavaVersion'
        declaredPythonVersion = Get-DeclaredToolchainVersion `
            -RepositoryRoot $repositoryRootPath `
            -PropertyName 'fingrindPythonVersion'
        windowsSdkVersion = Get-SafeVersion -Value $env:WindowsSDKVersion
        gradleWrapperVersion = Get-GradleWrapperVersion -RepositoryRoot $repositoryRootPath
    }
    managedSqlite = Get-ManagedSqliteProvenance -RepositoryRoot $repositoryRootPath
    bundle = Get-BundleProvenance -RepositoryRoot $repositoryRootPath
    testResults = @(Get-TestResultSummarySet -RepositoryRoot $repositoryRootPath)
    gradleProblemReports = @(Get-GradleProblemReportSummarySet -RepositoryRoot $repositoryRootPath)
}

$json = $evidence | ConvertTo-Json -Depth 12
Write-NewTrustedEvidenceDocument `
    -EvidenceDirectory $evidenceDirectoryPath `
    -TrustedEvidenceRoot $TrustedEvidenceRoot `
    -Content ($json + [System.Environment]::NewLine) | Out-Null
Write-Information -MessageData 'Wrote sanitized Windows failure evidence.' -InformationAction Continue
