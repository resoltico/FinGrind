#!/usr/bin/env bash
# Extract the self-contained FinGrind CLI bundle and verify that it runs without ambient Java or a
# preconfigured FINGRIND_SQLITE_LIBRARY.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

require_match() {
    local text=$1
    local pattern=$2
    local message=$3

    if ! printf '%s\n' "${text}" | grep -Eq "${pattern}"; then
        die "${message}"
    fi
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

sha256_of() {
    local file_path=$1
    if command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "${file_path}" | awk '{print $1}'
        return
    fi
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "${file_path}" | awk '{print $1}'
        return
    fi
    die "neither shasum nor sha256sum is available for bundle checksum verification"
}

posix_mode() {
    local file_path=$1
    if stat -f '%A' "${file_path}" >/dev/null 2>&1; then
        stat -f '%A' "${file_path}"
        return
    fi
    stat -c '%a' "${file_path}"
}

expected_native_library_name() {
    local uname_s
    uname_s="$(uname -s)"
    case "${uname_s}" in
        Darwin) printf '%s\n' 'libsqlite3.dylib' ;;
        Linux) printf '%s\n' 'libsqlite3.so.0' ;;
        *) die "unsupported bundle smoke operating system: ${uname_s}" ;;
    esac
}

host_bundle_classifier() {
    local operating_system architecture
    operating_system="$(uname -s)"
    case "${operating_system}" in
        Darwin) operating_system='macos' ;;
        Linux) operating_system='linux' ;;
        *) die "unsupported bundle smoke operating system: ${operating_system}" ;;
    esac

    architecture="$(uname -m)"
    case "${architecture}" in
        arm64|aarch64) architecture='aarch64' ;;
        amd64|x86_64|x64) architecture='x86_64' ;;
        *)
            architecture="$(printf '%s' "${architecture}" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g')"
            ;;
    esac

    printf '%s-%s\n' "${operating_system}" "${architecture}"
}

project_version() {
    local version
    version="$(awk -F= '/^version=/{print $2; exit}' "${repo_root}/gradle.properties")"
    [[ -n "${version}" ]] || die "could not determine project version from ${repo_root}/gradle.properties"
    printf '%s\n' "${version}"
}

run_bundle_command() {
    env -u FINGRIND_SQLITE_LIBRARY -u JAVA_HOME \
        PATH="/usr/bin:/bin" \
        "${bundle_launcher}" "$@"
}

readonly script_dir="$(resolve_script_dir)"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
bundle_archive_path="${1:-}"

if [[ -z "${bundle_archive_path}" ]]; then
    readonly expected_bundle_archive_name="fingrind-$(project_version)-$(host_bundle_classifier).tar.gz"
    bundle_archive_path="${repo_root}/cli/build/distributions/${expected_bundle_archive_name}"
fi

[[ -f "${bundle_archive_path}" ]] || die "missing bundle archive at ${bundle_archive_path}"
readonly bundle_archive_path
readonly bundle_checksum_path="${bundle_archive_path}.sha256"
[[ -f "${bundle_checksum_path}" ]] || die "missing bundle checksum file at ${bundle_checksum_path}"

expected_archive_name="$(awk 'NF { print $2; exit }' "${bundle_checksum_path}")"
expected_archive_name="${expected_archive_name#\*}"
[[ "${expected_archive_name}" == "$(basename -- "${bundle_archive_path}")" ]] || die \
    "bundle checksum file ${bundle_checksum_path} does not match archive $(basename -- "${bundle_archive_path}")"

expected_archive_sha256="$(awk 'NF { print $1; exit }' "${bundle_checksum_path}")"
actual_archive_sha256="$(sha256_of "${bundle_archive_path}")"
[[ "${actual_archive_sha256}" == "${expected_archive_sha256}" ]] || die \
    "bundle archive checksum mismatch for ${bundle_archive_path}"

smoke_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-bundle-smoke.XXXXXX")"
extract_root="${smoke_root}/extract"
work_root="${smoke_root}/workspace odd"
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

mapfile -t extracted_roots < <(find "${extract_root}" -mindepth 1 -maxdepth 1 -type d | sort)
[[ "${#extracted_roots[@]}" -eq 1 ]] || die \
    "expected exactly one extracted bundle root under ${extract_root}"
bundle_root="${extracted_roots[0]}"
bundle_launcher="${bundle_root}/bin/fingrind"

[[ -x "${bundle_launcher}" ]] || die "missing executable bundle launcher at ${bundle_launcher}"
[[ -x "${bundle_root}/runtime/bin/java" ]] || die \
    "missing bundled Java runtime at ${bundle_root}/runtime/bin/java"
