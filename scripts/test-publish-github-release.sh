#!/usr/bin/env bash
# Guard the GitHub release publisher against drifting away from the draft-staging, immutable-public
# publication contract.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
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

readonly script_dir="$(resolve_script_dir)"
readonly publisher="${script_dir}/publish-github-release.sh"
readonly finalizer="${script_dir}/finalize-github-release.sh"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly release_plan_reader="${script_dir}/read-release-publication-plan.py"

[[ -x "${publisher}" ]] || die "missing executable release publisher"
[[ -x "${finalizer}" ]] || die "missing executable release finalizer"
[[ -f "${release_plan_reader}" ]] || die "missing release-publication plan reader"

readonly release_assets_json="$(
    python3 "${release_plan_reader}" \
        --version 9.9.9 \
        --repository-root "${repo_root}" | jq -c '.releaseAssetNames'
)"
readonly first_release_asset_name="$(printf '%s' "${release_assets_json}" | jq -r '.[0]')"
readonly expected_asset_count="$(printf '%s' "${release_assets_json}" | jq -r 'length')"
[[ -n "${first_release_asset_name}" && "${first_release_asset_name}" != "null" ]] || die \
    "canonical release plan did not expose one release asset"
[[ "${expected_asset_count}" =~ ^[1-9][0-9]*$ ]] || die \
    "canonical release plan did not expose one nonempty release asset set"

fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-test-publish-github-release.XXXXXX")"
cleanup() {
    rm -rf "${fixture_root}"
}
trap cleanup EXIT

mkdir -p "${fixture_root}/bin"

cat > "${fixture_root}/bin/gh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

readonly state_file="${FAKE_RELEASE_STATE_FILE:?}"
readonly operation_log="${FAKE_RELEASE_OPERATION_LOG:?}"
readonly expected_tag="${RELEASE_TAG:-v9.9.9}"

python3_state() {
    python3 - "${state_file}" "$@" <<'PY'
from __future__ import annotations

import json
from pathlib import Path
import sys

state_path = Path(sys.argv[1])
action = sys.argv[2]

if state_path.exists():
    state = json.loads(state_path.read_text(encoding="utf-8"))
else:
    state = {
        "exists": False,
        "id": 444,
        "draft": False,
        "make_latest": "false",
        "assets": {},
        "visibilityDelayRemaining": 0,
    }

if action == "dump":
    print(json.dumps(state))
elif action == "exists":
    raise SystemExit(0 if state["exists"] else 1)
elif action == "field":
    value = state[sys.argv[3]]
    if isinstance(value, bool):
        print("true" if value else "false")
    else:
        print(value)
elif action == "asset-digest":
    print(state["assets"].get(sys.argv[3], ""))
elif action == "create":
    state["exists"] = True
    state["draft"] = True
    state["make_latest"] = "false"
    state_path.write_text(json.dumps(state), encoding="utf-8")
elif action == "patch":
    payload = json.loads(sys.argv[3])
    state["exists"] = True
    state["draft"] = payload["draft"]
    state["make_latest"] = payload["make_latest"]
    state_path.write_text(json.dumps(state), encoding="utf-8")
elif action == "delete-asset":
    state["assets"].pop(sys.argv[3], None)
    state_path.write_text(json.dumps(state), encoding="utf-8")
elif action == "delete-asset-id":
    asset_id = int(sys.argv[3])
    asset_names = sorted(state["assets"])
    if asset_id <= 0 or asset_id > len(asset_names):
        raise SystemExit(f"missing asset id: {asset_id}")
    state["assets"].pop(asset_names[asset_id - 1])
    state_path.write_text(json.dumps(state), encoding="utf-8")
elif action == "upload":
    state["assets"][sys.argv[3]] = sys.argv[4]
    state_path.write_text(json.dumps(state), encoding="utf-8")
else:
    raise SystemExit(f"unsupported state action: {action}")
PY
}

