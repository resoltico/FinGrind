#!/usr/bin/env bash
# Verify that the public CI workflow invokes the canonical root verification gate.

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
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly workflow_file="${repo_root}/.github/workflows/ci.yml"
readonly retired_wrapper_workflow="${repo_root}/.github/workflows/gradle-wrapper-validation.yml"
readonly release_publication_contract_reader="${repo_root}/scripts/read-release-publication-contract.py"
readonly developer_doc="${repo_root}/docs/DEVELOPER.md"
readonly developer_ci_doc="${repo_root}/docs/DEVELOPER_CI.md"
readonly developer_gradle_doc="${repo_root}/docs/DEVELOPER_GRADLE.md"
readonly developer_release_publication_doc="${repo_root}/docs/DEVELOPER_RELEASE_PUBLICATION.md"
readonly release_workflow_file="${repo_root}/.github/workflows/release.yml"
readonly container_promoter="${repo_root}/scripts/promote-container-image.sh"
readonly container_promotion_support="${repo_root}/scripts/container-promotion-support.sh"
readonly latest_policy_resolver="${repo_root}/scripts/resolve-release-latest-policy.py"
readonly release_tag_support="${repo_root}/scripts/release-tag-support.sh"
readonly release_workflow_initiator_verifier="${repo_root}/scripts/verify-release-workflow-initiator.sh"
readonly windows_publication_verifier="${repo_root}/scripts/verify-windows-publication-surface.ps1"
readonly windows_publication_support="${repo_root}/scripts/verify-windows-publication-surface-support.ps1"
readonly windows_publication_policy="${repo_root}/scripts/windows_publication_policy.py"
readonly windows_publication_plan_policy="${repo_root}/scripts/windows_publication_plan_policy.py"
readonly windows_publication_manifest_policy="${repo_root}/scripts/windows_publication_manifest_policy.py"
readonly windows_publication_protocol_policy="${repo_root}/scripts/windows_publication_policy_protocol.py"
readonly windows_publication_policy_boundary="${repo_root}/scripts/windows_publication_policy_boundary.py"
readonly windows_failure_evidence_writer="${repo_root}/scripts/write-windows-failure-evidence.ps1"
readonly powershell_metadata="${repo_root}/gradle/fingrind-build.properties"
readonly powershell_provisioner="${repo_root}/scripts/provision-powershell-runtime.py"
readonly python_runtime_support="${repo_root}/scripts/python-runtime-support.sh"

[[ -f "${python_runtime_support}" ]] || die \
    "missing Python runtime support helper at ${python_runtime_support}"
# shellcheck source=/dev/null
source "${python_runtime_support}"
prepare_python_runtime_env

# shellcheck source=./ci-release-surface-workflow-assertions-support.sh
source "${script_dir}/ci-release-surface-workflow-assertions-support.sh"

assert_ci_required_artifact_owners
assert_exact_zulu_toolchain_contract 'release workflow' "${release_workflow_file}" 2 printf
[[ -x "${container_promoter}" ]] || die "missing executable public-container promotion owner at ${container_promoter}"
[[ -f "${container_promotion_support}" ]] || die \
    "missing public-container promotion state-machine support at ${container_promotion_support}"
[[ -x "${latest_policy_resolver}" ]] || die \
    "missing executable latest-publication policy resolver at ${latest_policy_resolver}"
[[ -f "${windows_publication_verifier}" ]] || die \
    "missing shared native Windows publication verifier at ${windows_publication_verifier}"
[[ -f "${windows_publication_support}" ]] || die \
    "missing shared Windows publication filesystem adapter at ${windows_publication_support}"
[[ -f "${windows_publication_policy}" ]] || die \
    "missing cross-platform Windows publication policy owner at ${windows_publication_policy}"
[[ -f "${windows_publication_plan_policy}" ]] || die \
    "missing canonical Windows publication plan owner at ${windows_publication_plan_policy}"
[[ -f "${windows_publication_manifest_policy}" ]] || die \
    "missing Windows publication manifest owner at ${windows_publication_manifest_policy}"
[[ -f "${windows_publication_protocol_policy}" ]] || die \
    "missing Windows publication protocol owner at ${windows_publication_protocol_policy}"
[[ -f "${windows_publication_policy_boundary}" ]] || die \
    "missing Windows publication policy boundary at ${windows_publication_policy_boundary}"
[[ -f "${windows_failure_evidence_writer}" ]] || die \
    "missing centralized Windows failure-evidence writer at ${windows_failure_evidence_writer}"
[[ -f "${powershell_metadata}" ]] || die "missing canonical PowerShell metadata at ${powershell_metadata}"
[[ -f "${powershell_provisioner}" ]] || die "missing PowerShell provisioner at ${powershell_provisioner}"
required_pwsh_version="$(
    "${FINGRIND_PYTHON_EXECUTABLE}" "${powershell_provisioner}" \
        --metadata "${powershell_metadata}" \
        --print-version
)"
readonly required_pwsh_version
[[ -n "${required_pwsh_version}" ]] || die "canonical PowerShell metadata has no exact version"
assert_ci_bootstrap_and_windows_publication_contract
grep -Fq 'before its first public tag, unreleased repair commits may retain the target version' \
    "${developer_release_publication_doc}" || die \
    "release-publication reference no longer permits same-version repairs before the first public tag"
grep -Fq 'scripts/download-github-release-assets.sh' "${developer_release_publication_doc}" || die \
    "release-publication reference no longer names the draft-aware release asset downloader"
if grep -Fq 'gh release download' "${developer_release_publication_doc}"; then
    die "release-publication reference reintroduced gh release download for draft asset retrieval"
