#!/usr/bin/env bash
# Exercise the shipped Ledger-1 public projection validator and deterministic generator.

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
readonly tool="${repo_root}/scripts/remediation_plan.py"
readonly requirements="${repo_root}/requirements-remediation-plan.txt"

[[ -x "${tool}" ]] || die "missing executable remediation-plan tool"
[[ -f "${requirements}" ]] || die "missing remediation-plan requirements lock"

# shellcheck source=/dev/null
source "${repo_root}/scripts/python-runtime-support.sh"
prepare_python_runtime_env

run_plan() {
    fingrind_run_python_with_requirements "${requirements}" "${tool}" "$@"
}

run_plan validate | grep -Fx 'remediation-plan: validation PASS' >/dev/null || die \
    "public remediation validation did not report success"
run_plan check | grep -Fx 'remediation-plan: generated-byte check PASS' >/dev/null || die \
    "public remediation byte comparison did not report success"
run_plan generate | grep -E '^remediation-plan: generation PASS operation=[0-9a-f]{32}$' >/dev/null || die \
    "public remediation generator did not report a durable operation ID"
run_plan check | grep -Fx 'remediation-plan: generated-byte check PASS' >/dev/null || die \
    "generated public remediation bytes did not remain exact"

readonly recovery_id='0123456789abcdef0123456789abcdef'
readonly recovery_stage="${repo_root}/tmp/remediation-plan-${recovery_id}.stage"
readonly recovery_journal="${repo_root}/tmp/remediation-plan-${recovery_id}.json"
cleanup_recovery_fixture() {
    rm -rf "${recovery_stage}"
    rm -f "${recovery_journal}"
}
trap cleanup_recovery_fixture EXIT
mkdir -p "${recovery_stage}/backups/remediation"
printf 'retained prior requirements\n' > "${recovery_stage}/backups/requirements-remediation-plan.txt"
printf '%s\n' \
    '{' \
    "  \"operationId\": \"${recovery_id}\"," \
    '  "schema": "urn:fingrind:remediation:journal:v1",' \
    "  \"stage\": \"tmp/remediation-plan-${recovery_id}.stage\"," \
    '  "state": "installed-remediation",' \
    '  "targets": ["remediation", "requirements-remediation-plan.txt"]' \
    '}' > "${recovery_journal}"
run_plan recover --operation-id "${recovery_id}" | grep -Fx \
    "remediation-plan: recovery PASS operation=${recovery_id}" >/dev/null || die \
    "recovery did not clean a completed installation with retained backups"
[[ ! -e "${recovery_stage}" && ! -e "${recovery_journal}" ]] || die \
    "recovery left its completed retained evidence behind"

set +e
invalid_output="$(run_plan recover --operation-id invalid 2>&1)"
invalid_status=$?
set -e
[[ ${invalid_status} -ne 0 ]] || die "recovery accepted an invalid operation ID"
printf '%s\n' "${invalid_output}" | grep -Fq 'lowercase 32-character hexadecimal value' || die \
    "recovery did not explain its operation-ID admission rule"

printf 'Remediation-plan regression: success\n'
