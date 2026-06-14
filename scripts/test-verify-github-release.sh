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
readonly release_test_support="${script_dir}/github_release_test_support.py"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly stage_contract_script="${repo_root}/scripts/check-stage-contract.sh"
readonly release_workflow="${repo_root}/.github/workflows/release.yml"

[[ -x "${verifier}" ]] || die "missing executable release verifier"
[[ -f "${archive_verifier}" ]] || die "missing source archive verifier helper"
[[ -f "${release_test_support}" ]] || die "missing GitHub release test support helper"
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
grep -Fq 'finalize-release:' "${release_workflow}" || die \
    "release workflow no longer finalizes the staged GitHub release after container publication"
grep -Fq 'actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1' "${release_workflow}" || die \
    "release workflow no longer pins publication-staging uploads to the current Node24-backed artifact action"
grep -Fq 'actions/download-artifact@3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c # v8.0.1' "${release_workflow}" || die \
    "release workflow no longer pins publication-staging downloads to the current Node24-backed artifact action"
grep -Fq 'FINGRIND_WORKFLOW_HELPER_ROOT' "${release_workflow}" || die \
    "release workflow no longer resolves the workflow-owner helper surface for post-tag repair jobs"
grep -Fq 'path: workflow-owner-surface' "${release_workflow}" || die \
    "release workflow no longer checks out the workflow-owner helper surface for workflow-dispatch reruns"
grep -Fq 'build-staging-container:' "${release_workflow}" || die \
    "release workflow no longer stages native container images inside the release workflow"
grep -Fq 'promote-container:' "${release_workflow}" || die \
    "release workflow no longer promotes staged container images into the public release surface"
grep -Fq 'verify-release-candidate-tag.sh' "${release_workflow}" || die \
    "release workflow no longer routes the tag verifier through the repo-owned release script"
grep -Fq 'publish-github-release.sh' "${release_workflow}" || die \
    "release workflow no longer stages the draft GitHub release through the repo-owned publisher"
grep -Fq 'download-github-release-assets.sh' "${release_workflow}" || die \
    "release workflow no longer downloads staged or draft release assets through the repo-owned downloader"
grep -Fq 'finalize-github-release.sh' "${release_workflow}" || die \
    "release workflow no longer finalizes the staged GitHub release through the repo-owned finalizer"
grep -Fq 'FINGRIND_VERIFY_GITHUB_RELEASE_ALLOW_DRAFT: "true"' "${release_workflow}" || die \
    "release workflow no longer verifies the staged draft release before public container promotion"
grep -Fq 'verify-public-container-surface.sh' "${release_workflow}" || die \
    "release workflow no longer verifies staged and public container surfaces through the repo-owned verifier"
grep -Fq 'FINGRIND_RELEASE_MARK_LATEST' "${release_workflow}" || die \
    "release workflow no longer drives GitHub latest ownership from the canonical latest policy"
grep -Fq 'FINGRIND_VERIFY_PUBLIC_CONTAINER_LATEST' "${release_workflow}" || die \
    "release workflow no longer keeps public-container latest verification aligned with the latest policy"
grep -Fq './scripts/verify-github-release.sh' "${repo_root}/docs/RELEASE_PROTOCOL.md" || die \
    "release protocol no longer requires the GitHub release verifier"
grep -Fq './scripts/verify-public-container-surface.sh' "${repo_root}/docs/RELEASE_PROTOCOL.md" || die \
    "release protocol no longer requires public-container surface verification"

attest_job_surface="$(
    python3 "${release_test_support}" \
        extract-job-surface \
        --workflow "${release_workflow}" \
        --job attest-release-assets \
        --next-job verify-release
)"
printf '%s' "${attest_job_surface}" | grep -Fq 'uses: actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # v6.0.2' || die \
    "release workflow attestation job no longer checks out the repository before invoking repo-owned downloader scripts"
printf '%s' "${attest_job_surface}" | grep -Fq 'path: workflow-owner-surface' || die \
    "release workflow attestation job no longer checks out the workflow-owner helper surface for rerun-safe draft asset downloads"
printf '%s' "${attest_job_surface}" | grep -Fq 'contents: write' || die \
    "release workflow attestation job no longer keeps write-scoped contents permission for staged draft asset downloads"
printf '%s' "${attest_job_surface}" | grep -Fq '${FINGRIND_WORKFLOW_HELPER_ROOT}/scripts/download-github-release-assets.sh' || die \
    "release workflow attestation job no longer invokes the helper-rooted draft-aware asset downloader inside the attestation job surface"

verify_job_surface="$(
    python3 "${release_test_support}" \
        extract-job-surface \
        --workflow "${release_workflow}" \
        --job verify-release \
        --next-job build-staging-container
)"
printf '%s' "${verify_job_surface}" | grep -Fq 'contents: write' || die \
    "release workflow verifier job no longer keeps write-scoped contents permission for staged draft asset verification downloads"
printf '%s' "${verify_job_surface}" | grep -Fq 'FINGRIND_VERIFY_GITHUB_RELEASE_ALLOW_DRAFT: "true"' || die \
    "release workflow verifier job no longer verifies the staged draft release before container publication"

container_job_surface="$(
    python3 "${release_test_support}" \
        extract-job-surface \
        --workflow "${release_workflow}" \
        --job build-staging-container \
        --next-job promote-container
)"
printf '%s' "${container_job_surface}" | grep -Fq 'FINGRIND_DOCKER_SMOKE_REPO_ROOT: ${{ github.workspace }}' || die \
    "release workflow no longer passes the tagged checkout root into helper-rooted Docker smoke during staged-container publication"

release_assets_json="$(
    python3 "${repo_root}/scripts/read-release-publication-plan.py" --version 9.9.9 | jq -c '.releaseAssetNames'
)"

fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-test-verify-github-release.XXXXXX")"
cleanup() {
    rm -rf "${fixture_root}"
}
trap cleanup EXIT

mkdir -p "${fixture_root}/bin" "${fixture_root}/release-assets"

python3 "${release_test_support}" \
    build-release-fixtures \
    --fixture-root "${fixture_root}" \
    --release-assets-json "${release_assets_json}"

cat > "${fixture_root}/bin/gh" <<EOF
#!/usr/bin/env bash
set -euo pipefail
exec python3 "${release_test_support}" fake-gh "\$@"
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