fi
grep -Fq 'Published bundle smoke (${{ matrix.classifier }})' "${workflow_file}" || die \
    "CI workflow no longer publishes pre-merge smoke coverage for every published bundle classifier"
published_bundle_smoke_job="$(workflow_job_block 'published-bundle-smoke')"
mutation_job="$(workflow_job_block 'mutation')"
grep -Fq 'timeout-minutes: 130' <<< "${published_bundle_smoke_job}" || die \
    "published bundle smoke no longer has the observed-runtime budget for the slowest host proof"
release_prepare_job="$(workflow_job_block_from "${release_workflow_file}" 'prepare-publication')"
release_bundle_build_job="$(workflow_job_block_from "${release_workflow_file}" 'build-bundles')"
release_publish_job="$(workflow_job_block_from "${release_workflow_file}" 'publish-release')"
release_attestation_job="$(workflow_job_block_from "${release_workflow_file}" 'attest-release-assets')"
release_verification_job="$(workflow_job_block_from "${release_workflow_file}" 'verify-release')"
release_container_build_job="$(workflow_job_block_from "${release_workflow_file}" 'build-staging-container')"
release_container_promotion_job="$(workflow_job_block_from "${release_workflow_file}" 'promote-container')"
release_finalization_job="$(workflow_job_block_from "${release_workflow_file}" 'finalize-release')"
check_job="$(workflow_job_block 'check')"
devcontainer_changes_job="$(workflow_job_block 'devcontainer-changes')"
gate_job="$(workflow_job_block 'gate')"
wrapper_validation_job="$(workflow_job_block 'wrapper-validation')"
grep -Fq 'timeout-minutes: 130' <<< "${release_bundle_build_job}" || die \
    "release bundle builds no longer have the observed-runtime budget for the slowest publication proof"
if ! grep -Fq 'Install repo-owned Python tools on Unix' <<< "${published_bundle_smoke_job}" || \
    ! grep -Fq 'if: runner.os != '\''Windows'\''' <<< "${published_bundle_smoke_job}" || \
    ! grep -Fq 'python3 -m pip install --user "uv==${uv_version}"' <<< "${published_bundle_smoke_job}" || \
    ! grep -Fq 'ORG_GRADLE_PROJECT_fingrindUvExecutable=%s' <<< "${published_bundle_smoke_job}"; then
    die "published bundle smoke no longer provisions the metadata-pinned Unix uv launcher before bundle verification"
fi
release_container_python_step="$(
    workflow_step_block "${release_container_build_job}" 'Install repo-owned release-smoke Python tools'
)"
release_container_smoke_step="$(
    workflow_step_block "${release_container_build_job}" 'Smoke test the Docker image before publication staging'
)"
[[ -n "${release_container_python_step}" ]] || die \
    "staging-container release publication no longer provisions its pinned Python smoke environment"
[[ -n "${release_container_smoke_step}" ]] || die \
    "staging-container release publication no longer has its Docker acceptance step"
if ! grep -Fq 'actions/setup-python@5fda3b95a4ea91299a34e894583c3862153e4b97' \
        <<< "${release_container_build_job}" || \
    ! grep -Fq 'python-version: ${{ steps.build-metadata.outputs.python-version }}' \
        <<< "${release_container_build_job}" || \
    ! grep -Fq 'python -m pip install --user "uv==${uv_version}"' \
        <<< "${release_container_python_step}" || \
    ! grep -Fq 'requirements-release-smoke-workflow.txt' \
        <<< "${release_container_python_step}" || \
    ! grep -Fq 'ORG_GRADLE_PROJECT_fingrindUvExecutable=%s' \
        <<< "${release_container_python_step}"; then
    die "staging-container release publication no longer installs the metadata-pinned uv smoke environment"
fi
release_container_python_line="$(
    grep -n -F '      - name: Install repo-owned release-smoke Python tools' \
        <<< "${release_container_build_job}" | cut -d: -f1
)"
release_container_smoke_line="$(
    grep -n -F '      - name: Smoke test the Docker image before publication staging' \
        <<< "${release_container_build_job}" | cut -d: -f1
)"
[[ "${release_container_python_line}" =~ ^[0-9]+$ && "${release_container_smoke_line}" =~ ^[0-9]+$ && \
    "${release_container_python_line}" -lt "${release_container_smoke_line}" ]] || die \
    "staging-container release publication must provision its Python smoke environment before Docker acceptance"
grep -Fqx 'run-name: Release ${{ inputs.release_tag || github.ref_name }}' "${release_workflow_file}" || die \
    "release workflow no longer gives both tag-push and workflow-dispatch runs one deterministic target-derived display title"
readonly release_workflow_concurrency="$(
    awk '
        $0 == "concurrency:" { active = 1 }
        active && $0 == "jobs:" { exit }
        active { print }
    ' "${release_workflow_file}" | sed '/^[[:space:]]*$/d'
)"
if [[ "${release_workflow_concurrency}" != $'concurrency:\n  group: release-publication\n  cancel-in-progress: false\n  queue: max' ]]; then
    die "release workflow no longer serializes the complete repository publication path with the bounded queued release-publication concurrency group"
fi
if grep -Fq 'publication-${{ github.workflow }}-${{ inputs.release_tag || github.ref_name }}' \
    "${release_workflow_file}"; then
    die "release workflow restored tag-scoped publication concurrency that lets cross-tag latest ownership race"
fi
grep -Fqx '  push:' "${release_workflow_file}" || die \
    "release workflow no longer retains the tag-push target-identity path"
