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
readonly release_workflow="${repo_root}/.github/workflows/release.yml"

[[ -x "${verifier}" ]] || die "missing executable release verifier"
[[ -f "${archive_verifier}" ]] || die "missing source archive verifier helper"
[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
[[ -f "${release_workflow}" ]] || die "missing release workflow at ${release_workflow}"
grep -Fq 'scripts/test-verify-github-release.sh' "${stage_contract_script}" || die \
    "check stage contract no longer exercises the GitHub release verifier regression"
grep -Fq 'Checkout workflow-owner helper surface' "${release_workflow}" || die \
    "release workflow no longer materializes a replay-safe helper surface from main during workflow_dispatch reruns"
grep -Fq "printf 'path=%s\\n' './workflow-owner-surface' >> \"\$GITHUB_OUTPUT\"" "${release_workflow}" || die \
    "release workflow no longer resolves a distinct helper-root path for immutable-tag workflow replays"
grep -Fq '& "${{ steps.workflow-helper-root.outputs.path }}/scripts/setup-msvc-dev-cmd.ps1" -Arch x64' "${release_workflow}" || die \
    "release workflow no longer bootstraps the Windows MSVC environment through the replay-safe helper surface"
grep -Fq 'run: ${{ steps.workflow-helper-root.outputs.path }}/scripts/verify-release-candidate-tag.sh' "${release_workflow}" || die \
    "release workflow no longer routes the tag-handoff verifier through the replay-safe helper surface"
grep -Fq 'run: ${{ steps.workflow-helper-root.outputs.path }}/scripts/publish-github-release.sh' "${release_workflow}" || die \
    "release workflow no longer routes the release-object converger through the replay-safe helper surface"
grep -Fq '${{ steps.workflow-helper-root.outputs.path }}/scripts/verify-github-release.sh' "${release_workflow}" || die \
    "release workflow no longer routes the public release verifier through the replay-safe helper surface"
grep -Fq './scripts/verify-github-release.sh' "${repo_root}/docs/RELEASE_PROTOCOL.md" || die \
    "release protocol no longer requires the GitHub release verifier"
grep -Fq 'verify-security-policy-surface.sh' "${verifier}" || die \
    "GitHub release verifier no longer checks the live security-policy surface"
grep -Fq 'gh attestation verify' "${repo_root}/docs/RELEASE_PROTOCOL.md" || die \
    "release protocol no longer documents attestation-backed bundle verification"
grep -Fq 'published release assets' "${repo_root}/docs/RELEASE_PROTOCOL.md" || die \
    "release protocol no longer documents attesting the published release assets themselves"
grep -Fq 'Publication convergence is by asset name and digest' "${repo_root}/docs/RELEASE_PROTOCOL.md" || die \
    "release protocol no longer documents digest-aware release-asset convergence"
grep -Fq 'gh attestation verify' "${verifier}" || die \
    "release verifier no longer verifies published bundle attestations"
grep -Fq 'actions/attest@59d89421af93a897026c735860bf21b6eb4f7b26' "${release_workflow}" || die \
    "release workflow no longer pins the published bundle attestation action"
if grep -Fq 'ilammy/msvc-dev-cmd' "${release_workflow}"; then
    die "release workflow still depends on the deprecated third-party msvc-dev-cmd action"
fi
python3 - <<'PY' "${release_workflow}" || die \
    "release workflow no longer isolates published-asset attestation to the neutral post-upload job"
from pathlib import Path
import sys

workflow = Path(sys.argv[1]).read_text(encoding="utf-8")
job_start = workflow.find("  build-bundles:\n")
if job_start < 0:
    raise SystemExit("missing build-bundles job")
job_end = workflow.find("\n  attest-release-assets:\n", job_start)
if job_end < 0:
    raise SystemExit("missing attest-release-assets job delimiter")
job_text = workflow[job_start:job_end]
if "actions/attest@59d89421af93a897026c735860bf21b6eb4f7b26" in job_text:
    raise SystemExit("build-bundles job is attesting local artifacts instead of only publishing them")
if 'cli/build/distributions/fingrind-${{ needs.prepare-release.outputs.version }}-' in job_text:
    raise SystemExit("build-bundles job still assumes checkout-local cli/build/distributions bundle paths")
required_lines = (
    "    permissions:\n",
    "      contents: write\n",
    "          path: workflow-owner-surface\n",
    "        id: unix-bundle-build\n",
    "        id: windows-bundle-build\n",
    "          resolve_bundle_path() {\n",
    "                -e \"s/^${machine_prefix}=//p\" \\\n",
    "                -e \"s/^${legacy_prefix}//p\" | tail -n 1\n",
    "          archive_path=\"$(resolve_bundle_path 'bundle archive path' 'FINGRIND_BUNDLE_ARCHIVE' 'FinGrind bundle archive: ')\"\n",
    "          checksum_path=\"$(resolve_bundle_path 'bundle checksum path' 'FINGRIND_BUNDLE_CHECKSUM' 'FinGrind bundle checksum: ')\"\n",
    "          function Resolve-BundlePath {\n",
    "              $_.StartsWith($MachinePrefix) -or $_.StartsWith($LegacyPrefix)\n",
    '        run: & "${{ steps.workflow-helper-root.outputs.path }}/scripts/setup-msvc-dev-cmd.ps1" -Arch x64\n',
    '          ${{ steps.workflow-helper-root.outputs.path }}/scripts/bundle-smoke.sh \\\n',
    '          & "${{ steps.workflow-helper-root.outputs.path }}/scripts/bundle-smoke.ps1" `\n',
    '          bash "${{ steps.workflow-helper-root.outputs.path }}/scripts/publish-github-release.sh" \\\n',
    '          bash "${{ steps.workflow-helper-root.outputs.path }}/scripts/publish-github-release.sh" `\n',
    "            -LegacyPrefix 'FinGrind bundle archive: ' `\n",
    "            -LegacyPrefix 'FinGrind bundle checksum: ' `\n",
    "          Add-Content -Path $env:GITHUB_OUTPUT -Value \"archive-path=$archivePath\"\n",
    "          Add-Content -Path $env:GITHUB_OUTPUT -Value \"checksum-path=$checksumPath\"\n",
    '            "${{ steps.unix-bundle-build.outputs.archive-path }}"\n',
    '            "${{ steps.windows-bundle-build.outputs.archive-path }}"\n',
    '            "${{ steps.unix-bundle-build.outputs.checksum-path }}"\n',
    '            "${{ steps.windows-bundle-build.outputs.checksum-path }}"\n',
)
missing = [line.strip() for line in required_lines if line not in job_text]
if missing:
    raise SystemExit("missing build-bundles publish permission lines: " + ", ".join(missing))
if "bundleCliArchive did not report FINGRIND_BUNDLE_ARCHIVE" in job_text:
    raise SystemExit("build-bundles job still hard-fails on the current machine-readable archive label instead of accepting the immutable tag fallback line")
if "bundleCliArchive did not report FINGRIND_BUNDLE_CHECKSUM" in job_text:
    raise SystemExit("build-bundles job still hard-fails on the current machine-readable checksum label instead of accepting the immutable tag fallback line")
PY
python3 - <<'PY' "${release_workflow}" || die \
    "release workflow no longer grants the published-asset attestation job the permissions and download path required for exact-byte signing"
from pathlib import Path
import sys

workflow = Path(sys.argv[1]).read_text(encoding="utf-8")
job_start = workflow.find("  attest-release-assets:\n")
if job_start < 0:
    raise SystemExit("missing attest-release-assets job")
job_end = workflow.find("\n  verify-release:\n", job_start)
if job_end < 0:
    raise SystemExit("missing verify-release job delimiter")
job_text = workflow[job_start:job_end]
required_lines = (
    "    permissions:\n",
    "      contents: read\n",
    "      attestations: write\n",
    "      id-token: write\n",
    "      artifact-metadata: write\n",
    '          GH_REPO: ${{ github.repository }}\n',
    '          RELEASE_TAG: ${{ needs.prepare-release.outputs.tag }}\n',
    '          RELEASE_VERSION: ${{ needs.prepare-release.outputs.version }}\n',
    '          FINGRIND_RELEASE_ASSET_DOWNLOAD_RETRIES: "18"\n',
    '          FINGRIND_RELEASE_ASSET_DOWNLOAD_DELAY_SECONDS: "10"\n',
    '          readonly last_error_file="$(mktemp)"\n',
    "          trap 'rm -f \"${last_error_file}\"' EXIT\n",
    '            until gh release download "${RELEASE_TAG}" \\\n',
    '              --repo "${GH_REPO}" \\\n',
    '              --pattern "${asset_name}" \\\n',
    '              --dir release-assets \\\n',
    '              --clobber >/dev/null 2>"${last_error_file}"; do\n',
    '                cat "${last_error_file}" >&2\n',
    '            release-assets/fingrind-${{ needs.prepare-release.outputs.version }}-windows-x86_64.zip\n',
)
missing = [line.strip() for line in required_lines if line not in job_text]
if missing:
    raise SystemExit("missing attest-release-assets lines: " + ", ".join(missing))
PY
python3 - <<'PY' "${release_workflow}" || die \
    "release workflow no longer aligns the release-verifier timeout with its retry budget and published-asset attestation dependency"
from pathlib import Path
import sys

workflow = Path(sys.argv[1]).read_text(encoding="utf-8")
job_start = workflow.find("  verify-release:\n")
if job_start < 0:
    raise SystemExit("missing verify-release job")
job_text = workflow[job_start:]
required_lines = (
    '      - attest-release-assets\n',
    '    timeout-minutes: 15\n',
    '          FINGRIND_GITHUB_RELEASE_VERIFY_RETRIES: "36"\n',
    '          FINGRIND_GITHUB_RELEASE_VERIFY_DELAY_SECONDS: "10"\n',
)
missing = [line.strip() for line in required_lines if line not in job_text]
if missing:
    raise SystemExit("missing verify-release retry lines: " + ", ".join(missing))
PY

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

if [[ "${1:-}" == "release" && "${2:-}" == "download" ]]; then
    requested_tag="${3:-}"
    [[ "${requested_tag}" == "${tag}" ]] || exit 1
    [[ "${4:-}" == "--pattern" ]] || exit 1
    requested_asset="${5:-}"
    [[ "${6:-}" == "--dir" ]] || exit 1
    destination_dir="${7:-}"
    mkdir -p "${destination_dir}"
    printf 'downloaded %s\n' "${requested_asset}" > "${destination_dir}/${requested_asset}"
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

PATH="${fixture_root}/bin:${PATH}" \
    GITHUB_REF_NAME='44/merge' \
    GITHUB_REPOSITORY='resoltico/FinGrind' \
    FAKE_GH_TAG='v9.9.9' \
    FAKE_GH_REPOSITORY='resoltico/FinGrind' \
    FAKE_GH_RELEASE_URL='https://example.invalid/releases/v9.9.9' \
    FAKE_GH_GOOD_ZIP="${fixture_root}/good.zip" \
    FAKE_GH_GOOD_TAR="${fixture_root}/good.tar.gz" \
    FAKE_GH_BAD_ZIP="${fixture_root}/bad.zip" \
    bash "${verifier}" v9.9.9 fingrind.zip fingrind.sha256 >/dev/null

set +e
failure_output="$(
    PATH="${fixture_root}/bin:${PATH}" \
        FINGRIND_GITHUB_RELEASE_VERIFY_RETRIES='1' \
        FINGRIND_GITHUB_RELEASE_VERIFY_DELAY_SECONDS='0' \
        GITHUB_REF_NAME='44/merge' \
        GITHUB_REPOSITORY='resoltico/FinGrind' \
        FAKE_GH_MODE='bad-archive' \
        FAKE_GH_TAG='v9.9.9' \
        FAKE_GH_REPOSITORY='resoltico/FinGrind' \
        FAKE_GH_RELEASE_URL='https://example.invalid/releases/v9.9.9' \
        FAKE_GH_GOOD_ZIP="${fixture_root}/good.zip" \
        FAKE_GH_GOOD_TAR="${fixture_root}/good.tar.gz" \
        FAKE_GH_BAD_ZIP="${fixture_root}/bad.zip" \
        FAKE_GH_PRIVATE_REPORTING_ENABLED='true' \
        bash "${verifier}" v9.9.9 fingrind.zip fingrind.sha256 2>&1
)"
failure_exit=$?
set -e

