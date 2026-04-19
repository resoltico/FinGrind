#!/usr/bin/env bash
# Build the local Docker image and verify the FinGrind CLI as a real office-worker acceptance
# surface from a mounted workspace with unusual paths.

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
readonly image_tag="fingrind-docker-acceptance:$$"
readonly smoke_root="${repo_root}/tmp/docker acceptance.$$"
readonly docker_run_user="$(id -u):$(id -g)"
anonymous_docker_config=''
docker_endpoint=''

resolve_docker_buildx_plugin() {
    local docker_binary=''
    local -a candidates=()
    local candidate=''

    if candidate="$(command -v docker-buildx 2>/dev/null || true)"; then
        if [[ -n "${candidate}" ]]; then
            candidates+=("${candidate}")
        fi
    fi

    docker_binary="$(command -v docker)"
    candidates+=(
        "${HOME}/.docker/cli-plugins/docker-buildx"
        "/Applications/Docker.app/Contents/Resources/cli-plugins/docker-buildx"
        "/usr/local/lib/docker/cli-plugins/docker-buildx"
        "/usr/local/libexec/docker/cli-plugins/docker-buildx"
        "/opt/homebrew/lib/docker/cli-plugins/docker-buildx"
        "/opt/homebrew/libexec/docker/cli-plugins/docker-buildx"
        "/usr/lib/docker/cli-plugins/docker-buildx"
        "/usr/libexec/docker/cli-plugins/docker-buildx"
        "/usr/lib64/docker/cli-plugins/docker-buildx"
        "/usr/share/docker/cli-plugins/docker-buildx"
        "$(cd -P -- "$(dirname -- "${docker_binary}")" && pwd)/docker-buildx"
    )

    for candidate in "${candidates[@]}"; do
        if [[ -n "${candidate}" && -x "${candidate}" ]]; then
            printf '%s\n' "${candidate}"
            return 0
        fi
    done

    return 1
}

docker_with_repo_config() {
    if [[ -n "${docker_endpoint}" ]]; then
        DOCKER_CONFIG="${anonymous_docker_config}" DOCKER_HOST="${docker_endpoint}" docker "$@"
        return
    fi
    DOCKER_CONFIG="${anonymous_docker_config}" docker "$@"
}

cleanup() {
    local exit_code=$?
    rm -rf "${smoke_root}" || sudo rm -rf "${smoke_root}" || true
    if command -v docker >/dev/null 2>&1 && [[ -n "${anonymous_docker_config}" ]]; then
        docker_with_repo_config image rm -f "${image_tag}" >/dev/null 2>&1 || true
        rm -rf "${anonymous_docker_config}" || true
    fi
    exit "${exit_code}"
}

trap cleanup EXIT

command -v docker >/dev/null 2>&1 || die "docker is required for the Docker acceptance gate"
docker buildx version >/dev/null 2>&1 || die "docker buildx is required for the Docker acceptance gate"
[[ -f "${repo_root}/Dockerfile" ]] || die "missing Dockerfile at ${repo_root}/Dockerfile"
[[ -f "${repo_root}/cli/build/libs/fingrind.jar" ]] || die \
    "missing internal application JAR at ${repo_root}/cli/build/libs/fingrind.jar; run ./gradlew :cli:shadowJar first"
[[ -f "${repo_root}/cli/build/docker/runtime-modules.txt" ]] || die \
    "missing Docker runtime module list at ${repo_root}/cli/build/docker/runtime-modules.txt; run ./gradlew :cli:shadowJar first"

docker_endpoint="${DOCKER_HOST:-}"
if [[ -z "${docker_endpoint}" ]]; then
    docker_endpoint="$(
        docker context inspect "$(docker context show 2>/dev/null || true)" \
            --format '{{.Endpoints.docker.Host}}' 2>/dev/null || true
    )"
fi
anonymous_docker_config="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-docker-config.XXXXXX")"
printf '{}\n' > "${anonymous_docker_config}/config.json"

if ! docker_with_repo_config buildx version >/dev/null 2>&1; then
    docker_buildx_plugin="$(resolve_docker_buildx_plugin)" || die \
        "docker buildx is available in the current shell, but no reusable docker-buildx plugin binary was found for the anonymous DOCKER_CONFIG"
    mkdir -p "${anonymous_docker_config}/cli-plugins"
    ln -s "${docker_buildx_plugin}" "${anonymous_docker_config}/cli-plugins/docker-buildx"
    docker_with_repo_config buildx version >/dev/null 2>&1 || die \
        "docker buildx is not reachable through the anonymous DOCKER_CONFIG even after staging ${docker_buildx_plugin}"
