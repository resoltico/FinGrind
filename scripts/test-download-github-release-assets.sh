#!/usr/bin/env bash
# Guard draft-aware release asset downloading against regressions in tag-before-finalization flows.

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
readonly downloader="${script_dir}/download-github-release-assets.sh"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly stage_contract_script="${repo_root}/scripts/check-stage-contract.sh"
readonly release_workflow="${repo_root}/.github/workflows/release.yml"

[[ -x "${downloader}" ]] || die "missing executable draft-aware release asset downloader"
[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
[[ -f "${release_workflow}" ]] || die "missing release workflow at ${release_workflow}"
grep -Fq 'scripts/test-download-github-release-assets.sh' "${stage_contract_script}" || die \
    "check stage contract no longer exercises the draft-aware release asset downloader"
grep -Fq 'download-github-release-assets.sh' "${release_workflow}" || die \
    "release workflow no longer downloads draft-aware release assets through the repo-owned downloader"
grep -Fq 'path: workflow-owner-surface' "${release_workflow}" || die \
    "release workflow no longer checks out the workflow-owner helper surface for rerun-safe asset downloads"
grep -Fq 'FINGRIND_WORKFLOW_HELPER_ROOT' "${release_workflow}" || die \
    "release workflow no longer resolves the helper-root path for release asset downloads"

fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-test-download-github-release-assets.XXXXXX")"
cleanup() {
    rm -rf "${fixture_root}"
}
trap cleanup EXIT

mkdir -p "${fixture_root}/bin" "${fixture_root}/asset-source" "${fixture_root}/downloads"
printf 'macos bundle\n' > "${fixture_root}/asset-source/fingrind-0.51.0-macos-aarch64.tar.gz"

cat > "${fixture_root}/bin/gh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

mode="${FAKE_GH_MODE:-success}"
tag="${FAKE_GH_TAG:-v0.0.0}"
repo="${FAKE_GH_REPOSITORY:-resoltico/FinGrind}"
asset_source_root="${FAKE_GH_ASSET_SOURCE_ROOT:-}"
visibility_counter_file="${FAKE_GH_VISIBILITY_COUNTER_FILE:-}"
asset_json='{"assets":[{"name":"fingrind-0.51.0-macos-aarch64.tar.gz","apiUrl":"https://api.github.com/repos/resoltico/FinGrind/releases/assets/4242"}]}'

if [[ "${1:-}" == "release" && "${2:-}" == "view" ]]; then
    requested_tag="${3:-}"
    [[ "${requested_tag}" == "${tag}" ]] || exit 1
    [[ "${4:-}" == "--repo" && "${5:-}" == "${repo}" && "${6:-}" == "--json" && "${7:-}" == "assets" ]] || exit 1
    if [[ "${mode}" == "delayed-visibility" ]]; then
        counter=$(<"${visibility_counter_file}")
        if (( counter > 0 )); then
            printf '%s' $((counter - 1)) > "${visibility_counter_file}"
            printf '{"assets":[]}\n'
            exit 0
        fi
    fi
    if [[ "${mode}" == "missing-asset" ]]; then
        printf '{"assets":[]}\n'
        exit 0
    fi
    printf '%s\n' "${asset_json}"
    exit 0
fi

if [[ "${1:-}" == "api" ]]; then
    shift
    [[ "${1:-}" == "--method" && "${2:-}" == "GET" ]] || exit 1
    shift 2
    [[ "${1:-}" == "-H" && "${2:-}" == "Accept: application/octet-stream" ]] || exit 1
    shift 2
    endpoint="${1:-}"
    [[ "${endpoint}" == "/repos/${repo}/releases/assets/4242" ]] || exit 1
    cat "${asset_source_root}/fingrind-0.51.0-macos-aarch64.tar.gz"
    exit 0
fi

exit 1
EOF
chmod +x "${fixture_root}/bin/gh"

printf '1' > "${fixture_root}/visibility-counter.txt"
PATH="${fixture_root}/bin:${PATH}" \
    FAKE_GH_MODE='delayed-visibility' \
    FAKE_GH_TAG='v0.51.0' \
    FAKE_GH_REPOSITORY='resoltico/FinGrind' \
    FAKE_GH_ASSET_SOURCE_ROOT="${fixture_root}/asset-source" \
    FAKE_GH_VISIBILITY_COUNTER_FILE="${fixture_root}/visibility-counter.txt" \
    bash "${downloader}" \
        --repo resoltico/FinGrind \
        --tag v0.51.0 \
        --dir "${fixture_root}/downloads" \
        --retries 3 \
        --delay-seconds 0 \
        fingrind-0.51.0-macos-aarch64.tar.gz

cmp -s \
    "${fixture_root}/asset-source/fingrind-0.51.0-macos-aarch64.tar.gz" \
    "${fixture_root}/downloads/fingrind-0.51.0-macos-aarch64.tar.gz" || die \
    "draft-aware release asset downloader did not materialize the expected asset bytes"

rm -f "${fixture_root}/downloads/fingrind-0.51.0-macos-aarch64.tar.gz"

set +e
missing_asset_output="$(
    PATH="${fixture_root}/bin:${PATH}" \
        FAKE_GH_MODE='missing-asset' \
        FAKE_GH_TAG='v0.51.0' \
        FAKE_GH_REPOSITORY='resoltico/FinGrind' \
        FAKE_GH_ASSET_SOURCE_ROOT="${fixture_root}/asset-source" \
        FAKE_GH_VISIBILITY_COUNTER_FILE="${fixture_root}/visibility-counter.txt" \
        bash "${downloader}" \
            --repo resoltico/FinGrind \
            --tag v0.51.0 \
            --dir "${fixture_root}/downloads" \
            --retries 1 \
            --delay-seconds 0 \
            fingrind-0.51.0-macos-aarch64.tar.gz 2>&1
)"
missing_asset_exit=$?
set -e

if [[ ${missing_asset_exit} -eq 0 ]]; then
    die "draft-aware release asset downloader accepted a missing release asset"
fi
printf '%s\n' "${missing_asset_output}" | grep -Fq \
    'failed to download release asset fingrind-0.51.0-macos-aarch64.tar.gz from v0.51.0' || die \
    "draft-aware release asset downloader did not report the missing asset failure"

printf 'draft-aware release asset downloader regression: success\n'
