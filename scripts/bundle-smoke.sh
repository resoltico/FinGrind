#!/usr/bin/env bash
# Extract the self-contained FinGrind CLI bundle and run the public office-worker acceptance
# workflow without ambient Java or a preconfigured FINGRIND_SQLITE_LIBRARY.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

require_no_match() {
    local text=$1
    local pattern=$2
    local message=$3

    if printf '%s\n' "${text}" | grep -Eq -- "${pattern}"; then
        die "${message}"
    fi
}

require_match() {
    local text=$1
    local pattern=$2
    local message=$3

    if ! printf '%s\n' "${text}" | grep -Eq -- "${pattern}"; then
        die "${message}"
    fi
}

require_java_26() {
    local java_command=$1
    local version_output version_token

    version_output="$("${java_command}" --version 2>&1 | tr -d '\r')"
    version_token="$(printf '%s\n' "${version_output}" | awk 'NR == 1 { print $2 }')"
    case "${version_token}" in
        26|26.*) ;;
        *)
            printf '%s\n' "${version_output}" >&2
            die "bundled Java runtime did not report Java 26"
            ;;
    esac
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
        *) die "unsupported bundle acceptance operating system: ${uname_s}" ;;
    esac
}

host_bundle_classifier() {
    local operating_system architecture
    operating_system="$(uname -s)"
    case "${operating_system}" in
        Darwin) operating_system='macos' ;;
        Linux) operating_system='linux' ;;
        *) die "unsupported bundle acceptance operating system: ${operating_system}" ;;
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