fi

mkdir -p "${smoke_root}/requests odd"

readonly request_sale_rel='requests odd/--sale [docker #acceptance].json'
readonly request_adjustment_rel='requests odd/adjustment [docker #acceptance].json'
readonly invalid_request_rel='requests odd/bad fields [docker #acceptance].json'
readonly declare_cash_rel='requests odd/declare account cash [docker #acceptance].json'
readonly declare_revenue_rel='requests odd/declare account revenue [docker #acceptance].json'
readonly book_rel='books odd/Rīga büro/nested/-entity [docker #acceptance].sqlite'
readonly book_key_rel='keys odd/Rīga büro/nested/--entity [docker #acceptance].key'
readonly replacement_book_key_rel='keys odd/Rīga büro/nested/--entity [docker #acceptance]-replacement.key'
readonly wrong_book_key_rel='keys odd/Rīga büro/nested/--entity [docker #acceptance]-wrong.key'
readonly prompt_failure_book_rel='books odd/Rīga büro/nested/prompt unavailable [docker #acceptance].sqlite'
readonly trial_balance_pdf_rel='reports odd/trial balance [docker #acceptance].pdf'
readonly trial_balance_pdf_stderr_rel='reports odd/trial balance [docker #acceptance].stderr.txt'
readonly request_sale_path="${smoke_root}/${request_sale_rel}"
readonly request_adjustment_path="${smoke_root}/${request_adjustment_rel}"
readonly invalid_request_path="${smoke_root}/${invalid_request_rel}"
readonly declare_cash_path="${smoke_root}/${declare_cash_rel}"
readonly declare_revenue_path="${smoke_root}/${declare_revenue_rel}"
readonly book_path="${smoke_root}/${book_rel}"
readonly book_key_path="${smoke_root}/${book_key_rel}"
readonly replacement_book_key_path="${smoke_root}/${replacement_book_key_rel}"
readonly wrong_book_key_path="${smoke_root}/${wrong_book_key_rel}"
readonly trial_balance_pdf_path="${smoke_root}/${trial_balance_pdf_rel}"
readonly trial_balance_pdf_stderr_path="${smoke_root}/${trial_balance_pdf_stderr_rel}"

mkdir -p "$(dirname -- "${book_path}")"
mkdir -p "$(dirname -- "${book_key_path}")"
mkdir -p "$(dirname -- "${trial_balance_pdf_path}")"

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
    "actorId": "docker-acceptance",
    "actorType": "AGENT",
    "commandId": "docker-acceptance-sale",
    "idempotencyKey": "docker-acceptance-idem-1",
    "causationId": "docker-acceptance-cause-1"
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
    "actorId": "docker-acceptance",
    "actorType": "AGENT",
    "commandId": "docker-acceptance-adjustment",
    "idempotencyKey": "docker-acceptance-idem-2",
    "causationId": "docker-acceptance-cause-2"
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

printf 'definitely-wrong-docker-acceptance-passphrase\n' > "${wrong_book_key_path}"
chmod 600 "${wrong_book_key_path}"

printf 'Docker acceptance: building local image\n'
docker_with_repo_config buildx build --load -t "${image_tag}" "${repo_root}" >/dev/null

printf 'Docker acceptance: verifying version command\n'
version_output="$(docker_with_repo_config run --rm \
    --user "${docker_run_user}" \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    version --output json | tr -d '\r')"
require_match "${version_output}" '"status"[[:space:]]*:[[:space:]]*"ok"' \
    "version output did not report ok status"
require_match "${version_output}" '"application"[[:space:]]*:[[:space:]]*"FinGrind"' \
    "version output did not include application name"
require_match "${version_output}" "\"version\"[[:space:]]*:[[:space:]]*\"$(awk -F= '/^version=/{print $2; exit}' "${repo_root}/gradle.properties")\"" \
    "version output did not report the expected version"

printf 'Docker acceptance: verifying managed SQLite runtime contract\n'
capabilities_output="$(docker_with_repo_config run --rm \
    --user "${docker_run_user}" \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    capabilities --output json | tr -d '\r')"
