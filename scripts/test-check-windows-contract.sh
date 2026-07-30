#!/usr/bin/env bash
# Guard the explicit local Windows-contract preflight without treating it as native Windows proof.

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

path_without_directory() {
    local excluded_directory="$1"
    local path_entry
    local normalized_entry
    local filtered_path=''
    local -a path_entries=()
    IFS=':' read -r -a path_entries <<<"${PATH}"
    for path_entry in "${path_entries[@]}"; do
        [[ -n "${path_entry}" ]] || path_entry='.'
        normalized_entry="$(cd -P -- "${path_entry}" 2>/dev/null && pwd || printf '%s' "${path_entry}")"
        if [[ "${normalized_entry}" == "${excluded_directory}" ]]; then
            continue
        fi
        if [[ -n "${filtered_path}" ]]; then
            filtered_path+=':'
        fi
        filtered_path+="${path_entry}"
    done
    printf '%s' "${filtered_path}"
}

contains_path() {
    local expected_path="$1"
    shift
    local candidate_path
    for candidate_path in "$@"; do
        if [[ "${candidate_path}" == "${expected_path}" ]]; then
            return 0
        fi
    done
    return 1
}

readonly script_dir="$(resolve_script_dir)"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly stage_contract_script="${repo_root}/scripts/check-stage-contract.sh"
readonly windows_contract_script="${repo_root}/scripts/check-windows-contract.sh"
readonly windows_contract_runtime_support="${repo_root}/scripts/check-windows-contract-runtime-support.sh"
readonly powershell_metadata="${repo_root}/gradle/fingrind-build.properties"
readonly powershell_provisioner="${repo_root}/scripts/provision-powershell-runtime.py"
readonly python_runtime_support="${repo_root}/scripts/python-runtime-support.sh"
readonly git_executable="$(command -v git || true)"

[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
[[ -x "${windows_contract_script}" ]] || die \
    "local Windows-contract preflight is not executable at ${windows_contract_script}"
[[ -f "${powershell_metadata}" ]] || die "missing PowerShell metadata at ${powershell_metadata}"
[[ -f "${powershell_provisioner}" ]] || die "missing PowerShell provisioner at ${powershell_provisioner}"
[[ -f "${python_runtime_support}" ]] || die \
    "missing Python runtime support at ${python_runtime_support}"
[[ -n "${git_executable}" && -x "${git_executable}" ]] || die \
    "missing git required to derive the owned PowerShell source inventory"
"${git_executable}" -C "${repo_root}" rev-parse --is-inside-work-tree >/dev/null 2>&1 || die \
    "Windows-contract preflight regression requires a Git worktree at ${repo_root}"

# shellcheck source=/dev/null
source "${python_runtime_support}"
prepare_python_runtime_env || die \
    "Windows-contract preflight regression could not prepare the repository-owned exact Python runtime"
readonly python_executable="${FINGRIND_PYTHON_EXECUTABLE}"

# shellcheck source=/dev/null
source "${stage_contract_script}"

readonly required_pwsh_version="$(
    "${python_executable}" "${powershell_provisioner}" \
        --metadata "${powershell_metadata}" \
        --print-version
)"

contains_path 'scripts/check-windows-contract.sh' "${check_stage5_executable_script_paths[@]}" || die \
    "check stage contract no longer executes the mandatory Windows-contract preflight directly"
contains_path 'scripts/test-check-windows-contract.sh' "${check_stage5_executable_script_paths[@]}" || die \
    "check stage contract no longer exercises the Windows-contract preflight regression"
for script_path in "${check_windows_contract_preflight_script_paths[@]}"; do
    if contains_path "${script_path}" "${check_stage5_executable_script_paths[@]}"; then
        die "Windows-contract preflight subcheck is duplicated in the Stage 5 inventory: ${script_path}"
    fi
done

contains_path 'scripts/test-provision-powershell-runtime.sh' \
    "${check_windows_contract_preflight_script_paths[@]}" || die \
    "Windows-contract preflight no longer proves the checksum-pinned runtime provisioner"
contains_path 'scripts/test-powershell-quality-tools.sh' \
    "${check_windows_contract_preflight_script_paths[@]}" || die \
    "Windows-contract preflight no longer proves checksum-pinned Pester and PSScriptAnalyzer provisioning"
contains_path 'scripts/test-powershell-quality-runner.sh' \
    "${check_windows_contract_preflight_script_paths[@]}" || die \
    "Windows-contract preflight no longer proves that an empty Pester inventory is rejected"