grep -Fqx '  workflow_dispatch:' "${release_workflow_file}" || die \
    "release workflow no longer retains the manual target-identity path"
[[ -n "${published_bundle_smoke_job}" ]] || die "CI workflow no longer defines published bundle smoke as a job"
[[ -n "${mutation_job}" ]] || die "CI workflow no longer defines release-critical mutation execution"
[[ -n "${release_prepare_job}" ]] || die "release workflow no longer defines release preparation"
[[ -n "${release_bundle_build_job}" ]] || die "release workflow no longer defines the bundle build job"
[[ -n "${release_publish_job}" ]] || die "release workflow no longer defines the GitHub release staging job"
[[ -n "${release_attestation_job}" ]] || die "release workflow no longer defines the release attestation job"
[[ -n "${release_verification_job}" ]] || die "release workflow no longer defines the release verification job"
[[ -n "${release_container_build_job}" ]] || die "release workflow no longer defines the staging-container build job"
[[ -n "${release_container_promotion_job}" ]] || die "release workflow no longer defines the container-promotion job"
[[ -n "${release_finalization_job}" ]] || die "release workflow no longer defines the release finalization job"
[[ -n "${check_job}" ]] || die "CI workflow no longer defines the canonical Linux root-check job"
[[ -n "${devcontainer_changes_job}" ]] || die "CI workflow no longer defines devcontainer change detection"
[[ -n "${gate_job}" ]] || die "CI workflow no longer defines the aggregate Gate job"
[[ -n "${wrapper_validation_job}" ]] || die "CI workflow no longer defines Gradle wrapper validation"
grep -Fq 'timeout-minutes: 130' <<< "${check_job}" || die \
    "CI root check no longer has the observed-runtime budget for the canonical full gate"
grep -Fq 'name: Critical accounting mutation scopes' <<< "${mutation_job}" || die \
    "CI mutation job no longer has the release-contract display name"
grep -Fq 'timeout-minutes: 45' <<< "${mutation_job}" || die \
    "CI mutation job no longer has its bounded verification budget"
grep -Fq 'run: ./check_mutation.sh' <<< "${mutation_job}" || die \
    "CI mutation job no longer runs the fixed mutation wrapper"
grep -Fq 'if-no-files-found: error' <<< "${mutation_job}" || die \
    "CI mutation job no longer fails closed when report evidence is absent"
readonly wrapper_validation_display_name="$(
    printf '%s\n' "${wrapper_validation_job}" | sed -n 's/^    name: //p' | head -n 1
)"
[[ -n "${wrapper_validation_display_name}" ]] || die \
    "CI workflow no longer gives Gradle wrapper validation a display name"
readonly mutation_display_name="$(
    printf '%s\n' "${mutation_job}" | sed -n 's/^    name: //p' | head -n 1
)"
[[ "${mutation_display_name}" == 'Critical accounting mutation scopes' ]] || die \
    "CI workflow no longer gives release-critical mutation execution its canonical display name"
readonly required_ci_job_names_json="$(
    "${FINGRIND_PYTHON_EXECUTABLE}" "${release_publication_contract_reader}" | jq -c '.requiredCiJobNames'
)"
if ! jq -e --arg job_name "${wrapper_validation_display_name}" \
    'index($job_name) != null' <<< "${required_ci_job_names_json}" >/dev/null; then
    die "release-publication contract omits the Gradle wrapper validation Gate dependency"
fi
if ! jq -e --arg job_name "${mutation_display_name}" \
    'index($job_name) != null' <<< "${required_ci_job_names_json}" >/dev/null; then
    die "release-publication contract omits release-critical mutation execution"
fi
release_target_tag_step="$(workflow_step_block "${release_prepare_job}" 'Determine target release tag')"
[[ -n "${release_target_tag_step}" ]] || die \
    "release workflow no longer gives target-tag resolution one explicit owner"
[[ -f "${release_tag_support}" ]] || die \
    "release workflow no longer has one canonical stable release-tag helper"
[[ -x "${release_workflow_initiator_verifier}" ]] || die \
    "release workflow no longer has one canonical owner-only initiator verifier"
grep -Fq 'FINGRIND_DISPATCH_RELEASE_TAG: ${{ inputs.release_tag }}' <<< "${release_target_tag_step}" || die \
    "release workflow no longer passes workflow-dispatch tag input through an environment boundary"
grep -Fq 'FINGRIND_RELEASE_EVENT_NAME: ${{ github.event_name }}' <<< "${release_target_tag_step}" || die \
    "release workflow no longer passes the release event identity through an environment boundary"
grep -Fq 'TAG="${FINGRIND_DISPATCH_RELEASE_TAG}"' <<< "${release_target_tag_step}" || die \
    "release workflow no longer treats the dispatched release tag as shell data"
grep -Fq 'source "${FINGRIND_WORKFLOW_HELPER_ROOT}/scripts/release-tag-support.sh"' \
    <<< "${release_target_tag_step}" || die \
    "release workflow no longer sources the canonical stable release-tag helper before admission"
grep -Fq 'release_tag_is_stable "${TAG}"' <<< "${release_target_tag_step}" || die \
    "release workflow no longer rejects prerelease and malformed tags before publication jobs"
grep -Fq 'release_tag_version "${TAG}"' <<< "${release_target_tag_step}" || die \
    "release workflow no longer derives the release version from the validated stable tag"
if printf '%s\n' "${release_target_tag_step}" | awk '
    $0 == "        run: |" {
        in_run = 1
        next
    }
    in_run && /\$\{\{[[:space:]]*inputs\.release_tag[[:space:]]*\}\}/ {
        invalid = 1
    }
    END {
        exit invalid
    }
