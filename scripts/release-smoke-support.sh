#!/usr/bin/env bash
# Aggregates the shared release-surface support modules for bundle and Docker acceptance.

release_smoke_support_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=/dev/null
source "${release_smoke_support_dir}/release-smoke-common.sh"
# shellcheck source=/dev/null
source "${release_smoke_support_dir}/release-smoke-workflow.sh"

unset release_smoke_support_dir
