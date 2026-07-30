#!/usr/bin/env bash
# Reproduce and guard the release-candidate tag verifier so pre-tag admission blocks an invalid
# irreversible reference, initial publication requires the tagged commit to equal the current
# default-branch head, and immutable-tag reruns can verify a historical tagged commit after
# release-control repairs land on main.

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
readonly verifier="${repo_root}/scripts/verify-release-candidate-tag.sh"
readonly release_check_support="${repo_root}/scripts/release-check-support.sh"
readonly release_tag_support="${repo_root}/scripts/release-tag-support.sh"
readonly stage_contract_script="${repo_root}/scripts/check-stage-contract.sh"
readonly release_protocol="${repo_root}/docs/RELEASE_PROTOCOL.md"
readonly release_workflow="${repo_root}/.github/workflows/release.yml"

[[ -x "${verifier}" ]] || die "missing executable release-candidate verifier at ${verifier}"
[[ -f "${release_check_support}" ]] || die "missing release-check support helper at ${release_check_support}"
[[ -f "${release_tag_support}" ]] || die "missing release-tag support helper at ${release_tag_support}"
[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
[[ -f "${release_protocol}" ]] || die "missing release protocol at ${release_protocol}"
[[ -f "${release_workflow}" ]] || die "missing release workflow at ${release_workflow}"

# shellcheck source=/dev/null
source "${release_check_support}"
readonly expected_check_name="$(fingrind_required_ci_check_name)"
readonly expected_workflow_name="$(fingrind_required_ci_workflow_name)"
readonly expected_workflow_path="$(fingrind_required_ci_workflow_path)"
readonly required_ci_jobs_json="$(
    EXPECTED_GATE_JOB_NAME="${expected_check_name}" \
        FINGRIND_REQUIRED_CI_JOB_NAMES_JSON="$(fingrind_required_ci_job_names_json)" \
        python3 - <<'PY'
import json
import os

print(
    json.dumps(
        [os.environ["EXPECTED_GATE_JOB_NAME"], *json.loads(os.environ["FINGRIND_REQUIRED_CI_JOB_NAMES_JSON"])]
    )
)
PY
)"

grep -Fq 'scripts/test-verify-release-candidate-tag.sh' "${stage_contract_script}" || die \
    "check stage contract no longer exercises the release-candidate verifier regression"
grep -Fq './scripts/verify-release-candidate-tag.sh' "${release_protocol}" || die \
    "release protocol no longer requires the release-candidate verifier"
grep -Fq 'initial release tag' "${verifier}" || die \
    "release-candidate verifier no longer enforces the initial-publication head identity contract"
grep -Fq 'Pre-tag admission proves' "${verifier}" || die \
    "release-candidate verifier no longer documents the irreversible pre-tag admission contract"
grep -Fq 'pre-tag|initial|tag-publication|rerun)' "${verifier}" || die \
    "release-candidate verifier no longer exposes complete pre-tag, initial, queued, and rerun admission modes"
grep -Fq 'FINGRIND_RELEASE_TAG_VERIFIER_MODE' "${verifier}" || die \
    "release-candidate verifier no longer exposes the explicit pre-tag, initial, tag-publication, and rerun mode contract"
grep -Fq 'release_tag_is_stable "${tag_name}"' "${verifier}" || die \
    "release-candidate verifier no longer rejects prerelease and malformed publication tags before remote admission"
grep -Fq 'release-tag-support.sh' "${verifier}" || die \
    "release-candidate verifier no longer uses the canonical stable release-tag owner"
grep -Fq '"+refs/heads/${default_branch}:${default_branch_ref}"' "${verifier}" || die \
    "release-candidate verifier no longer refreshes origin/default-branch state before publication admission"
grep -Fq 'workflow-dispatch rerun automatically switches the verifier into `rerun` mode' "${release_protocol}" || die \
    "release protocol no longer documents the release-candidate verifier rerun mode"
grep -Fq 'Later unreleased repair commits may still become' "${release_protocol}" || die \
    "release protocol no longer documents the same-version pre-tag repair release invariant"
grep -Fq 'FINGRIND_RELEASE_TAG_VERIFIER_MODE=pre-tag' "${release_protocol}" || die \
    "release protocol no longer requires pre-tag candidate admission before creating an immutable tag"
grep -Fq 'FINGRIND_RELEASE_TAG_VERIFIER_MODE: ${{ github.event_name == '\''workflow_dispatch'\'' && '\''rerun'\'' || '\''tag-publication'\'' }}' "${release_workflow}" || die \
    "release workflow no longer distinguishes queued tag-publication admission from an operator initial-head check"
