#!/usr/bin/env bash
# Prove that Windows failure evidence remains an allowlisted, normalized diagnostic artifact.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

resolve_script_dir() {
    local source_path="${BASH_SOURCE[0]}"
    while [[ -h "${source_path}" ]]; do
        local source_dir
        source_dir="$(cd -P -- "$(dirname -- "${source_path}")" && pwd)"
        source_path="$(readlink "${source_path}")"
        if [[ "${source_path}" != /* ]]; then
            source_path="${source_dir}/${source_path}"
        fi
    done
    cd -P -- "$(dirname -- "${source_path}")" && pwd
}

readonly script_dir="$(resolve_script_dir)"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly collector_script="${repo_root}/scripts/collect-windows-ci-failure-evidence.ps1"
readonly writer_script="${repo_root}/scripts/write-windows-failure-evidence.ps1"
readonly output_policy_script="${repo_root}/scripts/windows-failure-evidence-output.ps1"
readonly stage_contract_script="${repo_root}/scripts/check-stage-contract.sh"
readonly ci_workflow="${repo_root}/.github/workflows/ci.yml"

[[ -f "${collector_script}" ]] || die "missing Windows failure-evidence collector at ${collector_script}"
[[ -f "${writer_script}" ]] || die "missing Windows failure-evidence writer at ${writer_script}"
[[ -f "${output_policy_script}" ]] || die "missing Windows failure-evidence output policy at ${output_policy_script}"
[[ -f "${stage_contract_script}" ]] || die "missing check stage contract at ${stage_contract_script}"
[[ -f "${ci_workflow}" ]] || die "missing CI workflow at ${ci_workflow}"
grep -Fq 'scripts/test-collect-windows-ci-failure-evidence.sh' "${stage_contract_script}" || die \
    "check stage contract no longer runs the Windows failure-evidence regression"
grep -Fq 'collectionMode = '\''allowlisted-normalized'\''' "${collector_script}" || die \
    "Windows failure-evidence collector no longer declares its allowlisted normalization mode"
if grep -Fq 'Get-ChildItem Env:' "${collector_script}"; then
    die "Windows failure-evidence collector enumerates the environment instead of reading fixed allowlisted values"
fi
if grep -Fq 'Copy-Item' "${collector_script}"; then
    die "Windows failure-evidence collector copies raw workspace files"
fi
if grep -Fq 'Get-FileHash' "${collector_script}"; then
    die "Windows failure-evidence collector emits raw-content file fingerprints"
fi
if grep -Fq 'Get-ChildItem -Recurse' "${collector_script}"; then
    die "Windows failure-evidence collector recursively scans arbitrary workspace data"
fi
if grep -Fq 'Get-ExecutableVersion' "${collector_script}" || \
    grep -Fq 'Get-Command -Name' "${collector_script}"; then
    die "Windows failure-evidence collector executes PATH-selected tools instead of reading bounded allowlisted provenance"
fi
grep -Fq 'Get-DeclaredToolchainVersion' "${collector_script}" || die \
    "Windows failure-evidence collector no longer reads declared toolchain provenance without executing tools"
grep -Fq 'windows-failure-evidence-output.ps1' "${collector_script}" || die \
    "Windows failure-evidence collector no longer delegates output-path safety to its shared owner"
grep -Fq 'TrustedEvidenceRoot' "${collector_script}" || die \
    "Windows failure-evidence collector no longer requires an explicit trusted output root"
grep -Fq 'collect-windows-ci-failure-evidence.ps1' "${writer_script}" || die \
    "Windows failure-evidence writer no longer delegates collected evidence to the allowlisted collector"
grep -Fq 'windows-failure-evidence-output.ps1' "${writer_script}" || die \
    "Windows failure-evidence writer no longer delegates output-path safety to its shared owner"
grep -Fq 'collectionStatus = "fallback"' "${writer_script}" || die \
    "Windows failure-evidence writer no longer records a distinct fallback evidence shape"
if ! grep -Fq 'try {' "${writer_script}" || ! grep -Fq 'catch {' "${writer_script}"; then
    die "Windows failure-evidence writer no longer preserves the original proof when collection fails"
fi
grep -Fq 'Evidence directory must be fresh' "${output_policy_script}" || die \
    "Windows failure-evidence output policy no longer requires a freshly owned evidence directory"
grep -Fq 'reparse point' "${output_policy_script}" || die \
    "Windows failure-evidence output policy no longer rejects reparse-point output paths"
grep -Fq '[System.IO.FileMode]::CreateNew' "${output_policy_script}" || die \
    "Windows failure-evidence output policy no longer refuses to overwrite an existing document"

if ! command -v pwsh >/dev/null 2>&1; then
    printf 'Windows failure-evidence regression: skipped (pwsh unavailable)\n'
    exit 0
fi

for power_shell_script in "${output_policy_script}" "${collector_script}" "${writer_script}"; do
    parse_probe="$(
        pwsh -NoLogo -NoProfile -Command \
            "\$tokens = \$null; \$errors = \$null; [System.Management.Automation.Language.Parser]::ParseFile('${power_shell_script}', [ref] \$tokens, [ref] \$errors) | Out-Null; if (\$errors.Count -gt 0) { \$errors | ForEach-Object Message; exit 1 }" \
            2>&1
    )" || die "Windows failure-evidence owner no longer parses as valid PowerShell (${power_shell_script}): ${parse_probe}"
done

fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-windows-failure-evidence.XXXXXX")"
trap 'rm -rf -- "${fixture_root}"' EXIT
fixture_repo="${fixture_root}/repository"
evidence_dir="${fixture_root}/evidence"
mkdir -p \
    "${fixture_repo}/gradle/wrapper" \
    "${fixture_repo}/core/build/test-results/test" \
    "${fixture_repo}/cli/build/generated/bundle" \
    "${fixture_repo}/cli/build/distributions" \
    "${fixture_repo}/cli/build/reports/problems" \
    "${fixture_repo}/sqlite/build/managed-sqlite/windows-x86_64" \
    "${fixture_repo}/unrelated"

printf '%s\n' 'version=0.62.0' > "${fixture_repo}/gradle.properties"
printf '%s\n' \
    'fingrindJavaVersion=26' \
    'fingrindPythonVersion=3.12' \
    > "${fixture_repo}/gradle/fingrind-build.properties"
printf '%s\n' \
    'distributionUrl=https\://services.gradle.org/distributions/gradle-9.3.0-bin.zip' \
    > "${fixture_repo}/gradle/wrapper/gradle-wrapper.properties"
printf '%s\n' \
    '<testsuite tests="2" failures="1" errors="0" skipped="1"><testcase name="TOP-SECRET-TEST"><failure message="BOOK-PRIVATE-MARKER">KEY-PRIVATE-MARKER</failure></testcase><system-out>BOOK-PRIVATE-MARKER</system-out></testsuite>' \
    > "${fixture_repo}/core/build/test-results/test/TEST-private.xml"
printf '%s\n' 'BOOK-PRIVATE-MARKER KEY-PRIVATE-MARKER' \
    > "${fixture_repo}/cli/build/reports/problems/problems-report.html"
printf '%s\n' 'BOOK-PRIVATE-MARKER' > "${fixture_repo}/unrelated/book.fingrind"
printf '%s\n' \
    'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb  BOOK-PRIVATE-MARKER.zip' \
    > "${fixture_repo}/unrelated/BOOK-PRIVATE-MARKER.zip.sha256"
printf '%s\n' \
    'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa  fingrind-0.62.0-windows-x86_64.zip' \
    > "${fixture_repo}/cli/build/distributions/fingrind-0.62.0-windows-x86_64.zip.sha256"
printf '%s\n' \
    '{' \
    '  "archivePath": "/private/BOOK-PRIVATE-MARKER.zip",' \
    "  \"checksumPath\": \"${fixture_repo}/unrelated/BOOK-PRIVATE-MARKER.zip.sha256\"" \
    '}' \
    > "${fixture_repo}/cli/build/generated/bundle/bundle-archive-manifest.json"
printf '%s\n' \
    '{' \
    '  "sqliteVersion": "3.53.4",' \
    '  "operatingSystemId": "windows",' \
    '  "requiredCompileOptions": ["SAFE"],' \
    '  "forbiddenCompileOptions": [],' \
    '  "requiresSecureMemorySupport": "KEY-PRIVATE-MARKER"' \
    '}' \
    > "${fixture_repo}/sqlite/build/managed-sqlite/windows-x86_64/build-contract.json"

REPOSITORY_ROOT="${fixture_repo}" \
EVIDENCE_DIRECTORY="${evidence_dir}" \
TRUSTED_EVIDENCE_ROOT="${fixture_root}" \
WRITER_SCRIPT="${writer_script}" \
pwsh -NoLogo -NoProfile -Command '
    & $env:WRITER_SCRIPT `
        -RepositoryRoot $env:REPOSITORY_ROOT `
        -EvidenceDirectory $env:EVIDENCE_DIRECTORY `
        -TrustedEvidenceRoot $env:TRUSTED_EVIDENCE_ROOT `
        -CommitSha "0123456789abcdef0123456789abcdef01234567" `
        -RunId "271828"
' >/dev/null

readonly evidence_path="${evidence_dir}/fingrind-windows-failure-evidence.json"
[[ -f "${evidence_path}" ]] || die "Windows failure-evidence collector did not emit its allowlisted JSON document"
[[ "$(find "${evidence_dir}" -type f | wc -l | tr -d ' ')" == '1' ]] || die \
    "Windows failure-evidence collector wrote files other than its one allowlisted JSON document"

EVIDENCE_PATH="${evidence_path}" pwsh -NoLogo -NoProfile -Command '
    $raw = [System.IO.File]::ReadAllText($env:EVIDENCE_PATH, [System.Text.Encoding]::UTF8)
    foreach ($forbidden in @("BOOK-PRIVATE-MARKER", "KEY-PRIVATE-MARKER", "TOP-SECRET-TEST", "FinGrind-private.zip")) {
        if ($raw.Contains($forbidden)) {
            throw "failure evidence leaked a private fixture marker: $forbidden"
        }
    }
    if ($raw.Contains("`"sha256`"")) {
        throw "failure evidence still carries a raw-content file fingerprint field"
    }
    $evidence = $raw | ConvertFrom-Json
    if ($evidence.schemaVersion -ne 1) {
        throw "unexpected failure-evidence schema version: $($evidence.schemaVersion)"
    }
    if ($evidence.collectionStatus -ne "collected") {
        throw "failure evidence did not declare the collected-schema variant"
    }
    if ($evidence.privacy.collectionMode -ne "allowlisted-normalized" -or
        $evidence.privacy.rawLogsIncluded -or
        $evidence.privacy.workspaceFilesCopied -or
        $evidence.privacy.bookOrKeyFilesIncluded -or
        $evidence.privacy.environmentDumpIncluded) {
        throw "failure evidence no longer states the required privacy boundary"
    }
    if ($evidence.provenance.commitSha -ne "0123456789abcdef0123456789abcdef01234567" -or
        $evidence.provenance.runId -ne "271828") {
        throw "failure evidence did not preserve normalized CI provenance"
    }
    if ($evidence.runner.declaredJavaVersion -ne "26" -or
        $evidence.runner.declaredPythonVersion -ne "3.12") {
        throw "failure evidence did not preserve bounded declared toolchain provenance"
    }
    foreach ($retiredLiveProbe in @("javaVersion", "pythonVersion", "msvcVersion")) {
        if ($null -ne $evidence.runner.PSObject.Properties[$retiredLiveProbe]) {
            throw "failure evidence retained the unbounded live tool probe field: $retiredLiveProbe"
        }
    }
    $core = @($evidence.testResults | Where-Object { $_.scope -eq "core" })
    if ($core.Count -ne 1 -or
        $core[0].resultFileCount -ne 1 -or
        $core[0].tests -ne 2 -or
        $core[0].failures -ne 1 -or
        $core[0].errors -ne 0 -or
        $core[0].skipped -ne 1) {
        throw "failure evidence did not preserve the normalized JUnit summary"
    }
    $problemReport = @($evidence.gradleProblemReports | Where-Object { $_.scope -eq "cli" })
    if ($problemReport.Count -ne 1 -or -not $problemReport[0].report.present -or
        -not $problemReport[0].report.withinByteLimit -or $problemReport[0].report.bytes -le 0) {
        throw "failure evidence did not preserve the allowlisted Gradle-problem metadata"
    }
    if (-not $evidence.bundle.archiveManifest.present -or
        -not $evidence.bundle.checksumFile.present -or
        $evidence.bundle.archiveSha256 -ne "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa") {
        throw "failure evidence did not preserve the normalized bundle checksum provenance"
    }
    if ($null -ne $evidence.managedSqlite.buildContract.requiresSecureMemorySupport) {
        throw "failure evidence did not normalize the managed SQLite secure-memory field"
    }
    if ([string]::IsNullOrWhiteSpace($evidence.managedSqlite.buildContract.sanitizedFingerprint)) {
        throw "failure evidence did not derive the managed SQLite fingerprint from sanitized facts"
    }
' || die "Windows failure-evidence writer no longer emits the required safe structured summary"

fallback_evidence_dir="${fixture_root}/fallback-evidence"
REPOSITORY_ROOT="${fixture_root}/missing-repository" \
EVIDENCE_DIRECTORY="${fallback_evidence_dir}" \
TRUSTED_EVIDENCE_ROOT="${fixture_root}" \
WRITER_SCRIPT="${writer_script}" \
pwsh -NoLogo -NoProfile -Command '
    & $env:WRITER_SCRIPT `
        -RepositoryRoot $env:REPOSITORY_ROOT `
        -EvidenceDirectory $env:EVIDENCE_DIRECTORY `
        -TrustedEvidenceRoot $env:TRUSTED_EVIDENCE_ROOT `
        -CommitSha "0123456789abcdef0123456789abcdef01234567" `
        -RunId "271828"
' >/dev/null || die "Windows failure-evidence writer allowed a collection failure to obscure the original proof"

readonly fallback_evidence_path="${fallback_evidence_dir}/fingrind-windows-failure-evidence.json"
[[ -f "${fallback_evidence_path}" ]] || die "Windows failure-evidence writer did not emit its safe fallback document"
[[ "$(find "${fallback_evidence_dir}" -type f | wc -l | tr -d ' ')" == '1' ]] || die \
    "Windows failure-evidence writer wrote files other than its one fallback JSON document"

EVIDENCE_PATH="${fallback_evidence_path}" pwsh -NoLogo -NoProfile -Command '
    $raw = [System.IO.File]::ReadAllText($env:EVIDENCE_PATH, [System.Text.Encoding]::UTF8)
    $evidence = $raw | ConvertFrom-Json
    if ($evidence.schemaVersion -ne 1 -or $evidence.collectionStatus -ne "fallback") {
        throw "fallback evidence did not declare the versioned fallback schema"
    }
    if (@($evidence.PSObject.Properties).Count -ne 3) {
        throw "fallback evidence contains gathered facts instead of the minimal safe envelope"
    }
    if ($evidence.privacy.collectionMode -ne "allowlisted-normalized" -or
        $evidence.privacy.rawLogsIncluded -or
        $evidence.privacy.workspaceFilesCopied -or
        $evidence.privacy.bookOrKeyFilesIncluded -or
        $evidence.privacy.environmentDumpIncluded) {
        throw "fallback evidence no longer preserves the required privacy boundary"
    }
' || die "Windows failure-evidence writer no longer emits the required minimal fallback envelope"

outside_evidence_dir="${fixture_root}/outside-evidence"
reparse_evidence_dir="${fixture_root}/reparse-evidence"
mkdir -p "${outside_evidence_dir}"
ln -s "${outside_evidence_dir}" "${reparse_evidence_dir}"
set +e
reparse_output="$(
    REPOSITORY_ROOT="${fixture_repo}" \
    EVIDENCE_DIRECTORY="${reparse_evidence_dir}" \
    TRUSTED_EVIDENCE_ROOT="${fixture_root}" \
    WRITER_SCRIPT="${writer_script}" \
    pwsh -NoLogo -NoProfile -Command '
        & $env:WRITER_SCRIPT `
            -RepositoryRoot $env:REPOSITORY_ROOT `
            -EvidenceDirectory $env:EVIDENCE_DIRECTORY `
            -TrustedEvidenceRoot $env:TRUSTED_EVIDENCE_ROOT
    ' 2>&1
)"
reparse_status=$?
set -e
[[ ${reparse_status} -ne 0 ]] || die \
    "Windows failure-evidence writer accepted a reparse-point evidence directory"
[[ "${reparse_output}" == *"reparse point"* ]] || die \
    "Windows failure-evidence writer did not explain its reparse-point rejection: ${reparse_output}"
[[ ! -e "${outside_evidence_dir}/fingrind-windows-failure-evidence.json" ]] || die \
    "Windows failure-evidence writer followed a reparse point outside the trusted output root"

printf 'Windows failure-evidence regression: success\n'
