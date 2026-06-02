#!/usr/bin/env bash
# Reproduce and guard the GitHub release verifier against drift away from the contract-derived,
# attestation-backed publication surface.

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
readonly release_workflow="${repo_root}/.github/workflows/release.yml"

[[ -x "${verifier}" ]] || die "missing executable release verifier"
[[ -f "${archive_verifier}" ]] || die "missing source archive verifier helper"
[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
[[ -f "${release_workflow}" ]] || die "missing release workflow at ${release_workflow}"
grep -Fq 'scripts/test-verify-github-release.sh' "${stage_contract_script}" || die \
    "check stage contract no longer exercises the GitHub release verifier regression"
grep -Fq 'prepare-publication:' "${release_workflow}" || die \
    "release workflow no longer prepares one canonical publication plan before publishing"
grep -Fq 'publish-release:' "${release_workflow}" || die \
    "release workflow no longer publishes the GitHub release inside the release workflow"
grep -Fq 'attest-release-assets:' "${release_workflow}" || die \
    "release workflow no longer attests published release assets"
grep -Fq 'verify-release:' "${release_workflow}" || die \
    "release workflow no longer verifies the published GitHub release"
grep -Fq 'actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1' "${release_workflow}" || die \
    "release workflow no longer pins publication-staging uploads to the current Node24-backed artifact action"
grep -Fq 'actions/download-artifact@3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c # v8.0.1' "${release_workflow}" || die \
    "release workflow no longer pins publication-staging downloads to the current Node24-backed artifact action"
grep -Fq 'container:' "${release_workflow}" || die \
    "release workflow no longer publishes the public container from the same release workflow"
grep -Fq 'run: ${{ steps.workflow-helper-root.outputs.path }}/scripts/verify-release-candidate-tag.sh' "${release_workflow}" || die \
    "release workflow no longer routes the tag verifier through the replay-safe helper surface"
grep -Fq 'bash "${{ steps.workflow-helper-root.outputs.path }}/scripts/publish-github-release.sh"' "${release_workflow}" || die \
    "release workflow no longer routes the release publisher through the replay-safe helper surface"
grep -Fq 'run: ${{ steps.workflow-helper-root.outputs.path }}/scripts/verify-github-release.sh' "${release_workflow}" || die \
    "release workflow no longer routes the release verifier through the replay-safe helper surface"
grep -Fq '${{ steps.workflow-helper-root.outputs.path }}/scripts/verify-public-container-surface.sh' "${release_workflow}" || die \
    "release workflow no longer routes public-container verification through the replay-safe helper surface"
grep -Fq 'FINGRIND_RELEASE_MARK_LATEST' "${release_workflow}" || die \
    "release workflow no longer drives GitHub latest ownership from the canonical latest policy"
grep -Fq 'FINGRIND_VERIFY_PUBLIC_CONTAINER_LATEST' "${release_workflow}" || die \
    "release workflow no longer keeps public-container latest verification aligned with the latest policy"
grep -Fq './scripts/verify-github-release.sh' "${repo_root}/docs/RELEASE_PROTOCOL.md" || die \
    "release protocol no longer requires the GitHub release verifier"
grep -Fq './scripts/verify-public-container-surface.sh' "${repo_root}/docs/RELEASE_PROTOCOL.md" || die \
    "release protocol no longer requires public-container surface verification"

release_assets_json="$(
    python3 "${repo_root}/scripts/read-release-publication-plan.py" --version 9.9.9 | jq -c '.releaseAssetNames'
)"

fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-test-verify-github-release.XXXXXX")"
cleanup() {
    rm -rf "${fixture_root}"
}
trap cleanup EXIT

mkdir -p "${fixture_root}/bin" "${fixture_root}/release-assets"

python3 - <<'PY' "${fixture_root}" "${release_assets_json}"
from __future__ import annotations

from hashlib import sha256
from pathlib import Path
import io
import json
import sys
import tarfile
import zipfile

