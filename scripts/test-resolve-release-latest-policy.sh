#!/usr/bin/env bash
# Prove latest ownership is resolved from every GitHub Releases API page, not a bounded CLI list.

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
readonly resolver="${script_dir}/resolve-release-latest-policy.py"

[[ -x "${resolver}" ]] || die "missing executable latest-publication policy resolver"

fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-test-resolve-release-latest-policy.XXXXXX")"
cleanup() {
    rm -rf "${fixture_root}"
}
trap cleanup EXIT

mkdir -p "${fixture_root}/bin"

cat > "${fixture_root}/bin/gh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

readonly operation_log="${FAKE_GH_OPERATION_LOG:?}"

printf '%s\n' "$*" >> "${operation_log}"
[[ "${1:-}" == 'api' && "${2:-}" == '--paginate' && "${3:-}" == '--slurp' ]] || {
    printf 'unexpected fake gh invocation: %s\n' "$*" >&2
    exit 1
}
[[ "${4:-}" == '/repos/example/fingrind/releases?per_page=100' ]] || {
    printf 'unexpected releases API endpoint: %s\n' "${4:-}" >&2
    exit 1
}
printf '%s\n' "${FAKE_GH_RELEASE_PAGES:?}"
EOF
chmod +x "${fixture_root}/bin/gh"

run_policy() {
    local release_tag=$1
    local pages_json=$2

    PATH="${fixture_root}/bin:${PATH}" \
        FAKE_GH_OPERATION_LOG="${fixture_root}/operations.log" \
        FAKE_GH_RELEASE_PAGES="${pages_json}" \
        python3 "${resolver}" --release-tag "${release_tag}" --repository example/fingrind
}

all_pages='[
  [
    {"tag_name":"v0.61.0","draft":false,"prerelease":false},
    {"tag_name":"v99.0.0","draft":true,"prerelease":false},
    {"tag_name":"v98.0.0-rc.1","draft":false,"prerelease":true}
  ],
  [
    {"tag_name":"v0.62.0","draft":false,"prerelease":false}
  ]
]'
: > "${fixture_root}/operations.log"
older_policy="$(run_policy v0.61.0 "${all_pages}")"
if ! jq -e '.markLatest == false and .latestPublicationPolicy == "newest-stable-release-only"' \
    <<< "${older_policy}" >/dev/null; then
    die "latest resolver did not account for the newer stable release on a later API page"
fi
grep -Fqx 'api --paginate --slurp /repos/example/fingrind/releases?per_page=100' \
    "${fixture_root}/operations.log" || die \
    "latest resolver no longer requests every GitHub Releases API page through the canonical endpoint"

newest_policy="$(run_policy v0.63.0 "${all_pages}")"
if ! jq -e '.markLatest == true' <<< "${newest_policy}" >/dev/null; then
    die "latest resolver did not assign ownership to a new stable release"
fi

nonstable_pages='[[{"tag_name":"v0.63.0-rc.1","draft":false,"prerelease":false}]]'
if run_policy v0.63.0 "${nonstable_pages}" >/dev/null 2>&1; then
    die "latest resolver accepted a published nonstable GitHub release tag"
fi

printf 'latest-publication policy regression: success\n'