contains_path 'scripts/test-verify-windows-publication-surface.sh' \
    "${check_windows_contract_preflight_script_paths[@]}" || die \
    "Windows-contract preflight no longer proves the shared Windows publication policy fixture"
contains_path 'scripts/test-windows-portable-archive-path-policy.sh' \
    "${check_windows_contract_preflight_script_paths[@]}" || die \
    "Windows-contract preflight no longer proves the canonical Windows-portable archive-path policy"
grep -Fq 'fingrind-build.properties' "${windows_contract_script}" || die \
    "Windows-contract preflight no longer derives its PowerShell pin from build metadata"
grep -Fq 'check-windows-contract-runtime-support.sh' "${windows_contract_script}" || die \
    "Windows-contract preflight no longer delegates runtime admission to its support owner"
grep -Fq 'FINGRIND_PWSH_EXECUTABLE' "${windows_contract_runtime_support}" || die \
    "Windows-contract preflight no longer accepts the provisioned pwsh executable explicitly"
grep -Fq 'export PATH="${pwsh_directory}:${PATH}"' "${windows_contract_runtime_support}" || die \
    "Windows-contract preflight no longer makes the exact provisioned pwsh first on PATH for subchecks"
grep -Fq 'export FINGRIND_PWSH_EXECUTABLE="${pwsh_executable}"' "${windows_contract_runtime_support}" || die \
    "Windows-contract preflight no longer exports the exact pwsh path for subchecks"
grep -Fq 'required_pwsh_version' "${windows_contract_runtime_support}" || die \
    "Windows-contract preflight no longer validates the exact pinned PowerShell version"
grep -Fq -- '--print-version' "${windows_contract_runtime_support}" || die \
    "Windows-contract preflight no longer queries the canonical PowerShell-version owner"
grep -Fq 'powershell-quality-tools.properties' "${windows_contract_runtime_support}" || die \
    "Windows-contract preflight no longer owns the immutable PowerShell quality-tool metadata"
grep -Fq 'provision-powershell-quality-tools.py' "${windows_contract_runtime_support}" || die \
    "Windows-contract preflight no longer provisions exact PowerShell quality tools"
grep -Fq 'invoke-powershell-quality.ps1' "${windows_contract_runtime_support}" || die \
    "Windows-contract preflight no longer invokes the owned PowerShell quality runner"
grep -Fq 'FINGRIND_POWERSHELL_QUALITY_TOOLS_ROOT' "${windows_contract_runtime_support}" || die \
    "Windows-contract preflight no longer owns an explicit private deterministic quality-tool root"
grep -Fq -- '-PesterTestPathsJson' "${windows_contract_script}" || die \
    "Windows-contract preflight no longer passes its Git-derived Pester test inventory to the quality runner"
grep -Fq '*.Tests.ps1' "${windows_contract_script}" || die \
    "Windows-contract preflight no longer derives Pester tests from the owned Git inventory"
if grep -Fq 'pwsh_major_version' "${windows_contract_script}"; then
    die "Windows-contract preflight still treats a PowerShell major-version floor as sufficient"
fi
grep -Fq 'Parser]::ParseFile' "${windows_contract_script}" || die \
    "Windows-contract preflight no longer parses every owned PowerShell script through the AST"
grep -Fq 'ls-files' "${windows_contract_script}" || die \
    "Windows-contract preflight no longer derives the owned PowerShell source inventory from Git"
grep -Fq -- '-z' "${windows_contract_script}" || die \
    "Windows-contract preflight no longer receives the owned PowerShell source inventory as NUL-delimited paths"
grep -Fq "read -r -d ''" "${windows_contract_script}" || die \
    "Windows-contract preflight no longer reads the owned PowerShell source inventory as NUL-delimited paths"
grep -Fq -- '--others' "${windows_contract_script}" || die \
    "Windows-contract preflight no longer includes in-flight owned PowerShell sources in AST coverage"
grep -Fq -- '--exclude-standard' "${windows_contract_script}" || die \
    "Windows-contract preflight no longer excludes ignored generated PowerShell files from AST coverage"
grep -Fq 'json.dumps' "${windows_contract_script}" || die \
    "Windows-contract preflight no longer transports owned PowerShell paths through JSON"
grep -Fq 'WindowsManagedSqliteCompilePlanTest' "${windows_contract_script}" || die \
    "Windows-contract preflight no longer executes the pure Windows MSVC command-plan test"
grep -Fq 'WindowsBundleManifestRendererTest' "${windows_contract_script}" || die \
    "Windows-contract preflight no longer executes the pure Windows bundle-manifest test"
