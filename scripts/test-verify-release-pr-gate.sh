#!/usr/bin/env bash
# Reproduce and guard the release-PR Gate verifier so a green Check job cannot be mistaken for a
# complete aggregate Gate on release PRs, while observational jobs cannot delay a green Gate.

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
readonly verifier="${repo_root}/scripts/verify-release-pr-gate.sh"
readonly verification_support="${repo_root}/scripts/release-check-verification-support.sh"
readonly release_check_support="${repo_root}/scripts/release-check-support.sh"
readonly stage_contract_script="${repo_root}/scripts/check-stage-contract.sh"
readonly release_protocol="${repo_root}/docs/RELEASE_PROTOCOL.md"
readonly release_publication_contract_reader="${repo_root}/scripts/read-release-publication-contract.py"

[[ -x "${verifier}" ]] || die "missing executable PR Gate verifier at ${verifier}"
[[ -f "${verification_support}" ]] || die "missing verification helper at ${verification_support}"
[[ -f "${release_check_support}" ]] || die "missing release-check support helper at ${release_check_support}"
[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
[[ -f "${release_protocol}" ]] || die "missing release protocol at ${release_protocol}"
[[ -f "${release_publication_contract_reader}" ]] || die \
    "missing release-publication contract reader at ${release_publication_contract_reader}"

grep -Fq 'scripts/test-verify-release-pr-gate.sh' "${stage_contract_script}" || die \
    "check stage contract no longer exercises the PR Gate verifier regression"
grep -Fq './scripts/verify-release-pr-gate.sh <N>' "${release_protocol}" || die \
    "release protocol no longer requires the PR Gate verifier"
grep -Fq 'FINGRIND_RELEASE_CHECK_TIMEOUT_SECONDS=3000 ./scripts/verify-release-pr-gate.sh <N>' "${release_protocol}" || die \
    "release protocol no longer documents the PR Gate verifier timeout override"
grep -Fq 'The aggregate `Gate` check run appears only after `Check`, the published Linux bundle-smoke' "${release_protocol}" || die \
    "release protocol no longer documents the delayed Gate materialization contract"
grep -Fq 'matrix, and the devcontainer gate pair have finished or been skipped in workflow `CI`.' "${release_protocol}" || die \
    "release protocol no longer documents the delayed Gate materialization contract"
grep -Fq 'therefore show `Check` green while `Gate` is absent. Treat a missing `Gate` as pending, not as' "${release_protocol}" || die \
    "release protocol no longer documents missing-Gate-as-pending semantics"
grep -Fq 'success. The verifier is the canonical owner of that waiting logic.' "${release_protocol}" || die \
    "release protocol no longer documents missing-Gate-as-pending semantics"
grep -Fq 'Do not wait for the observational Windows lane once `Gate` is green on the release PR head commit.' "${release_protocol}" || die \
    "release protocol no longer documents Gate-first PR completion semantics"
grep -Fq 'release-check-support.sh' "${verifier}" || die \
    "PR Gate verifier no longer sources the canonical release-check owner"
grep -Fq 'release-check-verification-support.sh' "${verifier}" || die \
    "PR Gate verifier no longer shares the canonical check-run polling helper"

readonly timeout_default="$(
    sed -n 's/^readonly timeout_seconds="${FINGRIND_RELEASE_CHECK_TIMEOUT_SECONDS:-\([0-9][0-9]*\)}"$/\1/p' \
        "${verifier}"
)"
[[ -n "${timeout_default}" ]] || die "failed to read PR Gate verifier default timeout"
(( timeout_default >= 2400 )) || die \
    "PR Gate verifier default timeout regressed below 2400 seconds (${timeout_default})"

fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-test-verify-release-pr-gate.XXXXXX")"
cleanup() {
    rm -rf "${fixture_root}"
}
trap cleanup EXIT

mkdir -p "${fixture_root}/bin" "${fixture_root}/state"