emit_release_listing() {
    python3 - "${state_file}" "${expected_tag}" <<'PY'
from __future__ import annotations

import json
from pathlib import Path
import sys

state_path = Path(sys.argv[1])
tag_name = sys.argv[2]

if state_path.exists():
    state = json.loads(state_path.read_text(encoding="utf-8"))
else:
    state = {
        "exists": False,
        "id": 444,
        "draft": False,
        "make_latest": "false",
        "assets": {},
        "visibilityDelayRemaining": 0,
    }

if state["exists"] and state.get("visibilityDelayRemaining", 0) > 0:
    state["visibilityDelayRemaining"] -= 1
    state_path.write_text(json.dumps(state), encoding="utf-8")
    print("[]")
    raise SystemExit(0)

if not state["exists"]:
    print("[]")
    raise SystemExit(0)

release = {
    "id": state["id"],
    "draft": state["draft"],
    "make_latest": state["make_latest"],
    "tag_name": tag_name,
    "assets": [
        {"id": index, "name": name, "digest": digest}
        for index, (name, digest) in enumerate(sorted(state["assets"].items()), start=1)
    ],
}
print(json.dumps([release]))
PY
}

compute_sha256() {
    python3 - "$1" <<'PY'
from hashlib import sha256
from pathlib import Path
import sys

target = Path(sys.argv[1])
digest = sha256()
with target.open("rb") as handle:
    for chunk in iter(lambda: handle.read(1024 * 1024), b""):
        digest.update(chunk)
print(digest.hexdigest())
PY
}

log_operation() {
    printf '%s\n' "$1" >> "${operation_log}"
}

case "${1:-}:${2:-}" in
    repo:view)
        [[ "${3:-}" == "--json" && "${4:-}" == "nameWithOwner" && "${5:-}" == "--jq" && "${6:-}" == ".nameWithOwner" ]] || exit 1
        printf 'resoltico/FinGrind\n'
        ;;
    api:--paginate)
        [[ "${3:-}" == "/repos/resoltico/FinGrind/releases?per_page=100" ]] || exit 1
        emit_release_listing
        ;;
    api:/repos/resoltico/FinGrind/releases/tags/*)
        exit 1
        ;;
    api:--method)
        case "${3:-}" in
            PATCH)
                [[ "${4:-}" == "/repos/resoltico/FinGrind/releases/444" ]] || exit 1
                [[ "${5:-}" == "--input" && "${6:-}" == "-" ]] || exit 1
                payload="$(cat)"
                python3_state patch "${payload}"
                log_operation "patch ${payload}"
                ;;
            DELETE)
                [[ "${4:-}" =~ ^/repos/resoltico/FinGrind/releases/assets/[1-9][0-9]*$ ]] || exit 1
                asset_id="${4##*/}"
                python3_state delete-asset-id "${asset_id}"
                log_operation "delete-id ${asset_id}"
                ;;
            *)
                exit 1
                ;;
        esac
        ;;
    release:create)
        python3_state create
        log_operation "create"
        ;;
    release:delete-asset)
        asset_name="${4:-}"
        python3_state delete-asset "${asset_name}"
        log_operation "delete ${asset_name}"
        ;;
    release:upload)
        asset_path="${4:-}"
        asset_name="$(basename -- "${asset_path}")"
        digest="sha256:$(compute_sha256 "${asset_path}")"
        python3_state upload "${asset_name}" "${digest}"
        log_operation "upload ${asset_name} ${digest}"
        ;;
    *)
        exit 1
        ;;
esac
EOF
chmod +x "${fixture_root}/bin/gh"

create_canonical_assets() {
    local state_dir=$1
    local content_seed=$2

    CANONICAL_RELEASE_ASSET_NAMES_JSON="${release_assets_json}" \
        CANONICAL_RELEASE_ASSET_ROOT="${state_dir}/release-assets" \
        CANONICAL_RELEASE_ASSET_CONTENT_SEED="${content_seed}" \
        python3 - <<'PY'
from __future__ import annotations

import json
import os
from pathlib import Path

asset_root = Path(os.environ["CANONICAL_RELEASE_ASSET_ROOT"])
asset_root.mkdir(parents=True, exist_ok=True)
for index, asset_name in enumerate(json.loads(os.environ["CANONICAL_RELEASE_ASSET_NAMES_JSON"])):
    asset_path = asset_root / asset_name
    asset_path.write_bytes(
        f"{os.environ['CANONICAL_RELEASE_ASSET_CONTENT_SEED']}:{index}:{asset_name}\n".encode(
            "utf-8"
        )
    )
PY
}

