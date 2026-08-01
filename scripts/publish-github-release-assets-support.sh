#!/usr/bin/env bash
# Asset-inventory and digest convergence helpers for a staged GitHub release.

publish_release_expected_asset_names_from_paths() {
    local asset_path
    local asset_name

    for asset_path in "$@"; do
        asset_name="$(basename -- "${asset_path}")"
        [[ -n "${asset_name}" && "${asset_name}" != *$'\n'* ]] || publish_release_die \
            "release asset path must resolve to one nonempty single-line file name: ${asset_path}"
        printf '%s\n' "${asset_name}"
    done
}

publish_release_expected_asset_names_json() {
    PUBLISH_RELEASE_EXPECTED_ASSET_NAMES="$(printf '%s\n' "$@")" python3 - <<'PY'
from __future__ import annotations

import json
import os

names = [name for name in os.environ["PUBLISH_RELEASE_EXPECTED_ASSET_NAMES"].splitlines() if name]
if not names or len(names) != len(set(names)):
    raise SystemExit("expected release asset names must be nonempty and unique")
print(json.dumps(names, separators=(",", ":")))
PY
}

publish_release_require_exact_asset_inventory() {
    local expected_names_json
    local release_json
    local inventory_output

    expected_names_json="$(publish_release_expected_asset_names_json "$@")" || publish_release_die \
        "could not resolve the expected release asset-name set"
    release_json="$(publish_release_view_json)" || publish_release_die \
        "failed to inspect release ${PUBLISH_RELEASE_TAG_NAME} asset inventory"
    inventory_output="$(
        PUBLISH_RELEASE_EXPECTED_ASSET_NAMES_JSON="${expected_names_json}" \
            PUBLISH_RELEASE_OBSERVED_RELEASE_JSON="${release_json}" \
            python3 - "${PUBLISH_RELEASE_TAG_NAME}" 2>&1 <<'PY'
from __future__ import annotations

import json
import os
import sys

tag_name = sys.argv[1]
expected = json.loads(os.environ["PUBLISH_RELEASE_EXPECTED_ASSET_NAMES_JSON"])
release = json.loads(os.environ["PUBLISH_RELEASE_OBSERVED_RELEASE_JSON"])
assets = release.get("assets") if isinstance(release, dict) else None
if not isinstance(assets, list):
    raise SystemExit(f"release {tag_name} did not expose an asset list")

actual: list[str] = []
for asset in assets:
    if not isinstance(asset, dict) or not isinstance(asset.get("name"), str) or not asset["name"]:
        raise SystemExit(f"release {tag_name} exposed an asset without one nonempty name")
    actual.append(asset["name"])

duplicates = sorted({name for name in actual if actual.count(name) > 1})
missing = sorted(set(expected) - set(actual))
unexpected = sorted(set(actual) - set(expected))
if duplicates or missing or unexpected:
    details: list[str] = []
    if duplicates:
        details.append("duplicate=" + ", ".join(duplicates))
    if missing:
        details.append("missing=" + ", ".join(missing))
    if unexpected:
        details.append("unexpected=" + ", ".join(unexpected))
    raise SystemExit(f"release {tag_name} asset inventory is not exact: " + "; ".join(details))
PY
    )" || publish_release_die "${inventory_output}"
}

publish_release_unexpected_draft_asset_ids() {
    local expected_names_json
    local release_json

    expected_names_json="$(publish_release_expected_asset_names_json "$@")" || return 1
    release_json="$(publish_release_view_json)" || return 1
    PUBLISH_RELEASE_EXPECTED_ASSET_NAMES_JSON="${expected_names_json}" \
        PUBLISH_RELEASE_OBSERVED_RELEASE_JSON="${release_json}" \
        python3 - "${PUBLISH_RELEASE_TAG_NAME}" <<'PY'
from __future__ import annotations

import json
import os
import sys

tag_name = sys.argv[1]
expected = set(json.loads(os.environ["PUBLISH_RELEASE_EXPECTED_ASSET_NAMES_JSON"]))
release = json.loads(os.environ["PUBLISH_RELEASE_OBSERVED_RELEASE_JSON"])
assets = release.get("assets") if isinstance(release, dict) else None
if not isinstance(assets, list):
    raise SystemExit(f"release {tag_name} did not expose an asset list")

actual: list[str] = []
for asset in assets:
    if not isinstance(asset, dict) or not isinstance(asset.get("name"), str) or not asset["name"]:
        raise SystemExit(f"release {tag_name} exposed an asset without one nonempty name")
    actual.append(asset["name"])
if len(actual) != len(set(actual)):
    raise SystemExit(f"release {tag_name} exposed duplicate asset names")

for asset in assets:
    if asset["name"] in expected:
        continue
    asset_id = asset.get("id")
    if not isinstance(asset_id, int) or asset_id <= 0:
        raise SystemExit(f"release {tag_name} exposed an unexpected asset without one positive numeric id")
    print(asset_id)
PY
}

