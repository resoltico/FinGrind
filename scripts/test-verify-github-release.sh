#!/usr/bin/env bash
# Reproduce and guard the GitHub release verifier against drifting back to metadata-only checks.

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
readonly verifier="${script_dir}/verify-github-release.sh"
readonly archive_verifier="${script_dir}/verify-source-archive.py"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly stage_contract_script="${repo_root}/scripts/check-stage-contract.sh"

[[ -x "${verifier}" ]] || die "missing executable release verifier"
[[ -f "${archive_verifier}" ]] || die "missing source archive verifier helper"
[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
grep -Fq 'scripts/test-verify-github-release.sh' "${stage_contract_script}" || die \
    "check stage contract no longer exercises the GitHub release verifier regression"
grep -Fq './scripts/verify-github-release.sh' "${repo_root}/docs/RELEASE_PROTOCOL.md" || die \
    "release protocol no longer requires the GitHub release verifier"

fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-test-verify-github-release.XXXXXX")"
cleanup() {
    rm -rf "${fixture_root}"
}
trap cleanup EXIT

mkdir -p "${fixture_root}/bin"

python3 - <<'PY' "${fixture_root}/good.zip" "${fixture_root}/good.tar.gz" "${fixture_root}/bad.zip"
import io
import pathlib
import sys
import tarfile
import zipfile

good_zip = pathlib.Path(sys.argv[1])
good_tar = pathlib.Path(sys.argv[2])
bad_zip = pathlib.Path(sys.argv[3])

with zipfile.ZipFile(good_zip, "w") as archive:
    archive.writestr("owner-repo-123456/README.md", "public archive\n")

with tarfile.open(good_tar, "w:gz") as archive:
    data = b"public archive\n"
    member = tarfile.TarInfo("owner-repo-123456/README.md")
    member.size = len(data)
    archive.addfile(member, io.BytesIO(data))

with zipfile.ZipFile(bad_zip, "w") as archive:
    archive.writestr("owner-repo-123456/AGENTS.md", "should not ship\n")
PY

cat > "${fixture_root}/bin/gh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

mode="${FAKE_GH_MODE:-success}"
tag="${FAKE_GH_TAG:-v0.0.0}"
repo="${FAKE_GH_REPOSITORY:-resoltico/fingrind}"
release_url="${FAKE_GH_RELEASE_URL:-https://example.invalid/release}"
good_zip="${FAKE_GH_GOOD_ZIP:-}"
good_tar="${FAKE_GH_GOOD_TAR:-}"
bad_zip="${FAKE_GH_BAD_ZIP:-}"
asset_listing="${FAKE_GH_ASSETS:-fingrind.zip fingrind.sha256}"

if [[ "${1:-}" == "repo" && "${2:-}" == "view" ]]; then
    [[ "${3:-}" == "--json" && "${4:-}" == "nameWithOwner" && "${5:-}" == "--jq" && "${6:-}" == ".nameWithOwner" ]] || exit 1
    printf '%s\n' "${repo}"
    exit 0
fi

if [[ "${1:-}" == "release" && "${2:-}" == "view" ]]; then
    requested_tag="${3:-}"
    [[ "${requested_tag}" == "${tag}" ]] || exit 1
    [[ "${4:-}" == "--json" ]] || exit 1
    json_field="${5:-}"
    [[ "${6:-}" == "--jq" ]] || exit 1
    jq_query="${7:-}"
    case "${json_field}:${jq_query}" in
        tagName:.tagName)
            printf '%s\n' "${tag}"
            ;;
        isDraft:.isDraft)
            printf 'false\n'
            ;;
        isPrerelease:.isPrerelease)
            printf 'false\n'
            ;;
        url:.url)
            printf '%s\n' "${release_url}"
            ;;
        assets:*)
            [[ "${jq_query}" == *'index('* ]] || exit 1
            asset_name="${jq_query#*index(\"}"
            asset_name="${asset_name%%\")*}"
            for known_asset in ${asset_listing}; do
                if [[ "${known_asset}" == "${asset_name}" ]]; then
                    printf 'true\n'
                    exit 0
                fi
            done
            printf 'false\n'
            ;;
        *)
            exit 1
            ;;
    esac
    exit 0
fi

if [[ "${1:-}" == "api" ]]; then
    endpoint="${2:-}"
    case "${endpoint}" in
        /repos/"${repo}"/zipball/"${tag}")
            if [[ "${mode}" == "bad-archive" ]]; then
                cat "${bad_zip}"
            else
                cat "${good_zip}"
            fi
            ;;
        /repos/"${repo}"/tarball/"${tag}")
            cat "${good_tar}"
            ;;
        *)
            exit 1
            ;;
    esac
    exit 0
fi

exit 1
EOF
chmod +x "${fixture_root}/bin/gh"

PATH="${fixture_root}/bin:${PATH}" \
    GITHUB_REF_NAME='44/merge' \
    FAKE_GH_TAG='v9.9.9' \
    FAKE_GH_REPOSITORY='resoltico/fingrind' \
    FAKE_GH_RELEASE_URL='https://example.invalid/releases/v9.9.9' \
    FAKE_GH_GOOD_ZIP="${fixture_root}/good.zip" \
    FAKE_GH_GOOD_TAR="${fixture_root}/good.tar.gz" \
    FAKE_GH_BAD_ZIP="${fixture_root}/bad.zip" \
    bash "${verifier}" v9.9.9 fingrind.zip fingrind.sha256 >/dev/null

set +e
failure_output="$(
    PATH="${fixture_root}/bin:${PATH}" \
        GITHUB_REF_NAME='44/merge' \
        FAKE_GH_MODE='bad-archive' \
        FAKE_GH_TAG='v9.9.9' \
        FAKE_GH_REPOSITORY='resoltico/fingrind' \
        FAKE_GH_RELEASE_URL='https://example.invalid/releases/v9.9.9' \
        FAKE_GH_GOOD_ZIP="${fixture_root}/good.zip" \
        FAKE_GH_GOOD_TAR="${fixture_root}/good.tar.gz" \
        FAKE_GH_BAD_ZIP="${fixture_root}/bad.zip" \
        bash "${verifier}" v9.9.9 fingrind.zip fingrind.sha256 2>&1
)"
failure_exit=$?
set -e

if [[ ${failure_exit} -eq 0 ]]; then
    die "GitHub release verifier accepted a source archive that leaked AGENTS.md"
fi
printf '%s\n' "${failure_output}" | grep -Fq 'forbidden repo-owned agent metadata leaked into source archive' || die \
    "GitHub release verifier did not report the leaked source-archive metadata"

printf 'GitHub release verifier regression: success\n'
