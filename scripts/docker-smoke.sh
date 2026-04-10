#!/usr/bin/env bash
# Build the local Docker image and verify the FinGrind CLI can open a SQLite book file from an
# unusual mounted path inside the container.

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
readonly sqlite_version="$(sed -n 's/^fingrindSqliteVersion=//p' "${repo_root}/gradle.properties" | head -1)"

cleanup() {
    local exit_code=$?
    rm -rf "${smoke_root}" || sudo rm -rf "${smoke_root}" || true
    docker image rm -f "${image_tag}" >/dev/null 2>&1 || true
    exit "${exit_code}"
}

trap cleanup EXIT

command -v docker >/dev/null 2>&1 || die "docker is required for the Docker smoke gate"
[[ -f "${repo_root}/Dockerfile" ]] || die "missing Dockerfile at ${repo_root}/Dockerfile"
[[ -f "${repo_root}/cli/build/libs/fingrind.jar" ]] || die \
    "missing CLI fat JAR at ${repo_root}/cli/build/libs/fingrind.jar; run ./gradlew :cli:shadowJar first"

mkdir -p "${smoke_root}/requests odd"

readonly request_rel='requests odd/request [docker #smoke].json'
readonly book_rel='books odd/nested/entity [docker #smoke].sqlite'
readonly request_path="${smoke_root}/${request_rel}"
readonly book_path="${smoke_root}/${book_rel}"

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

printf 'Docker smoke: building local image\n'
docker build -t "${image_tag}" "${repo_root}" >/dev/null

printf 'Docker smoke: verifying version command\n'
version_output="$(docker run --rm \
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

printf 'Docker smoke: verifying pinned SQLite inside the image\n'
sqlite_version_output="$(docker run --rm \
    --entrypoint /usr/local/bin/sqlite3 \
    "${image_tag}" \
    --version | tr -d '\r')"
[[ "${sqlite_version_output}" == "${sqlite_version}"* ]] || die \
    "docker image sqlite3 did not report pinned version ${sqlite_version}: ${sqlite_version_output}"

printf 'Docker smoke: verifying book write through mounted path\n'
commit_output="$(docker run --rm \
    -w /workdir \
    -v "${smoke_root}:/workdir" \
    "${image_tag}" \
    post-entry \
    --book-file "${book_rel}" \
    --request-file "${request_rel}" | tr -d '\r')"

[[ -f "${book_path}" ]] || die "docker smoke book file was not written: ${book_path}"
require_match "${commit_output}" '"status"[[:space:]]*:[[:space:]]*"committed"' \
    "docker smoke commit did not report committed status"

printf 'Docker smoke: success\n'
