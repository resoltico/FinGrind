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
readonly check_stage_contract="${repo_root}/scripts/check-stage-contract.sh"
readonly bootstrap_protocol="${repo_root}/docs/GITHUB_BOOTSTRAP_PROTOCOL.md"
readonly tag_governance_protocol="${repo_root}/docs/GITHUB_RELEASE_TAG_GOVERNANCE.md"
readonly release_protocol="${repo_root}/docs/RELEASE_PROTOCOL.md"
readonly dependabot_approval="${repo_root}/docs/DEVELOPER_DEPENDABOT_APPROVAL.md"
readonly developer_release_publication="${repo_root}/docs/DEVELOPER_RELEASE_PUBLICATION.md"
readonly release_publication_verification="${repo_root}/docs/RELEASE_PUBLICATION_VERIFICATION.md"
readonly merge_handoff_verifier="${repo_root}/scripts/verify-release-merge-handoff.sh"
readonly release_candidate_verifier="${repo_root}/scripts/verify-release-candidate-tag.sh"
readonly release_tag_ruleset_contract="${repo_root}/scripts/release_tag_ruleset_contract.py"
readonly release_tag_ruleset_configurator="${repo_root}/scripts/configure-release-tag-rulesets.sh"

[[ -f "${release_check_support}" ]] || die "missing release-check support helper at ${release_check_support}"
[[ -f "${check_stage_contract}" ]] || die \
    "missing release-surface stage contract at ${check_stage_contract}"
[[ -f "${bootstrap_protocol}" ]] || die "missing bootstrap protocol at ${bootstrap_protocol}"
[[ -f "${tag_governance_protocol}" ]] || die "missing tag governance protocol at ${tag_governance_protocol}"
[[ -f "${release_protocol}" ]] || die "missing release protocol at ${release_protocol}"
[[ -f "${dependabot_approval}" ]] || die "missing Dependabot approval policy at ${dependabot_approval}"
[[ -f "${developer_release_publication}" ]] || die \
    "missing developer release-publication reference at ${developer_release_publication}"
[[ -f "${release_publication_verification}" ]] || die \
    "missing release publication verification guide at ${release_publication_verification}"
[[ -f "${merge_handoff_verifier}" ]] || die \
    "missing merge-handoff verifier at ${merge_handoff_verifier}"
[[ -f "${release_candidate_verifier}" ]] || die \
    "missing release-candidate verifier at ${release_candidate_verifier}"
[[ -f "${release_tag_ruleset_contract}" ]] || die \
    "missing release-tag ruleset contract at ${release_tag_ruleset_contract}"
[[ -x "${release_tag_ruleset_configurator}" ]] || die \
    "missing executable release-tag ruleset configurator at ${release_tag_ruleset_configurator}"
grep -Fq 'scripts/test-configure-release-tag-rulesets.sh' "${check_stage_contract}" || die \
    "release-surface stage contract no longer exercises ruleset configuration reconciliation"
grep -Fq 'scripts/test-verify-release-workflow-initiator.sh' "${check_stage_contract}" || die \
    "release-surface stage contract no longer exercises owner-only workflow initiation"

# shellcheck source=/dev/null
source "${release_check_support}"
readonly expected_check_name="$(fingrind_required_ci_check_name)"
readonly expected_contexts_json="$(fingrind_required_ci_check_contexts_json)"
readonly required_ci_job_names_json="$(fingrind_required_ci_job_names_json)"
readonly expected_release_check_timeout_seconds=10800

[[ "$(fingrind_release_check_timeout_seconds)" == "${expected_release_check_timeout_seconds}" ]] || die \
    "release-check default timeout must cover the full release-blocking CI ceiling"
grep -Fq 'fingrind_release_check_default_timeout_seconds=10800' "${release_check_support}" || die \
    "release-check support no longer owns the three-hour release verification timeout"

grep -Fq "\"contexts\": ${expected_contexts_json}" "${bootstrap_protocol}" || die \
    "bootstrap protocol no longer configures branch protection with the canonical Gate context"
grep -Fq '"enforce_admins": false' "${bootstrap_protocol}" || die \
    "bootstrap protocol no longer leaves administrator bypass available for the protected release path"
grep -Fq 'default_permissions=read' "${bootstrap_protocol}" || die \
    "bootstrap protocol no longer sets read-only repository-wide Actions permissions"
