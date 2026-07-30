[CmdletBinding()]
param()

Set-StrictMode -Version Latest

# The failure-evidence writer owns a freshly created directory directly below the runner-controlled
# temporary root. Collection code may write only its one allowlisted document there; it must never
# follow a reparse point supplied by a failed build.

function Get-NormalizedAbsolutePath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    return [System.IO.Path]::GetFullPath($Path)
}

function Test-EvidencePathEquality {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Left,

        [Parameter(Mandatory = $true)]
        [string]$Right
    )

    $comparison = if ($IsWindows) {
        [System.StringComparison]::OrdinalIgnoreCase
    } else {
        [System.StringComparison]::Ordinal
    }
    return [string]::Equals($Left, $Right, $comparison)
}

function Test-EvidencePathDescendsFrom {
    param(
        [Parameter(Mandatory = $true)]
        [string]$CandidatePath,

        [Parameter(Mandatory = $true)]
        [string]$RootPath
    )

    $candidate = Get-NormalizedAbsolutePath -Path $CandidatePath
    $root = Get-NormalizedAbsolutePath -Path $RootPath
    $separator = [System.IO.Path]::DirectorySeparatorChar
    $rootPrefix = $root.TrimEnd([char]'/', [char]'\') + $separator
    $comparison = if ($IsWindows) {
        [System.StringComparison]::OrdinalIgnoreCase
    } else {
        [System.StringComparison]::Ordinal
    }
    return $candidate.StartsWith($rootPrefix, $comparison)
}

function Test-EvidencePathTreeWithoutReparsePoint {
    param(
        [Parameter(Mandatory = $true)]
        [string]$CandidatePath,

        [Parameter(Mandatory = $true)]
        [string]$RootPath
    )

    $currentPath = Get-NormalizedAbsolutePath -Path $CandidatePath
    $root = Get-NormalizedAbsolutePath -Path $RootPath
    while ($true) {
        $item = Get-Item -LiteralPath $currentPath -Force -ErrorAction Stop
        if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne [System.IO.FileAttributes]::None) {
            return $false
        }
        if (Test-EvidencePathEquality -Left $currentPath -Right $root) {
            return $true
        }
        $parent = [System.IO.Directory]::GetParent($currentPath)
        if ($null -eq $parent) {
            return $false
        }
        $currentPath = $parent.FullName
    }
}

function Assert-TrustedEvidenceRoot {
    param(
        [Parameter(Mandatory = $true)]
        [string]$TrustedEvidenceRoot
    )

    $root = Get-NormalizedAbsolutePath -Path $TrustedEvidenceRoot
    if (-not (Test-Path -LiteralPath $root -PathType Container)) {
        throw "Trusted evidence root is not an existing directory: $root"
    }
    if (-not (Test-EvidencePathTreeWithoutReparsePoint -CandidatePath $root -RootPath $root)) {
        throw "Trusted evidence root is a reparse point: $root"
    }
    return $root
}

function Assert-EvidenceDirectoryWithinTrustedRoot {
    param(
        [Parameter(Mandatory = $true)]
        [string]$EvidenceDirectory,

        [Parameter(Mandatory = $true)]
        [string]$TrustedEvidenceRoot
    )

    $root = Assert-TrustedEvidenceRoot -TrustedEvidenceRoot $TrustedEvidenceRoot
    $directory = Get-NormalizedAbsolutePath -Path $EvidenceDirectory
    if (-not (Test-EvidencePathDescendsFrom -CandidatePath $directory -RootPath $root)) {
        throw "Evidence directory must descend from the trusted evidence root: $directory"
    }
    return [ordered]@{
        directory = $directory
        root = $root
    }
}