current_utc_date() {
    date -u +%F
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
[[ -f "${bundle_root}/LICENSE-SIL-OFL-1.1" ]] || die "missing LICENSE-SIL-OFL-1.1 in bundle root"
[[ -f "${bundle_root}/NOTICE" ]] || die "missing NOTICE in bundle root"
[[ -f "${bundle_root}/LICENSE-SQLITE3MULTIPLECIPHERS" ]] || die \
    "missing LICENSE-SQLITE3MULTIPLECIPHERS in bundle root"
[[ -f "${bundle_root}/PATENTS.md" ]] || die "missing PATENTS.md in bundle root"
[[ -f "${bundle_root}/README.md" ]] || die "missing README.md in bundle root"
[[ -f "${bundle_root}/bundle-manifest.json" ]] || die "missing bundle-manifest.json in bundle root"

bundle_readme="$(tr -d '\r' < "${bundle_root}/README.md")"
require_match "${bundle_readme}" '^# FinGrind ' \
    "bundle README did not start with the FinGrind title"
require_match "${bundle_readme}" 'bundle-manifest\.json' \
    "bundle README did not mention the machine-readable bundle manifest"

bundle_manifest="$(tr -d '\r' < "${bundle_root}/bundle-manifest.json")"
bundle_manifest_compact="$(printf '%s' "${bundle_manifest}" | tr -d '[:space:]')"
require_match "${bundle_manifest_compact}" '"runtimeDistribution":"self-contained-bundle"' \
    "bundle manifest did not report the self-contained runtime distribution"
require_match "${bundle_manifest_compact}" "\"classifier\":\"$(host_bundle_classifier)\"" \
    "bundle manifest did not report the current host classifier"
require_match "${bundle_manifest_compact}" '"supportedPublicCliBundleTargets":\[[^]]*"windows-x86_64"' \
    "bundle manifest did not report the supported public bundle targets"
require_match "${bundle_manifest_compact}" '"unsupportedPublicCliOperatingSystems":\[\]' \
    "bundle manifest did not report the current unsupported public operating systems"
require_match "${bundle_manifest_compact}" '"recommendedFirstCommand":\[[^]]*"help"' \
    "bundle manifest did not publish the canonical bootstrap help command"
require_match "${bundle_manifest_compact}" '"machineReadableContractCommand":\[[^]]*"capabilities"' \
    "bundle manifest did not publish the canonical machine-readable contract command"
require_match "${bundle_manifest_compact}" '"requestTemplateCommand":\[[^]]*"print-request-template"' \
    "bundle manifest did not publish the canonical request-template bootstrap command"
require_match "${bundle_manifest_compact}" '"planTemplateCommand":\[[^]]*"print-plan-template"' \
    "bundle manifest did not publish the canonical plan-template bootstrap command"
require_no_match "${bundle_manifest_compact}" '"discoveryCommands":' \
    "bundle manifest still reauthored static command-group arrays instead of pointing to the canonical contract"
require_no_match "${bundle_manifest_compact}" '"administrationCommands":' \
    "bundle manifest still reauthored static command-group arrays instead of pointing to the canonical contract"
require_no_match "${bundle_manifest_compact}" '"queryCommands":' \
    "bundle manifest still reauthored static command-group arrays instead of pointing to the canonical contract"
require_no_match "${bundle_manifest_compact}" '"writeCommands":' \
    "bundle manifest still reauthored static command-group arrays instead of pointing to the canonical contract"

require_java_26 "${bundle_root}/runtime/bin/java"
runtime_modules_output="$("${bundle_root}/runtime/bin/java" --list-modules | tr -d '\r')"
require_no_match "${runtime_modules_output}" '^jdk\.jlink@' \
    "bundled Java runtime still contains jdk.jlink"
require_no_match "${runtime_modules_output}" '^jdk\.jpackage@' \
    "bundled Java runtime still contains jdk.jpackage"
require_no_match "${runtime_modules_output}" '^jdk\.jdeps@' \
    "bundled Java runtime still contains jdk.jdeps"
require_match "${runtime_modules_output}" '^jdk\.unsupported@' \
    "bundled Java runtime omitted jdk.unsupported, which PDF export requires for a noise-free runtime"

readonly request_sale_path="${work_root}/requests odd/--sale [bundle #acceptance].json"
readonly request_adjustment_path="${work_root}/requests odd/adjustment [bundle #acceptance].json"
readonly invalid_request_path="${work_root}/requests odd/bad fields [bundle #acceptance].json"
readonly declare_cash_path="${work_root}/requests odd/declare account cash [bundle #acceptance].json"
readonly declare_revenue_path="${work_root}/requests odd/declare account revenue [bundle #acceptance].json"
readonly book_path="${work_root}/books odd/nested/-entity [bundle #acceptance].sqlite"
readonly book_key_path="${work_root}/keys odd/nested/--entity [bundle #acceptance].key"
readonly replacement_book_key_path="${work_root}/keys odd/nested/--entity [bundle #acceptance]-replacement.key"
readonly wrong_book_key_path="${work_root}/keys odd/nested/--entity [bundle #acceptance]-wrong.key"
readonly prompt_failure_book_path="${work_root}/books odd/nested/prompt unavailable [bundle #acceptance].sqlite"
readonly trial_balance_pdf_path="${work_root}/reports odd/trial balance [bundle #acceptance].pdf"
readonly trial_balance_pdf_stderr_path="${work_root}/reports odd/trial balance [bundle #acceptance].stderr.txt"

mkdir -p \
    "$(dirname -- "${request_sale_path}")" \
    "$(dirname -- "${book_path}")" \
    "$(dirname -- "${book_key_path}")" \
    "$(dirname -- "${trial_balance_pdf_path}")"

cat > "${request_sale_path}" <<JSON
{
  "effectiveDate": "2026-04-07",
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
    "actorId": "bundle-acceptance",
    "actorType": "AGENT",
    "commandId": "bundle-acceptance-sale",
    "idempotencyKey": "bundle-acceptance-idem-1",
    "causationId": "bundle-acceptance-cause-1"
  }
}
JSON

cat > "${request_adjustment_path}" <<JSON
{
  "effectiveDate": "2026-04-08",
  "lines": [
    {
      "accountCode": "1000",
      "side": "CREDIT",
      "currencyCode": "EUR",
      "amount": "4.00"
    },
    {
      "accountCode": "2000",
      "side": "DEBIT",
      "currencyCode": "EUR",
      "amount": "4.00"
    }
  ],
  "provenance": {
    "actorId": "bundle-acceptance",
    "actorType": "AGENT",
    "commandId": "bundle-acceptance-adjustment",
    "idempotencyKey": "bundle-acceptance-idem-2",
    "causationId": "bundle-acceptance-cause-2"
  }
}
JSON

