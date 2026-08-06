#!/usr/bin/env bash
# Reproduce and guard the live GitHub security-policy verifier against drifting back to doc-only checks.

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
readonly verifier="${script_dir}/verify-security-policy-surface.sh"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly stage_contract_script="${repo_root}/scripts/check-stage-contract.sh"
readonly release_verifier="${repo_root}/scripts/verify-github-release.sh"
readonly release_verifier_support="${repo_root}/scripts/verify-github-release-support.sh"
readonly security_reference="${repo_root}/docs/DEVELOPER_SECURITY.md"
readonly security_policy="${repo_root}/SECURITY.md"
readonly dependabot_approval="${repo_root}/docs/DEVELOPER_DEPENDABOT_APPROVAL.md"

[[ -x "${verifier}" ]] || die "missing executable security-policy verifier"
[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
[[ -f "${release_verifier}" ]] || die "missing GitHub release verifier at ${release_verifier}"
[[ -f "${release_verifier_support}" ]] || die \
    "missing GitHub release verifier support owner at ${release_verifier_support}"
[[ -f "${security_reference}" ]] || die "missing developer security reference"
[[ -f "${security_policy}" ]] || die "missing SECURITY.md"
[[ -f "${dependabot_approval}" ]] || die "missing Dependabot approval policy"
grep -Fq 'scripts/test-verify-security-policy-surface.sh' "${stage_contract_script}" || die \
    "check stage contract no longer exercises the security-policy verifier regression"
grep -Fq 'verify-github-release-support.sh' "${release_verifier}" || die \
    "GitHub release verifier no longer delegates to its support owner"
grep -Fq 'verify-security-policy-surface.sh' "${release_verifier_support}" || die \
    "GitHub release verifier support no longer checks the live security-policy surface"
grep -Fq './scripts/verify-security-policy-surface.sh' "${security_reference}" || die \
    "developer security reference no longer points at the live security-policy verifier"
grep -Fq './scripts/verify-security-policy-surface.sh' "${security_policy}" || die \
    "SECURITY.md no longer points at the live security-policy verifier"
grep -Fq './scripts/verify-security-policy-surface.sh' "${dependabot_approval}" || die \
    "Dependabot approval policy no longer points at the live security-policy verifier"

fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-test-verify-security-policy.XXXXXX")"
cleanup() {
    rm -rf "${fixture_root}"
}
trap cleanup EXIT

mkdir -p "${fixture_root}/bin"

cat > "${fixture_root}/bin/gh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

repo="${FAKE_GH_REPOSITORY:-resoltico/FinGrind}"
private_reporting_enabled="${FAKE_GH_PRIVATE_REPORTING_ENABLED:-true}"
dependabot_security_updates_status="${FAKE_GH_DEPENDABOT_SECURITY_UPDATES_STATUS:-enabled}"
dependabot_alerts_status="${FAKE_GH_DEPENDABOT_ALERTS_STATUS:-enabled}"

if [[ "${1:-}" == "repo" && "${2:-}" == "view" ]]; then
    [[ "${3:-}" == "--json" && "${4:-}" == "nameWithOwner" && "${5:-}" == "--jq" && "${6:-}" == ".nameWithOwner" ]] || exit 1
    printf '%s\n' "${repo}"
    exit 0
fi

if [[ "${1:-}" == "api" && "${2:-}" == "/repos/${repo}/private-vulnerability-reporting" ]]; then
    [[ "${3:-}" == "--jq" && "${4:-}" == ".enabled" ]] || exit 1
    printf '%s\n' "${private_reporting_enabled}"
    exit 0
fi

if [[ "${1:-}" == "api" && "${2:-}" == "/repos/${repo}" ]]; then
    [[ "${3:-}" == "--jq" && "${4:-}" == ".security_and_analysis.dependabot_security_updates.status" ]] || exit 1
    printf '%s\n' "${dependabot_security_updates_status}"
    exit 0
fi

if [[ "${1:-}" == "api" && "${2:-}" == "/repos/${repo}/dependabot/alerts?state=open&per_page=1" ]]; then
    [[ "${3:-}" == "--jq" && "${4:-}" == 'if type == "array" then "enabled" else "invalid" end' ]] || exit 1
    printf '%s\n' "${dependabot_alerts_status}"
    exit 0
fi

exit 1
EOF
chmod +x "${fixture_root}/bin/gh"

run_verifier() {
    PATH="${fixture_root}/bin:${PATH}" \
        GITHUB_REPOSITORY='resoltico/FinGrind' \
        FAKE_GH_REPOSITORY='resoltico/FinGrind' \
        bash "${verifier}"
}

assert_rejected_setting() {
    local setting_environment_variable="$1"
    local expected_message="$2"
    local failure_output
    local failure_exit

    set +e
    failure_output="$(
        (
            export "${setting_environment_variable}=disabled"
            run_verifier
        ) 2>&1
    )"
    failure_exit=$?
    set -e

    if [[ ${failure_exit} -eq 0 ]]; then
        die "security-policy verifier accepted ${setting_environment_variable}=disabled"
    fi
    printf '%s\n' "${failure_output}" | grep -Fq "${expected_message}" || die \
        "security-policy verifier did not report ${setting_environment_variable}=disabled"
}

run_verifier >/dev/null
assert_rejected_setting 'FAKE_GH_PRIVATE_REPORTING_ENABLED' 'private vulnerability reporting is disabled'
assert_rejected_setting 'FAKE_GH_DEPENDABOT_SECURITY_UPDATES_STATUS' 'Dependabot security updates are disabled'
assert_rejected_setting 'FAKE_GH_DEPENDABOT_ALERTS_STATUS' 'Dependabot alerts are disabled'

printf 'Security-policy verifier regression: success\n'