grep -Fq 'Publication jobs declare the narrow write' "${bootstrap_protocol}" || die \
    "bootstrap protocol no longer documents job-scoped publication write authority"
grep -Fq 'GITHUB_RELEASE_TAG_GOVERNANCE.md' "${bootstrap_protocol}" || die \
    "bootstrap protocol no longer routes tag governance to its dedicated guide"
grep -Fq 'Authorize FinGrind release tag creation' "${tag_governance_protocol}" || die \
    "tag governance protocol no longer creates the release-tag creation authorization ruleset"
grep -Fq 'Protect FinGrind release tag immutability' "${tag_governance_protocol}" || die \
    "tag governance protocol no longer creates the release-tag immutability ruleset"
grep -Fq './scripts/configure-release-tag-rulesets.sh' "${tag_governance_protocol}" || die \
    "tag governance protocol no longer delegates mutation to the canonical idempotent configurator"
grep -Fq 'only the missing canonical rule' "${tag_governance_protocol}" || die \
    "tag governance protocol no longer documents safe recovery from a partial ruleset configuration"
grep -Fq '## Drift recovery' "${tag_governance_protocol}" || die \
    "tag governance protocol no longer documents the fail-closed drift-recovery procedure"
grep -Fq 'repos/${REPO}/rulesets/<identified-repository-ruleset-id>' "${tag_governance_protocol}" || die \
    "tag governance protocol no longer requires an identified repository ruleset ID for controlled drift repair"
grep -Fq 'repository owner or an administrator with repository-ruleset read and write authority' \
    "${tag_governance_protocol}" || die \
    "tag governance protocol no longer states the authority required to read and create rulesets"
grep -Fq 'A bypass on a combined creation, update, and deletion ruleset' "${tag_governance_protocol}" || die \
    "tag governance protocol no longer explains why release tag authorization and immutability are separated"
if grep -Fq 'default_permissions=write' "${bootstrap_protocol}"; then
    die "bootstrap protocol reintroduced repository-wide Actions write permission"
fi
grep -Fq "required checks remain exactly \`${expected_check_name}\`" "${bootstrap_protocol}" || die \
    "bootstrap protocol no longer documents Gate as the sole required check"
grep -Fq "\`main\` protection requires exactly the aggregate \`${expected_check_name}\` check" \
    "${release_protocol}" || die \
    "release protocol no longer documents Gate as the sole required status check"
grep -Fq './scripts/verify-release-repo-settings.sh' "${release_protocol}" || die \
    "release protocol no longer requires the repository-settings verifier"
grep -Fq 'FINGRIND_RELEASE_TAG_VERIFIER_MODE=pre-tag' "${release_protocol}" || die \
    "release protocol no longer requires executable pre-tag admission before creating an immutable release tag"
grep -Fq 'one owner-authorized `v*` creation ruleset and one no-bypass `v*` update/deletion ruleset' \
    "${release_protocol}" || die \
    "release protocol no longer names the release-tag control-plane preflight"
grep -Fq 'force-pushing, deleting, or recreating a' "${release_protocol}" || die \
    "release protocol no longer forbids release-tag policy workarounds"
grep -Fq 'GITHUB_RELEASE_TAG_GOVERNANCE.md' "${release_publication_verification}" || die \
    "release publication verification no longer routes tag-policy truth to its canonical governance guide"
grep -Fq 'complete effective tag-ruleset inventory is exactly' "${release_protocol}" || die \
    "release protocol no longer includes tag governance in its complete repository-settings summary"
grep -Fq './scripts/verify-release-pr-gate.sh <N>' "${release_protocol}" || die \
    "release protocol no longer requires the PR Gate verifier"
grep -Fq 'The aggregate `Gate` check run appears only after Gradle wrapper validation, `Check`, the published' "${release_protocol}" || die \
    "release protocol no longer documents delayed aggregate Gate materialization"
grep -Fq 'A PR can therefore show `Check` green while `Gate` is absent.' "${release_protocol}" || die \
    "release protocol no longer documents missing-Gate-as-pending semantics"
grep -Fq 'Treat a missing `Gate` as pending,' "${release_protocol}" || die \
    "release protocol no longer documents missing-Gate-as-pending semantics"
grep -Fq 'not as success. The verifier is the canonical owner of that waiting logic.' "${release_protocol}" || die \
    "release protocol no longer documents missing-Gate-as-pending semantics"