[[ -f "${bundle_root}/lib/app/fingrind.jar" ]] || die \
    "missing bundled FinGrind application JAR at ${bundle_root}/lib/app/fingrind.jar"
[[ -f "${bundle_root}/lib/native/$(expected_native_library_name)" ]] || die \
    "missing bundled native SQLite library under ${bundle_root}/lib/native"
[[ -f "${bundle_root}/LICENSE" ]] || die "missing LICENSE in bundle root"
[[ -f "${bundle_root}/LICENSE-APACHE-2.0" ]] || die "missing LICENSE-APACHE-2.0 in bundle root"
[[ -f "${bundle_root}/NOTICE" ]] || die "missing NOTICE in bundle root"
[[ -f "${bundle_root}/LICENSE-SQLITE3MULTIPLECIPHERS" ]] || die \
    "missing LICENSE-SQLITE3MULTIPLECIPHERS in bundle root"
[[ -f "${bundle_root}/PATENTS.md" ]] || die "missing PATENTS.md in bundle root"

runtime_version_output="$("${bundle_root}/runtime/bin/java" --version | tr -d '\r')"
require_match "${runtime_version_output}" '^openjdk 26 ' \
    "bundled Java runtime did not report Java 26"

readonly request_path="${work_root}/requests odd/request [bundle #smoke].json"
readonly declare_cash_path="${work_root}/requests odd/declare account cash [bundle #smoke].json"
readonly declare_revenue_path="${work_root}/requests odd/declare account revenue [bundle #smoke].json"
readonly book_path="${work_root}/books odd/nested/entity [bundle #smoke].sqlite"
readonly book_key_path="${work_root}/books odd/nested/entity [bundle #smoke].key"
readonly wrong_book_key_path="${work_root}/books odd/nested/entity [bundle #smoke]-wrong.key"

mkdir -p "$(dirname -- "${request_path}")" "$(dirname -- "${book_path}")"

cat > "${request_path}" <<JSON
{
  "effectiveDate": "2026-04-14",
  "lines": [
    {
      "accountCode": "1000",
      "side": "DEBIT",
      "currencyCode": "EUR",
      "amount": "10.00"
    },
    {
      "accountCode": "2000",
      "side": "CREDIT",
      "currencyCode": "EUR",
      "amount": "10.00"
    }
  ],
  "provenance": {
    "actorId": "bundle-smoke",
    "actorType": "AGENT",
    "commandId": "bundle-smoke-command",
    "idempotencyKey": "bundle-smoke-idem",
    "causationId": "bundle-smoke-cause"
  }
}
JSON

cat > "${declare_cash_path}" <<JSON
{
  "accountCode": "1000",
  "accountName": "Cash",
  "normalBalance": "DEBIT"
}
JSON

cat > "${declare_revenue_path}" <<JSON
{
  "accountCode": "2000",
  "accountName": "Revenue",
  "normalBalance": "CREDIT"
}
JSON

printf 'definitely-wrong-bundle-smoke-passphrase\n' > "${wrong_book_key_path}"
chmod 600 "${wrong_book_key_path}"

printf 'Bundle smoke: verifying version command\n'
version_output="$(run_bundle_command version | tr -d '\r')"
require_match "${version_output}" '"status"[[:space:]]*:[[:space:]]*"ok"' \
    "version output did not report ok status"
require_match "${version_output}" '"application"[[:space:]]*:[[:space:]]*"FinGrind"' \
    "version output did not include application name"
require_match "${version_output}" '"version"[[:space:]]*:[[:space:]]*"' \
    "version output did not include version"

printf 'Bundle smoke: verifying self-contained runtime contract\n'
capabilities_output="$(run_bundle_command capabilities | tr -d '\r')"
require_match "${capabilities_output}" '"publicCliDistribution"[[:space:]]*:[[:space:]]*"self-contained-bundle"' \
    "capabilities output did not report the self-contained bundle distribution"
require_match "${capabilities_output}" '"sqliteLibraryMode"[[:space:]]*:[[:space:]]*"managed-only"' \
    "capabilities output did not report the managed-only SQLite runtime mode"
require_match "${capabilities_output}" '"sqliteLibraryBundleHomeSystemProperty"[[:space:]]*:[[:space:]]*"fingrind\.bundle\.home"' \
    "capabilities output did not report the bundle-home system property"
require_match "${capabilities_output}" '"sqliteRuntimeStatus"[[:space:]]*:[[:space:]]*"ready"' \
    "capabilities output did not report a ready SQLite runtime"