publish_release_remove_unexpected_draft_assets() {
    local unexpected_asset_ids
    local asset_id

    unexpected_asset_ids="$(publish_release_unexpected_draft_asset_ids "$@")" || publish_release_die \
        "failed to inspect draft release ${PUBLISH_RELEASE_TAG_NAME} for unexpected assets"
    while IFS= read -r asset_id; do
        [[ -n "${asset_id}" ]] || continue
        [[ "${asset_id}" =~ ^[1-9][0-9]*$ ]] || publish_release_die \
            "draft release ${PUBLISH_RELEASE_TAG_NAME} exposed an invalid unexpected asset id"
        gh api --method DELETE \
            "/repos/${PUBLISH_RELEASE_REPO_FULL_NAME}/releases/assets/${asset_id}" >/dev/null || \
            publish_release_die \
                "failed to remove unexpected draft release asset id ${asset_id} from ${PUBLISH_RELEASE_TAG_NAME}"
    done <<< "${unexpected_asset_ids}"
}

publish_release_delete_asset_if_present() {
    local asset_name=$1
    local observed_digest

    observed_digest="$(publish_release_resolve_asset_digest_or_die "${asset_name}")"
    [[ -n "${observed_digest}" ]] || return 0
    gh release delete-asset "${PUBLISH_RELEASE_TAG_NAME}" "${asset_name}" --yes >/dev/null 2>&1 || {
        observed_digest="$(publish_release_resolve_asset_digest_or_die "${asset_name}")"
        [[ -z "${observed_digest}" ]] || publish_release_die \
            "failed to replace draft release asset ${asset_name} on ${PUBLISH_RELEASE_TAG_NAME}"
    }
}

publish_release_upload_asset() {
    local asset_path=$1
    local asset_name
    local observed_digest
    local expected_digest

    asset_name="$(basename -- "${asset_path}")"
    gh release upload "${PUBLISH_RELEASE_TAG_NAME}" "${asset_path}" >/dev/null 2>&1 || {
        observed_digest="$(publish_release_resolve_asset_digest_or_die "${asset_name}")"
        expected_digest="sha256:$(publish_release_compute_sha256 "${asset_path}")"
        [[ "${observed_digest}" == "${expected_digest}" ]] || publish_release_die \
            "failed to upload ${asset_name} to release ${PUBLISH_RELEASE_TAG_NAME}"
    }
}

publish_release_converge_asset() {
    local asset_path=$1
    local asset_name
    local expected_digest
    local observed_digest

    asset_name="$(basename -- "${asset_path}")"
    [[ -f "${asset_path}" ]] || publish_release_die "missing release asset at ${asset_path}"
    expected_digest="sha256:$(publish_release_compute_sha256 "${asset_path}")"
    observed_digest="$(publish_release_resolve_asset_digest_or_die "${asset_name}")"

    if [[ "${observed_digest}" == "${expected_digest}" ]]; then
        return
    fi

    publish_release_delete_asset_if_present "${asset_name}"
    publish_release_upload_asset "${asset_path}"

    observed_digest="$(publish_release_resolve_asset_digest_or_die "${asset_name}")"
    [[ "${observed_digest}" == "${expected_digest}" ]] || publish_release_die \
        "release ${PUBLISH_RELEASE_TAG_NAME} asset ${asset_name} did not converge to digest ${expected_digest}"
}

publish_release_assets_match_expected() {
    local asset_path
    local asset_name
    local expected_digest
    local observed_digest
    local expected_asset_names=()

    while IFS= read -r asset_name; do
        expected_asset_names+=("${asset_name}")
    done < <(publish_release_expected_asset_names_from_paths "${PUBLISH_RELEASE_ASSET_PATHS[@]}")
    publish_release_require_exact_asset_inventory "${expected_asset_names[@]}" || return 1

    for asset_path in "${PUBLISH_RELEASE_ASSET_PATHS[@]}"; do
        asset_name="$(basename -- "${asset_path}")"
        expected_digest="sha256:$(publish_release_compute_sha256 "${asset_path}")"
        observed_digest="$(publish_release_resolve_asset_digest_or_die "${asset_name}")"
        [[ "${observed_digest}" == "${expected_digest}" ]] || return 1
    done
    return 0
}