grep -Fq "Step 9 must close the superseded PR and delete its branch" "${release_protocol}" || die \
    "release protocol no longer closes superseded release-starting PRs"
grep -Fq "No superseded ordinary PR may remain open after release hygiene." "${release_protocol}" || die \
    "release protocol no longer forbids superseded ordinary PR leftovers"
grep -Fq 'DEVELOPER_DEPENDABOT_APPROVAL.md' "${release_protocol}" || die \
    "release protocol no longer routes Dependabot approval to its dedicated policy"
grep -Fq 'the sole required branch-protection context, and confirms every contract-listed release-blocking' \
    "${dependabot_approval}" || die \
    "Dependabot approval policy no longer distinguishes the canonical Gate context from its contract-listed CI owners"
grep -Fq 'Do not substitute `statusCheckRollup` or' "${dependabot_approval}" || die \
    "Dependabot approval policy no longer forbids Dependabot merge readiness from being inferred from statusCheckRollup"
grep -Fq 'git -C "$PRIMARY_CHECKOUT" diff --binary HEAD > "$RELEASE_PATCH"' \
    "${release_protocol}" || die \
    "release protocol bootstrap patch no longer includes both staged and unstaged tracked payload changes"
grep -Fq 'gh workflow run release.yml --repo "$REPO" --ref main -f release_tag=vX.Y.Z' "${release_protocol}" || die \
    "release protocol no longer dispatches post-tag repair control from main explicitly"
grep -Fq 'gh workflow run release.yml --repo "$REPO" --ref main -f release_tag=vX.Y.Z' "${developer_release_publication}" || die \
    "developer release-publication reference no longer names the explicit main-controlled repair dispatch"
grep -Fq "RELEASE_RUN_NAME='Release vX.Y.Z'" "${release_protocol}" || die \
    "release protocol no longer gives workflow monitoring a deterministic release-target identity"
grep -Fq 'gh api --paginate --slurp' "${release_protocol}" || die \
    "release protocol no longer paginates every release-workflow history page before target matching"
grep -Fq 'actions/workflows/release.yml/runs?per_page=100' "${release_protocol}" || die \
    "release protocol no longer uses the release workflow-runs endpoint for all-page discovery"
grep -Fq 'select(.display_title == $release_run_name)' "${release_protocol}" || die \
    "release protocol no longer selects REST workflow runs by their exact display-title target identity"
if ! grep -Fq 'databaseId: .id,' "${release_protocol}" || \
    ! grep -Fq 'displayTitle: .display_title,' "${release_protocol}" || \
    ! grep -Fq 'headSha: .head_sha,' "${release_protocol}"; then
    die "release protocol no longer normalizes REST workflow-run identity and state fields"
fi
grep -Fq '`push` or `workflow_dispatch`' "${release_protocol}" || die \
    "release protocol no longer validates both supported release-run trigger kinds"
grep -Fq 'a tag-push run has the tag commit as `headSha`, while' "${release_protocol}" || die \
    "release protocol no longer distinguishes tag-push and manual-rerun workflow identities"
grep -Fq 'An initial `[]` is Actions propagation-pending' "${release_protocol}" || die \
    "release protocol no longer treats initial empty workflow discovery as bounded propagation-pending"
grep -Fq 'A matching `queued` record is pending' "${release_protocol}" || die \
    "release protocol no longer distinguishes queued publication work from failure"
grep -Fq 'A matching `in_progress` record is active publication work' "${release_protocol}" || die \
    "release protocol no longer distinguishes active publication work from queued work"
grep -Fq 'is a past failure' "${release_protocol}" || die \
    "release protocol no longer distinguishes completed failure from active or queued publication"
grep -Fq 'tag-triggered run uses `tag-publication` mode' "${release_protocol}" || die \
    "release protocol no longer distinguishes queued tag publication from initial operator admission"
grep -Fq 'Public release admission is stable-only' "${release_protocol}" || die \
    "release protocol no longer documents stable-only public release admission"
grep -Fq 'Determine target release tag' "${release_protocol}" || die \
    "release protocol no longer names the stable-tag enforcement boundary"
grep -Fq 'arbitrary `v*` tags are not release targets' "${developer_release_publication}" || die \
    "developer release publication reference no longer excludes arbitrary v-prefixed release tags"
grep -Fq 'a separate no-bypass ruleset blocks' "${developer_release_publication}" || die \
    "developer release publication reference no longer distinguishes tag creation authorization from immutability"
