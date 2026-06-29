#!/usr/bin/env bash
# Keep the canonical Gate release-check contract synchronized across support code and docs.

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
readonly release_check_support="${repo_root}/scripts/release-check-support.sh"
readonly bootstrap_protocol="${repo_root}/docs/GITHUB_BOOTSTRAP_PROTOCOL.md"
readonly release_protocol="${repo_root}/docs/RELEASE_PROTOCOL.md"
readonly merge_handoff_verifier="${repo_root}/scripts/verify-release-merge-handoff.sh"
readonly release_candidate_verifier="${repo_root}/scripts/verify-release-candidate-tag.sh"

[[ -f "${release_check_support}" ]] || die "missing release-check support helper at ${release_check_support}"
[[ -f "${bootstrap_protocol}" ]] || die "missing bootstrap protocol at ${bootstrap_protocol}"
[[ -f "${release_protocol}" ]] || die "missing release protocol at ${release_protocol}"
[[ -f "${merge_handoff_verifier}" ]] || die \
    "missing merge-handoff verifier at ${merge_handoff_verifier}"
[[ -f "${release_candidate_verifier}" ]] || die \
    "missing release-candidate verifier at ${release_candidate_verifier}"

# shellcheck source=/dev/null
source "${release_check_support}"
readonly expected_check_name="$(fingrind_required_ci_check_name)"
readonly expected_contexts_json="$(fingrind_required_ci_check_contexts_json)"
readonly required_ci_job_names_json="$(fingrind_required_ci_job_names_json)"

grep -Fq "\"contexts\": ${expected_contexts_json}" "${bootstrap_protocol}" || die \
    "bootstrap protocol no longer configures branch protection with the canonical Gate context"
grep -Fq '"enforce_admins": false' "${bootstrap_protocol}" || die \
    "bootstrap protocol no longer leaves administrator bypass available for the protected release path"
grep -Fq "required checks remain exactly \`${expected_check_name}\`" "${bootstrap_protocol}" || die \
    "bootstrap protocol no longer documents Gate as the sole required check"
grep -Fq "\`main\` protection requires exactly the aggregate \`${expected_check_name}\` check" \
    "${release_protocol}" || die \
    "release protocol no longer documents Gate as the sole required status check"
grep -Fq './scripts/verify-release-repo-settings.sh' "${release_protocol}" || die \
    "release protocol no longer requires the repository-settings verifier"
grep -Fq './scripts/verify-release-pr-gate.sh <N>' "${release_protocol}" || die \
    "release protocol no longer requires the PR Gate verifier"
grep -Fq 'The aggregate `Gate` check run appears only after `Check`, the published bundle-smoke matrix, and' "${release_protocol}" || die \
    "release protocol no longer documents delayed aggregate Gate materialization"
grep -Fq 'therefore show `Check` green while `Gate` is absent. Treat a missing `Gate` as pending, not as' "${release_protocol}" || die \
    "release protocol no longer documents missing-Gate-as-pending semantics"
grep -Fq 'success. The verifier is the canonical owner of that waiting logic.' "${release_protocol}" || die \
    "release protocol no longer documents missing-Gate-as-pending semantics"
grep -Fq "Step 10 must close the superseded PR and delete its branch" "${release_protocol}" || die \
    "release protocol no longer closes superseded release-starting PRs"
grep -Fq "No superseded ordinary PR may remain open after release hygiene." "${release_protocol}" || die \
    "release protocol no longer forbids superseded ordinary PR leftovers"
if printf '%s' "${required_ci_job_names_json}" | grep -Fq 'Windows non-public bundle smoke'; then
    die "release-publication contract still carries the retired observational Windows smoke lane"
fi
if grep -Fq 'Check`, `Windows bundle smoke`, and `Docker smoke`' "${bootstrap_protocol}"; then
    die "bootstrap protocol reintroduced the obsolete three-check branch-protection contract"
fi
if grep -Fq 'Check`, `Windows bundle smoke`, and `Docker smoke`' "${release_protocol}"; then
    die "release protocol reintroduced the obsolete Windows-and-Docker release-blocking contract"
fi
if grep -Fq 'admin enforcement' "${release_protocol}"; then
    die "release protocol still documents the obsolete admin-enforcement merge deadlock"
fi
if grep -Fq 'Contributor devcontainer' "${release_candidate_verifier}"; then
    die "release-candidate verifier reintroduced the obsolete contributor-devcontainer check"
fi
if grep -Fq 'Contributor devcontainer' "${merge_handoff_verifier}"; then
    die "merge-handoff verifier reintroduced the obsolete contributor-devcontainer check"
fi

printf 'release check contract regression: success\n'
