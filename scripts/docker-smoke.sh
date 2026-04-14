#!/usr/bin/env bash
# Build the local Docker image and verify the FinGrind CLI can initialize a book, declare
# accounts, and commit through an unusual mounted path inside the container.

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

readonly script_dir="$(resolve_script_dir)"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly image_tag="fingrind-docker-smoke:$$"
readonly smoke_root="${repo_root}/tmp/docker smoke.$$"
anonymous_docker_config=''
docker_endpoint=''

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

command -v docker >/dev/null 2>&1 || die "docker is required for the Docker smoke gate"
[[ -f "${repo_root}/Dockerfile" ]] || die "missing Dockerfile at ${repo_root}/Dockerfile"
[[ -f "${repo_root}/cli/build/libs/fingrind.jar" ]] || die \
    "missing CLI fat JAR at ${repo_root}/cli/build/libs/fingrind.jar; run ./gradlew :cli:shadowJar first"

docker_endpoint="${DOCKER_HOST:-}"
if [[ -z "${docker_endpoint}" ]]; then
    docker_endpoint="$(
        docker context inspect "$(docker context show 2>/dev/null || true)" \
            --format '{{.Endpoints.docker.Host}}' 2>/dev/null || true
    )"
fi
anonymous_docker_config="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-docker-config.XXXXXX")"
printf '{}\n' > "${anonymous_docker_config}/config.json"

mkdir -p "${smoke_root}/requests odd"

readonly request_rel='requests odd/request [docker #smoke].json'
readonly declare_cash_rel='requests odd/declare account cash [docker #smoke].json'
readonly declare_revenue_rel='requests odd/declare account revenue [docker #smoke].json'
readonly book_rel='books odd/nested/entity [docker #smoke].sqlite'
readonly book_key_rel='books odd/nested/entity [docker #smoke].key'
readonly wrong_book_key_rel='books odd/nested/entity [docker #smoke]-wrong.key'
readonly request_path="${smoke_root}/${request_rel}"
readonly declare_cash_path="${smoke_root}/${declare_cash_rel}"
readonly declare_revenue_path="${smoke_root}/${declare_revenue_rel}"
readonly book_path="${smoke_root}/${book_rel}"
readonly book_key_path="${smoke_root}/${book_key_rel}"
readonly wrong_book_key_path="${smoke_root}/${wrong_book_key_rel}"

mkdir -p "$(dirname -- "${book_path}")"
mkdir -p "$(dirname -- "${book_key_path}")"

cat > "${request_path}" <<JSON
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
    "actorId": "docker-smoke",
    "actorType": "AGENT",
    "commandId": "docker-smoke-command",
    "idempotencyKey": "docker-smoke-idem",
    "causationId": "docker-smoke-cause"
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

printf 'docker-smoke-passphrase\n' > "${book_key_path}"
printf 'definitely-wrong-docker-smoke-passphrase\n' > "${wrong_book_key_path}"

printf 'Docker smoke: building local image\n'
docker_with_repo_config build -t "${image_tag}" "${repo_root}" >/dev/null

printf 'Docker smoke: verifying version command\n'
version_output="$(docker_with_repo_config run --rm \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    version | tr -d '\r')"
require_match "${version_output}" '"status"[[:space:]]*:[[:space:]]*"ok"' \
    "version output did not report ok status"
require_match "${version_output}" '"application"[[:space:]]*:[[:space:]]*"FinGrind"' \
    "version output did not include application name"
require_match "${version_output}" '"version"[[:space:]]*:[[:space:]]*"' \
    "version output did not include version"

printf 'Docker smoke: verifying managed SQLite runtime contract\n'
capabilities_output="$(docker_with_repo_config run --rm \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    capabilities | tr -d '\r')"
require_match "${capabilities_output}" '"sqliteLibrarySource"[[:space:]]*:[[:space:]]*"managed"' \
    "capabilities output did not report the managed SQLite library source"
require_match "${capabilities_output}" '"storageDriver"[[:space:]]*:[[:space:]]*"sqlite-ffm-sqlite3mc"' \
    "capabilities output did not report the SQLite3 Multiple Ciphers storage driver"
require_match "${capabilities_output}" '"bookProtectionMode"[[:space:]]*:[[:space:]]*"required"' \
    "capabilities output did not report required book protection"
require_match "${capabilities_output}" '"defaultBookCipher"[[:space:]]*:[[:space:]]*"chacha20"' \
    "capabilities output did not report the default chacha20 cipher"
require_match "${capabilities_output}" '"bookPassphraseOptions"[[:space:]]*:[[:space:]]*\[[[:space:]]*"--book-key-file"[[:space:]]*,[[:space:]]*"--book-passphrase-stdin"[[:space:]]*,[[:space:]]*"--book-passphrase-prompt"[[:space:]]*\]' \
    "capabilities output did not report the supported book passphrase options"