'; then
    :
else
    die "release workflow interpolates workflow-dispatch tag input directly into shell source"
fi
release_initiator_step="$(workflow_step_block "${release_prepare_job}" 'Verify release workflow initiator')"
[[ -n "${release_initiator_step}" ]] || die \
    "release workflow no longer verifies its initiator before release-tag admission"
for initiator_context in \
    'FINGRIND_RELEASE_EVENT_NAME: ${{ github.event_name }}' \
    'FINGRIND_RELEASE_REF: ${{ github.ref }}' \
    'FINGRIND_RELEASE_ACTOR_ID: ${{ github.actor_id }}' \
    'FINGRIND_RELEASE_REPOSITORY_OWNER_ID: ${{ github.repository_owner_id }}' \
    'FINGRIND_RELEASE_TRIGGERING_ACTOR: ${{ github.triggering_actor }}' \
    'FINGRIND_RELEASE_REPOSITORY_OWNER: ${{ github.repository_owner }}'; do
    grep -Fq "${initiator_context}" <<< "${release_initiator_step}" || die \
        "release workflow no longer passes ${initiator_context} through its owner-only initiator boundary"
done
grep -Fq 'verify-release-workflow-initiator.sh' <<< "${release_initiator_step}" || die \
    "release workflow no longer delegates initiator authorization to its canonical verifier"
release_plan_step="$(workflow_step_block "${release_prepare_job}" 'Render canonical release-publication plan')"
[[ -n "${release_plan_step}" ]] || die \
    "release workflow no longer renders one canonical release-publication plan"
grep -Fq -- '--repository-root "${GITHUB_WORKSPACE}"' <<< "${release_plan_step}" || die \
    "release workflow no longer binds its publication plan to the immutable tagged checkout"
grep -Fq 'workflow-helper-commit: ${{ steps.workflow-helper.outputs.commit }}' \
    "${release_workflow_file}" || die \
    "release workflow no longer exposes the immutable rerun helper commit from prepare-publication"
grep -Fq 'release-commit: ${{ steps.target-tag.outputs.commit }}' \
    "${release_workflow_file}" || die \
    "release workflow no longer exposes the immutable candidate commit for OCI metadata"
grep -Fq "printf 'commit=%s\\n' \"\$(git rev-parse HEAD)\" >> \"\$GITHUB_OUTPUT\"" \
    "${release_workflow_file}" || die \
    "release workflow no longer binds the OCI revision to the tagged checkout"
grep -Fq 'Pin the rerun release-control helper commit' "${release_workflow_file}" || die \
    "release workflow no longer resolves one rerun helper commit before fan-out"
grep -Fq 'git -C workflow-owner-surface rev-parse HEAD' "${release_workflow_file}" || die \
    "release workflow no longer derives the rerun helper commit from the checked-out main control surface"
release_helper_pin_step="$(workflow_step_block "${release_prepare_job}" 'Pin the rerun release-control helper commit')"
[[ -n "${release_helper_pin_step}" ]] || die \
    "release workflow no longer defines the rerun helper-commit pinning step"
if [[ "$(grep -Fc 'Release control helper commit:' <<< "${release_helper_pin_step}")" -ne 2 ]]; then
    die "release workflow no longer records the exact pinned main helper commit in both logs and the step summary"
fi
grep -Fqx "          printf 'Release control helper commit: %s\\n' \"\${helper_commit}\"" \
    <<< "${release_helper_pin_step}" || die \
    "release workflow no longer emits the exact pinned main helper commit to the run log"
grep -Fq "printf 'Release control helper commit: %s\\n' \"\${helper_commit}\" >> \"\$GITHUB_STEP_SUMMARY\"" \
    <<< "${release_helper_pin_step}" || die \
    "release workflow no longer publishes the exact pinned main helper commit to the step summary"
if [[ "$(grep -Fxc '          ref: main' "${release_workflow_file}")" -ne 1 ]]; then
    die "release workflow must resolve mutable main exactly once before pinning rerun helper jobs"
fi
if [[ "$(grep -Fxc '          ref: ${{ needs.prepare-publication.outputs.workflow-helper-commit }}' "${release_workflow_file}")" -ne 7 ]]; then
    die "release workflow no longer pins every post-prepare rerun helper checkout to one immutable commit"
fi
if [[ "$(grep -Fxc '          path: workflow-owner-surface' "${release_workflow_file}")" -ne 8 ]]; then
    die "release workflow no longer gives every rerun control owner the one pinned helper checkout"
fi
if grep -Fq 'latest-policy' <<< "${release_prepare_job}" || \
    grep -Fq 'mark-latest:' <<< "${release_prepare_job}"; then
    die "release preparation still resolves mutable latest ownership before immutable public exact-container acceptance"
fi
grep -Fq 'outputs:' <<< "${release_container_promotion_job}" || die \
    "release container promotion no longer exposes the post-acceptance latest decision"
grep -Fq 'mark-latest: ${{ steps.latest-policy.outputs.mark-latest }}' \
    <<< "${release_container_promotion_job}" || die \
    "release container promotion no longer exposes its fresh latest-policy result as a job output"
grep -Fq 'Accept the immutable public exact container' <<< "${release_container_promotion_job}" || die \
    "release container promotion no longer accepts immutable exact publication before latest ownership resolution"
grep -Fq 'Verify accepted immutable public exact container' <<< "${release_container_promotion_job}" || die \
    "release container promotion no longer verifies immutable public exact publication before latest ownership resolution"
