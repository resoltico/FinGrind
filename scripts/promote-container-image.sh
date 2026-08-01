#!/usr/bin/env bash
# Promote one durable staging candidate into immutable public container tags.

set -euo pipefail

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

readonly script_dir="$(resolve_script_dir)"
# shellcheck source=./container-promotion-support.sh
source "${script_dir}/container-promotion-support.sh"

if [[ $# -ne 4 ]]; then
    container_promotion_die \
        'usage: promote-container-image.sh <staging-image-ref> <public-image-ref> <X.Y.Z> <true|false>'
fi

container_promotion_main "$@"
