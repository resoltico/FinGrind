#!/usr/bin/env bash
# Extract the self-contained FinGrind CLI bundle and run the public office-worker acceptance
# workflow without ambient Java or a preconfigured FINGRIND_SQLITE_LIBRARY.

set -euo pipefail

print_usage() {
    printf '%s\n' \
        'Usage: ./scripts/bundle-smoke.sh [bundle-archive-path]' \
        '' \
        'Extracts one self-contained FinGrind CLI bundle and runs the public office-worker acceptance workflow.' \
        'When no archive path is supplied, the script uses the host bundle produced under cli/build/distributions/.'
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
bundle_archive_path="${1:-}"
is_darwin=false
case "$(uname -s)" in
    Darwin) is_darwin=true ;;
esac

[[ -f "${gradle_wrapper_support}" ]] || die "missing Gradle wrapper support helper at ${gradle_wrapper_support}"
[[ -f "${contract_values_reader}" ]] || die "missing contract-values reader at ${contract_values_reader}"
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

contract_runtime_environment_value() {
    local key=$1
    FINGRIND_CONTRACT_VALUES_JSON="${contract_values_json}" python3 - <<'PY' "${key}"
import json
import os
import sys

runtime_environment = json.loads(os.environ["FINGRIND_CONTRACT_VALUES_JSON"])[
    "runtimeEnvironment"
]
print(runtime_environment[sys.argv[1]])
PY
}

readonly host_bundle_classifier="$(contract_host_bundle_value classifier)"
readonly host_bundle_archive_format="$(contract_host_bundle_value archiveFormat)"
readonly host_bundle_launcher_path="$(contract_host_bundle_value launcherPath)"
readonly host_bundle_native_library_name="$(contract_host_bundle_value sqliteLibraryFileName)"
readonly source_checkout_java="$(contract_runtime_environment_value sourceCheckoutJava)"

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

[[ -x "${bundle_launcher}" ]] || die "missing executable bundle launcher at ${bundle_launcher}"
[[ -x "${bundle_root}/runtime/bin/java" ]] || die \
    "missing bundled Java runtime at ${bundle_root}/runtime/bin/java"
[[ -f "${bundle_root}/lib/app/fingrind.jar" ]] || die \
    "missing bundled FinGrind application JAR at ${bundle_root}/lib/app/fingrind.jar"
[[ -f "${bundle_root}/lib/native/${host_bundle_native_library_name}" ]] || die \
    "missing bundled native SQLite library under ${bundle_root}/lib/native"

for bundle_file in \
    LICENSE \
    LICENSE-APACHE-2.0 \
    LICENSE-SIL-OFL-1.1 \
    NOTICE \
    LICENSE-SQLITE3MULTIPLECIPHERS \
    PATENTS.md \
    README.md \
    bundle-manifest.json; do
    [[ -f "${bundle_root}/${bundle_file}" ]] || die "missing ${bundle_file} in bundle root"
done

bundle_readme="$(tr -d '\r' < "${bundle_root}/README.md")"
require_match "${bundle_readme}" '^# FinGrind ' \
    "bundle README did not start with the FinGrind title"
require_match "${bundle_readme}" 'bundle-manifest\.json' \
    "bundle README did not mention the machine-readable bundle manifest"

bundle_manifest="$(tr -d '\r' < "${bundle_root}/bundle-manifest.json")"
bundle_manifest_compact="$(printf '%s' "${bundle_manifest}" | tr -d '[:space:]')"
require_no_match "${bundle_manifest_compact}" '"discoveryCommands":' \
    "bundle manifest still reauthored static command-group arrays instead of pointing to the canonical contract"
require_no_match "${bundle_manifest_compact}" '"administrationCommands":' \
    "bundle manifest still reauthored static command-group arrays instead of pointing to the canonical contract"
require_no_match "${bundle_manifest_compact}" '"queryCommands":' \
    "bundle manifest still reauthored static command-group arrays instead of pointing to the canonical contract"
require_no_match "${bundle_manifest_compact}" '"writeCommands":' \
    "bundle manifest still reauthored static command-group arrays instead of pointing to the canonical contract"

FINGRIND_CONTRACT_VALUES_JSON="${contract_values_json}" python3 - <<'PY' \
    "${bundle_root}/bundle-manifest.json" \
    "${host_bundle_classifier}"
import json
import os
import sys

manifest_path = sys.argv[1]
host_classifier = sys.argv[2]
manifest = json.load(open(manifest_path, encoding="utf-8"))
contract = json.loads(os.environ["FINGRIND_CONTRACT_VALUES_JSON"])
runtime_surface = contract["runtimeSurface"]
public_distribution = contract["publicDistribution"]
operation_ids = contract["operationIds"]
host_bundle_target = contract["bundleLayout"]["hostBundleTarget"]