cat > "${invalid_request_path}" <<JSON
{
  "accountCode": "1000",
  "accountName": "Cash",
  "normalBalance": "DEBIT",
  "nonsenseOne": "unexpected",
  "nonsenseTwo": "unexpected"
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

printf 'definitely-wrong-bundle-acceptance-passphrase\n' > "${wrong_book_key_path}"
chmod 600 "${wrong_book_key_path}"

printf 'Bundle acceptance: verifying version command\n'
version_output="$(run_bundle_command version --output json | tr -d '\r')"
require_match "${version_output}" '"status"[[:space:]]*:[[:space:]]*"ok"' \
    "version output did not report ok status"
require_match "${version_output}" '"application"[[:space:]]*:[[:space:]]*"FinGrind"' \
    "version output did not include application name"
require_match "${version_output}" "\"version\"[[:space:]]*:[[:space:]]*\"$(project_version)\"" \
    "version output did not report the expected version"

printf 'Bundle acceptance: verifying self-contained runtime contract\n'
capabilities_output="$(run_bundle_command capabilities --output json | tr -d '\r')"
printf '%s\n' "${capabilities_output}" | python3 -c '
import json
import sys

payload = json.load(sys.stdin)["payload"]
environment = payload["environment"]
distribution = environment["distribution"]
storage = environment["storage"]
sqlite = environment["sqlite"]
query_commands = payload["commands"]["query"]
query_output_modes = payload["requestInput"]["queryOutputModes"]
error_codes = [descriptor["code"] for descriptor in payload["responseModel"]["errorDescriptors"]]

checks = [
    (distribution["runtimeDistribution"] == "self-contained-bundle", "capabilities output did not report the self-contained bundle runtime"),
    (distribution["publicCliDistribution"] == "self-contained-bundle", "capabilities output did not report the self-contained bundle distribution"),
    ("windows-x86_64" in distribution["supportedPublicCliBundleTargets"], "capabilities output did not report the supported public bundle targets"),
    (distribution["unsupportedPublicCliOperatingSystems"] == [], "capabilities output did not report the current unsupported public operating systems"),
    (storage["storageDriver"] == "sqlite-ffm-sqlite3mc", "capabilities output did not report the SQLite3 Multiple Ciphers storage driver"),
    (storage["bookProtectionMode"] == "required", "capabilities output did not report required book protection"),
    (storage["defaultBookCipher"] == "chacha20", "capabilities output did not report the default chacha20 cipher"),
    (sqlite["libraryMode"] == "managed-only", "capabilities output did not report the managed-only SQLite runtime mode"),
    (sqlite["bundleHomeSystemProperty"] == "fingrind.bundle.home", "capabilities output did not report the bundle-home system property"),
    (sqlite["runtimeStatus"] == "ready", "capabilities output did not report a ready SQLite runtime"),
    (sqlite["loadedSqliteVersion"] == "3.53.0", "capabilities output did not report SQLite 3.53.0"),
    (sqlite["loadedSqlite3mcVersion"] == "2.3.3", "capabilities output did not report SQLite3 Multiple Ciphers 2.3.3"),
    ("trial-balance" in query_commands, "capabilities output did not report the trial-balance query command"),
    ("account-ledger" in query_commands, "capabilities output did not report the account-ledger query command"),
    ("period-summary" in query_commands, "capabilities output did not report the period-summary query command"),
    (query_output_modes == ["json", "human", "csv"], "capabilities output did not report the supported query output modes"),
    ("invalid-page-cursor" in error_codes, "capabilities output did not report the invalid-page-cursor error descriptor"),
    ("interactive-prompt-unavailable" in error_codes, "capabilities output did not report the interactive-prompt-unavailable error descriptor"),
    ("book-authentication-failed" in error_codes, "capabilities output did not report the book-authentication-failed error descriptor"),
]

for passed, message in checks:
    if not passed:
        print(message, file=sys.stderr)
        raise SystemExit(1)
'

printf 'Bundle acceptance: generating a dedicated book key file\n'
generate_key_output="$(run_bundle_command generate-book-key-file --book-key-file "${book_key_path}" | tr -d '\r')"
[[ -f "${book_key_path}" ]] || die "bundle acceptance did not generate the requested key file: ${book_key_path}"
require_match "${generate_key_output}" '"status"[[:space:]]*:[[:space:]]*"ok"' \
    "bundle acceptance key generation did not report ok status"
require_match "${generate_key_output}" '"permissions"[[:space:]]*:[[:space:]]*"0600"' \
    "bundle acceptance key generation did not report owner-only file permissions"
[[ "$(posix_mode "${book_key_path}")" == "600" ]] || die \
    "generated key file did not use 0600 permissions"
generated_book_passphrase="$(cat "${book_key_path}")"
[[ -n "${generated_book_passphrase}" ]] || die "bundle acceptance generated an empty key file"

printf 'Bundle acceptance: verifying explicit book initialization\n'
open_output="$(
    printf '%s' "${generated_book_passphrase}" \
        | run_bundle_command open-book --book-file "${book_path}" --book-passphrase-stdin \
        | tr -d '\r'
)"
[[ -f "${book_path}" ]] || die "bundle acceptance book file was not initialized: ${book_path}"
require_match "${open_output}" '"status"[[:space:]]*:[[:space:]]*"ok"' \
    "bundle acceptance open-book did not report ok status"