grep -Fq 'Resolve latest publication policy after exact acceptance' <<< "${release_container_promotion_job}" || die \
    "release container promotion no longer resolves latest ownership after exact acceptance"
grep -Fq 'resolve-release-latest-policy.py' <<< "${release_container_promotion_job}" || die \
    "release container promotion no longer uses the canonical all-page latest policy resolver"
grep -Fq 'Converge latest from the accepted immutable exact container' <<< "${release_container_promotion_job}" || die \
    "release container promotion no longer derives latest from the accepted immutable exact container"
grep -Fq "if: steps.latest-policy.outputs.mark-latest == 'true'" \
    <<< "${release_container_promotion_job}" || die \
    "release container promotion no longer gates latest mutation on the fresh policy result"
grep -Fq 'FINGRIND_VERIFY_PUBLIC_CONTAINER_LATEST: ${{ steps.latest-policy.outputs.mark-latest }}' \
    <<< "${release_container_promotion_job}" || die \
    "release container promotion no longer verifies latest with the same fresh policy decision"
if grep -Fq 'docker buildx imagetools create' <<< "${release_container_promotion_job}"; then
    die "release workflow reintroduced inline container-tag mutation instead of the repo-owned immutable state machine"
fi
grep -Fq 'FINGRIND_RELEASE_MARK_LATEST: ${{ needs.promote-container.outputs.mark-latest }}' \
    <<< "${release_finalization_job}" || die \
    "release finalization no longer consumes the post-exact-acceptance latest policy decision"
if grep -Fq 'needs.prepare-publication.outputs.mark-latest' <<< "${release_finalization_job}"; then
    die "release finalization still consumes stale latest ownership from release preparation"
fi
release_prefinal_verification_step="$(
    workflow_step_block \
        "${release_finalization_job}" \
        'Reverify staged GitHub release handoff before public finalization'
)"
[[ -n "${release_prefinal_verification_step}" ]] || die \
    "release finalization no longer re-verifies the GitHub-hosted staged asset set before public finalization"
for prefinal_verification_input in \
    'GH_TOKEN: ${{ github.token }}' \
    'RELEASE_TAG: ${{ needs.prepare-publication.outputs.tag }}' \
    'FINGRIND_RELEASE_PAYLOAD_ROOT: ${{ github.workspace }}' \
    'FINGRIND_GITHUB_RELEASE_VERIFY_RETRIES: "36"' \
    'FINGRIND_GITHUB_RELEASE_VERIFY_DELAY_SECONDS: "10"' \
    'FINGRIND_VERIFY_GITHUB_RELEASE_ALLOW_DRAFT: "true"' \
    'run: "${FINGRIND_WORKFLOW_HELPER_ROOT}/scripts/verify-github-release.sh"'; do
    grep -Fq "${prefinal_verification_input}" <<< "${release_prefinal_verification_step}" || die \
        "release pre-finalization verification no longer carries ${prefinal_verification_input}"
done
next_release_finalization_step_name="$(
    printf '%s\n' "${release_finalization_job}" | awk '
        $0 == "      - name: Reverify staged GitHub release handoff before public finalization" {
            seen = 1
            next
        }
        seen && $0 ~ /^      - name: / {
            print
            exit
        }
    '
)"
[[ "${next_release_finalization_step_name}" == '      - name: Finalize the staged GitHub release' ]] || die \
    "release workflow no longer finalizes immediately after its complete staged-asset re-verification"
assert_job_permissions \
    'release bundle build' \
    "${release_bundle_build_job}" \
    $'artifact-metadata: write\nattestations: write\ncontents: read\nid-token: write'
assert_job_permissions 'release staging' "${release_publish_job}" 'contents: write'
assert_job_permissions \
    'release asset attestation' \
    "${release_attestation_job}" \
    $'artifact-metadata: write\nattestations: write\ncontents: write\nid-token: write'
assert_job_permissions 'release verification' "${release_verification_job}" 'contents: write'
assert_job_permissions \
    'release staging-container build' \
    "${release_container_build_job}" \
    $'contents: read\nid-token: write\npackages: write'
grep -Fq 'FINGRIND_IMAGE_VERSION=${{ needs.prepare-publication.outputs.version }}' \
    <<< "${release_container_build_job}" || die \
    "release container build no longer writes the exact OCI version label"
grep -Fq 'FINGRIND_IMAGE_REVISION=${{ needs.prepare-publication.outputs.release-commit }}' \
    <<< "${release_container_build_job}" || die \
    "release container build no longer writes the immutable OCI revision label"
assert_job_permissions \
    'release container promotion' \
    "${release_container_promotion_job}" \
    $'contents: read\nid-token: write\npackages: write'
assert_job_permissions 'release finalization' "${release_finalization_job}" 'contents: write'
grep -Fq 'Provision pinned PowerShell runtime for mandatory Windows-contract preflight' <<< "${check_job}" || die \
    "CI root check no longer provisions the exact PowerShell runtime before mandatory Stage 5"
grep -Fq 'scripts/provision-powershell-runtime.py' <<< "${check_job}" || die \
    "CI root check no longer uses the repository-owned PowerShell provisioner"
grep -Fq 'FINGRIND_PWSH_EXECUTABLE' <<< "${check_job}" || die \
    "CI root check no longer exports the exact provisioned PowerShell executable"
grep -Fq 'GITHUB_PATH' <<< "${check_job}" || die \
    "CI root check no longer makes the exact provisioned PowerShell executable first on PATH"
grep -Fq 'actual_pwsh_version' <<< "${check_job}" || die \
    "CI root check no longer proves the provisioned PowerShell version before root verification"