fixture_root = Path(sys.argv[1])
asset_names = json.loads(sys.argv[2])
release_assets_root = fixture_root / "release-assets"
archive_digests: dict[str, str] = {}

for asset_name in asset_names:
    asset_path = release_assets_root / asset_name
    if asset_name.endswith(".sha256"):
        continue
    if asset_name.endswith(".zip"):
        with zipfile.ZipFile(asset_path, "w") as archive:
            archive.writestr("payload.txt", f"{asset_name}\n")
    else:
        asset_path.write_text(f"{asset_name}\n", encoding="utf-8")
    archive_digests[asset_name] = sha256(asset_path.read_bytes()).hexdigest()

for asset_name in asset_names:
    asset_path = release_assets_root / asset_name
    if asset_name.endswith(".sha256"):
        archive_name = asset_name[: -len(".sha256")]
        asset_path.write_text(
            f"{archive_digests[archive_name]}  {archive_name}\n",
            encoding="utf-8",
        )

good_zip = fixture_root / "good-source.zip"
good_tar = fixture_root / "good-source.tar.gz"
bad_zip = fixture_root / "bad-source.zip"
bad_checksum_root = fixture_root / "bad-checksum-assets"
bad_checksum_root.mkdir(parents=True, exist_ok=True)

with zipfile.ZipFile(good_zip, "w") as archive:
    archive.writestr("owner-repo-123456/README.md", "public archive\n")

with tarfile.open(good_tar, "w:gz") as archive:
    data = b"public archive\n"
    member = tarfile.TarInfo("owner-repo-123456/README.md")
    member.size = len(data)
    archive.addfile(member, io.BytesIO(data))

with zipfile.ZipFile(bad_zip, "w") as archive:
    archive.writestr("owner-repo-123456/AGENTS.md", "should not ship\n")

for asset_name in asset_names:
    source = release_assets_root / asset_name
    target = bad_checksum_root / asset_name
    target.write_bytes(source.read_bytes())
if asset_names:
    first_archive = next(name for name in asset_names if not name.endswith(".sha256"))
    checksum_path = bad_checksum_root / f"{first_archive}.sha256"
    checksum_path.write_text(
        f"{'0' * 64}  {first_archive}\n",
        encoding="utf-8",
    )
PY

cat > "${fixture_root}/bin/gh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

mode="${FAKE_GH_MODE:-success}"
tag="${FAKE_GH_TAG:-v0.0.0}"
repo="${FAKE_GH_REPOSITORY:-resoltico/FinGrind}"
release_url="${FAKE_GH_RELEASE_URL:-https://example.invalid/release}"
good_zip="${FAKE_GH_GOOD_ZIP:-}"
good_tar="${FAKE_GH_GOOD_TAR:-}"
bad_zip="${FAKE_GH_BAD_ZIP:-}"
asset_root="${FAKE_GH_ASSET_ROOT:-}"
bad_checksum_root="${FAKE_GH_BAD_CHECKSUM_ROOT:-}"
asset_listing_json="${FAKE_GH_ASSETS_JSON:-[]}"
private_reporting_enabled="${FAKE_GH_PRIVATE_REPORTING_ENABLED:-true}"

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
            asset_name="${jq_query#*index(\"}"
            asset_name="${asset_name%%\")*}"
            ASSET_LISTING_JSON="${asset_listing_json}" python3 - "${asset_name}" <<'PY'
import json
import os
import sys

asset_name = sys.argv[1]
assets = json.loads(os.environ["ASSET_LISTING_JSON"])
print("true" if asset_name in assets else "false")
PY
            ;;
        *)
            exit 1
            ;;
    esac
    exit 0
fi

