#!/usr/bin/env bash
# Canonical stable release-tag syntax shared by every publication boundary.

release_tag_is_stable() {
    local release_tag_value=${1:-}
    [[ "${release_tag_value}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]
}

release_tag_version() {
    local release_tag_value=${1:-}

    release_tag_is_stable "${release_tag_value}" || return 1
    printf '%s\n' "${release_tag_value#v}"
}