function New-TrustedEvidenceDirectory {
    [CmdletBinding(SupportsShouldProcess)]
    param(
        [Parameter(Mandatory = $true)]
        [string]$EvidenceDirectory,

        [Parameter(Mandatory = $true)]
        [string]$TrustedEvidenceRoot
    )

    $paths = Assert-EvidenceDirectoryWithinTrustedRoot `
        -EvidenceDirectory $EvidenceDirectory `
        -TrustedEvidenceRoot $TrustedEvidenceRoot
    $directory = $paths.directory
    $root = $paths.root
    if (Test-Path -LiteralPath $directory) {
        if (-not (Test-EvidencePathTreeWithoutReparsePoint -CandidatePath $directory -RootPath $root)) {
            throw "Evidence directory contains a reparse point: $directory"
        }
        throw "Evidence directory must be fresh: $directory"
    }

    $parent = [System.IO.Directory]::GetParent($directory)
    if ($null -eq $parent -or -not (Test-Path -LiteralPath $parent.FullName -PathType Container)) {
        throw "Evidence directory parent is not an existing directory: $directory"
    }
    if (-not (Test-EvidencePathEquality -Left $parent.FullName -Right $root) -and
        -not (Test-EvidencePathDescendsFrom -CandidatePath $parent.FullName -RootPath $root)) {
        throw "Evidence directory parent escapes the trusted evidence root: $directory"
    }
    if (-not (Test-EvidencePathTreeWithoutReparsePoint -CandidatePath $parent.FullName -RootPath $root)) {
        throw "Evidence directory parent contains a reparse point: $directory"
    }

    if (-not $PSCmdlet.ShouldProcess($directory, "create trusted Windows failure-evidence directory")) {
        throw "creation of trusted Windows failure-evidence directory was declined: $directory"
    }
    New-Item -ItemType Directory -Path $directory -ErrorAction Stop | Out-Null
    if (-not (Test-EvidencePathTreeWithoutReparsePoint -CandidatePath $directory -RootPath $root)) {
        throw "Evidence directory became a reparse point while it was created: $directory"
    }
    return $directory
}

function Assert-PreparedTrustedEvidenceDirectory {
    param(
        [Parameter(Mandatory = $true)]
        [string]$EvidenceDirectory,

        [Parameter(Mandatory = $true)]
        [string]$TrustedEvidenceRoot
    )

    $paths = Assert-EvidenceDirectoryWithinTrustedRoot `
        -EvidenceDirectory $EvidenceDirectory `
        -TrustedEvidenceRoot $TrustedEvidenceRoot
    $directory = $paths.directory
    if (-not (Test-Path -LiteralPath $directory -PathType Container)) {
        throw "Prepared evidence directory is not an existing directory: $directory"
    }
    if (-not (Test-EvidencePathTreeWithoutReparsePoint `
                -CandidatePath $directory `
                -RootPath $paths.root)) {
        throw "Prepared evidence directory contains a reparse point: $directory"
    }
    return $directory
}

function Write-NewTrustedEvidenceDocument {
    param(
        [Parameter(Mandatory = $true)]
        [string]$EvidenceDirectory,

        [Parameter(Mandatory = $true)]
        [string]$TrustedEvidenceRoot,

        [Parameter(Mandatory = $true)]
        [string]$Content
    )

    $directory = Assert-PreparedTrustedEvidenceDirectory `
        -EvidenceDirectory $EvidenceDirectory `
        -TrustedEvidenceRoot $TrustedEvidenceRoot
    $documentPath = Join-Path $directory 'fingrind-windows-failure-evidence.json'
    $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($Content)
    $stream = [System.IO.File]::Open(
        $documentPath,
        [System.IO.FileMode]::CreateNew,
        [System.IO.FileAccess]::Write,
        [System.IO.FileShare]::None
    )
    try {
        $stream.Write($bytes, 0, $bytes.Length)
    } finally {
        $stream.Dispose()
    }

    if (-not (Test-EvidencePathTreeWithoutReparsePoint `
                -CandidatePath $documentPath `
                -RootPath $TrustedEvidenceRoot)) {
        throw "Evidence document became a reparse point while it was written: $documentPath"
    }
    return $documentPath
}