if [[ "${1:-}" == "release" && "${2:-}" == "download" ]]; then
    requested_tag="${3:-}"
    [[ "${requested_tag}" == "${tag}" ]] || exit 1
    shift 3
    requested_asset=''
    destination_dir=''
    while [[ $# -gt 0 ]]; do
        case "${1}" in
            --pattern)
                requested_asset="${2:-}"
                shift 2
                ;;
            --dir)
                destination_dir="${2:-}"
                shift 2
                ;;
            --repo|--clobber)
                if [[ "${1}" == "--repo" ]]; then
                    shift 2
                else
                    shift
                fi
                ;;
            *)
                exit 1
                ;;
        esac
    done
    [[ -n "${requested_asset}" && -n "${destination_dir}" ]] || exit 1
    mkdir -p "${destination_dir}"
    source_root="${asset_root}"
    if [[ "${mode}" == "bad-checksum" && "${requested_asset}" == *.sha256 ]]; then
        source_root="${bad_checksum_root}"
    fi
    cp "${source_root}/${requested_asset}" "${destination_dir}/${requested_asset}"
    exit 0
fi

if [[ "${1:-}" == "attestation" && "${2:-}" == "verify" ]]; then
    [[ "${mode}" == "bad-attestation" ]] && exit 1
    exit 0
fi

if [[ "${1:-}" == "api" ]]; then
    endpoint="${2:-}"
    case "${endpoint}" in
        /repos/"${repo}"/private-vulnerability-reporting)
            [[ "${3:-}" == "--jq" && "${4:-}" == ".enabled" ]] || exit 1
            printf '%s\n' "${private_reporting_enabled}"
            ;;
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

run_verifier() {
    PATH="${fixture_root}/bin:${PATH}" \
        GITHUB_REF_NAME='44/merge' \
        GITHUB_REPOSITORY='resoltico/FinGrind' \
        FAKE_GH_TAG='v9.9.9' \
        FAKE_GH_REPOSITORY='resoltico/FinGrind' \
        FAKE_GH_RELEASE_URL='https://example.invalid/releases/v9.9.9' \
        FAKE_GH_GOOD_ZIP="${fixture_root}/good-source.zip" \
        FAKE_GH_GOOD_TAR="${fixture_root}/good-source.tar.gz" \
        FAKE_GH_BAD_ZIP="${fixture_root}/bad-source.zip" \
        FAKE_GH_ASSET_ROOT="${fixture_root}/release-assets" \
        FAKE_GH_BAD_CHECKSUM_ROOT="${fixture_root}/bad-checksum-assets" \
        FAKE_GH_ASSETS_JSON="${release_assets_json}" \
        FAKE_GH_PRIVATE_REPORTING_ENABLED='true' \
        bash "${verifier}" v9.9.9
}

run_verifier >/dev/null

set +e
checksum_failure_output="$(
    PATH="${fixture_root}/bin:${PATH}" \
        FINGRIND_GITHUB_RELEASE_VERIFY_RETRIES='1' \
        FINGRIND_GITHUB_RELEASE_VERIFY_DELAY_SECONDS='0' \
        FAKE_GH_MODE='bad-checksum' \
        GITHUB_REF_NAME='44/merge' \
        GITHUB_REPOSITORY='resoltico/FinGrind' \
        FAKE_GH_TAG='v9.9.9' \
        FAKE_GH_REPOSITORY='resoltico/FinGrind' \
        FAKE_GH_RELEASE_URL='https://example.invalid/releases/v9.9.9' \
        FAKE_GH_GOOD_ZIP="${fixture_root}/good-source.zip" \
        FAKE_GH_GOOD_TAR="${fixture_root}/good-source.tar.gz" \
        FAKE_GH_BAD_ZIP="${fixture_root}/bad-source.zip" \
        FAKE_GH_ASSET_ROOT="${fixture_root}/release-assets" \
        FAKE_GH_BAD_CHECKSUM_ROOT="${fixture_root}/bad-checksum-assets" \
        FAKE_GH_ASSETS_JSON="${release_assets_json}" \
        FAKE_GH_PRIVATE_REPORTING_ENABLED='true' \
        bash "${verifier}" v9.9.9 2>&1
)"
checksum_failure_exit=$?
set -e

if [[ ${checksum_failure_exit} -eq 0 ]]; then
    die "GitHub release verifier accepted a bad published checksum pair"