assert_devcontainer_change_inputs "${devcontainer_changes_job}"
if grep -Eq '^[[:space:]]*needs:' <<< "${published_bundle_smoke_job}"; then
    die "published bundle smoke no longer starts independently of the Linux root gate"
fi
readonly approved_bundle_runner_rows=$'macos-15|macos-aarch64|macos|aarch64\nmacos-15-intel|macos-x86_64|macos|x86_64\nubuntu-24.04|linux-x86_64|linux|x86_64\nubuntu-24.04-arm|linux-aarch64|linux|aarch64\nwindows-2022|windows-x86_64|windows|x86_64' approved_container_runner_rows=$'ubuntu-24.04|linux-x86_64|linux|x86_64\nubuntu-24.04-arm|linux-aarch64|linux|aarch64'
assert_literal_runner_matrix \
    'CI published bundle smoke' \
    "${published_bundle_smoke_job}" \
    "${approved_bundle_runner_rows}"
assert_literal_runner_matrix \
    'release bundle build' \
    "${release_bundle_build_job}" \
    "${approved_bundle_runner_rows}"
assert_literal_runner_matrix \
    'release staging-container build' \
    "${release_container_build_job}" \
    "${approved_container_runner_rows}"
grep -Fq 'runs-on: ubuntu-24.04' <<< "${release_container_promotion_job}" || die \
    "release container promotion no longer uses its literal ubuntu-24.04 control runner"
if grep -Fq 'container-matrix-json' <<< "${release_container_promotion_job}"; then
    die "release container promotion still consumes dynamic matrix output"
fi
if ! grep -Eq '^[[:space:]]*-[[:space:]]+check$' <<< "${gate_job}" || \
    ! grep -Eq '^[[:space:]]*-[[:space:]]+mutation$' <<< "${gate_job}" || \
    ! grep -Eq '^[[:space:]]*-[[:space:]]+published-bundle-smoke$' <<< "${gate_job}"; then
    die "aggregate Gate no longer requires root verification, mutation evidence, and the published bundle proof"
fi
if grep -Fq 'prepare-published-bundle-smoke-matrix' <<< "${gate_job}" || \
    grep -Fq 'prepare-published-bundle-smoke-matrix:' "${workflow_file}"; then
    die "CI workflow still carries a dynamic runner-matrix preparation job"
fi
grep -Fq './scripts/verify-runner-identity.py' "${workflow_file}" || die \
    "CI workflow no longer delegates runner-identity normalization to the canonical verifier"
grep -Fq 'Smoke test the published Unix CLI bundle on the host runner' "${workflow_file}" || die \
    "CI workflow no longer smoke-tests the non-Windows published bundle classifiers before release"
grep -Fq './scripts/bundle-smoke.sh "${{ steps.unix-bundle-build.outputs.archive-path }}"' "${workflow_file}" || die \
    "CI workflow no longer delegates non-Windows published bundle smoke to the canonical Bash owner"
grep -Fq -- '--execution-surface compatibility-floor' "${workflow_file}" || die \
    "CI workflow no longer re-proves the published Linux bundle on the compatibility floor"
grep -Fq 'source ./scripts/gradle-wrapper-support.sh' "${workflow_file}" || die \
    "CI workflow no longer sources the canonical Gradle wrapper helper before reading the bundle manifest"
grep -Fq "manifest_path=\"\$(fg_gradle_bundle_archive_manifest_path \"\$PWD\" 'cli' \"\${is_darwin}\")\"" "${workflow_file}" || die \
    "CI workflow no longer resolves the canonical bundle manifest path for published bundle smoke"
grep -Fq 'python3 -c '\''import json, sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["archivePath"])'\''' "${workflow_file}" || die \
    "CI workflow no longer reads the bundle archive path from the generated manifest"
grep -Fq 'python3 -c '\''import json, sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["checksumPath"])'\''' "${workflow_file}" || die \
    "CI workflow no longer reads the bundle checksum path from the generated manifest"
grep -Fq 'uv.exe' "${workflow_file}" || die \
    "CI workflow no longer bootstraps the pinned uv launcher on Windows before Gradle-owned Python tool tasks"
grep -Fq -- '-File .\scripts\setup-msvc-dev-cmd.ps1' "${workflow_file}" || die \
    "CI workflow no longer bootstraps the Windows MSVC environment through the repo-owned PowerShell owner"
grep -Fq '& $env:FINGRIND_PWSH_EXECUTABLE' "${workflow_file}" || die \
    "CI workflow no longer invokes native Windows PowerShell owners through the verified exact executable"
grep -Fq '.\scripts\configure-windows-defender-build-exclusions.ps1' "${workflow_file}" || die \
    "CI workflow no longer delegates Windows Defender build exclusions to the repo-owned PowerShell owner"
if grep -Fq 'Add-MpPreference -ExclusionPath' "${workflow_file}"; then
    die "CI workflow carries inline Windows Defender exclusion calls instead of the repo-owned PowerShell owner"
fi
if grep -Fq 'site.USER_BASE' "${workflow_file}"; then
    die "CI workflow computes the uv launcher path from site.USER_BASE instead of Python's scripts scheme"
fi
if grep -Fq '.\gradlew.bat check --no-daemon --console=plain' "${workflow_file}"; then
    die "CI workflow reruns the canonical root gate inside the Windows bundle publication lane"
fi
if grep -Fq 'resolve_bundle_path()' "${workflow_file}"; then
    die "CI workflow scrapes published bundle smoke artifact paths from Gradle console output instead of the canonical bundle manifest"
