#!/usr/bin/env bash
# Extract the self-contained FinGrind CLI bundle and run the public office-worker acceptance
# workflow without ambient Java or a preconfigured FINGRIND_SQLITE_LIBRARY.

set -euo pipefail

print_usage() {
    printf '%s\n' \
        'Usage: ./scripts/bundle-smoke.sh [bundle-archive-path]' \
        '' \
        'Extracts one self-contained FinGrind CLI bundle and runs the public office-worker acceptance workflow.' \
        'When no archive path is supplied, the script uses the host bundle produced under the active CLI Gradle build directory.'
}

for argument in "$@"; do
    case "${argument}" in
        -h|--help)
            print_usage
            exit 0
            ;;
    esac
done

# shellcheck source=/dev/null
source "$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/release-smoke-support.sh"

readonly script_dir="$(resolve_script_dir)"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly gradle_wrapper_support="${repo_root}/scripts/gradle-wrapper-support.sh"
readonly contract_values_reader="${repo_root}/scripts/read-contract-values.py"
readonly bundle_contract_verifier="${repo_root}/scripts/verify-bundle-archive-contract.py"
bundle_archive_path="${1:-}"
is_darwin=false
case "$(uname -s)" in
    Darwin) is_darwin=true ;;
esac

[[ -f "${gradle_wrapper_support}" ]] || die "missing Gradle wrapper support helper at ${gradle_wrapper_support}"
[[ -f "${contract_values_reader}" ]] || die "missing contract-values reader at ${contract_values_reader}"
[[ -f "${bundle_contract_verifier}" ]] || die \
    "missing bundle contract verifier at ${bundle_contract_verifier}"
# shellcheck source=/dev/null
source "${gradle_wrapper_support}"
readonly cli_build_dir="$(fg_gradle_project_build_dir "${repo_root}" 'cli' "${is_darwin}")"
readonly contract_values_json="$(python3 "${contract_values_reader}")"

contract_host_bundle_value() {
    local key=$1
    FINGRIND_CONTRACT_VALUES_JSON="${contract_values_json}" python3 - <<'PY' "${key}"
import json
import os
import sys

host_bundle_target = json.loads(os.environ["FINGRIND_CONTRACT_VALUES_JSON"])["bundleLayout"][
    "hostBundleTarget"
]
print(host_bundle_target[sys.argv[1]])
PY
}

readonly host_bundle_classifier="$(contract_host_bundle_value classifier)"
readonly host_bundle_archive_format="$(contract_host_bundle_value archiveFormat)"
readonly host_bundle_launcher_path="$(contract_host_bundle_value launcherPath)"

if [[ -z "${bundle_archive_path}" ]]; then
    readonly expected_bundle_archive_name="fingrind-$(project_version "${repo_root}")-${host_bundle_classifier}.${host_bundle_archive_format}"
    bundle_archive_path="${cli_build_dir}/distributions/${expected_bundle_archive_name}"
fi

[[ -f "${bundle_archive_path}" ]] || die "missing bundle archive at ${bundle_archive_path}"
readonly bundle_archive_path
readonly bundle_checksum_path="${bundle_archive_path}.sha256"
[[ -f "${bundle_checksum_path}" ]] || die "missing bundle checksum file at ${bundle_checksum_path}"

printf 'Bundle acceptance: using archive %s\n' "${bundle_archive_path}"

expected_archive_name="$(awk 'NF { print $2; exit }' "${bundle_checksum_path}")"
expected_archive_name="${expected_archive_name#\*}"
[[ "${expected_archive_name}" == "$(basename -- "${bundle_archive_path}")" ]] || die \
    "bundle checksum file ${bundle_checksum_path} does not match archive $(basename -- "${bundle_archive_path}")"

expected_archive_sha256="$(awk 'NF { print $1; exit }' "${bundle_checksum_path}")"
actual_archive_sha256="$(sha256_of "${bundle_archive_path}")"
[[ "${actual_archive_sha256}" == "${expected_archive_sha256}" ]] || die \
    "bundle archive checksum mismatch for ${bundle_archive_path}"

smoke_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-bundle-acceptance.XXXXXX")"
extract_root="${smoke_root}/extract"
work_root="${smoke_root}/workspace odd/Rīga büro/2026 Q2 close"
bundle_root=''
bundle_launcher=''

cleanup() {
    local exit_code=$?
    rm -rf "${smoke_root}" || true
    exit "${exit_code}"
}

trap cleanup EXIT

mkdir -p "${extract_root}" "${work_root}"
tar -xzf "${bundle_archive_path}" -C "${extract_root}"

extracted_roots=()
while IFS= read -r extracted_root; do
    extracted_roots+=("${extracted_root}")
done < <(find "${extract_root}" -mindepth 1 -maxdepth 1 -type d | sort)
[[ "${#extracted_roots[@]}" -eq 1 ]] || die \
    "expected exactly one extracted bundle root under ${extract_root}"
bundle_root="${extracted_roots[0]}"
bundle_launcher="${bundle_root}/${host_bundle_launcher_path}"

python3 "${bundle_contract_verifier}" \
    --repo-root "${repo_root}" \
    --bundle-root "${bundle_root}"

export FINGRIND_RELEASE_SMOKE_LABEL="Bundle acceptance"
export FINGRIND_RELEASE_SMOKE_REPO_ROOT="${repo_root}"
export FINGRIND_RELEASE_SMOKE_COMMAND_PREFIX_JSON="$(json_array_of_strings "${bundle_launcher}")"
export FINGRIND_RELEASE_SMOKE_COMMAND_ENV_DROP_JSON='["FINGRIND_SQLITE_LIBRARY","JAVA_HOME"]'
export FINGRIND_RELEASE_SMOKE_COMMAND_ENV_SET_JSON='{"PATH":"/usr/bin:/bin"}'
export FINGRIND_RELEASE_SMOKE_RUNTIME_DISTRIBUTION_KEY="bundleRuntimeDistribution"
export FINGRIND_RELEASE_SMOKE_EXPECT_LOADED_SQLITE_DETAILS="true"
export FINGRIND_RELEASE_SMOKE_EXPECT_BUNDLE_HOME_PROPERTY="true"
export FINGRIND_RELEASE_SMOKE_WORK_ROOT="${work_root}"
export FINGRIND_RELEASE_SMOKE_ARGUMENT_PATH_MODE="absolute"
export FINGRIND_RELEASE_SMOKE_SCENARIO_ID="bundle-acceptance"
export FINGRIND_RELEASE_SMOKE_BOOK_KEY_OUTPUT_PERMISSIONS="0600"
export FINGRIND_RELEASE_SMOKE_OPEN_BOOK_MODE='generated-key-stdin'

release_smoke_run_office_worker_acceptance
