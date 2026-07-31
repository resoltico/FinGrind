param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $FilePath
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = $utf8NoBom
$OutputEncoding = $utf8NoBom

$file = [System.IO.FileInfo]::new($FilePath)
if (-not $file.Exists -or ($file.Attributes -band [System.IO.FileAttributes]::ReparsePoint)) {
    throw "owner-only file must be one existing regular non-reparse-point file: $FilePath"
}

$ownerSid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
if ($null -eq $ownerSid) {
    throw "could not resolve the current Windows owner SID"
}

$fileSecurity = [System.Security.AccessControl.FileSecurity]::new()
$fileSecurity.SetAccessRuleProtection($true, $false)
$fileSecurity.SetOwner($ownerSid)
$ownerOnlyRule = [System.Security.AccessControl.FileSystemAccessRule]::new(
    $ownerSid,
    [System.Security.AccessControl.FileSystemRights]::FullControl,
    [System.Security.AccessControl.AccessControlType]::Allow
)
$fileSecurity.SetAccessRule($ownerOnlyRule)
[System.IO.FileSystemAclExtensions]::SetAccessControl($file, $fileSecurity)

$appliedSecurity = [System.IO.FileSystemAclExtensions]::GetAccessControl(
    $file,
    (
        [System.Security.AccessControl.AccessControlSections]::Access -bor
            [System.Security.AccessControl.AccessControlSections]::Owner
    )
)
if (-not $appliedSecurity.AreAccessRulesProtected) {
    throw "owner-only file retained inherited access rules: $FilePath"
}
$appliedOwner = $appliedSecurity.GetOwner([System.Security.Principal.SecurityIdentifier])
if ($appliedOwner.Value -ne $ownerSid.Value) {
    throw "owner-only file owner differs from the current Windows owner: $FilePath"
}
$appliedRules = @(
    $appliedSecurity.GetAccessRules(
        $true,
        $false,
        [System.Security.Principal.SecurityIdentifier]
    )
)
if ($appliedRules.Count -ne 1) {
    throw "owner-only file grants access to a principal other than its owner: $FilePath"
}
$appliedRule = $appliedRules[0]
if (
    $appliedRule.IdentityReference.Value -ne $ownerSid.Value -or
    $appliedRule.AccessControlType -ne [System.Security.AccessControl.AccessControlType]::Allow -or
    $appliedRule.FileSystemRights -ne [System.Security.AccessControl.FileSystemRights]::FullControl -or
    $appliedRule.InheritanceFlags -ne [System.Security.AccessControl.InheritanceFlags]::None -or
    $appliedRule.PropagationFlags -ne [System.Security.AccessControl.PropagationFlags]::None
) {
    throw "owner-only file does not grant exactly one full-control rule to its owner: $FilePath"
}
