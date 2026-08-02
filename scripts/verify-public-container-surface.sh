#!/usr/bin/env bash
# Verify the public container surface from anonymous Docker state, including a mounted-book
# bookkeeping loop and PDF artifact generation.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

require_match() {
    local text=$1
    local pattern=$2

    printf '%s\n' "${text}" | grep -Eq "${pattern}"
}

require_literal_block() {
    local text=$1
    local block=$2

    [[ "${text}" == *"${block}"* ]]
}

readonly image_name="${1:-}"
readonly expected_version="${2:-}"
readonly explicit_tag_ref="${3:-}"
readonly retry_count="${FINGRIND_PUBLICATION_VERIFY_RETRIES:-12}"
readonly retry_delay_seconds="${FINGRIND_PUBLICATION_VERIFY_DELAY_SECONDS:-10}"
readonly verify_latest_ref="${FINGRIND_VERIFY_PUBLIC_CONTAINER_LATEST:-true}"
readonly fixture_entity_name='Release Protocol Fixture'
readonly fixture_functional_currency='EUR'
readonly fixture_fiscal_year_start='01-01'
readonly fixture_attestation_founder_principal_id='4bc17dd7-145f-4ea7-bb55-167ca2f6ac11'
readonly fixture_attestation_founder_passphrase='release-protocol-fixture-attestation-passphrase'
readonly docker_run_user="$(id -u):$(id -g)"
docker_config_dir=''
report_root=''
readonly container_support_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
primary_tag_ref=''

[[ -n "${image_name}" ]] || die "image name is required"
[[ -n "${expected_version}" ]] || die "expected version is required"
[[ "${verify_latest_ref}" == "true" || "${verify_latest_ref}" == "false" ]] || die \
    "FINGRIND_VERIFY_PUBLIC_CONTAINER_LATEST must be true or false"

source "${container_support_dir}/verify-public-container-run-support.sh"
source "${container_support_dir}/verify-public-container-book-surface-support.sh"

cleanup() {
    [[ -n "${report_root}" ]] && rm -rf "${report_root}"
    [[ -n "${docker_config_dir}" ]] && rm -rf "${docker_config_dir}"
}

trap cleanup EXIT

docker_config_dir="$(mktemp -d)"
report_root="$(mktemp -d)"

verify_ref() {
    local tag_ref=$1
    local image_ref="${image_name}:${tag_ref}"
    local attempt version_output

    for ((attempt = 1; attempt <= retry_count; attempt++)); do
        if anonymous_docker pull "${image_ref}" >/dev/null 2>&1; then
            version_output="$(anonymous_docker run --rm "${image_ref}" version --output json 2>/dev/null | tr -d '\r')"
            if require_match "${version_output}" '"status"[[:space:]]*:[[:space:]]*"ok"' \
                && require_match "${version_output}" '"application"[[:space:]]*:[[:space:]]*"FinGrind"' \
                && require_match "${version_output}" "\"version\"[[:space:]]*:[[:space:]]*\"${expected_version}\""; then
                printf 'Verified published container: %s\n' "${image_ref}"
                return
            fi
        fi

        if (( attempt < retry_count )); then
            sleep "${retry_delay_seconds}"
        fi
    done

    die "published container ${image_ref} did not report FinGrind version ${expected_version}"
}

verify_native_provenance_surface() {
    local image_ref="${image_name}:${primary_tag_ref}"

    require_nonempty_container_file \
        "${image_ref}" \
        /opt/fingrind/lib/native/toolchain-fingerprint.json \
        "native toolchain fingerprint"
    require_nonempty_container_file \
        "${image_ref}" \
        /opt/fingrind/lib/native/build-contract.json \
        "native build contract"

    printf 'Verified native provenance surface: %s\n' "${image_ref}"
}

if [[ -n "${explicit_tag_ref}" ]]; then
    primary_tag_ref="${explicit_tag_ref}"
else
    primary_tag_ref="${expected_version}"
fi
verify_ref "${primary_tag_ref}"
if [[ -z "${explicit_tag_ref}" && "${verify_latest_ref}" == "true" ]]; then
    verify_ref latest
fi
verify_native_provenance_surface
verify_mounted_book_surface