canonical_asset_digests_json() {
    local state_dir=$1

    CANONICAL_RELEASE_ASSET_NAMES_JSON="${release_assets_json}" \
        CANONICAL_RELEASE_ASSET_ROOT="${state_dir}/release-assets" \
        python3 - <<'PY'
from __future__ import annotations

from hashlib import sha256
import json
import os
from pathlib import Path

asset_root = Path(os.environ["CANONICAL_RELEASE_ASSET_ROOT"])
digests: dict[str, str] = {}
for asset_name in json.loads(os.environ["CANONICAL_RELEASE_ASSET_NAMES_JSON"]):
    asset_path = asset_root / asset_name
    if not asset_path.is_file():
        raise SystemExit(f"missing canonical release fixture asset: {asset_path}")
    digests[asset_name] = "sha256:" + sha256(asset_path.read_bytes()).hexdigest()
print(json.dumps(digests, sort_keys=True, separators=(",", ":")))
PY
}

write_release_state() {
    local state_dir=$1
    local exists=$2
    local draft=$3
    local make_latest=$4
    local assets_json=$5
    local visibility_delay=${6:-0}

    python3 - \
        "${state_dir}/release-state.json" \
        "${exists}" \
        "${draft}" \
        "${make_latest}" \
        "${assets_json}" \
        "${visibility_delay}" <<'PY'
from __future__ import annotations

import json
from pathlib import Path
import sys

state_path = Path(sys.argv[1])
exists, draft, make_latest = sys.argv[2:5]
assets = json.loads(sys.argv[5])
visibility_delay = int(sys.argv[6])
state_path.write_text(
    json.dumps(
        {
            "exists": exists == "true",
            "id": 444,
            "draft": draft == "true",
            "make_latest": make_latest,
            "assets": assets,
            "visibilityDelayRemaining": visibility_delay,
        },
        sort_keys=True,
    ),
    encoding="utf-8",
)
PY
}

assert_state_asset_inventory() {
    local state_dir=$1
    local expected_assets_json=$2
    local observed_assets_json

    observed_assets_json="$(jq -cS '.assets' "${state_dir}/release-state.json")"
    [[ "${observed_assets_json}" == "$(printf '%s' "${expected_assets_json}" | jq -cS .)" ]] || die \
        "release fixture asset inventory did not converge to the canonical exact set"
}

assert_no_release_mutation() {
    local state_dir=$1

    if [[ -s "${state_dir}/operations.log" ]]; then
        die "release control mutated a surface that should have remained immutable"
    fi
}

run_publish_fixture() {
    local state_dir=$1
    local release_tag=${2:-v9.9.9}
    local asset_name
    local asset_paths=()

    while IFS= read -r asset_name; do
        asset_paths+=("${state_dir}/release-assets/${asset_name}")
    done < <(printf '%s' "${release_assets_json}" | jq -r '.[]')
    PATH="${fixture_root}/bin:${PATH}" \
        FINGRIND_RELEASE_DRAFT_VISIBILITY_DELAY_SECONDS=0 \
        GH_TOKEN='test-token' \
        RELEASE_TAG="${release_tag}" \
        FAKE_RELEASE_STATE_FILE="${state_dir}/release-state.json" \
        FAKE_RELEASE_OPERATION_LOG="${state_dir}/operations.log" \
        bash "${publisher}" "${asset_paths[@]}" >/dev/null
}

run_finalize_fixture() {
    local state_dir=$1
    local mark_latest=$2
    local release_tag=${3:-v9.9.9}
    PATH="${fixture_root}/bin:${PATH}" \
        GH_TOKEN='test-token' \
        RELEASE_TAG="${release_tag}" \
        FINGRIND_RELEASE_MARK_LATEST="${mark_latest}" \
        FINGRIND_RELEASE_PAYLOAD_ROOT="${repo_root}" \
        FAKE_RELEASE_STATE_FILE="${state_dir}/release-state.json" \
        FAKE_RELEASE_OPERATION_LOG="${state_dir}/operations.log" \
        bash "${finalizer}" >/dev/null
}

