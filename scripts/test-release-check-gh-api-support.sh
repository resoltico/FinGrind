#!/usr/bin/env bash
# Exercise the release GitHub API helpers with responses too large for environment-variable
# transport. Release-check workflow data can exceed that OS limit as the repository grows.

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

script_dir="$(resolve_script_dir)"
readonly script_dir
repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly repo_root
readonly github_api_support="${repo_root}/scripts/release-check-gh-api-support.sh"
readonly stage_contract_script="${repo_root}/scripts/check-stage-contract.sh"
readonly payload_character_count=524288

[[ -f "${github_api_support}" ]] || die "missing GitHub API support helper at ${github_api_support}"
[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
grep -Fq 'scripts/test-release-check-gh-api-support.sh' "${stage_contract_script}" || die \
    "check stage contract no longer exercises the release GitHub API support regression"

fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-test-release-check-gh-api-support.XXXXXX")"
stub_dir="${fixture_root}/bin"
mkdir -p "${stub_dir}"
cleanup() {
    rm -rf "${fixture_root}"
}
trap cleanup EXIT

cat > "${stub_dir}/gh" <<EOF
#!/usr/bin/env bash
set -euo pipefail

[[ "\${1:-}" == "api" && "\${2:-}" == "/synthetic-large-payload" ]] || exit 1
python3 -c 'import json; print(json.dumps({"payload": "x" * ${payload_character_count}}))'
EOF
chmod +x "${stub_dir}/gh"

# shellcheck source=/dev/null
source "${github_api_support}"

api_payload="$(PATH="${stub_dir}:${PATH}" fingrind_release_github_api_json \
    "synthetic large payload" \
    /synthetic-large-payload)"
printf '%s' "${api_payload}" | python3 -c '
import json
import sys

payload = json.load(sys.stdin)
assert payload == {"payload": "x" * 524288}
'

error_payload="$(python3 -c 'import json; print(json.dumps({"_fingrindGhApiError": {"description": "synthetic", "message": "x" * 524288}}))')"
error_message="$(fingrind_release_payload_error_message "${error_payload}")"
printf '%s' "${error_message}" | python3 -c '
import sys

assert sys.stdin.read() == "synthetic: " + ("x" * 524288)
'

printf 'release GitHub API support regression: success\n'