readonly required_ci_jobs_json="$(
    python3 "${release_publication_contract_reader}" | jq -c \
        '[.requiredCiGateJobName] + .requiredCiJobNames'
)"
readonly required_ci_workflow_name="$(
    python3 "${release_publication_contract_reader}" | jq -r '.requiredCiWorkflowName'
)"
readonly required_ci_workflow_path="$(
    python3 "${release_publication_contract_reader}" | jq -r '.requiredCiWorkflowPath'
)"

cat > "${fixture_root}/bin/gh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

repo="${FAKE_GH_REPOSITORY:-resoltico/FinGrind}"
pr_number="${FAKE_GH_PR_NUMBER:-52}"
pr_state="${FAKE_GH_PR_STATE:-OPEN}"
pr_head_ref_name="${FAKE_GH_HEAD_REF_NAME:-release/0.32.0}"
pr_head_ref_oid="${FAKE_GH_HEAD_REF_OID:-99e1257ea944d7b7c10acb3b14cbede94cc93c11}"
pr_url="${FAKE_GH_PR_URL:-https://github.com/resoltico/FinGrind/pull/52}"
check_mode="${FAKE_GH_CHECK_MODE:-pending-then-success}"
state_dir="${FAKE_GH_STATE_DIR:?}"
required_ci_jobs_json="${FAKE_GH_REQUIRED_CI_JOB_NAMES_JSON:-[]}"
required_ci_workflow_name="${FAKE_GH_REQUIRED_CI_WORKFLOW_NAME:-CI}"
required_ci_workflow_path="${FAKE_GH_REQUIRED_CI_WORKFLOW_PATH:-.github/workflows/ci.yml}"
workflow_run_id="${FAKE_GH_WORKFLOW_RUN_ID:-7001}"
workflow_status="${FAKE_GH_WORKFLOW_STATUS:-completed}"
workflow_conclusion="${FAKE_GH_WORKFLOW_CONCLUSION:-success}"

if [[ "${1:-}" == "repo" && "${2:-}" == "view" ]]; then
    [[ "${3:-}" == "--json" && "${4:-}" == "nameWithOwner" && "${5:-}" == "--jq" && "${6:-}" == ".nameWithOwner" ]] || exit 1
    printf '%s\n' "${repo}"
    exit 0
fi

if [[ "${1:-}" == "pr" && "${2:-}" == "view" ]]; then
    [[ "${3:-}" == "${pr_number}" ]] || exit 1
    [[ "${4:-}" == "--repo" && "${5:-}" == "${repo}" && "${6:-}" == "--json" ]] || exit 1
    printf '{"number":%s,"state":"%s","headRefName":"%s","headRefOid":"%s","url":"%s"}\n' \
        "${pr_number}" \
        "${pr_state}" \
        "${pr_head_ref_name}" \
        "${pr_head_ref_oid}" \
        "${pr_url}"
    exit 0
fi

if [[ "${1:-}" == "api" && "${2:-}" == "/repos/${repo}/actions/runs?head_sha=${pr_head_ref_oid}&per_page=100" ]]; then
    python3 - <<'PY'
from __future__ import annotations

import json
import os