matching_state_dir="${fixture_root}/matching"
mkdir -p "${matching_state_dir}"
create_canonical_assets "${matching_state_dir}" 'matching-asset'
matching_assets_json="$(canonical_asset_digests_json "${matching_state_dir}")"
write_release_state "${matching_state_dir}" true false false "${matching_assets_json}"
: > "${matching_state_dir}/operations.log"
run_publish_fixture "${matching_state_dir}"
assert_no_release_mutation "${matching_state_dir}"

replacement_state_dir="${fixture_root}/replacement"
mkdir -p "${replacement_state_dir}"
create_canonical_assets "${replacement_state_dir}" 'replacement-asset'
replacement_assets_json="$(canonical_asset_digests_json "${replacement_state_dir}")"
replacement_state_assets_json="$(
    printf '%s' "${replacement_assets_json}" | jq \
        --arg asset_name "${first_release_asset_name}" \
        '.[$asset_name] = "sha256:obsolete"'
)"
write_release_state "${replacement_state_dir}" true false true "${replacement_state_assets_json}"
: > "${replacement_state_dir}/operations.log"
if run_publish_fixture "${replacement_state_dir}"; then
    die "release publisher rewrote a published release instead of failing for immutable public assets"
fi
assert_no_release_mutation "${replacement_state_dir}"

created_state_dir="${fixture_root}/created"
mkdir -p "${created_state_dir}"
create_canonical_assets "${created_state_dir}" 'created-asset'
created_assets_json="$(canonical_asset_digests_json "${created_state_dir}")"
write_release_state "${created_state_dir}" false false false '{}'
: > "${created_state_dir}/operations.log"
run_publish_fixture "${created_state_dir}"
grep -Fq 'create' "${created_state_dir}/operations.log" || die \
    "release publisher did not create a draft release when none existed"
[[ "$(grep -c '^upload ' "${created_state_dir}/operations.log" || true)" == "${expected_asset_count}" ]] || die \
    "release publisher did not upload every canonical release asset"
assert_state_asset_inventory "${created_state_dir}" "${created_assets_json}"
if grep -Fq '"draft": false' "${created_state_dir}/operations.log"; then
    die "release publisher finalized the release during draft staging"
fi

created_visibility_state_dir="${fixture_root}/created-with-visibility-delay"
mkdir -p "${created_visibility_state_dir}"
create_canonical_assets "${created_visibility_state_dir}" 'created-with-visibility-delay'
created_visibility_assets_json="$(canonical_asset_digests_json "${created_visibility_state_dir}")"
write_release_state "${created_visibility_state_dir}" false false false '{}' 1
: > "${created_visibility_state_dir}/operations.log"
run_publish_fixture "${created_visibility_state_dir}"
grep -Fq 'create' "${created_visibility_state_dir}/operations.log" || die \
    "release publisher did not create a draft release before waiting for visibility"
[[ "$(grep -c '^upload ' "${created_visibility_state_dir}/operations.log" || true)" == "${expected_asset_count}" ]] || die \
    "release publisher did not upload every canonical release asset after draft visibility lag"
assert_state_asset_inventory "${created_visibility_state_dir}" "${created_visibility_assets_json}"

draft_extra_state_dir="${fixture_root}/draft-extra"
mkdir -p "${draft_extra_state_dir}"
create_canonical_assets "${draft_extra_state_dir}" 'draft-extra-asset'
draft_extra_expected_assets_json="$(canonical_asset_digests_json "${draft_extra_state_dir}")"
draft_extra_state_assets_json="$(
    printf '%s' "${draft_extra_expected_assets_json}" | jq \
        --arg extra_asset_name 'retired-release-asset.zip' \
        --arg extra_digest 'sha256:obsolete' \
        '. + {($extra_asset_name): $extra_digest}'
)"
write_release_state "${draft_extra_state_dir}" true true false "${draft_extra_state_assets_json}"
: > "${draft_extra_state_dir}/operations.log"
run_publish_fixture "${draft_extra_state_dir}"
grep -Fq 'delete-id ' "${draft_extra_state_dir}/operations.log" || die \
    "release publisher did not remove an unexpected draft-only asset"