grep -Fq 'release-check-support.sh' "${verifier}" || die \
    "release-candidate verifier no longer sources the canonical release-check owner"
grep -Fq "${expected_check_name}" "${release_protocol}" || die \
    "release protocol no longer documents the canonical Gate release check"
if grep -Fq 'gh workflow run container.yml' "${release_protocol}"; then
    die "release protocol documents rerunning a retired standalone container workflow"
fi
if grep -Fq 'Contributor devcontainer' "${verifier}"; then
    die "release-candidate verifier reintroduced the obsolete contributor-devcontainer release check"
fi

fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-test-verify-release-candidate-tag.XXXXXX")"
cleanup() {
    rm -rf "${fixture_root}"
}
trap cleanup EXIT

readonly origin_repo="${fixture_root}/origin.git"
readonly seed_repo="${fixture_root}/seed"
readonly release_checkout="${fixture_root}/release-checkout"

git -c init.defaultBranch=main init --bare "${origin_repo}" >/dev/null
git init -b main "${seed_repo}" >/dev/null
git -C "${seed_repo}" config user.name "FinGrind Test"
git -C "${seed_repo}" config user.email "fingrind-test@example.invalid"
git -C "${seed_repo}" remote add origin "${origin_repo}"
mkdir -p "${seed_repo}/docs"
cat > "${seed_repo}/gradle.properties" <<'EOF'
version=9.9.8
EOF
cat > "${seed_repo}/docs/placeholder.md" <<'EOF'
placeholder
EOF
git -C "${seed_repo}" add gradle.properties docs/placeholder.md
git -C "${seed_repo}" commit -m "seed baseline" >/dev/null

cat > "${seed_repo}/gradle.properties" <<'EOF'
version=9.9.9
EOF
git -C "${seed_repo}" add gradle.properties
git -C "${seed_repo}" commit -m "seed release candidate" >/dev/null
readonly release_commit_sha="$(git -C "${seed_repo}" rev-parse HEAD)"
git -C "${seed_repo}" push -u origin main >/dev/null

git clone "${origin_repo}" "${release_checkout}" >/dev/null
git -C "${release_checkout}" checkout --detach "${release_commit_sha}" >/dev/null

mkdir -p "${fixture_root}/bin"
cat > "${fixture_root}/bin/gh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

repo="${FAKE_GH_REPOSITORY:-resoltico/FinGrind}"
default_branch="${FAKE_GH_DEFAULT_BRANCH:-main}"
tag_name="${FAKE_GH_TAG_NAME:-v9.9.9}"
tag_sha="${FAKE_GH_TAG_SHA:?}"
workflow_run_id="${FAKE_GH_WORKFLOW_RUN_ID:-7001}"
workflow_name="${FAKE_GH_WORKFLOW_NAME:-CI}"
workflow_path="${FAKE_GH_WORKFLOW_PATH:-.github/workflows/ci.yml}"
workflow_status="${FAKE_GH_WORKFLOW_STATUS:-completed}"
workflow_conclusion="${FAKE_GH_WORKFLOW_CONCLUSION:-success}"
required_jobs_json="${FAKE_GH_REQUIRED_JOBS_JSON:-[]}"

if [[ -n "${FAKE_GH_INVOCATION_LOG:-}" ]]; then
    printf '%s\n' "$*" >> "${FAKE_GH_INVOCATION_LOG}"
fi

if [[ "${1:-}" == "repo" && "${2:-}" == "view" ]]; then
    [[ "${3:-}" == "--json" ]] || exit 1
    if [[ "${4:-}" == "nameWithOwner" && "${5:-}" == "--jq" && "${6:-}" == ".nameWithOwner" ]]; then
        printf '%s\n' "${repo}"
        exit 0
    fi
    if [[ "${4:-}" == "defaultBranchRef" && "${5:-}" == "--jq" && "${6:-}" == ".defaultBranchRef.name" ]]; then
        printf '%s\n' "${default_branch}"
        exit 0
    fi
    exit 1
fi

if [[ "${1:-}" == "api" && "${2:-}" == "/repos/${repo}/git/ref/tags/${tag_name}" ]]; then
    [[ "${3:-}" == "--jq" ]] || exit 1
    case "${4:-}" in
        .object.type)
            printf '%s\n' "commit"
            ;;
        .object.sha)
            printf '%s\n' "${tag_sha}"
            ;;
        *)
            exit 1
            ;;
    esac
    exit 0
fi

if [[ "${1:-}" == "api" && "${2:-}" == "/repos/${repo}/actions/runs?head_sha=${tag_sha}&per_page=100" ]]; then
    python3 - <<'PY'
from __future__ import annotations

