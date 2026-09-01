#!/usr/bin/env bash
# Docker-client discovery and anonymous-configuration helpers for release smoke entrypoints.

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
    candidates=(
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
