#!/usr/bin/env bash
# Prove the public bundle archive task prunes obsolete host-bundle archives and checksums.

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
readonly common_support="${repo_root}/scripts/release-smoke-common.sh"
readonly gradle_wrapper_support="${repo_root}/scripts/gradle-wrapper-support.sh"
readonly python_runtime_support="${repo_root}/scripts/python-runtime-support.sh"

[[ -f "${common_support}" ]] || die "missing release-smoke common helper at ${common_support}"
[[ -f "${gradle_wrapper_support}" ]] || die \
    "missing Gradle wrapper support helper at ${gradle_wrapper_support}"
[[ -f "${python_runtime_support}" ]] || die \
    "missing Python runtime support helper at ${python_runtime_support}"

# shellcheck source=/dev/null
source "${common_support}"
# shellcheck source=/dev/null
source "${gradle_wrapper_support}"
# shellcheck source=/dev/null
source "${python_runtime_support}"

prepare_python_runtime_env

is_darwin=false
case "$(uname -s)" in
    Darwin) is_darwin=true ;;
esac

readonly cli_build_dir="$(fg_gradle_project_build_dir "${repo_root}" 'cli' "${is_darwin}")"
readonly distributions_dir="${cli_build_dir}/distributions"
readonly legacy_distributions_dir="${repo_root}/cli/build/distributions"

read -r host_bundle_classifier host_bundle_archive_format < <(
    python3 - <<'PY' "${repo_root}"
import pathlib
import sys

repo_root = pathlib.Path(sys.argv[1])
sys.path.insert(0, str(repo_root / "scripts"))

from contract_values import load_contract_values  # noqa: E402

bundle_target = load_contract_values(repo_root)["bundleLayout"]["hostBundleTarget"]
print(bundle_target["classifier"], bundle_target["archiveFormat"])
PY
)

readonly current_archive_name="fingrind-$(project_version "${repo_root}")-${host_bundle_classifier}.${host_bundle_archive_format}"
readonly current_archive_path="${distributions_dir}/${current_archive_name}"
readonly current_checksum_path="${current_archive_path}.sha256"
readonly stale_archive_path="${distributions_dir}/fingrind-0.00.0-obsolete.${host_bundle_archive_format}"
readonly stale_checksum_path="${stale_archive_path}.sha256"
readonly stale_host_archive_path="${distributions_dir}/fingrind-9.99.9-${host_bundle_classifier}.${host_bundle_archive_format}"
readonly stale_host_checksum_path="${stale_host_archive_path}.sha256"
readonly legacy_stale_archive_path="${legacy_distributions_dir}/fingrind-legacy-obsolete.${host_bundle_archive_format}"
readonly legacy_stale_checksum_path="${legacy_stale_archive_path}.sha256"
readonly task_output_path="${repo_root}/tmp/test-bundle-archive-pruning.output"

cleanup() {
    rm -f "${task_output_path}"
}

trap cleanup EXIT

mkdir -p "${distributions_dir}"
printf 'obsolete bundle payload\n' > "${stale_archive_path}"
printf 'deadbeef *%s\n' "$(basename -- "${stale_archive_path}")" > "${stale_checksum_path}"
printf 'obsolete host bundle payload\n' > "${stale_host_archive_path}"
printf 'feedface *%s\n' "$(basename -- "${stale_host_archive_path}")" > "${stale_host_checksum_path}"
if [[ "${legacy_distributions_dir}" != "${distributions_dir}" ]]; then
    mkdir -p "${legacy_distributions_dir}"
    printf 'legacy obsolete bundle payload\n' > "${legacy_stale_archive_path}"
    printf 'cafebabe *%s\n' "$(basename -- "${legacy_stale_archive_path}")" > "${legacy_stale_checksum_path}"
fi

mkdir -p "${repo_root}/tmp"
./gradlew :cli:bundleCliArchive --no-daemon --console=plain | tee "${task_output_path}" >/dev/null

[[ ! -e "${stale_archive_path}" ]] || die "bundleCliArchive left the seeded obsolete archive behind"
[[ ! -e "${stale_checksum_path}" ]] || die "bundleCliArchive left the seeded obsolete checksum behind"
[[ ! -e "${stale_host_archive_path}" ]] || die \
    "bundleCliArchive left the seeded obsolete host archive behind"
[[ ! -e "${stale_host_checksum_path}" ]] || die \
    "bundleCliArchive left the seeded obsolete host checksum behind"
[[ -f "${current_archive_path}" ]] || die \
    "bundleCliArchive did not produce the current host bundle archive ${current_archive_path}"
[[ -f "${current_checksum_path}" ]] || die \
    "bundleCliArchive did not produce the current host bundle checksum ${current_checksum_path}"
if [[ "${legacy_distributions_dir}" != "${distributions_dir}" ]]; then
    [[ ! -e "${legacy_stale_archive_path}" ]] || die \
        "bundleCliArchive left the legacy checkout stale archive behind"
    [[ ! -e "${legacy_stale_checksum_path}" ]] || die \
        "bundleCliArchive left the legacy checkout stale checksum behind"
fi

mapfile -t versioned_distribution_entries < <(
    find "${distributions_dir}" -maxdepth 1 -name 'fingrind-*' -print | sort
)

[[ ${#versioned_distribution_entries[@]} -eq 2 ]] || die \
    "bundleCliArchive should leave exactly one host archive plus checksum under ${distributions_dir}"

for entry_path in "${versioned_distribution_entries[@]}"; do
    case "${entry_path}" in
        "${current_archive_path}"|"${current_checksum_path}") ;;
        *)
            die "bundleCliArchive left an unexpected versioned distribution artifact behind: ${entry_path}"
            ;;
    esac
done

if [[ "${legacy_distributions_dir}" != "${distributions_dir}" && -d "${legacy_distributions_dir}" ]]; then
    mapfile -t legacy_versioned_entries < <(
        find "${legacy_distributions_dir}" -maxdepth 1 -name 'fingrind-*' -print | sort
    )
    [[ ${#legacy_versioned_entries[@]} -eq 0 ]] || die \
        "bundleCliArchive should not leave versioned distribution artifacts behind under the legacy checkout path ${legacy_distributions_dir}"
fi

grep -Fqx "FinGrind bundle archive: ${current_archive_path}" "${task_output_path}" || die \
    "bundleCliArchive did not report the produced host bundle archive path ${current_archive_path}"
grep -Fqx "FinGrind bundle checksum: ${current_checksum_path}" "${task_output_path}" || die \
    "bundleCliArchive did not report the produced host bundle checksum path ${current_checksum_path}"

printf 'bundle archive pruning regression: success\n'