fi
grep -Fq 'Verify managed SQLite CLI runtimes on Unix' "${workflow_file}" || die \
    "CI workflow no longer verifies the managed Unix runtime surfaces before bundle publication smoke"
grep -Fq 'Verify native Windows publication surface' "${workflow_file}" || die \
    "CI workflow no longer orchestrates the shared native Windows publication verifier"
grep -Fq 'id: windows-publication-proof' "${workflow_file}" || die \
    "CI workflow no longer gives shared Windows publication evidence one workflow-output owner"
grep -Fq '.\scripts\verify-windows-publication-surface.ps1' "${workflow_file}" || die \
    "CI workflow no longer calls the repository-owned native Windows publication verifier"
grep -Fq -- '-File .\scripts\verify-windows-publication-surface.ps1' "${workflow_file}" || die \
    "CI workflow no longer launches the native Windows publication verifier through the verified executable"
grep -Fq -- '-PowerShellExecutable $env:FINGRIND_PWSH_EXECUTABLE' "${workflow_file}" || die \
    "CI workflow no longer supplies the verified PowerShell executable to the native publication verifier"
grep -Fq 'Verify native Windows publication surface' "${release_workflow_file}" || die \
    "release workflow no longer orchestrates the shared native Windows publication verifier"
grep -Fq '$env:FINGRIND_WORKFLOW_HELPER_ROOT/scripts/verify-windows-publication-surface.ps1' "${release_workflow_file}" || die \
    "release workflow no longer takes its Windows release-control verifier from the explicit helper root"
grep -Fq -- '-File "$env:FINGRIND_WORKFLOW_HELPER_ROOT/scripts/verify-windows-publication-surface.ps1"' "${release_workflow_file}" || die \
    "release workflow no longer launches its Windows publication verifier through the verified executable"
grep -Fq -- '-RepositoryRoot "${{ github.workspace }}"' "${release_workflow_file}" || die \
    "release workflow no longer passes the tagged source checkout explicitly to the Windows verifier"
grep -Fq -- '-WorkflowHelperRoot "$env:FINGRIND_WORKFLOW_HELPER_ROOT"' "${release_workflow_file}" || die \
    "release workflow no longer passes the release-control helper root explicitly to the Windows verifier"
grep -Fq -- '-PowerShellExecutable $env:FINGRIND_PWSH_EXECUTABLE' "${release_workflow_file}" || die \
    "release workflow no longer supplies the verified PowerShell executable to the native publication verifier"
for workflow_surface in "${workflow_file}" "${release_workflow_file}"; do
    grep -Fq 'Provision pinned PowerShell runtime' "${workflow_surface}" || die \
        "Windows workflow no longer provisions the exact repository-owned PowerShell runtime"
    grep -Fq 'provision-powershell-runtime.py' "${workflow_surface}" || die \
        "Windows workflow no longer delegates PowerShell installation to the checksum-pinned provisioner"
    grep -Fq 'GITHUB_PATH' "${workflow_surface}" || die \
        "Windows workflow no longer places the verified PowerShell directory on subsequent-step PATH"
    grep -Fq 'FINGRIND_PWSH_EXECUTABLE' "${workflow_surface}" || die \
        "Windows workflow no longer exports the verified PowerShell executable explicitly"
    grep -Fq -- '--print-version' "${workflow_surface}" || die \
        "Windows workflow no longer derives its expected PowerShell version from canonical metadata"
    grep -Fq '$requiredVersionLines' "${workflow_surface}" || die \
        "Windows workflow no longer requires exactly one canonical PowerShell version result"
    grep -Fq '$actualVersionLines' "${workflow_surface}" || die \
        "Windows workflow no longer requires exactly one provisioned PowerShell version result"
    grep -Fq 'does not match the canonical pin' "${workflow_surface}" || die \
        "Windows workflow no longer rejects a provisioned PowerShell version that differs from the canonical pin"
done
for retired_windows_step in \
    'Verify Windows runner identity matches bundle target' \
    'Run included build-logic tests on Windows' \
    'Prove canonical attestation codec determinism on Windows' \
    'Verify direct-Java SQLite CLI runtime on Windows' \
    'Verify source-checkout SQLite CLI runtime on Windows' \
    'Build self-contained Windows CLI bundle' \
    'Build Windows self-contained CLI bundle' \
    'Smoke test the Windows CLI bundle' \
    'Smoke test the Windows self-contained CLI bundle'; do
    if grep -Fq "${retired_windows_step}" "${workflow_file}" || \
        grep -Fq "${retired_windows_step}" "${release_workflow_file}"; then
        die "Windows verification is split back into retired YAML-owned step: ${retired_windows_step}"
    fi
done
if grep -Fq 'windows-bundle-build.outputs' "${workflow_file}" || \
    grep -Fq 'windows-bundle-build.outputs' "${release_workflow_file}"; then
    die "Windows publication workflows still consume retired bundle-build outputs"