require_match "${capabilities_output}" '"loadedSqliteVersion"[[:space:]]*:[[:space:]]*"3\.53\.0"' \
    "capabilities output did not report SQLite 3.53.0"
require_match "${capabilities_output}" '"loadedSqlite3mcVersion"[[:space:]]*:[[:space:]]*"2\.3\.3"' \
    "capabilities output did not report SQLite3 Multiple Ciphers 2.3.3"

printf 'Bundle smoke: generating a dedicated book key file\n'
generate_key_output="$(run_bundle_command generate-book-key-file --book-key-file "${book_key_path}" | tr -d '\r')"
[[ -f "${book_key_path}" ]] || die "bundle smoke did not generate the requested key file: ${book_key_path}"
require_match "${generate_key_output}" '"status"[[:space:]]*:[[:space:]]*"ok"' \
    "bundle smoke key generation did not report ok status"
require_match "${generate_key_output}" '"permissions"[[:space:]]*:[[:space:]]*"0600"' \
    "bundle smoke key generation did not report owner-only file permissions"
[[ "$(posix_mode "${book_key_path}")" == "600" ]] || die \
    "generated key file did not use 0600 permissions"
generated_book_passphrase="$(cat "${book_key_path}")"
[[ -n "${generated_book_passphrase}" ]] || die "bundle smoke generated an empty key file"

printf 'Bundle smoke: verifying explicit book initialization\n'
open_output="$(
    printf '%s' "${generated_book_passphrase}" \
        | run_bundle_command open-book --book-file "${book_path}" --book-passphrase-stdin \
        | tr -d '\r'
)"
[[ -f "${book_path}" ]] || die "bundle smoke book file was not initialized: ${book_path}"
require_match "${open_output}" '"status"[[:space:]]*:[[:space:]]*"ok"' \
    "bundle smoke open-book did not report ok status"

printf 'Bundle smoke: verifying account declaration and registry listing\n'
declare_cash_output="$(run_bundle_command declare-account --book-file "${book_path}" --book-key-file "${book_key_path}" --request-file "${declare_cash_path}" | tr -d '\r')"
declare_revenue_output="$(run_bundle_command declare-account --book-file "${book_path}" --book-key-file "${book_key_path}" --request-file "${declare_revenue_path}" | tr -d '\r')"
list_output="$(run_bundle_command list-accounts --book-file "${book_path}" --book-key-file "${book_key_path}" | tr -d '\r')"
require_match "${declare_cash_output}" '"accountCode"[[:space:]]*:[[:space:]]*"1000"' \
    "bundle smoke cash declaration did not echo account 1000"
require_match "${declare_revenue_output}" '"accountCode"[[:space:]]*:[[:space:]]*"2000"' \
    "bundle smoke revenue declaration did not echo account 2000"
require_match "${list_output}" '"accountCode"[[:space:]]*:[[:space:]]*"1000"' \
    "bundle smoke account listing did not include account 1000"
require_match "${list_output}" '"accountCode"[[:space:]]*:[[:space:]]*"2000"' \
    "bundle smoke account listing did not include account 2000"

printf 'Bundle smoke: verifying wrong key is rejected deterministically\n'
set +e
wrong_key_output="$(run_bundle_command list-accounts --book-file "${book_path}" --book-key-file "${wrong_book_key_path}" | tr -d '\r')"
wrong_key_status=$?
set -e
[[ "${wrong_key_status}" -eq 1 ]] || die \
    "bundle smoke wrong-key listing exited with ${wrong_key_status} instead of 1"
require_match "${wrong_key_output}" '"code"[[:space:]]*:[[:space:]]*"runtime-failure"' \
    "bundle smoke wrong-key listing did not report a runtime-failure"
require_match "${wrong_key_output}" 'SQLITE_NOTADB' \
    "bundle smoke wrong-key listing did not expose the SQLite NOTADB failure"

printf 'Bundle smoke: verifying preflight and commit\n'
preflight_output="$(run_bundle_command preflight-entry --book-file "${book_path}" --book-key-file "${book_key_path}" --request-file "${request_path}" | tr -d '\r')"
commit_output="$(run_bundle_command post-entry --book-file "${book_path}" --book-key-file "${book_key_path}" --request-file "${request_path}" | tr -d '\r')"
require_match "${preflight_output}" '"status"[[:space:]]*:[[:space:]]*"preflight-accepted"' \
    "bundle smoke preflight did not report preflight-accepted status"
require_match "${commit_output}" '"status"[[:space:]]*:[[:space:]]*"committed"' \
    "bundle smoke commit did not report committed status"

printf 'Bundle smoke: success\n'