printf 'Bundle acceptance: verifying account declaration and registry listing\n'
declare_cash_output="$(run_bundle_command declare-account --book-file "${book_path}" --book-key-file "${book_key_path}" --request-file "${declare_cash_path}" | tr -d '\r')"
declare_revenue_output="$(run_bundle_command declare-account --book-file "${book_path}" --book-key-file "${book_key_path}" --request-file "${declare_revenue_path}" | tr -d '\r')"
list_output="$(run_bundle_command list-accounts --book-file "${book_path}" --book-key-file "${book_key_path}" | tr -d '\r')"
require_match "${declare_cash_output}" '"accountCode"[[:space:]]*:[[:space:]]*"1000"' \
    "bundle acceptance cash declaration did not echo account 1000"
require_match "${declare_revenue_output}" '"accountCode"[[:space:]]*:[[:space:]]*"2000"' \
    "bundle acceptance revenue declaration did not echo account 2000"
require_match "${list_output}" '"accountCode"[[:space:]]*:[[:space:]]*"1000"' \
    "bundle acceptance account listing did not include account 1000"
require_match "${list_output}" '"accountCode"[[:space:]]*:[[:space:]]*"2000"' \
    "bundle acceptance account listing did not include account 2000"

printf 'Bundle acceptance: verifying preflight and commit\n'
preflight_output="$(run_bundle_command preflight-entry --book-file "${book_path}" --book-key-file "${book_key_path}" --request-file "${request_sale_path}" | tr -d '\r')"
commit_sale_output="$(run_bundle_command post-entry --book-file "${book_path}" --book-key-file "${book_key_path}" --request-file "${request_sale_path}" | tr -d '\r')"
commit_adjustment_output="$(run_bundle_command post-entry --book-file "${book_path}" --book-key-file "${book_key_path}" --request-file "${request_adjustment_path}" | tr -d '\r')"
require_match "${preflight_output}" '"status"[[:space:]]*:[[:space:]]*"preflight-accepted"' \
    "bundle acceptance preflight did not report preflight-accepted status"
require_match "${commit_sale_output}" '"status"[[:space:]]*:[[:space:]]*"committed"' \
    "bundle acceptance sale commit did not report committed status"
require_match "${commit_adjustment_output}" '"status"[[:space:]]*:[[:space:]]*"committed"' \
    "bundle acceptance adjustment commit did not report committed status"

printf 'Bundle acceptance: verifying operator query and report surfaces\n'
list_postings_json_output="$(run_bundle_command list-postings --book-file "${book_path}" --book-key-file "${book_key_path}" --limit 1 | tr -d '\r')"
require_match "${list_postings_json_output}" '"nextCursor"[[:space:]]*:[[:space:]]*"' \
    "bundle acceptance list-postings did not report nextCursor for the first page"