grep -Fq 'WindowsBundleArchiveTaskContractTest' "${windows_contract_script}" || die \
    "Windows-contract preflight no longer executes the Windows archive-task configuration proof"
grep -Fq 'ValidateBundleArchiveMembersTaskTest' "${windows_contract_script}" || die \
    "Windows-contract preflight no longer executes staged archive-member validation coverage"
grep -Fq 'WindowsBundleStagingLayoutTest' "${windows_contract_script}" || die \
    "Windows-contract preflight no longer executes the synthetic Windows bundle-layout test"
grep -Fq 'SyntheticTargetBundleLayoutTest' "${windows_contract_script}" || die \
    "Windows-contract preflight no longer executes the synthetic target-layout materialization test"
grep -Fq ':cli:verifyTargetBundleLayout' "${windows_contract_script}" || die \
    "Windows-contract preflight no longer executes the canonical synthetic target-layout verifier"
grep -Fq -- '-PfingrindVerificationTargetClassifier=windows-x86_64' "${windows_contract_script}" || die \
    "Windows-contract preflight no longer verifies the published Windows target layout explicitly"
grep -Fq -- '--no-daemon' "${windows_contract_script}" || die \
    "Windows-contract preflight no longer uses an isolated focused Gradle invocation"
grep -Fq -- '--console=plain' "${windows_contract_script}" || die \
    "Windows-contract preflight no longer keeps focused Gradle output deterministic"

help_output="$("${windows_contract_script}" --help)"
printf '%s' "${help_output}" | grep -Fq 'not native Windows execution' || die \
    "Windows-contract preflight help no longer states its native-execution boundary"
printf '%s' "${help_output}" | grep -Fq 'repository-owned exact Python runtime, a Git worktree' || die \
    "Windows-contract preflight help no longer states its Python and Git worktree requirements"

owned_powershell_paths=()
while IFS= read -r -d '' owned_powershell_path; do
    owned_powershell_paths+=("${owned_powershell_path}")
done < <(
    "${git_executable}" -C "${repo_root}" ls-files \
        --cached \
        --others \
        --exclude-standard \
        -z \
        -- '*.ps1'
)
readonly expected_ast_count="${#owned_powershell_paths[@]}"
[[ ${expected_ast_count} -gt 0 ]] || die \
    "owned PowerShell source inventory is unexpectedly empty"
contains_path 'cli/src/bundle/bin/fingrind.ps1' "${owned_powershell_paths[@]}" || die \
    "owned PowerShell source inventory no longer includes the bundled launcher"
contains_path 'scripts/gradle-wrapper-support.ps1' "${owned_powershell_paths[@]}" || die \
    "owned PowerShell source inventory no longer includes the Gradle wrapper support owner"
contains_path 'scripts/setup-msvc-dev-cmd.ps1' "${owned_powershell_paths[@]}" || die \
    "owned PowerShell source inventory no longer includes the MSVC setup entrypoint"
contains_path 'scripts/verify-windows-publication-surface.ps1' "${owned_powershell_paths[@]}" || die \
    "owned PowerShell source inventory no longer includes the native Windows publication adapter"
contains_path 'scripts/verify-windows-publication-surface-support.ps1' "${owned_powershell_paths[@]}" || die \
    "owned PowerShell source inventory no longer includes the shared Windows publication policy owner"
contains_path 'scripts/write-windows-failure-evidence.ps1' "${owned_powershell_paths[@]}" || die \
    "owned PowerShell source inventory no longer includes the centralized failure-evidence writer"
contains_path 'scripts/windows-failure-evidence-output.ps1' "${owned_powershell_paths[@]}" || die \
    "owned PowerShell source inventory no longer includes the failure-evidence output boundary"

os_property_spoofs="$(
    rg -n -U \
        --glob '*.java' \
        --glob '*.kt' \
        'System\.(setProperty|clearProperty)\s*\(\s*"os\.(name|arch)"' \
        "${repo_root}" || true
)"
[[ -z "${os_property_spoofs}" ]] || die \
    "tests must inject platform inputs instead of mutating os.name or os.arch: ${os_property_spoofs}"

if [[ -n "${FINGRIND_PWSH_EXECUTABLE:-}" ]]; then
    pwsh_executable="${FINGRIND_PWSH_EXECUTABLE}"
else
    pwsh_executable="$(command -v pwsh || true)"
fi
readonly pwsh_executable
[[ -n "${pwsh_executable}" && -f "${pwsh_executable}" && -x "${pwsh_executable}" ]] || die \
    "Windows-contract regression requires exact pinned PowerShell ${required_pwsh_version}"
