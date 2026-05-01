#!/usr/bin/env bash
# Aggregates the shared release-surface support modules for bundle and Docker acceptance.

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    printf 'error: %s\n' "release-smoke-support.sh is a library and must be sourced by a release-smoke entrypoint." >&2
    exit 1
fi

release_smoke_support_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=/dev/null
source "${release_smoke_support_dir}/release-smoke-common.sh"
# shellcheck source=/dev/null
source "${release_smoke_support_dir}/release-smoke-workflow-support.sh"

unset release_smoke_support_dir