checks = [
    (
        manifest["runtimeDistribution"] == runtime_surface["bundleRuntimeDistribution"],
        "bundle manifest did not report the self-contained runtime distribution",
    ),
    (
        manifest["publicCliDistribution"] == runtime_surface["publicCliDistribution"],
        "bundle manifest did not report the public bundle distribution contract",
    ),
    (
        manifest["managedSqlite"]["storageDriver"] == runtime_surface["storageDriver"],
        "bundle manifest did not report the canonical storage driver",
    ),
    (
        manifest["managedSqlite"]["storageEngine"] == runtime_surface["storageEngine"],
        "bundle manifest did not report the canonical storage engine",
    ),
    (
        manifest["managedSqlite"]["bookProtectionMode"] == runtime_surface["bookProtectionMode"],
        "bundle manifest did not report the canonical book protection mode",
    ),
    (
        manifest["managedSqlite"]["defaultBookCipher"] == runtime_surface["defaultBookCipher"],
        "bundle manifest did not report the canonical default book cipher",
    ),
    (
        manifest["managedSqlite"]["libraryMode"] == runtime_surface["sqliteLibraryMode"],
        "bundle manifest did not report the canonical SQLite library mode",
    ),
    (
        manifest["managedSqlite"]["requiredMinimumSqliteVersion"]
        == contract["managedSqlite"]["requiredMinimumSqliteVersion"],
        "bundle manifest did not report the canonical minimum SQLite version",
    ),
    (
        manifest["managedSqlite"]["requiredSqlite3mcVersion"]
        == contract["managedSqlite"]["requiredSqlite3mcVersion"],
        "bundle manifest did not report the canonical SQLite3 Multiple Ciphers version",
    ),
    (
        manifest["managedSqlite"]["requiredSqliteSourceId"]
        == contract["managedSqlite"]["requiredSqliteSourceId"],
        "bundle manifest did not report the canonical SQLite source id",
    ),
    (
        manifest["managedSqlite"]["requiredCompileOptions"]
        == contract["managedSqlite"]["requiredCompileOptions"],
        "bundle manifest did not report the canonical SQLite compile options",
    ),
    (
        manifest["bundleTarget"]["classifier"] == host_bundle_target["classifier"] == host_classifier,
        "bundle manifest did not report the current host classifier",
    ),
    (
        manifest["archiveFormat"] == host_bundle_target["archiveFormat"],
        "bundle manifest did not report the platform-native archive format",
    ),
    (
        manifest["launcher"] == host_bundle_target["launcherPath"],
        "bundle manifest did not report the canonical launcher path",
    ),
    (
        manifest["supportedPublicCliBundleTargets"]
        == public_distribution["supportedPublicCliBundleTargets"],
        "bundle manifest did not report the supported public bundle targets",
    ),
    (
        manifest["unsupportedPublicCliBundleTargets"]
        == public_distribution["unsupportedPublicCliBundleTargets"],
        "bundle manifest did not report the current unsupported public bundle targets",
    ),
    (
        manifest["bootstrap"]["recommendedFirstCommand"][-1] == operation_ids["help"],
        "bundle manifest did not publish the canonical bootstrap help command",
    ),
    (
        manifest["bootstrap"]["machineReadableContractCommand"][-1]
        == operation_ids["capabilities"],
        "bundle manifest did not publish the canonical machine-readable contract command",
    ),
    (
        manifest["bootstrap"]["requestTemplateCommand"][-1]
        == operation_ids["printRequestTemplate"],
        "bundle manifest did not publish the canonical request-template bootstrap command",
    ),
    (
        manifest["bootstrap"]["planTemplateCommand"][-1]
        == operation_ids["printPlanTemplate"],
        "bundle manifest did not publish the canonical plan-template bootstrap command",
    ),
]

for passed, message in checks:
    if not passed:
        print(message, file=sys.stderr)
        raise SystemExit(1)
PY

require_java_runtime_version "${bundle_root}/runtime/bin/java" "${source_checkout_java}"
runtime_modules_output="$("${bundle_root}/runtime/bin/java" --list-modules | tr -d '\r')"
require_no_match "${runtime_modules_output}" '^jdk\.jlink@' \
    "bundled Java runtime still contains jdk.jlink"
require_no_match "${runtime_modules_output}" '^jdk\.jpackage@' \
    "bundled Java runtime still contains jdk.jpackage"
require_no_match "${runtime_modules_output}" '^jdk\.jdeps@' \
    "bundled Java runtime still contains jdk.jdeps"
require_match "${runtime_modules_output}" '^jdk\.unsupported@' \
    "bundled Java runtime omitted jdk.unsupported, which PDF export requires for a noise-free runtime"

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