assert_state_asset_inventory "${draft_extra_state_dir}" "${draft_extra_expected_assets_json}"

public_extra_state_dir="${fixture_root}/public-extra"
mkdir -p "${public_extra_state_dir}"
create_canonical_assets "${public_extra_state_dir}" 'public-extra-asset'
public_extra_expected_assets_json="$(canonical_asset_digests_json "${public_extra_state_dir}")"
public_extra_state_assets_json="$(
    printf '%s' "${public_extra_expected_assets_json}" | jq \
        --arg extra_asset_name 'retired-release-asset.zip' \
        --arg extra_digest 'sha256:obsolete' \
        '. + {($extra_asset_name): $extra_digest}'
)"
write_release_state "${public_extra_state_dir}" true false false "${public_extra_state_assets_json}"
: > "${public_extra_state_dir}/operations.log"
if run_publish_fixture "${public_extra_state_dir}"; then
    die "release publisher accepted an immutable public release with an extra asset"
fi
assert_no_release_mutation "${public_extra_state_dir}"

run_finalize_fixture "${created_state_dir}" true
grep -Fq '"draft": false' "${created_state_dir}/operations.log" || die \
    "release finalizer did not publish the staged release"
grep -Fq '"make_latest": "true"' "${created_state_dir}/operations.log" || die \
    "release finalizer did not apply the resolved latest policy"

finalizer_missing_state_dir="${fixture_root}/finalizer-missing"
mkdir -p "${finalizer_missing_state_dir}"
create_canonical_assets "${finalizer_missing_state_dir}" 'finalizer-missing-asset'
finalizer_missing_expected_assets_json="$(canonical_asset_digests_json "${finalizer_missing_state_dir}")"
finalizer_missing_state_assets_json="$(
    printf '%s' "${finalizer_missing_expected_assets_json}" | jq \
        --arg asset_name "${first_release_asset_name}" \
        'del(.[$asset_name])'
)"
write_release_state "${finalizer_missing_state_dir}" true true false "${finalizer_missing_state_assets_json}"
: > "${finalizer_missing_state_dir}/operations.log"
if run_finalize_fixture "${finalizer_missing_state_dir}" false; then
    die "release finalizer accepted a draft missing one canonical asset"
fi
assert_no_release_mutation "${finalizer_missing_state_dir}"

finalizer_extra_state_dir="${fixture_root}/finalizer-extra"
mkdir -p "${finalizer_extra_state_dir}"
create_canonical_assets "${finalizer_extra_state_dir}" 'finalizer-extra-asset'
finalizer_extra_expected_assets_json="$(canonical_asset_digests_json "${finalizer_extra_state_dir}")"
finalizer_extra_state_assets_json="$(
    printf '%s' "${finalizer_extra_expected_assets_json}" | jq \
        --arg extra_asset_name 'retired-release-asset.zip' \
        --arg extra_digest 'sha256:obsolete' \
        '. + {($extra_asset_name): $extra_digest}'
)"
write_release_state "${finalizer_extra_state_dir}" true true false "${finalizer_extra_state_assets_json}"
: > "${finalizer_extra_state_dir}/operations.log"
if run_finalize_fixture "${finalizer_extra_state_dir}" false; then
    die "release finalizer accepted a draft with an extra asset"
fi
assert_no_release_mutation "${finalizer_extra_state_dir}"

invalid_tag_state_dir="${fixture_root}/invalid-tag"
mkdir -p "${invalid_tag_state_dir}"
create_canonical_assets "${invalid_tag_state_dir}" 'invalid-tag-asset'
write_release_state "${invalid_tag_state_dir}" false false false '{}'
: > "${invalid_tag_state_dir}/operations.log"
if run_publish_fixture "${invalid_tag_state_dir}" 'v9.9.9-rc.1'; then
    die "release publisher accepted a prerelease tag"
fi
assert_no_release_mutation "${invalid_tag_state_dir}"
if run_finalize_fixture "${invalid_tag_state_dir}" false 'v9.9.9-rc.1'; then
    die "release finalizer accepted a prerelease tag"
fi
assert_no_release_mutation "${invalid_tag_state_dir}"

printf 'GitHub release publisher regression: success\n'