import json
import os

print(
    json.dumps(
        {
            "workflow_runs": [
                {
                    "id": int(os.environ["FAKE_GH_WORKFLOW_RUN_ID"]),
                    "name": os.environ["FAKE_GH_WORKFLOW_NAME"],
                    "path": os.environ["FAKE_GH_WORKFLOW_PATH"],
                    "status": os.environ["FAKE_GH_WORKFLOW_STATUS"],
                    "conclusion": os.environ["FAKE_GH_WORKFLOW_CONCLUSION"],
                    "event": "push",
                    "html_url": "https://example.invalid/actions/runs/7001",
                    "run_number": 88,
                    "run_attempt": 1,
                    "created_at": "2026-06-01T00:00:00Z",
                }
            ]
        }
    )
)
PY
    exit 0
fi

if [[ "${1:-}" == "api" && "${2:-}" == "/repos/${repo}/actions/runs/${workflow_run_id}/jobs?per_page=100" ]]; then
    [[ "${3:-}" == "--paginate" ]] || exit 1
    FAKE_GH_REQUIRED_JOBS_JSON="${required_jobs_json}" python3 - <<'PY'
from __future__ import annotations

import json
import os

print(
    json.dumps(
        {
            "jobs": [
                {"name": name, "status": "completed", "conclusion": "success"}
                for name in json.loads(os.environ["FAKE_GH_REQUIRED_JOBS_JSON"])
            ]
        }
    )
)
PY
    exit 0
fi

exit 1
EOF
chmod +x "${fixture_root}/bin/gh"

run_verifier() {
    local tag_sha="$1"
    shift
    (
        cd "${release_checkout}"
        PATH="${fixture_root}/bin:${PATH}" \
            FAKE_GH_REPOSITORY='resoltico/FinGrind' \
            FAKE_GH_DEFAULT_BRANCH='main' \
            FAKE_GH_TAG_NAME='v9.9.9' \
            FAKE_GH_TAG_SHA="${tag_sha}" \
            FAKE_GH_WORKFLOW_RUN_ID='7001' \
            FAKE_GH_WORKFLOW_NAME="${expected_workflow_name}" \
            FAKE_GH_WORKFLOW_PATH="${expected_workflow_path}" \
            FAKE_GH_WORKFLOW_STATUS='completed' \
            FAKE_GH_WORKFLOW_CONCLUSION='success' \
            FAKE_GH_REQUIRED_JOBS_JSON="${required_ci_jobs_json}" \
            FINGRIND_RELEASE_CHECK_POLL_INTERVAL_SECONDS='0' \
            FINGRIND_RELEASE_CHECK_TIMEOUT_SECONDS='0' \
            "$@"
    )
}

pre_tag_log="${fixture_root}/pre-tag-gh.log"
: > "${pre_tag_log}"
FAKE_GH_INVOCATION_LOG="${pre_tag_log}" \
    run_verifier "${release_commit_sha}" env FINGRIND_RELEASE_TAG_VERIFIER_MODE='pre-tag' \
        bash "${verifier}" v9.9.9 >/dev/null
if grep -Fq '/git/ref/tags/' "${pre_tag_log}"; then
    die "pre-tag release admission queried a remote tag that must not exist yet"
fi

git -C "${release_checkout}" tag v9.9.9 "${release_commit_sha}"
git -C "${release_checkout}" push origin refs/tags/v9.9.9 >/dev/null

git -C "${release_checkout}" tag -d v9.9.9 >/dev/null
set +e
pre_tag_remote_existing_output="$(
    run_verifier "${release_commit_sha}" env FINGRIND_RELEASE_TAG_VERIFIER_MODE='pre-tag' \
        bash "${verifier}" v9.9.9 2>&1
)"
pre_tag_remote_existing_status=$?
set -e
if [[ ${pre_tag_remote_existing_status} -eq 0 ]]; then
    die "pre-tag release admission accepted an already-created remote tag"
fi
printf '%s\n' "${pre_tag_remote_existing_output}" | grep -Fq \
    'pre-tag release candidate v9.9.9 already exists on origin' || die \
    "pre-tag release admission did not explain the pre-existing remote reference"
git -C "${release_checkout}" tag v9.9.9 "${release_commit_sha}"

# Keep the clone's remote-tracking branch stale, then advance the actual remote. The verifier must
# refresh origin/main rather than accepting the old local remote-tracking SHA.
printf 'release fix marker\n' >> "${seed_repo}/docs/placeholder.md"
git -C "${seed_repo}" add docs/placeholder.md
git -C "${seed_repo}" commit -m "post-release-control repair" >/dev/null
readonly remote_head_sha="$(git -C "${seed_repo}" rev-parse HEAD)"
git -C "${seed_repo}" push origin main >/dev/null
readonly stale_remote_tracking_sha="$(git -C "${release_checkout}" rev-parse refs/remotes/origin/main)"
[[ "${stale_remote_tracking_sha}" == "${release_commit_sha}" ]] || die \
    "candidate verifier fixture did not retain a stale origin/main reference before admission"

