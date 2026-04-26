#!/usr/bin/env bash
# Shared low-level helpers for release-surface acceptance workflows.

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

warn() {
    printf 'warning: %s\n' "$1" >&2
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

project_version() {
    local repository_root=$1
    local version
    version="$(awk -F= '/^version=/{print $2; exit}' "${repository_root}/gradle.properties")"
    [[ -n "${version}" ]] || die \
        "could not determine project version from ${repository_root}/gradle.properties"
    printf '%s\n' "${version}"
}

json_array_of_strings() {
    python3 -c '
import json
import sys

print(json.dumps(sys.argv[1:]))
' "$@"
}
