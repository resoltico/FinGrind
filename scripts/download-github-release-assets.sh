#!/usr/bin/env bash
# Download a named GitHub release asset set from either a draft or a published release.

set -euo pipefail

download_release_assets_die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

download_release_assets_asset_api_path() {
    local assets_json=$1
    local asset_name=$2

    RELEASE_ASSETS_JSON="${assets_json}" python3 - "${asset_name}" <<'PY'
from __future__ import annotations

import json
import os
import sys
from urllib.parse import urlsplit

target_name = sys.argv[1]
assets = json.loads(os.environ["RELEASE_ASSETS_JSON"]).get("assets", [])
for asset in assets:
    if asset.get("name") == target_name:
        api_url = asset.get("apiUrl", "")
        if not api_url:
            raise SystemExit("release asset apiUrl was empty")
        print(urlsplit(api_url).path)
        break
PY
}

download_release_assets_release_assets_json() {
    local repo_slug=$1
    local tag_name=$2

    gh release view "${tag_name}" \
        --repo "${repo_slug}" \
        --json assets 2>/dev/null
}

download_release_assets_fetch_one() {
    local repo_slug=$1
    local tag_name=$2
    local output_dir=$3
    local retry_count=$4
    local retry_delay_seconds=$5
    local asset_name=$6

    local attempt=1
    local asset_api_path=''
    local assets_json=''
    local output_path="${output_dir}/${asset_name}"
    local temp_path=''

    while true; do
        assets_json="$(download_release_assets_release_assets_json "${repo_slug}" "${tag_name}")" || assets_json=''
        if [[ -n "${assets_json}" ]]; then
            asset_api_path="$(download_release_assets_asset_api_path "${assets_json}" "${asset_name}")" || asset_api_path=''
        else
            asset_api_path=''
        fi

        if [[ -n "${asset_api_path}" ]]; then
            temp_path="$(mktemp "${output_path}.XXXXXX")"
            if gh api --method GET -H 'Accept: application/octet-stream' "${asset_api_path}" >"${temp_path}" 2>/dev/null; then
                mv "${temp_path}" "${output_path}"
                return 0
            fi
            rm -f "${temp_path}"
        fi

        if (( attempt >= retry_count )); then
            download_release_assets_die \
                "failed to download release asset ${asset_name} from ${tag_name}"
        fi
        attempt=$((attempt + 1))
        sleep "${retry_delay_seconds}"
    done
}

download_release_assets_parse_args() {
    local repo_slug=''
    local tag_name=''
    local output_dir=''
    local retry_count=1
    local retry_delay_seconds=1

    while [[ $# -gt 0 ]]; do
        case "$1" in
            --repo)
                repo_slug="${2:-}"
                shift 2
                ;;
            --tag)
                tag_name="${2:-}"
                shift 2
                ;;
            --dir)
                output_dir="${2:-}"
                shift 2
                ;;
            --retries)
                retry_count="${2:-}"
                shift 2
                ;;
            --delay-seconds)
                retry_delay_seconds="${2:-}"
                shift 2
                ;;
            --)
                shift
                break
                ;;
            -*)
                download_release_assets_die "unsupported argument $1"
                ;;
            *)
                break
                ;;
        esac
    done

    [[ -n "${repo_slug}" ]] || download_release_assets_die "--repo is required"
    [[ -n "${tag_name}" ]] || download_release_assets_die "--tag is required"
    [[ -n "${output_dir}" ]] || download_release_assets_die "--dir is required"
    [[ "${retry_count}" =~ ^[0-9]+$ && "${retry_count}" -ge 1 ]] || \
        download_release_assets_die "--retries must be a positive integer"
    [[ "${retry_delay_seconds}" =~ ^[0-9]+$ && "${retry_delay_seconds}" -ge 0 ]] || \
        download_release_assets_die "--delay-seconds must be a non-negative integer"
    [[ $# -gt 0 ]] || download_release_assets_die "at least one release asset name is required"

    readonly DOWNLOAD_RELEASE_ASSETS_REPOSITORY_SLUG="${repo_slug}"
    readonly DOWNLOAD_RELEASE_ASSETS_TAG_NAME="${tag_name}"
    readonly DOWNLOAD_RELEASE_ASSETS_OUTPUT_DIR="${output_dir}"
    readonly DOWNLOAD_RELEASE_ASSETS_RETRY_COUNT="${retry_count}"
    readonly DOWNLOAD_RELEASE_ASSETS_RETRY_DELAY_SECONDS="${retry_delay_seconds}"
    DOWNLOAD_RELEASE_ASSETS_NAMES=("$@")
}

download_release_assets_main() {
    download_release_assets_parse_args "$@"
    mkdir -p "${DOWNLOAD_RELEASE_ASSETS_OUTPUT_DIR}"

    local asset_name
    for asset_name in "${DOWNLOAD_RELEASE_ASSETS_NAMES[@]}"; do
        download_release_assets_fetch_one \
            "${DOWNLOAD_RELEASE_ASSETS_REPOSITORY_SLUG}" \
            "${DOWNLOAD_RELEASE_ASSETS_TAG_NAME}" \
            "${DOWNLOAD_RELEASE_ASSETS_OUTPUT_DIR}" \
            "${DOWNLOAD_RELEASE_ASSETS_RETRY_COUNT}" \
            "${DOWNLOAD_RELEASE_ASSETS_RETRY_DELAY_SECONDS}" \
            "${asset_name}"
    done
}

download_release_assets_main "$@"
