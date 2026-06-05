#!/usr/bin/env bash
# Guard the shared release-smoke workflow wiring so Bash and PowerShell stay delegated.

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
readonly workflow_py="${repo_root}/scripts/release-smoke-workflow.py"
readonly workflow_contract_py="${repo_root}/scripts/test-release-smoke-workflow-contract.py"
readonly workflow_package_dir="${repo_root}/scripts/release_smoke_workflow"
readonly python_runtime_support="${repo_root}/scripts/python-runtime-support.sh"
readonly common_support_sh="${repo_root}/scripts/release-smoke-common.sh"
readonly workflow_support_sh="${repo_root}/scripts/release-smoke-workflow-support.sh"
readonly bundle_support_sh="${repo_root}/scripts/release-smoke-support.sh"
readonly bundle_office_worker_ps1="${repo_root}/scripts/bundle-smoke-office-worker.ps1"
readonly bundle_command_bridge_ps1="${repo_root}/scripts/bundle-smoke-command-bridge.ps1"
readonly bundle_smoke_sh="${repo_root}/scripts/bundle-smoke.sh"
readonly docker_smoke_sh="${repo_root}/scripts/docker-smoke.sh"

[[ -f "${workflow_py}" ]] || die "missing shared release smoke workflow runner at ${workflow_py}"
[[ -f "${workflow_contract_py}" ]] || die \
    "missing release smoke workflow contract regression owner at ${workflow_contract_py}"
[[ -d "${workflow_package_dir}" ]] || die "missing release smoke workflow package at ${workflow_package_dir}"
[[ -f "${python_runtime_support}" ]] || die "missing Python runtime support helper at ${python_runtime_support}"
[[ -f "${common_support_sh}" ]] || die "missing Bash release smoke common helper at ${common_support_sh}"
[[ -f "${workflow_support_sh}" ]] || die "missing Bash release smoke workflow support helper at ${workflow_support_sh}"
[[ -f "${bundle_support_sh}" ]] || die "missing Bash release smoke support wrapper at ${bundle_support_sh}"
[[ -f "${bundle_office_worker_ps1}" ]] || die "missing PowerShell office-worker wrapper at ${bundle_office_worker_ps1}"
[[ -f "${bundle_command_bridge_ps1}" ]] || die "missing PowerShell command bridge at ${bundle_command_bridge_ps1}"
[[ -f "${bundle_smoke_sh}" ]] || die "missing Bash bundle smoke entrypoint at ${bundle_smoke_sh}"
[[ -f "${docker_smoke_sh}" ]] || die "missing Bash Docker smoke entrypoint at ${docker_smoke_sh}"
grep -Fq 'release-smoke-workflow-support.sh' "${bundle_support_sh}" || die \
    "release-smoke-support.sh no longer sources the shared workflow support helper"
grep -Fq 'release-smoke-workflow.py' "${workflow_support_sh}" || die \
    "release-smoke-workflow-support.sh no longer delegates to the shared Python workflow owner"
grep -Fq 'release_smoke_workflow.runner import main' "${workflow_py}" || die \
    "release-smoke-workflow.py no longer delegates into the release_smoke_workflow package"
grep -Fq 'operation_ids["capabilities"], "--output", "json", "--detail", "full"' \
    "${workflow_package_dir}/discovery_checks.py" || die \
    "release smoke runtime verification no longer requests the full capabilities contract"
grep -Fq 'required_mapping(payload, "fullContract")' \
    "${workflow_package_dir}/discovery_assertions.py" || die \
    "release smoke assertions no longer require the full capabilities contract envelope"
grep -Fq 'required_mapping(full_contract, "responseModel")' \
    "${workflow_package_dir}/discovery_assertions.py" || die \
    "release smoke assertions no longer read responseModel from the full capabilities contract"
grep -Fq 'error_descriptor_exit_codes' "${workflow_package_dir}/discovery_assertions.py" || die \
    "release smoke assertions no longer derive published exit-code mappings from error descriptors"
grep -Fq 'error_exit_codes["protected-book-verification-failed"]' \
    "${workflow_package_dir}/rekey_failure_checks.py" || die \
    "release smoke wrong-key verification no longer uses the published protected-book verification exit code"
grep -Fq 'machine_prompt_failure_status == error_exit_codes["unsupported-output-selection"]' \
    "${workflow_package_dir}/request_failure_checks.py" || die \
    "release smoke machine-output prompt verification no longer uses the published unsupported-output-selection exit code"
grep -Fq 'terminal_prompt_failure_status == error_exit_codes["interactive-prompt-unavailable"]' \
    "${workflow_package_dir}/request_failure_checks.py" || die \
    "release smoke prompt verification no longer uses the published interactive prompt exit code"
grep -Fq 'error_exit_codes["invalid-request"]' \
    "${workflow_package_dir}/request_failure_checks.py" || die \
    "release smoke invalid-request verification no longer uses the published invalid-request exit code"
if grep -Fq 'required_mapping(payload, "responseModel")' \
    "${workflow_package_dir}/discovery_assertions.py"; then
    die "release smoke assertions still read responseModel from the compact capabilities payload"
fi
grep -Fq '"--effective-date-as-of"' "${workflow_package_dir}/query_checks.py" || die \
    "release smoke query verification no longer uses the canonical trial-balance as-of flag"
if grep -Fq 'instead of 2' "${workflow_package_dir}/request_failure_checks.py"; then
    die "release smoke failure verification still hardcodes retired exit-code expectations"
fi
python3 - <<'PY' "${workflow_package_dir}/query_checks.py"
from pathlib import Path
import sys

