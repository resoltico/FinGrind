#!/usr/bin/env bash

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    printf '%s\n' 'docker-smoke-target-support.sh is a library and must be sourced by docker-smoke.sh.' >&2
    exit 1
fi

docker_smoke_normalize_target_architecture() {
    local raw_architecture=$1

    case "${raw_architecture}" in
        arm64|aarch64)
            printf '%s\n' 'aarch64'
            ;;
        amd64|x86_64|x64)
            printf '%s\n' 'x86_64'
            ;;
        *)
            return 1
            ;;
    esac
}

docker_smoke_platform_for_architecture() {
    local architecture_id=$1

    case "${architecture_id}" in
        x86_64)
            printf '%s\n' 'linux/amd64'
            ;;
        aarch64)
            printf '%s\n' 'linux/arm64'
            ;;
        *)
            return 1
            ;;
    esac
}

docker_smoke_resolve_target_architecture() {
    local requested_architecture=$1
    local host_architecture=$2

    if [[ -n "${requested_architecture}" ]]; then
        docker_smoke_normalize_target_architecture "${requested_architecture}" || die \
            "unsupported Docker target architecture ${requested_architecture}; expected x86_64 or aarch64"
        return
    fi

    docker_smoke_normalize_target_architecture "${host_architecture}" || die \
        "unsupported host architecture ${host_architecture}; set FINGRIND_DOCKER_TARGET_ARCHITECTURE_ID to x86_64 or aarch64"
}
