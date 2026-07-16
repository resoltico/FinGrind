#!/usr/bin/env bash
# Resolve the active CLI build directory and invoke the developer direct-Java wrapper surface.

set -euo pipefail

readonly script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly wrapper_entrypoint="${script_dir}/source-checkout-cli-entrypoint.sh"

[[ -f "${wrapper_entrypoint}" ]] || {
    printf 'error: missing CLI wrapper entrypoint helper at %s\n' "${wrapper_entrypoint}" >&2
    exit 1
}

# shellcheck source=/dev/null
source "${wrapper_entrypoint}"

fg_cli_wrapper_launch_direct_java "./scripts/direct-java-cli.sh" "$@"
