#!/usr/bin/env bash
# Run the local host-independent contract preflight for Windows-owned command surfaces.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

print_usage() {
    printf '%s\n' \
        'Usage: ./scripts/check-windows-contract.sh' \
        '' \
        'Runs the canonical local PowerShell parser and pure-behavior checks for Windows-owned command surfaces.' \
        'It requires the repository-owned exact Python runtime, a Git worktree, and the exact PowerShell release pinned in gradle/fingrind-build.properties.' \
        'The pinned runtime must be available through a real pwsh executable; Pester and PSScriptAnalyzer are provisioned from checksum-pinned PowerShell Gallery archives into a private deterministic tool root.' \
        'It is not native Windows execution or a substitute for it.' \
        '' \
        'This command does not accept additional arguments.'
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

for argument in "$@"; do
    case "${argument}" in
        -h|--help)
            print_usage
            exit 0
            ;;
        *)
            printf 'error: unsupported argument for check-windows-contract.sh: %s\n\n' \
                "${argument}" >&2
            print_usage >&2
            exit 1
            ;;
    esac
done

readonly script_dir="$(resolve_script_dir)"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
# shellcheck source=./check-windows-contract-runtime-support.sh
source "${script_dir}/check-windows-contract-runtime-support.sh"

# shellcheck source=/dev/null
source "${stage_contract_script}"

if [[ ${#check_windows_contract_preflight_script_paths[@]} -eq 0 ]]; then
    die 'canonical Windows-contract preflight inventory is empty'
fi

owned_powershell_scripts=()
production_powershell_scripts=()
pester_test_scripts=()
while IFS= read -r -d '' owned_powershell_relative_path; do
    owned_powershell_script="${repo_root}/${owned_powershell_relative_path}"
    owned_powershell_scripts+=("${owned_powershell_script}")
    if [[ "${owned_powershell_relative_path}" == *.Tests.ps1 ]]; then
        pester_test_scripts+=("${owned_powershell_script}")
    else
        production_powershell_scripts+=("${owned_powershell_script}")
    fi
done < <(
    "${git_executable}" -C "${repo_root}" ls-files \
        --cached \
        --others \
        --exclude-standard \
        -z \
        -- '*.ps1'
)
if [[ ${#production_powershell_scripts[@]} -eq 0 ]]; then
    die 'no production PowerShell scripts were found for analysis'
fi
if [[ ${#pester_test_scripts[@]} -eq 0 ]]; then
    die 'no owned Pester tests (*.Tests.ps1) were found'
fi
readonly owned_powershell_scripts_json="$(
    "${python_executable}" - "${owned_powershell_scripts[@]}" <<'PY'
import json
import sys

print(json.dumps(sys.argv[1:]))
PY
)"
readonly production_powershell_scripts_json="$(
    "${python_executable}" - "${production_powershell_scripts[@]}" <<'PY'
import json
import sys

print(json.dumps(sys.argv[1:]))
PY
)"
readonly pester_test_scripts_json="$(
    "${python_executable}" - "${pester_test_scripts[@]}" <<'PY'
import json
import sys

print(json.dumps(sys.argv[1:]))
PY
)"

printf '%s\n' \
    "Windows contract local preflight: using ${pwsh_executable} (${pwsh_version})" \
    'This verifies PowerShell parsing and host-independent pure behavior only; it is not native Windows execution.'
ast_parse_output="$(
    FINGRIND_OWNED_POWERSHELL_SCRIPTS_JSON="${owned_powershell_scripts_json}" \
        "${pwsh_executable}" -NoLogo -NoProfile -Command '
            $scriptPaths = @($env:FINGRIND_OWNED_POWERSHELL_SCRIPTS_JSON | ConvertFrom-Json)
            if ($scriptPaths.Count -eq 0) {
                throw "owned PowerShell script inventory is empty"
            }
            foreach ($scriptPath in $scriptPaths) {
                $tokens = $null
                $errors = $null
                [System.Management.Automation.Language.Parser]::ParseFile(
                    $scriptPath,
                    [ref] $tokens,
                    [ref] $errors
                ) | Out-Null
                if ($errors.Count -gt 0) {
                    $errors | ForEach-Object { "${scriptPath}: $($_.Message)" }
                    exit 1
                }
            }
            "parsed=$($scriptPaths.Count)"
        ' \
        2>&1
)" || die "owned PowerShell AST parsing failed: ${ast_parse_output}"
printf 'Windows contract AST parser: %s\n' "${ast_parse_output}"
printf '%s\n' \
    "windows-contract subcheck: PSScriptAnalyzer ${script_analyzer_version} and Pester ${pester_version}" \
    "PowerShell quality tool root: ${quality_tools_root}"
FINGRIND_REPOSITORY_ROOT="${repo_root}" \
    FINGRIND_PYTHON_EXECUTABLE="${python_executable}" \
    "${pwsh_executable}" -NoLogo -NoProfile -NonInteractive -File "${powershell_quality_runner}" \
    -ProductionScriptPathsJson "${production_powershell_scripts_json}" \
    -PesterTestPathsJson "${pester_test_scripts_json}" \
    -PesterManifest "${pester_manifest}" \
    -PesterVersion "${pester_version}" \
    -ScriptAnalyzerManifest "${script_analyzer_manifest}" \
    -ScriptAnalyzerVersion "${script_analyzer_version}"
for script_path in "${check_windows_contract_preflight_script_paths[@]}"; do
    [[ "${script_path}" == scripts/* ]] || die \
        "canonical Windows-contract preflight path is outside scripts/: ${script_path}"
    [[ -f "${repo_root}/${script_path}" ]] || die \
        "canonical Windows-contract preflight test is missing at ${repo_root}/${script_path}"
    printf 'windows-contract subcheck: %s\n' "${script_path}"
    bash "${repo_root}/${script_path}"
done

printf '%s\n' 'windows-contract subcheck: pure Windows MSVC, bundle-manifest, archive-member, staging-layout, and synthetic-layout build-logic tests'
"${gradlew}" \
    -p "${repo_root}/gradle/build-logic" \
    test \
    --tests dev.erst.fingrind.buildlogic.WindowsManagedSqliteCompilePlanTest \
    --tests dev.erst.fingrind.buildlogic.WindowsBundleManifestRendererTest \
    --tests dev.erst.fingrind.buildlogic.WindowsBundleArchiveTaskContractTest \
    --tests dev.erst.fingrind.buildlogic.ValidateBundleArchiveMembersTaskTest \
    --tests dev.erst.fingrind.buildlogic.WindowsBundleStagingLayoutTest \
    --tests dev.erst.fingrind.buildlogic.SyntheticTargetBundleLayoutTest \
    --no-daemon \
    --console=plain

printf '%s\n' 'windows-contract subcheck: synthetic Windows target layout through the canonical bundle verifier'
"${gradlew}" \
    :cli:verifyTargetBundleLayout \
    -PfingrindVerificationTargetClassifier=windows-x86_64 \
    --no-daemon \
    --console=plain

printf '%s\n' \
    'Windows contract local preflight: success (host-independent checks only; native Windows execution remains separately required).'
