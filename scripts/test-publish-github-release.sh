#!/usr/bin/env bash
# Guard the GitHub release publisher against drifting back to name-only convergence.

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

[[ -x "${publisher}" ]] || die "missing executable release publisher"

fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-test-publish-github-release.XXXXXX")"
cleanup() {
    rm -rf "${fixture_root}"
}
trap cleanup EXIT

mkdir -p "${fixture_root}/bin"

cat > "${fixture_root}/bin/gh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

state_file="${FAKE_RELEASE_STATE_FILE:?}"
operation_log="${FAKE_RELEASE_OPERATION_LOG:?}"
release_exists_file="${FAKE_RELEASE_EXISTS_FILE:?}"

compute_sha256() {
    python3 - "$1" <<'PY'
from hashlib import sha256
from pathlib import Path
import sys

digest = sha256()
with Path(sys.argv[1]).open("rb") as handle:
    for chunk in iter(lambda: handle.read(1024 * 1024), b""):
        digest.update(chunk)
print(digest.hexdigest())
PY
}

asset_digest() {
    local asset_name=$1
    [[ -f "${state_file}" ]] || return 0
    awk -F'|' -v name="${asset_name}" '$1 == name { print $2; exit }' "${state_file}"
}

remove_asset() {
    local asset_name=$1
    [[ -f "${state_file}" ]] || return 0
    awk -F'|' -v name="${asset_name}" '$1 != name { print $0 }' "${state_file}" > "${state_file}.next"
    mv "${state_file}.next" "${state_file}"
}

case "${1:-}:${2:-}" in
    release:view)
        tag_name="${3:-}"
        if [[ ! -f "${release_exists_file}" ]]; then
            exit 1
        fi
        if [[ $# -eq 3 ]]; then
            printf 'release %s\n' "${tag_name}"
            exit 0
        fi
        [[ "${4:-}" == "--json" ]] || exit 1
        [[ "${5:-}" == "assets" ]] || exit 1
        [[ "${6:-}" == "--jq" ]] || exit 1
        jq_query="${7:-}"
        asset_name="${jq_query#*select(.name == \"}"
        asset_name="${asset_name%%\"*}"
        asset_digest_output="$(asset_digest "${asset_name}")"
        printf '%s\n' "${asset_digest_output}"
        ;;
    release:edit)
        : > /dev/null
        printf 'edit\n' >> "${operation_log}"
        ;;
    release:create)
        touch "${release_exists_file}"
        printf 'create\n' >> "${operation_log}"
        ;;
    release:delete-asset)
        asset_name="${4:-}"
        remove_asset "${asset_name}"
        printf 'delete %s\n' "${asset_name}" >> "${operation_log}"
        ;;
    release:upload)
        asset_path="${4:-}"
        asset_name="$(basename -- "${asset_path}")"
        digest="sha256:$(compute_sha256 "${asset_path}")"
        remove_asset "${asset_name}"
        {
            [[ -f "${state_file}" ]] && cat "${state_file}"
            printf '%s|%s\n' "${asset_name}" "${digest}"
        } > "${state_file}.next"
        mv "${state_file}.next" "${state_file}"
        printf 'upload %s %s\n' "${asset_name}" "${digest}" >> "${operation_log}"
        ;;
    *)
        exit 1
        ;;
esac
EOF
chmod +x "${fixture_root}/bin/gh"

create_asset() {
    local destination=$1
    local content=$2
    printf '%s\n' "${content}" > "${destination}"
}

run_publish_fixture() {
    local state_dir=$1
    local asset_path=$2
    local tag_name=$3
    PATH="${fixture_root}/bin:${PATH}" \
        GH_TOKEN='test-token' \
        RELEASE_TAG="${tag_name}" \
        FAKE_RELEASE_STATE_FILE="${state_dir}/assets.tsv" \
        FAKE_RELEASE_OPERATION_LOG="${state_dir}/operations.log" \
        FAKE_RELEASE_EXISTS_FILE="${state_dir}/release-exists" \
        bash "${publisher}" "${asset_path}" >/dev/null
}

matching_state_dir="${fixture_root}/matching"
mkdir -p "${matching_state_dir}"
create_asset "${matching_state_dir}/fingrind.tar.gz" 'matching-asset'
touch "${matching_state_dir}/release-exists"
matching_digest="sha256:$(python3 - "${matching_state_dir}/fingrind.tar.gz" <<'PY'
from hashlib import sha256
from pathlib import Path
import sys
data = Path(sys.argv[1]).read_bytes()
print(sha256(data).hexdigest())
PY
)"
printf 'fingrind.tar.gz|%s\n' "${matching_digest}" > "${matching_state_dir}/assets.tsv"
: > "${matching_state_dir}/operations.log"
(
    cd "${matching_state_dir}"
    run_publish_fixture "${matching_state_dir}" "${matching_state_dir}/fingrind.tar.gz" 'v9.9.9'
)
if grep -Eq '^(delete|upload) ' "${matching_state_dir}/operations.log"; then
    die "release publisher rewrote an already matching release asset"
fi

replacement_state_dir="${fixture_root}/replacement"
mkdir -p "${replacement_state_dir}"
create_asset "${replacement_state_dir}/fingrind.tar.gz" 'replacement-asset'
touch "${replacement_state_dir}/release-exists"
printf 'fingrind.tar.gz|sha256:obsolete\n' > "${replacement_state_dir}/assets.tsv"
: > "${replacement_state_dir}/operations.log"
(
    cd "${replacement_state_dir}"
    run_publish_fixture "${replacement_state_dir}" "${replacement_state_dir}/fingrind.tar.gz" 'v9.9.9'
)
grep -Fq 'delete fingrind.tar.gz' "${replacement_state_dir}/operations.log" || die \
    "release publisher did not delete the stale release asset before re-upload"
grep -Fq 'upload fingrind.tar.gz ' "${replacement_state_dir}/operations.log" || die \
    "release publisher did not upload the replacement release asset"
current_digest="$(awk -F'|' '$1 == "fingrind.tar.gz" { print $2; exit }' "${replacement_state_dir}/assets.tsv")"
expected_digest="sha256:$(python3 - "${replacement_state_dir}/fingrind.tar.gz" <<'PY'
from hashlib import sha256
from pathlib import Path
import sys
data = Path(sys.argv[1]).read_bytes()
print(sha256(data).hexdigest())
PY
)"
[[ "${current_digest}" == "${expected_digest}" ]] || die \
    "release publisher did not converge the replacement asset onto the expected digest"

printf 'GitHub release publisher regression: success\n'