fi
printf '%s\n' "${checksum_failure_output}" | grep -Fq 'published checksum asset declared' || die \
    "GitHub release verifier did not report the bad published checksum pair"

set +e
archive_failure_output="$(
    PATH="${fixture_root}/bin:${PATH}" \
        FINGRIND_GITHUB_RELEASE_VERIFY_RETRIES='1' \
        FINGRIND_GITHUB_RELEASE_VERIFY_DELAY_SECONDS='0' \
        FAKE_GH_MODE='bad-archive' \
        GITHUB_REF_NAME='44/merge' \
        GITHUB_REPOSITORY='resoltico/FinGrind' \
        FAKE_GH_TAG='v9.9.9' \
        FAKE_GH_REPOSITORY='resoltico/FinGrind' \
        FAKE_GH_RELEASE_URL='https://example.invalid/releases/v9.9.9' \
        FAKE_GH_GOOD_ZIP="${fixture_root}/good-source.zip" \
        FAKE_GH_GOOD_TAR="${fixture_root}/good-source.tar.gz" \
        FAKE_GH_BAD_ZIP="${fixture_root}/bad-source.zip" \
        FAKE_GH_ASSET_ROOT="${fixture_root}/release-assets" \
        FAKE_GH_BAD_CHECKSUM_ROOT="${fixture_root}/bad-checksum-assets" \
        FAKE_GH_ASSETS_JSON="${release_assets_json}" \
        FAKE_GH_PRIVATE_REPORTING_ENABLED='true' \
        bash "${verifier}" v9.9.9 2>&1
)"
archive_failure_exit=$?
set -e

if [[ ${archive_failure_exit} -eq 0 ]]; then
    die "GitHub release verifier accepted a source archive that leaked AGENTS.md"
fi
printf '%s\n' "${archive_failure_output}" | grep -Fq 'forbidden repo-owned agent metadata leaked into source archive' || die \
    "GitHub release verifier did not report the leaked source-archive metadata"

set +e
attestation_failure_output="$(
    PATH="${fixture_root}/bin:${PATH}" \
        FINGRIND_GITHUB_RELEASE_VERIFY_RETRIES='1' \
        FINGRIND_GITHUB_RELEASE_VERIFY_DELAY_SECONDS='0' \
        FAKE_GH_MODE='bad-attestation' \
        GITHUB_REF_NAME='44/merge' \
        GITHUB_REPOSITORY='resoltico/FinGrind' \
        FAKE_GH_TAG='v9.9.9' \
        FAKE_GH_REPOSITORY='resoltico/FinGrind' \
        FAKE_GH_RELEASE_URL='https://example.invalid/releases/v9.9.9' \
        FAKE_GH_GOOD_ZIP="${fixture_root}/good-source.zip" \
        FAKE_GH_GOOD_TAR="${fixture_root}/good-source.tar.gz" \
        FAKE_GH_BAD_ZIP="${fixture_root}/bad-source.zip" \
        FAKE_GH_ASSET_ROOT="${fixture_root}/release-assets" \
        FAKE_GH_BAD_CHECKSUM_ROOT="${fixture_root}/bad-checksum-assets" \
        FAKE_GH_ASSETS_JSON="${release_assets_json}" \
        FAKE_GH_PRIVATE_REPORTING_ENABLED='true' \
        bash "${verifier}" v9.9.9 2>&1
)"
attestation_failure_exit=$?
set -e

if [[ ${attestation_failure_exit} -eq 0 ]]; then
    die "GitHub release verifier accepted bundle assets without a valid published attestation"
fi
printf '%s\n' "${attestation_failure_output}" | grep -Fq 'published attestation verification failed' || die \
    "GitHub release verifier no longer reports a failing published asset attestation"
printf '%s\n' "${attestation_failure_output}" | grep -Fq 'release v9.9.9 verification failed:' || die \
    "GitHub release verifier did not fail with the structured release-verification prefix"

printf 'GitHub release verifier regression: success\n'