printf '%s\n' "${capabilities_output}" | python3 -c '
import json
import sys

payload = json.load(sys.stdin)["payload"]
environment = payload["environment"]
query_commands = payload["queryCommands"]
query_output_modes = payload["requestInput"]["queryOutputModes"]
error_codes = [descriptor["code"] for descriptor in payload["responseModel"]["errorDescriptors"]]

checks = [
    (environment["runtimeDistribution"] == "container-image", "capabilities output did not report the container runtime distribution"),
    (environment["publicCliDistribution"] == "self-contained-bundle", "capabilities output did not report the public bundle distribution contract"),
    ("windows-x86_64" in environment["supportedPublicCliBundleTargets"], "capabilities output did not report the supported public bundle targets"),
    (environment["unsupportedPublicCliOperatingSystems"] == [], "capabilities output did not report the current unsupported public operating systems"),
    (environment["sqliteLibraryMode"] == "managed-only", "capabilities output did not report the managed-only SQLite runtime mode"),
    (environment["storageDriver"] == "sqlite-ffm-sqlite3mc", "capabilities output did not report the SQLite3 Multiple Ciphers storage driver"),
    (environment["bookProtectionMode"] == "required", "capabilities output did not report required book protection"),
    (environment["defaultBookCipher"] == "chacha20", "capabilities output did not report the default chacha20 cipher"),
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

runtime_modules_output="$(docker_with_repo_config run --rm \
    --entrypoint /opt/fingrind/runtime/bin/java \
    "${image_tag}" \
    --list-modules | tr -d '\r')"
require_no_match "${runtime_modules_output}" '^jdk\.jlink@' \
    "container runtime still contains jdk.jlink"
require_no_match "${runtime_modules_output}" '^jdk\.jpackage@' \
    "container runtime still contains jdk.jpackage"
require_no_match "${runtime_modules_output}" '^jdk\.jdeps@' \
    "container runtime still contains jdk.jdeps"
require_match "${runtime_modules_output}" '^jdk\.unsupported@' \
    "container runtime omitted jdk.unsupported, which PDF export requires for a noise-free runtime"

printf 'Docker acceptance: generating a dedicated book key file inside the mounted workspace\n'
generate_key_output="$(docker_with_repo_config run --rm \
    --user "${docker_run_user}" \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    generate-book-key-file \
    --book-key-file "${book_key_rel}" | tr -d '\r')"
[[ -f "${book_key_path}" ]] || die "docker acceptance did not generate the requested key file: ${book_key_path}"
require_match "${generate_key_output}" '"status"[[:space:]]*:[[:space:]]*"ok"' \
    "docker acceptance key generation did not report ok status"
require_match "${generate_key_output}" '"permissions"[[:space:]]*:[[:space:]]*"0600"' \
    "docker acceptance key generation did not report owner-only file permissions"

printf 'Docker acceptance: verifying explicit book initialization through mounted path\n'
open_output="$(docker_with_repo_config run --rm \
    --user "${docker_run_user}" \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    open-book \
    --book-file "${book_rel}" \
    --book-key-file "${book_key_rel}" | tr -d '\r')"
[[ -f "${book_path}" ]] || die "docker acceptance book file was not initialized: ${book_path}"
require_match "${open_output}" '"status"[[:space:]]*:[[:space:]]*"ok"' \
    "docker acceptance open-book did not report ok status"

printf 'Docker acceptance: verifying account declaration and registry listing\n'
declare_cash_output="$(docker_with_repo_config run --rm \
    --user "${docker_run_user}" \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    declare-account \
    --book-file "${book_rel}" \
    --book-key-file "${book_key_rel}" \
    --request-file "${declare_cash_rel}" | tr -d '\r')"
declare_revenue_output="$(docker_with_repo_config run --rm \
    --user "${docker_run_user}" \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    declare-account \
    --book-file "${book_rel}" \
    --book-key-file "${book_key_rel}" \
    --request-file "${declare_revenue_rel}" | tr -d '\r')"
list_output="$(docker_with_repo_config run --rm \
    --user "${docker_run_user}" \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    list-accounts \
    --book-file "${book_rel}" \
    --book-key-file "${book_key_rel}" | tr -d '\r')"
require_match "${declare_cash_output}" '"accountCode"[[:space:]]*:[[:space:]]*"1000"' \
    "docker acceptance cash declaration did not echo account 1000"
require_match "${declare_revenue_output}" '"accountCode"[[:space:]]*:[[:space:]]*"2000"' \
    "docker acceptance revenue declaration did not echo account 2000"
require_match "${list_output}" '"accountCode"[[:space:]]*:[[:space:]]*"1000"' \
    "docker acceptance account listing did not include account 1000"
require_match "${list_output}" '"accountCode"[[:space:]]*:[[:space:]]*"2000"' \
    "docker acceptance account listing did not include account 2000"

printf 'Docker acceptance: verifying preflight and commit through mounted path\n'
preflight_output="$(docker_with_repo_config run --rm \
    --user "${docker_run_user}" \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    preflight-entry \
    --book-file "${book_rel}" \
    --book-key-file "${book_key_rel}" \
    --request-file "${request_sale_rel}" | tr -d '\r')"
commit_sale_output="$(docker_with_repo_config run --rm \
    --user "${docker_run_user}" \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    post-entry \
    --book-file "${book_rel}" \
    --book-key-file "${book_key_rel}" \
    --request-file "${request_sale_rel}" | tr -d '\r')"
commit_adjustment_output="$(docker_with_repo_config run --rm \
    --user "${docker_run_user}" \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    post-entry \
    --book-file "${book_rel}" \
    --book-key-file "${book_key_rel}" \
    --request-file "${request_adjustment_rel}" | tr -d '\r')"
require_match "${preflight_output}" '"status"[[:space:]]*:[[:space:]]*"preflight-accepted"' \
    "docker acceptance preflight did not report preflight-accepted status"
require_match "${commit_sale_output}" '"status"[[:space:]]*:[[:space:]]*"committed"' \
    "docker acceptance sale commit did not report committed status"
require_match "${commit_adjustment_output}" '"status"[[:space:]]*:[[:space:]]*"committed"' \
    "docker acceptance adjustment commit did not report committed status"

printf 'Docker acceptance: verifying operator query and report surfaces\n'
list_postings_json_output="$(docker_with_repo_config run --rm \
    --user "${docker_run_user}" \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    list-postings \
    --book-file "${book_rel}" \
    --book-key-file "${book_key_rel}" \
    --limit 1 | tr -d '\r')"
require_match "${list_postings_json_output}" '"nextCursor"[[:space:]]*:[[:space:]]*"' \
    "docker acceptance list-postings did not report nextCursor for the first page"
next_cursor="$(printf '%s\n' "${list_postings_json_output}" | sed -E -n 's/.*"nextCursor"[[:space:]]*:[[:space:]]*"([^"]*)".*/\1/p')"
[[ -n "${next_cursor}" ]] || die "docker acceptance could not extract nextCursor from list-postings output"
list_postings_second_page_output="$(docker_with_repo_config run --rm \
    --user "${docker_run_user}" \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    list-postings \
    --book-file "${book_rel}" \
    --book-key-file "${book_key_rel}" \
    --cursor "${next_cursor}" \
    --limit 25 | tr -d '\r')"
require_match "${list_postings_second_page_output}" 'docker-acceptance-sale' \
    "docker acceptance second posting page did not round-trip the opaque nextCursor"
list_postings_human_output="$(docker_with_repo_config run --rm \
    --user "${docker_run_user}" \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    list-postings \
    --book-file "${book_rel}" \
    --book-key-file "${book_key_rel}" \
    --limit 25 \
    --output human | tr -d '\r')"
require_match "${list_postings_human_output}" '^Effective date[[:space:]]+\|[[:space:]]+Recorded at' \
    "docker acceptance human posting register did not render a table header"
require_match "${list_postings_human_output}" '10\.00' \
    "docker acceptance human posting register did not render accounting-scale amounts"
account_balance_human_output="$(docker_with_repo_config run --rm \
    --user "${docker_run_user}" \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    account-balance \
    --book-file "${book_rel}" \
    --book-key-file "${book_key_rel}" \
    --account-code 1000 \
    --output human | tr -d '\r')"
require_match "${account_balance_human_output}" '^Account Balance$' \
    "docker acceptance human account-balance output did not render the report title"
require_match "${account_balance_human_output}" 'Account[[:space:]]+:[[:space:]]+1000' \
    "docker acceptance human account-balance output did not render the selected account"
require_match "${account_balance_human_output}" '6\.00' \
    "docker acceptance human account-balance output did not render the expected net balance"
trial_balance_human_output="$(docker_with_repo_config run --rm \
    --user "${docker_run_user}" \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    trial-balance \
    --book-file "${book_rel}" \
    --book-key-file "${book_key_rel}" \
    --effective-date-to 2026-04-08 \
    --output human | tr -d '\r')"
require_match "${trial_balance_human_output}" '^Trial Balance$' \
    "docker acceptance trial-balance output did not render the report title"
require_match "${trial_balance_human_output}" 'Effective date to[[:space:]]+:[[:space:]]+2026-04-08' \
    "docker acceptance trial-balance output did not render the effective date"
require_match "${trial_balance_human_output}" '1000' \
    "docker acceptance trial-balance output did not render account 1000"
require_match "${trial_balance_human_output}" '6\.00' \
    "docker acceptance trial-balance output did not render the expected net amount"
docker_with_repo_config run --rm \
    --user "${docker_run_user}" \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    trial-balance \
    --book-file "${book_rel}" \
    --book-key-file "${book_key_rel}" \
    --effective-date-to 2026-04-08 \
    --output human \
    --pdf-out "${trial_balance_pdf_rel}" >/dev/null 2>"${trial_balance_pdf_stderr_path}"
[[ -f "${trial_balance_pdf_path}" ]] || die \
    "docker acceptance trial-balance did not write the requested PDF artifact"
[[ "$(head -c 5 "${trial_balance_pdf_path}")" == "%PDF-" ]] || die \
    "docker acceptance trial-balance PDF artifact did not start with %PDF-"
[[ ! -s "${trial_balance_pdf_stderr_path}" ]] || die \
    "docker acceptance PDF export wrote unexpected stderr: $(tr -d '\r' < "${trial_balance_pdf_stderr_path}")"
account_ledger_csv_output="$(docker_with_repo_config run --rm \
    --user "${docker_run_user}" \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    account-ledger \
    --book-file "${book_rel}" \
    --book-key-file "${book_key_rel}" \
    --account-code 1000 \
    --effective-date-from 2026-04-07 \
    --effective-date-to 2026-04-08 \
    --output csv | tr -d '\r')"
require_match "${account_ledger_csv_output}" '^accountCode,accountName,effectiveDateFrom,effectiveDateTo,postingId,effectiveDate,recordedAt,currencyCode,debitAmount,creditAmount,runningBalance,runningBalanceSide,counterpartAccounts$' \
    "docker acceptance account-ledger CSV output did not render the expected header"
require_match "${account_ledger_csv_output}" '^1000,Cash,2026-04-07,2026-04-08,[^,]+,2026-04-07,[^,]+,EUR,10\.00,0\.00,10\.00,DEBIT,2000$' \
    "docker acceptance account-ledger CSV output did not render the opening ledger movement row"
require_match "${account_ledger_csv_output}" '^1000,Cash,2026-04-07,2026-04-08,[^,]+,2026-04-08,[^,]+,EUR,0\.00,4\.00,6\.00,DEBIT,2000$' \
    "docker acceptance account-ledger CSV output did not render the running-balance adjustment row"
period_summary_human_output="$(docker_with_repo_config run --rm \
    --user "${docker_run_user}" \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    period-summary \
    --book-file "${book_rel}" \
    --book-key-file "${book_key_rel}" \
    --effective-date-from 2026-04-07 \
    --effective-date-to 2026-04-08 \
    --output human | tr -d '\r')"
require_match "${period_summary_human_output}" '^Period Summary$' \
    "docker acceptance period-summary output did not render the report title"
require_match "${period_summary_human_output}" 'Posting count' \
    "docker acceptance period-summary output did not render posting-count metadata"
require_match "${period_summary_human_output}" '2' \
    "docker acceptance period-summary output did not render the expected posting count"

printf 'Docker acceptance: verifying rekey and wrong-key semantics\n'
replacement_key_output="$(docker_with_repo_config run --rm \
    --user "${docker_run_user}" \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    generate-book-key-file \
    --book-key-file "${replacement_book_key_rel}" | tr -d '\r')"
require_match "${replacement_key_output}" '"status"[[:space:]]*:[[:space:]]*"ok"' \
    "docker acceptance replacement key generation did not report ok status"
rekey_output="$(docker_with_repo_config run --rm \
    --user "${docker_run_user}" \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    rekey-book \
    --book-file "${book_rel}" \
    --book-key-file "${book_key_rel}" \
    --new-book-key-file "${replacement_book_key_rel}" | tr -d '\r')"
require_match "${rekey_output}" '"status"[[:space:]]*:[[:space:]]*"ok"' \
    "docker acceptance rekey-book did not report ok status"
set +e
wrong_key_output="$(docker_with_repo_config run --rm \
    --user "${docker_run_user}" \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    list-accounts \
    --book-file "${book_rel}" \
    --book-key-file "${book_key_rel}" | tr -d '\r')"
wrong_key_status=$?
set -e
[[ "${wrong_key_status}" -eq 2 ]] || die \
    "docker acceptance wrong-key listing exited with ${wrong_key_status} instead of 2"
require_match "${wrong_key_output}" '"code"[[:space:]]*:[[:space:]]*"book-authentication-failed"' \
    "docker acceptance wrong-key listing did not report book-authentication-failed"
require_no_match "${wrong_key_output}" 'SQLITE_NOTADB' \
    "docker acceptance wrong-key listing leaked the SQLite NOTADB storage symptom"
replacement_key_trial_balance_output="$(docker_with_repo_config run --rm \
    --user "${docker_run_user}" \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    trial-balance \
    --book-file "${book_rel}" \
    --book-key-file "${replacement_book_key_rel}" \
    --effective-date-to 2026-04-08 \
    --output human | tr -d '\r')"
require_match "${replacement_key_trial_balance_output}" '1000' \
    "docker acceptance trial-balance did not remain readable after rekey"

printf 'Docker acceptance: verifying deterministic nonsense workflows\n'
set +e
invalid_cursor_output="$(docker_with_repo_config run --rm \
    --user "${docker_run_user}" \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    list-postings \
    --book-file "${book_rel}" \
    --book-key-file "${replacement_book_key_rel}" \
    --cursor definitely-not-a-valid-cursor | tr -d '\r')"
invalid_cursor_status=$?
prompt_failure_output="$(docker_with_repo_config run --rm \
    --user "${docker_run_user}" \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    open-book \
    --book-file "${prompt_failure_book_rel}" \
    --book-passphrase-prompt | tr -d '\r')"
prompt_failure_status=$?
invalid_request_output="$(docker_with_repo_config run --rm \
    --user "${docker_run_user}" \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    declare-account \
    --book-file "${book_rel}" \
    --book-key-file "${replacement_book_key_rel}" \
    --request-file "${invalid_request_rel}" | tr -d '\r')"
invalid_request_status=$?
set -e
[[ "${invalid_cursor_status}" -eq 2 ]] || die \
    "docker acceptance invalid cursor exited with ${invalid_cursor_status} instead of 2"
require_match "${invalid_cursor_output}" '"code"[[:space:]]*:[[:space:]]*"invalid-page-cursor"' \
    "docker acceptance invalid cursor did not report invalid-page-cursor"
require_no_match "${invalid_cursor_output}" '"code"[[:space:]]*:[[:space:]]*"runtime-failure"' \
    "docker acceptance invalid cursor regressed to runtime-failure"
[[ "${prompt_failure_status}" -eq 2 ]] || die \
    "docker acceptance prompt-unavailable exited with ${prompt_failure_status} instead of 2"
require_match "${prompt_failure_output}" '"code"[[:space:]]*:[[:space:]]*"interactive-prompt-unavailable"' \
    "docker acceptance prompt-unavailable did not report interactive-prompt-unavailable"
require_match "${prompt_failure_output}" '--book-passphrase-stdin' \
    "docker acceptance prompt-unavailable did not report a repair hint"
[[ "${invalid_request_status}" -eq 2 ]] || die \
    "docker acceptance invalid request exited with ${invalid_request_status} instead of 2"
require_match "${invalid_request_output}" '"code"[[:space:]]*:[[:space:]]*"invalid-request"' \
    "docker acceptance invalid request did not report invalid-request"
require_match "${invalid_request_output}" 'Unexpected fields: nonsenseOne, nonsenseTwo' \
    "docker acceptance invalid request did not report all unexpected fields together"

printf 'Docker acceptance: success\n'