grep -Fq 'release asset-name inventory is exact' "${release_publication_verification}" || die \
    "release publication verification no longer requires an exact asset-name inventory"
grep -Fq 'duplicate or additional asset name' "${release_publication_verification}" || die \
    "release publication verification no longer rejects duplicate or extra release assets"
grep -Fq 'only repository-owner creation and rejects every update and deletion' \
    "${release_publication_verification}" || die \
    "release publication verification no longer separates tag-policy proof from public asset proof"
grep -Fq 'RELEASE_PUBLICATION_CONTAINER_VERIFICATION.md' "${release_protocol}" || die \
    "release protocol no longer delegates the public-container handoff to its dedicated guide"
if grep -Fq 'TAG_SHA=$(git rev-list -n 1 vX.Y.Z)' "${release_protocol}" || \
    grep -Fq -- '--commit "$TAG_SHA"' "${release_protocol}" || \
    grep -Fq -- '--event=push' "${release_protocol}" || \
    grep -Fq 'gh run list --workflow=release.yml' "${release_protocol}"; then
    die "release protocol still narrows target run discovery to tag-commit push events"
fi
readonly release_monitor_fixture='[{"workflow_runs":[{"id":100,"display_title":"Release v0.62.0","event":"push","head_sha":"tag-commit","status":"queued","conclusion":null,"html_url":"https://example.invalid/runs/100"},{"id":101,"display_title":"Release v0.62.0","event":"push","head_sha":"tag-commit","status":"in_progress","conclusion":null,"html_url":"https://example.invalid/runs/101"},{"id":102,"display_title":"Release v0.62.0","event":"push","head_sha":"tag-commit","status":"completed","conclusion":"success","html_url":"https://example.invalid/runs/102"}]},{"workflow_runs":[{"id":103,"display_title":"Release v0.62.0","event":"workflow_dispatch","head_sha":"main-control-commit","status":"completed","conclusion":"failure","html_url":"https://example.invalid/runs/103"},{"id":104,"display_title":"Release v0.62.1","event":"push","head_sha":"other-tag-commit","status":"completed","conclusion":"success","html_url":"https://example.invalid/runs/104"}]}]'
readonly release_monitor_empty_fixture='[{"workflow_runs":[]}]'
release_monitor_empty_matches="$(
    printf '%s\n' "${release_monitor_empty_fixture}" | jq --arg release_run_name 'Release v0.62.0' \
        '[.[].workflow_runs[] | select(.display_title == $release_run_name)]'
)"
readonly release_monitor_empty_matches
[[ "${release_monitor_empty_matches}" == '[]' ]] || die \
    "release workflow-run discovery fixture no longer preserves an initial empty propagation result"
release_monitor_matches="$(
    printf '%s\n' "${release_monitor_fixture}" | jq --arg release_run_name 'Release v0.62.0' \
        '[.[].workflow_runs[]
          | select(.display_title == $release_run_name)
          | {
              databaseId: .id,
              displayTitle: .display_title,
              event,
              headSha: .head_sha,
              status,
              conclusion,
              url: .html_url
            }
        ]'
)"
readonly release_monitor_matches
if ! jq -e '
    length == 4 and
    .[0] == {
        databaseId: 100,
        displayTitle: "Release v0.62.0",
        event: "push",
        headSha: "tag-commit",
        status: "queued",
        conclusion: null,
        url: "https://example.invalid/runs/100"
    } and
    .[1] == {
        databaseId: 101,
        displayTitle: "Release v0.62.0",
        event: "push",
        headSha: "tag-commit",
        status: "in_progress",
        conclusion: null,
        url: "https://example.invalid/runs/101"
    } and
    .[2] == {
        databaseId: 102,
        displayTitle: "Release v0.62.0",
        event: "push",
        headSha: "tag-commit",
        status: "completed",
        conclusion: "success",
        url: "https://example.invalid/runs/102"
    } and
    .[3] == {
        databaseId: 103,
        displayTitle: "Release v0.62.0",
        event: "workflow_dispatch",
        headSha: "main-control-commit",
        status: "completed",
        conclusion: "failure",
        url: "https://example.invalid/runs/103"
    }
' <<< "${release_monitor_matches}" >/dev/null; then
    die "release workflow-run monitor no longer preserves queued, active, successful, and past-failure target records"
fi
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