require_match "${capabilities_output}" '"requiredMinimumSqliteVersion"[[:space:]]*:[[:space:]]*"3\.53\.0"' \
    "capabilities output did not report the required SQLite 3.53.0 minimum"
require_match "${capabilities_output}" '"requiredSqlite3mcVersion"[[:space:]]*:[[:space:]]*"2\.3\.3"' \
    "capabilities output did not report the required SQLite3 Multiple Ciphers 2.3.3 version"
require_match "${capabilities_output}" '"sqliteRuntimeStatus"[[:space:]]*:[[:space:]]*"ready"' \
    "capabilities output did not report a ready SQLite runtime"
require_match "${capabilities_output}" '"loadedSqliteVersion"[[:space:]]*:[[:space:]]*"3\.53\.0"' \
    "capabilities output did not report SQLite 3.53.0"
require_match "${capabilities_output}" '"loadedSqlite3mcVersion"[[:space:]]*:[[:space:]]*"2\.3\.3"' \
    "capabilities output did not report SQLite3 Multiple Ciphers 2.3.3"

printf 'Docker smoke: verifying explicit book initialization through mounted path\n'
open_output="$(docker_with_repo_config run --rm \
    -i \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    open-book \
    --book-file "${book_rel}" \
    --book-passphrase-stdin <<< 'docker-smoke-passphrase' | tr -d '\r')"

[[ -f "${book_path}" ]] || die "docker smoke book file was not initialized: ${book_path}"
require_match "${open_output}" '"status"[[:space:]]*:[[:space:]]*"ok"' \
    "docker smoke open-book did not report ok status"
require_match "${open_output}" '"initializedAt"[[:space:]]*:[[:space:]]*"' \
    "docker smoke open-book did not include initializedAt"

printf 'Docker smoke: verifying account declaration and registry listing\n'
declare_cash_output="$(docker_with_repo_config run --rm \
    -i \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    declare-account \
    --book-file "${book_rel}" \
    --book-passphrase-stdin \
    --request-file "${declare_cash_rel}" <<< 'docker-smoke-passphrase' | tr -d '\r')"
declare_revenue_output="$(docker_with_repo_config run --rm \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    declare-account \
    --book-file "${book_rel}" \
    --book-key-file "${book_key_rel}" \
    --request-file "${declare_revenue_rel}" | tr -d '\r')"
list_output="$(docker_with_repo_config run --rm \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    list-accounts \
    --book-file "${book_rel}" \
    --book-key-file "${book_key_rel}" | tr -d '\r')"

require_match "${declare_cash_output}" '"status"[[:space:]]*:[[:space:]]*"ok"' \
    "docker smoke cash declaration did not report ok status"
require_match "${declare_cash_output}" '"accountCode"[[:space:]]*:[[:space:]]*"1000"' \
    "docker smoke cash declaration did not echo the declared account"
require_match "${declare_revenue_output}" '"accountCode"[[:space:]]*:[[:space:]]*"2000"' \
    "docker smoke revenue declaration did not echo the declared account"
require_match "${list_output}" '"accountCode"[[:space:]]*:[[:space:]]*"1000"' \
    "docker smoke account listing did not include account 1000"
require_match "${list_output}" '"accountCode"[[:space:]]*:[[:space:]]*"2000"' \
    "docker smoke account listing did not include account 2000"

printf 'Docker smoke: verifying wrong key is rejected deterministically\n'
set +e
wrong_key_output="$(docker_with_repo_config run --rm \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    list-accounts \
    --book-file "${book_rel}" \
    --book-key-file "${wrong_book_key_rel}" | tr -d '\r')"
wrong_key_status=$?
set -e

[[ "${wrong_key_status}" -eq 1 ]] || die \
    "docker smoke wrong-key listing exited with ${wrong_key_status} instead of 1"
require_match "${wrong_key_output}" '"code"[[:space:]]*:[[:space:]]*"runtime-failure"' \
    "docker smoke wrong-key listing did not report a runtime-failure"
require_match "${wrong_key_output}" 'SQLITE_NOTADB' \
    "docker smoke wrong-key listing did not expose the SQLite NOTADB failure"

printf 'Docker smoke: verifying preflight and commit through mounted path\n'
preflight_output="$(docker_with_repo_config run --rm \
    -i \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    preflight-entry \
    --book-file "${book_rel}" \
    --book-passphrase-stdin \
    --request-file "${request_rel}" <<< 'docker-smoke-passphrase' | tr -d '\r')"
commit_output="$(docker_with_repo_config run --rm \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    post-entry \
    --book-file "${book_rel}" \
    --book-key-file "${book_key_rel}" \
    --request-file "${request_rel}" | tr -d '\r')"

require_match "${preflight_output}" '"status"[[:space:]]*:[[:space:]]*"preflight-accepted"' \
    "docker smoke preflight did not report preflight-accepted status"
require_match "${commit_output}" '"status"[[:space:]]*:[[:space:]]*"committed"' \
    "docker smoke commit did not report committed status"

printf 'Docker smoke: success\n'