next_cursor="$(printf '%s\n' "${list_postings_json_output}" | sed -n 's/.*"nextCursor":"\([^"]*\)".*/\1/p')"
[[ -n "${next_cursor}" ]] || die "bundle acceptance could not extract nextCursor from list-postings output"
list_postings_human_output="$(run_bundle_command list-postings --book-file "${book_path}" --book-key-file "${book_key_path}" --limit 25 --output human | tr -d '\r')"
require_match "${list_postings_human_output}" '^Effective date[[:space:]]+\|[[:space:]]+Recorded at' \
    "bundle acceptance human posting register did not render a table header"
require_match "${list_postings_human_output}" '10\.00' \
    "bundle acceptance human posting register did not render accounting-scale amounts"
account_balance_human_output="$(run_bundle_command account-balance --book-file "${book_path}" --book-key-file "${book_key_path}" --account-code 1000 --output human | tr -d '\r')"
require_match "${account_balance_human_output}" '^Account Balance$' \
    "bundle acceptance human account-balance output did not render the report title"
require_match "${account_balance_human_output}" 'Account[[:space:]]+:[[:space:]]+1000' \
    "bundle acceptance human account-balance output did not render the selected account"
require_match "${account_balance_human_output}" '6\.00' \
    "bundle acceptance human account-balance output did not render the net balance"
trial_balance_human_output="$(run_bundle_command trial-balance --book-file "${book_path}" --book-key-file "${book_key_path}" --effective-date-to 2026-04-08 --output human | tr -d '\r')"
require_match "${trial_balance_human_output}" '^Trial Balance$' \
    "bundle acceptance trial-balance output did not render the report title"
require_match "${trial_balance_human_output}" 'Effective date to[[:space:]]+:[[:space:]]+2026-04-08' \
    "bundle acceptance trial-balance output did not render the effective date"
require_match "${trial_balance_human_output}" '1000' \
    "bundle acceptance trial-balance output did not render account 1000"
require_match "${trial_balance_human_output}" '6\.00' \
    "bundle acceptance trial-balance output did not render the expected net amount"
run_bundle_command trial-balance \
    --book-file "${book_path}" \
    --book-key-file "${book_key_path}" \
    --effective-date-to 2026-04-08 \
    --output human \
    --pdf-out "${trial_balance_pdf_path}" >/dev/null 2>"${trial_balance_pdf_stderr_path}"
[[ -f "${trial_balance_pdf_path}" ]] || die \
    "bundle acceptance trial-balance did not write the requested PDF artifact"
[[ "$(head -c 5 "${trial_balance_pdf_path}")" == "%PDF-" ]] || die \
    "bundle acceptance trial-balance PDF artifact did not start with %PDF-"
[[ ! -s "${trial_balance_pdf_stderr_path}" ]] || die \
    "bundle acceptance PDF export wrote unexpected stderr: $(tr -d '\r' < "${trial_balance_pdf_stderr_path}")"
list_postings_second_page_output="$(run_bundle_command list-postings --book-file "${book_path}" --book-key-file "${book_key_path}" --cursor "${next_cursor}" --limit 25 | tr -d '\r')"
require_match "${list_postings_second_page_output}" '"postings"[[:space:]]*:[[:space:]]*\[[^]]*"bundle-acceptance-idem-1"' \
    "bundle acceptance second posting page did not round-trip the opaque nextCursor"
account_ledger_csv_output="$(run_bundle_command account-ledger --book-file "${book_path}" --book-key-file "${book_key_path}" --account-code 1000 --effective-date-from 2026-04-07 --effective-date-to 2026-04-08 --output csv | tr -d '\r')"
require_match "${account_ledger_csv_output}" '^accountCode,accountName,effectiveDateFrom,effectiveDateTo,postingId,effectiveDate,recordedAt,currencyCode,debitAmount,creditAmount,runningBalance,runningBalanceSide,counterpartAccounts$' \
    "bundle acceptance account-ledger CSV output did not render the expected header"
require_match "${account_ledger_csv_output}" '^1000,Cash,2026-04-07,2026-04-08,[^,]+,2026-04-07,[^,]+,EUR,10\.00,0\.00,10\.00,DEBIT,2000$' \
    "bundle acceptance account-ledger CSV output did not render the opening ledger movement row"
require_match "${account_ledger_csv_output}" '^1000,Cash,2026-04-07,2026-04-08,[^,]+,2026-04-08,[^,]+,EUR,0\.00,4\.00,6\.00,DEBIT,2000$' \
    "bundle acceptance account-ledger CSV output did not render the running-balance adjustment row"
period_summary_human_output="$(run_bundle_command period-summary --book-file "${book_path}" --book-key-file "${book_key_path}" --effective-date-from 2026-04-07 --effective-date-to 2026-04-08 --output human | tr -d '\r')"
require_match "${period_summary_human_output}" '^Period Summary$' \
    "bundle acceptance period-summary output did not render the report title"
require_match "${period_summary_human_output}" 'Posting count' \
    "bundle acceptance period-summary output did not render posting-count metadata"
require_match "${period_summary_human_output}" '2' \
    "bundle acceptance period-summary output did not render the expected posting count"

printf 'Bundle acceptance: verifying rekey and wrong-key semantics\n'
replacement_key_output="$(run_bundle_command generate-book-key-file --book-key-file "${replacement_book_key_path}" | tr -d '\r')"
require_match "${replacement_key_output}" '"status"[[:space:]]*:[[:space:]]*"ok"' \
    "bundle acceptance replacement key generation did not report ok status"
rekey_output="$(run_bundle_command rekey-book --book-file "${book_path}" --book-key-file "${book_key_path}" --new-book-key-file "${replacement_book_key_path}" | tr -d '\r')"
require_match "${rekey_output}" '"status"[[:space:]]*:[[:space:]]*"ok"' \
    "bundle acceptance rekey-book did not report ok status"
set +e
wrong_key_output="$(run_bundle_command list-accounts --book-file "${book_path}" --book-key-file "${book_key_path}" | tr -d '\r')"
wrong_key_status=$?
set -e
[[ "${wrong_key_status}" -eq 2 ]] || die \
    "bundle acceptance wrong-key listing exited with ${wrong_key_status} instead of 2"
require_match "${wrong_key_output}" '"code"[[:space:]]*:[[:space:]]*"book-authentication-failed"' \
    "bundle acceptance wrong-key listing did not report book-authentication-failed"
require_no_match "${wrong_key_output}" 'SQLITE_NOTADB' \
    "bundle acceptance wrong-key listing leaked the SQLite NOTADB storage symptom"
replacement_key_trial_balance_output="$(run_bundle_command trial-balance --book-file "${book_path}" --book-key-file "${replacement_book_key_path}" --effective-date-to 2026-04-08 --output human | tr -d '\r')"
require_match "${replacement_key_trial_balance_output}" '1000' \
    "bundle acceptance trial-balance did not remain readable after rekey"

printf 'Bundle acceptance: verifying deterministic nonsense workflows\n'
set +e
invalid_cursor_output="$(run_bundle_command list-postings --book-file "${book_path}" --book-key-file "${replacement_book_key_path}" --cursor definitely-not-a-valid-cursor | tr -d '\r')"
invalid_cursor_status=$?
prompt_failure_output="$(run_bundle_command open-book --book-file "${prompt_failure_book_path}" --book-passphrase-prompt | tr -d '\r')"
prompt_failure_status=$?
invalid_request_output="$(run_bundle_command declare-account --book-file "${book_path}" --book-key-file "${replacement_book_key_path}" --request-file "${invalid_request_path}" | tr -d '\r')"
invalid_request_status=$?
set -e
[[ "${invalid_cursor_status}" -eq 1 ]] || die \
    "bundle acceptance invalid cursor exited with ${invalid_cursor_status} instead of 1"
require_match "${invalid_cursor_output}" '"code"[[:space:]]*:[[:space:]]*"invalid-page-cursor"' \
    "bundle acceptance invalid cursor did not report invalid-page-cursor"
require_no_match "${invalid_cursor_output}" '"code"[[:space:]]*:[[:space:]]*"runtime-failure"' \
    "bundle acceptance invalid cursor regressed to runtime-failure"
[[ "${prompt_failure_status}" -eq 2 ]] || die \
    "bundle acceptance prompt-unavailable exited with ${prompt_failure_status} instead of 2"
require_match "${prompt_failure_output}" '"code"[[:space:]]*:[[:space:]]*"interactive-prompt-unavailable"' \
    "bundle acceptance prompt-unavailable did not report interactive-prompt-unavailable"
require_match "${prompt_failure_output}" '--book-passphrase-stdin' \
    "bundle acceptance prompt-unavailable did not report a repair hint"
[[ "${invalid_request_status}" -eq 1 ]] || die \
    "bundle acceptance invalid request exited with ${invalid_request_status} instead of 1"
require_match "${invalid_request_output}" '"code"[[:space:]]*:[[:space:]]*"invalid-request"' \
    "bundle acceptance invalid request did not report invalid-request"
require_match "${invalid_request_output}" 'Unexpected fields: nonsenseOne, nonsenseTwo' \
    "bundle acceptance invalid request did not report all unexpected fields together"

printf 'Bundle acceptance: success\n'