for raw_path in sys.argv[1:]:
    path = Path(raw_path)
    text = path.read_text(encoding="utf-8")
    cursor = 0
    found = False
    marker = 'operation_ids["trialBalance"]'
    while True:
        index = text.find(marker, cursor)
        if index < 0:
            break
        found = True
        window = text[index : index + 400]
        if '"--effective-date-as-of"' not in window:
            raise SystemExit(
                f"error: {path.name} no longer uses the canonical trial-balance as-of flag"
            )
        if '"--effective-date-to"' in window:
            raise SystemExit(
                f"error: {path.name} uses the retired effective-date-to flag for trial-balance verification"
            )
        cursor = index + len(marker)
    if not found:
        raise SystemExit(f"error: {path.name} no longer exercises the trial-balance command")
PY
grep -Fq 'release-smoke-workflow.py' "${bundle_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer delegates to the shared Python workflow owner"
grep -Fq 'bundle-smoke-command-bridge.ps1' "${bundle_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer delegates Windows command execution through the bridge owner"
grep -Fq 'FINGRIND_RELEASE_SMOKE_COMMAND_BRIDGE_PREFIX_JSON' "${bundle_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer publishes the Windows bridge command contract"
grep -Fq 'release-smoke-common.sh' "${bundle_support_sh}" || die \
    "release-smoke-support.sh no longer sources the shared common helper owner"
if grep -Fq 'release-smoke-fixtures.sh' "${bundle_support_sh}"; then
    die "release-smoke-support.sh still sources the deleted Bash fixture owner"
fi
if grep -Fq 'release-smoke-assertions.sh' "${bundle_support_sh}"; then
    die "release-smoke-support.sh still sources the deleted Bash assertion owner"
fi

assert_source_only_guard() {
    local script_path=$1
    local expected_fragment=$2
    local output
    local status

    set +e
    output="$(bash "${script_path}" 2>&1)"
    status=$?
    set -e

    [[ ${status} -ne 0 ]] || die "${script_path} unexpectedly succeeded when executed directly"
    [[ "${output}" == *"${expected_fragment}"* ]] || die \
        "${script_path} did not explain that it must be sourced"
}

assert_source_only_guard \
    "${common_support_sh}" \
    "release-smoke-common.sh is a library and must be sourced by a release-smoke support script."
assert_source_only_guard \
    "${bundle_support_sh}" \
    "release-smoke-support.sh is a library and must be sourced by a release-smoke entrypoint."
assert_source_only_guard \
    "${workflow_support_sh}" \
    "release-smoke-workflow-support.sh is a library and must be sourced by release-smoke-support.sh."
grep -Fq 'FINGRIND_RELEASE_SMOKE_WORK_ROOT' "${bundle_smoke_sh}" || die \
    "bundle-smoke.sh no longer publishes the compact shared work-root contract"
grep -Fq 'Bundle acceptance: using archive' "${bundle_smoke_sh}" || die \
    "bundle-smoke.sh no longer reports which archive the acceptance run selected"
grep -Fq 'FINGRIND_RELEASE_SMOKE_ARGUMENT_PATH_MODE' "${bundle_smoke_sh}" || die \
    "bundle-smoke.sh no longer publishes the shared argument-path-mode contract"
grep -Fq 'FINGRIND_RELEASE_SMOKE_SCENARIO_ID' "${bundle_smoke_sh}" || die \
    "bundle-smoke.sh no longer publishes the shared scenario-id contract"
grep -Fq 'FINGRIND_RELEASE_SMOKE_WORK_ROOT' "${docker_smoke_sh}" || die \
    "docker-smoke.sh no longer publishes the compact shared work-root contract"
grep -Fq 'FINGRIND_RELEASE_SMOKE_REPORTED_WORK_ROOT' "${docker_smoke_sh}" || die \
    "docker-smoke.sh no longer publishes the shared reported-work-root contract"
grep -Fq 'FINGRIND_RELEASE_SMOKE_ARGUMENT_PATH_MODE' "${docker_smoke_sh}" || die \
    "docker-smoke.sh no longer publishes the shared argument-path-mode contract"
grep -Fq 'FINGRIND_RELEASE_SMOKE_SCENARIO_ID' "${docker_smoke_sh}" || die \
    "docker-smoke.sh no longer publishes the shared scenario-id contract"
if grep -Fq 'FINGRIND_RELEASE_SMOKE_REQUEST_SALE_ARG' "${bundle_smoke_sh}"; then
    die "bundle-smoke.sh still exports legacy per-path release-smoke arguments"
fi
if grep -Fq 'FINGRIND_RELEASE_SMOKE_REQUEST_SALE_ARG' "${docker_smoke_sh}"; then
    die "docker-smoke.sh still exports legacy per-path release-smoke arguments"
fi

# shellcheck source=/dev/null
source "${python_runtime_support}"

prepare_python_runtime_env

python3 -m py_compile "${workflow_py}" "${workflow_package_dir}"/*.py >/dev/null
python3 -m py_compile "${workflow_contract_py}" >/dev/null
python3 "${workflow_contract_py}" "${repo_root}"

set +e
missing_env_output="$(python3 "${workflow_py}" 2>&1)"
missing_env_status=$?
set -e
[[ "${missing_env_status}" -ne 0 ]] || die \
    "shared release smoke workflow unexpectedly succeeded without required environment wiring"
printf '%s\n' "${missing_env_output}" | grep -Fq 'FINGRIND_RELEASE_SMOKE_COMMAND_PREFIX_JSON' || die \
    "shared release smoke workflow did not fail through its required-environment guard"

printf 'release smoke workflow wiring regression: success\n'
