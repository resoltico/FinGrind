param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $DirectoryPath
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = $utf8NoBom
$OutputEncoding = $utf8NoBom

$directory = [System.IO.DirectoryInfo]::new($DirectoryPath)
if (-not $directory.Exists) {
    throw "owner-only directory does not exist: $DirectoryPath"
}

$ownerSid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
if ($null -eq $ownerSid) {
    throw "could not resolve the current Windows owner SID"
}

$inheritance = [System.Security.AccessControl.InheritanceFlags]::None
$directorySecurity = [System.Security.AccessControl.DirectorySecurity]::new()
$directorySecurity.SetAccessRuleProtection($true, $false)
$directorySecurity.SetOwner($ownerSid)
$ownerOnlyRule = [System.Security.AccessControl.FileSystemAccessRule]::new(
    $ownerSid,
    [System.Security.AccessControl.FileSystemRights]::FullControl,
    $inheritance,
    [System.Security.AccessControl.PropagationFlags]::None,
    [System.Security.AccessControl.AccessControlType]::Allow
)
$directorySecurity.SetAccessRule($ownerOnlyRule)
[System.IO.FileSystemAclExtensions]::SetAccessControl($directory, $directorySecurity)

$appliedSecurity = [System.IO.FileSystemAclExtensions]::GetAccessControl(
    $directory,
    (
        [System.Security.AccessControl.AccessControlSections]::Access -bor
            [System.Security.AccessControl.AccessControlSections]::Owner
    )
)
if (-not $appliedSecurity.AreAccessRulesProtected) {
    throw "owner-only directory retained inherited access rules: $DirectoryPath"
}
$appliedOwner = $appliedSecurity.GetOwner([System.Security.Principal.SecurityIdentifier])
if ($appliedOwner.Value -ne $ownerSid.Value) {
    throw "owner-only directory owner differs from the current Windows owner: $DirectoryPath"
}
$appliedRules = @(
    $appliedSecurity.GetAccessRules(
        $true,
        $false,
        [System.Security.Principal.SecurityIdentifier]
    )
)
if ($appliedRules.Count -ne 1) {
    throw "owner-only directory grants access to a principal other than its owner: $DirectoryPath"
}
$appliedRule = $appliedRules[0]
if (
    $appliedRule.IdentityReference.Value -ne $ownerSid.Value -or
    $appliedRule.AccessControlType -ne [System.Security.AccessControl.AccessControlType]::Allow -or
    $appliedRule.FileSystemRights -ne [System.Security.AccessControl.FileSystemRights]::FullControl -or
    $appliedRule.InheritanceFlags -ne $inheritance -or
    $appliedRule.PropagationFlags -ne [System.Security.AccessControl.PropagationFlags]::None
) {
    throw "owner-only directory does not grant exactly one non-inheritable full-control rule to its owner: $DirectoryPath"
}
