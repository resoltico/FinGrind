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

[[ -x "${publisher}" ]] || die "missing executable release publisher"
[[ -x "${finalizer}" ]] || die "missing executable release finalizer"

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
        {"name": name, "digest": digest}
        for name, digest in sorted(state["assets"].items())
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
        [[ "${3:-}" == "PATCH" ]] || exit 1
        [[ "${4:-}" == "/repos/resoltico/FinGrind/releases/444" ]] || exit 1
        [[ "${5:-}" == "--input" && "${6:-}" == "-" ]] || exit 1
        payload="$(cat)"
        python3_state patch "${payload}"
        log_operation "patch ${payload}"
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

write_state() {
    local state_path=$1
    local payload=$2
    printf '%s\n' "${payload}" > "${state_path}"
}

create_asset() {
    local destination=$1
    local content=$2
    printf '%s\n' "${content}" > "${destination}"
}

run_publish_fixture() {
    local state_dir=$1
    local asset_path=$2
    PATH="${fixture_root}/bin:${PATH}" \
        FINGRIND_RELEASE_DRAFT_VISIBILITY_DELAY_SECONDS=0 \
        GH_TOKEN='test-token' \
        RELEASE_TAG='v9.9.9' \
        FAKE_RELEASE_STATE_FILE="${state_dir}/release-state.json" \
        FAKE_RELEASE_OPERATION_LOG="${state_dir}/operations.log" \
        bash "${publisher}" "${asset_path}" >/dev/null
}

run_finalize_fixture() {
    local state_dir=$1
    local mark_latest=$2
    PATH="${fixture_root}/bin:${PATH}" \
        GH_TOKEN='test-token' \
        RELEASE_TAG='v9.9.9' \
        FINGRIND_RELEASE_MARK_LATEST="${mark_latest}" \
        FAKE_RELEASE_STATE_FILE="${state_dir}/release-state.json" \
        FAKE_RELEASE_OPERATION_LOG="${state_dir}/operations.log" \
        bash "${finalizer}" >/dev/null
}

matching_state_dir="${fixture_root}/matching"
mkdir -p "${matching_state_dir}"
create_asset "${matching_state_dir}/fingrind.tar.gz" 'matching-asset'
matching_digest="sha256:$(python3 - "${matching_state_dir}/fingrind.tar.gz" <<'PY'
from hashlib import sha256
from pathlib import Path
import sys
print(sha256(Path(sys.argv[1]).read_bytes()).hexdigest())
PY
)"
write_state "${matching_state_dir}/release-state.json" "$(cat <<JSON
{"exists": true, "id": 444, "draft": false, "make_latest": "false", "assets": {"fingrind.tar.gz": "${matching_digest}"}}
JSON
)"
: > "${matching_state_dir}/operations.log"
run_publish_fixture "${matching_state_dir}" "${matching_state_dir}/fingrind.tar.gz"
if grep -Eq '^(delete|upload|patch|create) ' "${matching_state_dir}/operations.log"; then
    die "release publisher rewrote an already matching release asset"
fi

replacement_state_dir="${fixture_root}/replacement"
mkdir -p "${replacement_state_dir}"
create_asset "${replacement_state_dir}/fingrind.tar.gz" 'replacement-asset'
write_state "${replacement_state_dir}/release-state.json" \
    '{"exists": true, "id": 444, "draft": false, "make_latest": "true", "assets": {"fingrind.tar.gz": "sha256:obsolete"}}'
: > "${replacement_state_dir}/operations.log"
if run_publish_fixture "${replacement_state_dir}" "${replacement_state_dir}/fingrind.tar.gz"; then
    die "release publisher rewrote a published release instead of failing for immutable public assets"
fi
if grep -Eq '^(delete|upload|patch|create) ' "${replacement_state_dir}/operations.log"; then
    die "release publisher mutated an immutable public release"
fi

created_state_dir="${fixture_root}/created"
mkdir -p "${created_state_dir}"
create_asset "${created_state_dir}/fingrind.tar.gz" 'created-asset'
write_state "${created_state_dir}/release-state.json" \
    '{"exists": false, "id": 444, "draft": false, "make_latest": "false", "assets": {}}'
: > "${created_state_dir}/operations.log"
run_publish_fixture "${created_state_dir}" "${created_state_dir}/fingrind.tar.gz"
grep -Fq 'create' "${created_state_dir}/operations.log" || die \
    "release publisher did not create a draft release when none existed"
grep -Fq 'upload fingrind.tar.gz ' "${created_state_dir}/operations.log" || die \
    "release publisher did not upload the initial release asset"
if grep -Fq '"draft": false' "${created_state_dir}/operations.log"; then
    die "release publisher finalized the release during draft staging"
fi

created_visibility_state_dir="${fixture_root}/created-with-visibility-delay"
mkdir -p "${created_visibility_state_dir}"
create_asset "${created_visibility_state_dir}/fingrind.tar.gz" 'created-with-visibility-delay'
write_state "${created_visibility_state_dir}/release-state.json" \
    '{"exists": false, "id": 444, "draft": false, "make_latest": "false", "assets": {}, "visibilityDelayRemaining": 1}'
: > "${created_visibility_state_dir}/operations.log"
run_publish_fixture "${created_visibility_state_dir}" "${created_visibility_state_dir}/fingrind.tar.gz"
grep -Fq 'create' "${created_visibility_state_dir}/operations.log" || die \
    "release publisher did not create a draft release before waiting for visibility"
grep -Fq 'upload fingrind.tar.gz ' "${created_visibility_state_dir}/operations.log" || die \
    "release publisher did not upload the initial release asset after draft visibility lag"

run_finalize_fixture "${created_state_dir}" true
grep -Fq '"draft": false' "${created_state_dir}/operations.log" || die \
    "release finalizer did not publish the staged release"
grep -Fq '"make_latest": "true"' "${created_state_dir}/operations.log" || die \
    "release finalizer did not apply the resolved latest policy"

printf 'GitHub release publisher regression: success\n'