fi
for failure_evidence_job in "${published_bundle_smoke_job}" "${release_bundle_build_job}"; do
    grep -Fq 'Write sanitized Windows failure evidence' <<< "${failure_evidence_job}" || die \
        "Windows publication workflow no longer writes sanitized failure evidence"
    grep -Fq 'Upload sanitized Windows failure evidence' <<< "${failure_evidence_job}" || die \
        "Windows publication workflow no longer uploads sanitized failure evidence"
    grep -Fq 'actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1' <<< "${failure_evidence_job}" || die \
        "Windows publication workflow no longer pins the failure-evidence artifact action"
    grep -Fq 'path: ${{ runner.temp }}\fingrind-windows-failure-evidence\fingrind-windows-failure-evidence.json' <<< "${failure_evidence_job}" || die \
        "Windows publication workflow no longer uploads exactly one allowlisted failure-evidence document"
    grep -Fq 'if-no-files-found: error' <<< "${failure_evidence_job}" || die \
        "Windows publication workflow no longer fails visibly when the failure-evidence document is absent"
    grep -Fq 'retention-days: 7' <<< "${failure_evidence_job}" || die \
        "Windows publication workflow no longer bounds failure-evidence retention"
    grep -Fq -- '-RepositoryRoot "${{ github.workspace }}"' <<< "${failure_evidence_job}" || die \
        "Windows publication workflow no longer passes the target checkout explicitly to failure-evidence collection"
    grep -Fq -- '-TrustedEvidenceRoot $env:FINGRIND_WINDOWS_FAILURE_EVIDENCE_ROOT' <<< "${failure_evidence_job}" || die \
        "Windows publication workflow no longer anchors failure evidence beneath the trusted runner temporary root"
    if [[ "$(grep -Fc "if: failure() && runner.os == 'Windows'" <<< "${failure_evidence_job}")" -ne 2 ]]; then
        die "Windows publication workflow must write and upload failure evidence only after a Windows matrix failure"
    fi
done
grep -Fq '.\scripts\write-windows-failure-evidence.ps1' <<< "${published_bundle_smoke_job}" || die \
    "CI workflow no longer delegates failure-evidence control to the centralized writer"
grep -Fq '$env:FINGRIND_WORKFLOW_HELPER_ROOT/scripts/write-windows-failure-evidence.ps1' <<< "${release_bundle_build_job}" || die \
    "release workflow no longer takes failure-evidence control from the release helper root"
for failure_evidence_job in "${published_bundle_smoke_job}" "${release_bundle_build_job}"; do
    evidence_writer_step="$(printf '%s\n' "${failure_evidence_job}" | awk '
        $0 == "      - name: Write sanitized Windows failure evidence" {
            active = 1
        }
        active {
            if ($0 ~ /^      - name: / && $0 != "      - name: Write sanitized Windows failure evidence") {
                exit
            }
            print
        }
    ')"
    [[ -n "${evidence_writer_step}" ]] || die \
        "Windows publication workflow no longer defines the failure-evidence writer step"
    grep -Fq 'shell: pwsh' <<< "${evidence_writer_step}" || die \
        "Windows failure evidence must run in the runner-provided PowerShell step shell"
    if grep -Fq 'FINGRIND_PWSH_EXECUTABLE' <<< "${evidence_writer_step}"; then
        die "Windows failure evidence executes the mutable pinned build runtime after untrusted build code"
    fi
done
if grep -Fq 'collectionStatus = ' "${workflow_file}" || grep -Fq 'collectionStatus = ' "${release_workflow_file}"; then
    die "Windows publication workflows still duplicate fallback-evidence policy instead of using the centralized writer"
fi
unexpected_runs_on="$(
    grep -n '^    runs-on: ' "${workflow_file}" "${release_workflow_file}" |
        grep -E -v 'runs-on: (ubuntu-24\.04|\$\{\{ matrix\.runner \}\})$' || true
)"
[[ -z "${unexpected_runs_on}" ]] || die \
    "CI or release workflow defines a runner outside the literal audited runner allowlist: ${unexpected_runs_on}"
if grep -Fq 'continue-on-error: true' "${workflow_file}"; then
    die "CI workflow still marks the published bundle smoke surface as observational"
fi
if grep -Fq 'matrix.expectedOs' "${workflow_file}" || grep -Fq 'matrix.expectedArch' "${workflow_file}"; then
    die "CI workflow still depends on retired runner-spelling matrix fields"
fi
if grep -Fq 'windows-bundle-smoke:' "${workflow_file}"; then
    die "CI workflow carries the retired release-blocking Windows bundle-smoke job key"
fi
if ! grep -A12 -F 'gate:' "${workflow_file}" | grep -Fq 'published-bundle-smoke'; then
    die "CI workflow no longer requires the aggregate Gate job to wait for the canonical published bundle smoke matrix"
fi
if grep -A12 -F 'gate:' "${workflow_file}" | grep -Fq 'prepare-published-bundle-smoke-matrix'; then
    die "CI workflow still lets a dynamic matrix-preparation owner affect the aggregate Gate"
fi
if ! grep -A14 -F 'gate:' "${workflow_file}" | grep -Fq 'wrapper-validation'; then
    die "CI workflow no longer requires the aggregate Gate job to wait for wrapper validation"
fi
if ! grep -Fq 'cache-read-only: true' <<< "${published_bundle_smoke_job}"; then
    die "published bundle smoke no longer uses read-only Gradle caching"
fi
if grep -Fq 'Run root quality gates and included build-logic tests on Windows' "${workflow_file}"; then
    die "CI workflow combines Windows root verification and build-logic verification in one non-fail-fast step"
fi
if grep -Fq '.\gradlew.bat -q :cli:run "--args=capabilities --output json"' "${workflow_file}"; then
    die "CI workflow carries the retired ad hoc Windows direct-Java runtime probe"
fi
if grep -Fq '.\scripts\source-checkout-cli.ps1 capabilities --output json' "${workflow_file}"; then
    die "CI workflow carries the retired ad hoc Windows source-checkout runtime probe"
fi
if grep -Fq 'ilammy/msvc-dev-cmd' "${workflow_file}"; then
    die "CI workflow depends on the deprecated third-party msvc-dev-cmd action"
fi

printf 'CI release-surface workflow regression: success\n'