git -C "${release_checkout}" tag -d v9.9.9 >/dev/null
git -C "${release_checkout}" push origin --delete refs/tags/v9.9.9 >/dev/null
set +e
pre_tag_head_mismatch_output="$(
    run_verifier "${release_commit_sha}" env FINGRIND_RELEASE_TAG_VERIFIER_MODE='pre-tag' \
        bash "${verifier}" v9.9.9 2>&1
)"
pre_tag_head_mismatch_status=$?
set -e
if [[ ${pre_tag_head_mismatch_status} -eq 0 ]]; then
    die "pre-tag release admission accepted a stale default-branch head"
fi
printf '%s\n' "${pre_tag_head_mismatch_output}" | grep -Fq \
    "pre-tag release candidate v9.9.9 checked-out HEAD ${release_commit_sha} does not match origin/main head ${remote_head_sha}" || die \
    "pre-tag release admission did not explain the default-branch-head mismatch"
git -C "${release_checkout}" tag v9.9.9 "${release_commit_sha}"
git -C "${release_checkout}" push origin refs/tags/v9.9.9 >/dev/null

set +e
pre_tag_existing_output="$(
    run_verifier "${release_commit_sha}" env FINGRIND_RELEASE_TAG_VERIFIER_MODE='pre-tag' \
        bash "${verifier}" v9.9.9 2>&1
)"
pre_tag_existing_status=$?
set -e
if [[ ${pre_tag_existing_status} -eq 0 ]]; then
    die "pre-tag release admission accepted an already-created local tag"
fi
printf '%s\n' "${pre_tag_existing_output}" | grep -Fq \
    'pre-tag release candidate v9.9.9 already exists locally' || die \
    "pre-tag release admission did not explain the pre-existing local reference"

invalid_tag_log="${fixture_root}/invalid-tag-gh.log"
: > "${invalid_tag_log}"
set +e
invalid_tag_output="$(
    FAKE_GH_INVOCATION_LOG="${invalid_tag_log}" \
        run_verifier "${release_commit_sha}" bash "${verifier}" v9.9.9-rc.1 2>&1
)"
invalid_tag_status=$?
set -e
if [[ ${invalid_tag_status} -eq 0 ]]; then
    die "release-candidate verifier accepted a prerelease publication tag"
fi
printf '%s\n' "${invalid_tag_output}" | grep -Fq 'release tag must match stable vX.Y.Z' || die \
    "release-candidate verifier did not report stable-only tag rejection"
[[ ! -s "${invalid_tag_log}" ]] || die \
    "release-candidate verifier contacted GitHub before rejecting an invalid publication tag"

set +e
initial_failure_output="$(
    run_verifier "${release_commit_sha}" bash "${verifier}" v9.9.9 2>&1
)"
initial_failure_status=$?
set -e

if [[ ${initial_failure_status} -eq 0 ]]; then
    die "release-candidate verifier accepted an initial tag whose commit did not equal origin/main"
fi
printf '%s\n' "${initial_failure_output}" | grep -Fq \
    "initial release tag v9.9.9 commit ${release_commit_sha} does not match origin/main head ${remote_head_sha}" || die \
    "release-candidate verifier did not report the initial-publication head mismatch"

run_verifier "${release_commit_sha}" env FINGRIND_RELEASE_TAG_VERIFIER_MODE='rerun' bash "${verifier}" v9.9.9 >/dev/null
run_verifier "${release_commit_sha}" env FINGRIND_RELEASE_TAG_VERIFIER_MODE='tag-publication' bash "${verifier}" v9.9.9 >/dev/null
run_verifier "${release_commit_sha}" env GITHUB_EVENT_NAME='workflow_dispatch' bash "${verifier}" v9.9.9 >/dev/null

git -C "${release_checkout}" checkout --detach "${remote_head_sha}" >/dev/null
git -C "${release_checkout}" tag -d v9.9.9 >/dev/null
git -C "${release_checkout}" tag v9.9.9 "${remote_head_sha}"
git -C "${release_checkout}" push --force origin refs/tags/v9.9.9 >/dev/null

run_verifier "${remote_head_sha}" bash "${verifier}" v9.9.9 >/dev/null

printf 'verify-release-candidate-tag regression: success\n'