readonly pwsh_version="$("${pwsh_executable}" -NoLogo -NoProfile -NonInteractive -Command '$PSVersionTable.PSVersion.ToString()')"
[[ "${pwsh_version}" == "${required_pwsh_version}" ]] || die \
    "Windows-contract regression requires exact pinned PowerShell ${required_pwsh_version}; local pwsh reports ${pwsh_version}"

readonly temporary_root="$(mktemp -d "${repo_root}/tmp/check-windows-contract.XXXXXX")"
cleanup_temporary_root() {
    case "${temporary_root}" in
        "${repo_root}"/tmp/check-windows-contract.*) rm -rf -- "${temporary_root}" ;;
        *) die "refusing to remove an unexpected Windows-contract regression temporary path: ${temporary_root}" ;;
    esac
}
trap cleanup_temporary_root EXIT
readonly mismatched_pwsh="${temporary_root}/pwsh"
printf '%s\n' '#!/usr/bin/env bash' "printf '%s\\n' '7.6.3'" > "${mismatched_pwsh}"
chmod 700 "${mismatched_pwsh}"
set +e
mismatched_pwsh_output="$(
    FINGRIND_PWSH_EXECUTABLE="${mismatched_pwsh}" \
        /bin/bash "${windows_contract_script}" 2>&1
)"
mismatched_pwsh_status=$?
set -e
[[ ${mismatched_pwsh_status} -ne 0 ]] || die \
    "Windows-contract preflight ignored FINGRIND_PWSH_EXECUTABLE in favor of an ambient pwsh"
printf '%s\n' "${mismatched_pwsh_output}" | \
    grep -Fq "requires exact pinned PowerShell ${required_pwsh_version}; found 7.6.3" || die \
    "Windows-contract preflight no longer rejects a mismatched explicit pwsh executable first"

set +e
blank_pwsh_output="$(
    FINGRIND_PWSH_EXECUTABLE='' \
        /bin/bash "${windows_contract_script}" 2>&1
)"
blank_pwsh_status=$?
set -e
[[ ${blank_pwsh_status} -ne 0 ]] || die \
    "Windows-contract preflight treated a set blank FINGRIND_PWSH_EXECUTABLE as permission to fall back to PATH"
printf '%s\n' "${blank_pwsh_output}" | grep -Fq "requires exact pinned pwsh ${required_pwsh_version}" || die \
    "Windows-contract preflight no longer rejects a blank explicit pwsh executable"

transport_fixture_paths=(
    'scripts/space path/Žemaitija.ps1'
    $'scripts/newline\npath.ps1'
)
transport_fixture_json="$(
    "${python_executable}" - "${transport_fixture_paths[@]}" <<'PY'
import json
import sys

print(json.dumps(sys.argv[1:]))
PY
)"
transport_round_trip_json="$(
    FINGRIND_OWNED_POWERSHELL_SCRIPTS_JSON="${transport_fixture_json}" \
        "${pwsh_executable}" -NoLogo -NoProfile -Command '
            $scriptPaths = @($env:FINGRIND_OWNED_POWERSHELL_SCRIPTS_JSON | ConvertFrom-Json)
            $scriptPaths | ConvertTo-Json -Compress
        '
)"
"${python_executable}" - <<'PY' "${transport_fixture_json}" "${transport_round_trip_json}"
import json
import sys

expected = json.loads(sys.argv[1])
actual = json.loads(sys.argv[2])
if actual != expected:
    raise SystemExit(
        "owned PowerShell source JSON transport did not preserve spaces, Unicode, and newlines: "
        f"expected={expected!r}, actual={actual!r}"
    )
PY

readonly pwsh_directory="$(cd -P -- "$(dirname -- "${pwsh_executable}")" && pwd)"
readonly path_without_pwsh="$(path_without_directory "${pwsh_directory}")"
set +e
missing_pwsh_output="$(
    PATH="${path_without_pwsh}" \
        FINGRIND_PWSH_EXECUTABLE= \
        /bin/bash "${windows_contract_script}" 2>&1
)"
missing_pwsh_status=$?
set -e
[[ ${missing_pwsh_status} -ne 0 ]] || die \
    "Windows-contract preflight unexpectedly succeeded after the real pwsh executable was removed from PATH"
printf '%s\n' "${missing_pwsh_output}" | grep -Fq "requires exact pinned pwsh ${required_pwsh_version}" || die \
    "Windows-contract preflight no longer rejects a missing pwsh executable explicitly"

printf 'Windows-contract preflight regression: success\n'
