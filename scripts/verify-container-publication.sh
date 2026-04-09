#!/usr/bin/env bash
# Verify that both the exact version tag and latest tag are publicly pullable and runnable from
# GHCR, accounting for short registry propagation delays after publication.

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

readonly image_name="${1:-}"
readonly expected_version="${2:-}"
readonly retry_count="${FINGRIND_PUBLICATION_VERIFY_RETRIES:-12}"
readonly retry_delay_seconds="${FINGRIND_PUBLICATION_VERIFY_DELAY_SECONDS:-10}"
docker_config_dir=''

[[ -n "${image_name}" ]] || die "image name is required"
[[ -n "${expected_version}" ]] || die "expected version is required"

cleanup() {
    [[ -n "${docker_config_dir}" ]] || return
    rm -rf "${docker_config_dir}"
}

trap cleanup EXIT

docker_config_dir="$(mktemp -d)"

anonymous_docker() {
    docker --config "${docker_config_dir}" "$@"
}

verify_ref() {
    local tag_ref=$1
    local image_ref="${image_name}:${tag_ref}"
    local attempt version_output

    for ((attempt = 1; attempt <= retry_count; attempt++)); do
        if anonymous_docker pull "${image_ref}" >/dev/null 2>&1; then
            version_output="$(anonymous_docker run --rm "${image_ref}" version 2>/dev/null | tr -d '\r')"
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

verify_ref "${expected_version}"
verify_ref latest