print(
    json.dumps(
        {
            "workflow_runs": [
                {
                    "id": int(os.environ.get("FAKE_GH_WORKFLOW_RUN_ID", "7001")),
                    "name": os.environ.get("FAKE_GH_REQUIRED_CI_WORKFLOW_NAME", "CI"),
                    "path": os.environ.get("FAKE_GH_REQUIRED_CI_WORKFLOW_PATH", ".github/workflows/ci.yml"),
                    "status": os.environ.get("FAKE_GH_WORKFLOW_STATUS", "completed"),
                    "conclusion": None
                    if os.environ.get("FAKE_GH_WORKFLOW_CONCLUSION", "success") == "null"
                    else os.environ.get("FAKE_GH_WORKFLOW_CONCLUSION", "success"),
                    "event": "pull_request",
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
    counter_file="${state_dir}/check-runs-count"
    count=0
    if [[ -f "${counter_file}" ]]; then
        count="$(cat "${counter_file}")"
    fi
    count="$((count + 1))"
    printf '%s' "${count}" > "${counter_file}"

    FAKE_GH_REQUIRED_CI_JOB_NAMES_JSON="${required_ci_jobs_json}" \
        FAKE_GH_CHECK_MODE="${check_mode}" \
        FAKE_GH_JOB_CALL_COUNT="${count}" \
        python3 - <<'PY'
from __future__ import annotations

import json
import os

jobs = [
    {"name": name, "status": "completed", "conclusion": "success"}
    for name in json.loads(os.environ["FAKE_GH_REQUIRED_CI_JOB_NAMES_JSON"])
]
mode = os.environ["FAKE_GH_CHECK_MODE"]
count = int(os.environ["FAKE_GH_JOB_CALL_COUNT"])

if mode == "pending-then-success" and count == 1:
    for job in jobs:
        if job["name"] == "Gate":
            job["status"] = "in_progress"
            job["conclusion"] = None
            break
elif mode == "gate-failure":
    for job in jobs:
        if job["name"] == "Gate":
            job["conclusion"] = "failure"
            break
elif mode == "gate-success-observational-pending":
    jobs.append(
        {
            "name": "Windows non-public bundle smoke",
            "status": "in_progress",
            "conclusion": None,
        }
    )
elif mode != "pending-then-success":
    raise SystemExit(f"unsupported check mode: {mode}")

print(json.dumps({"jobs": jobs}))
PY
    exit 0
fi

exit 1
EOF
chmod +x "${fixture_root}/bin/gh"

PATH="${fixture_root}/bin:${PATH}" \
    FAKE_GH_STATE_DIR="${fixture_root}/state" \
    FAKE_GH_REQUIRED_CI_JOB_NAMES_JSON="${required_ci_jobs_json}" \
    FAKE_GH_REQUIRED_CI_WORKFLOW_NAME="${required_ci_workflow_name}" \
    FAKE_GH_REQUIRED_CI_WORKFLOW_PATH="${required_ci_workflow_path}" \
    bash "${verifier}" 52 >/dev/null

check_runs_count="$(cat "${fixture_root}/state/check-runs-count")"
(( check_runs_count >= 2 )) || die \
    "PR Gate verifier accepted a missing Gate instead of waiting for the aggregate check run"

rm -f "${fixture_root}/state/check-runs-count"

PATH="${fixture_root}/bin:${PATH}" \
    FAKE_GH_STATE_DIR="${fixture_root}/state" \
    FAKE_GH_CHECK_MODE='gate-success-observational-pending' \
    FAKE_GH_WORKFLOW_STATUS='in_progress' \
    FAKE_GH_WORKFLOW_CONCLUSION='null' \
    FAKE_GH_REQUIRED_CI_JOB_NAMES_JSON="${required_ci_jobs_json}" \
    FAKE_GH_REQUIRED_CI_WORKFLOW_NAME="${required_ci_workflow_name}" \
    FAKE_GH_REQUIRED_CI_WORKFLOW_PATH="${required_ci_workflow_path}" \
    bash "${verifier}" 52 >/dev/null

check_runs_count="$(cat "${fixture_root}/state/check-runs-count")"
(( check_runs_count == 1 )) || die \
    "PR Gate verifier waited for an observational job after Gate success"

rm -f "${fixture_root}/state/check-runs-count"

set +e
failure_output="$(
    PATH="${fixture_root}/bin:${PATH}" \
        FAKE_GH_STATE_DIR="${fixture_root}/state" \
        FAKE_GH_CHECK_MODE='gate-failure' \
        FAKE_GH_REQUIRED_CI_JOB_NAMES_JSON="${required_ci_jobs_json}" \
        FAKE_GH_REQUIRED_CI_WORKFLOW_NAME="${required_ci_workflow_name}" \
        FAKE_GH_REQUIRED_CI_WORKFLOW_PATH="${required_ci_workflow_path}" \
        bash "${verifier}" 52 2>&1
)"
failure_exit=$?
set -e

if [[ ${failure_exit} -eq 0 ]]; then
    die "PR Gate verifier accepted a failed Gate check"
fi
printf '%s\n' "${failure_output}" | grep -Fq 'required CI jobs did not conclude with success: Gate=failure' || die \
    "PR Gate verifier did not report the failed Gate check"

printf 'verify-release-pr-gate regression: success\n'
