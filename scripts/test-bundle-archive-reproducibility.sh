#!/usr/bin/env bash
# Verify that FinGrind's public bundle archive task emits byte-identical output across clean
# rebuilds of the same checkout.

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
readonly gradlew="${repo_root}/gradlew"
readonly gradle_wrapper_support="${repo_root}/scripts/gradle-wrapper-support.sh"
readonly python_runtime_support="${repo_root}/scripts/python-runtime-support.sh"
readonly bundle_contract_verifier="${repo_root}/scripts/verify-bundle-archive-contract.py"

[[ -x "${gradlew}" ]] || die "missing Gradle wrapper at ${gradlew}"
[[ -f "${gradle_wrapper_support}" ]] || die \
    "missing Gradle wrapper support helper at ${gradle_wrapper_support}"
[[ -f "${python_runtime_support}" ]] || die \
    "missing Python runtime support helper at ${python_runtime_support}"
[[ -f "${bundle_contract_verifier}" ]] || die \
    "missing bundle contract verifier at ${bundle_contract_verifier}"
# shellcheck source=/dev/null
source "${gradle_wrapper_support}"
# shellcheck source=/dev/null
source "${python_runtime_support}"

prepare_python_runtime_env

is_darwin=false
case "$(uname -s)" in
    Darwin) is_darwin=true ;;
esac

readonly gradle_user_home="${FINGRIND_GRADLE_USER_HOME:-$(fg_gradle_user_home_dir "${repo_root}" "${is_darwin}")}"
readonly bundle_archive_manifest_path="$(
    fg_gradle_bundle_archive_manifest_path "${repo_root}" 'cli' "${is_darwin}"
)"
readonly reproducibility_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-bundle-reproducibility.XXXXXX")"

cleanup() {
    rm -rf "${reproducibility_root}"
}
trap cleanup EXIT

mkdir -p "${gradle_user_home}"

build_bundle_archive() {
    env GRADLE_USER_HOME="${gradle_user_home}" \
        "${gradlew}" :cli:bundleCliArchive --no-daemon --console=plain >/dev/null
}

bundle_archive_path() {
    python3 -c 'import json, sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["archivePath"])' \
        "${bundle_archive_manifest_path}"
}

build_bundle_archive
archive_path_1="$(bundle_archive_path)"
[[ -f "${archive_path_1}" ]] || die \
    "bundle archive manifest did not point at a built archive after the first run: ${archive_path_1}"
cp "${archive_path_1}" "${reproducibility_root}/bundle-first.archive"
mkdir -p "${reproducibility_root}/bundle-first-extracted"
tar -xzf "${archive_path_1}" -C "${reproducibility_root}/bundle-first-extracted"
bundle_root_1="$(find "${reproducibility_root}/bundle-first-extracted" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
[[ -n "${bundle_root_1}" ]] || die "failed to find extracted bundle root under ${reproducibility_root}/bundle-first-extracted"
PYTHONPATH="${repo_root}/scripts${PYTHONPATH:+:${PYTHONPATH}}" \
    python3 "${bundle_contract_verifier}" --repo-root "${repo_root}" --bundle-root "${bundle_root_1}"

rm -f "${archive_path_1}" "${archive_path_1}.sha256"
build_bundle_archive
archive_path_2="$(bundle_archive_path)"
[[ -f "${archive_path_2}" ]] || die \
    "bundle archive manifest did not point at a built archive after the second run: ${archive_path_2}"
cp "${archive_path_2}" "${reproducibility_root}/bundle-second.archive"

cmp -s \
    "${reproducibility_root}/bundle-first.archive" \
    "${reproducibility_root}/bundle-second.archive" || die \
    "bundleCliArchive emitted different bytes across two rebuilds of the same checkout"

printf 'bundle archive reproducibility regression: success\n'
