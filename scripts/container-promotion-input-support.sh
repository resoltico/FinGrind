#!/usr/bin/env bash
# Input and retry policy validation for container publication.

container_promotion_validate_version() {
    local version=$1

    [[ "${version}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || container_promotion_die \
        "container release version must be one stable X.Y.Z version, got ${version}"
}

container_promotion_read_retry_count() {
    local retries="${FINGRIND_CONTAINER_PROMOTION_VERIFY_RETRIES:-6}"

    [[ "${retries}" =~ ^[1-9][0-9]*$ ]] || container_promotion_die \
        'FINGRIND_CONTAINER_PROMOTION_VERIFY_RETRIES must be one positive integer'
    printf '%s\n' "${retries}"
}

container_promotion_read_retry_delay_seconds() {
    local delay_seconds="${FINGRIND_CONTAINER_PROMOTION_VERIFY_DELAY_SECONDS:-2}"

    [[ "${delay_seconds}" =~ ^[0-9]+$ ]] || container_promotion_die \
        'FINGRIND_CONTAINER_PROMOTION_VERIFY_DELAY_SECONDS must be one nonnegative integer'
    printf '%s\n' "${delay_seconds}"
}