if [[ ${failure_exit} -eq 0 ]]; then
    die "GitHub release verifier accepted a source archive that leaked AGENTS.md"
fi
printf '%s\n' "${failure_output}" | grep -Fq 'forbidden repo-owned agent metadata leaked into source archive' || die \
    "GitHub release verifier did not report the leaked source-archive metadata"

set +e
attestation_failure_output="$(
    PATH="${fixture_root}/bin:${PATH}" \
        FINGRIND_GITHUB_RELEASE_VERIFY_RETRIES='1' \
        FINGRIND_GITHUB_RELEASE_VERIFY_DELAY_SECONDS='0' \
        GITHUB_REF_NAME='44/merge' \
        GITHUB_REPOSITORY='resoltico/FinGrind' \
        FAKE_GH_MODE='bad-attestation' \
        FAKE_GH_TAG='v9.9.9' \
        FAKE_GH_REPOSITORY='resoltico/FinGrind' \
        FAKE_GH_RELEASE_URL='https://example.invalid/releases/v9.9.9' \
        FAKE_GH_GOOD_ZIP="${fixture_root}/good.zip" \
        FAKE_GH_GOOD_TAR="${fixture_root}/good.tar.gz" \
        FAKE_GH_BAD_ZIP="${fixture_root}/bad.zip" \
        FAKE_GH_PRIVATE_REPORTING_ENABLED='true' \
        bash "${verifier}" v9.9.9 fingrind.zip fingrind.sha256 2>&1
)"
attestation_failure_exit=$?
set -e

if [[ ${attestation_failure_exit} -eq 0 ]]; then
    die "GitHub release verifier accepted bundle assets without a valid published attestation"
fi
printf '%s\n' "${attestation_failure_output}" | grep -Fq 'published attestation verification failed for fingrind.zip' || die \
    "GitHub release verifier no longer reports the failing published asset attestation"
printf '%s\n' "${attestation_failure_output}" | grep -Fq 'release v9.9.9 verification failed:' || die \
    "GitHub release verifier did not fail with the structured release-verification prefix"

printf 'GitHub release verifier regression: success\n'
