#!/usr/bin/env bash
# Shared helpers for release-blocking GitHub workflow-run verification.

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    printf 'error: %s\n' \
        "release-check-verification-support.sh is a library and must be sourced by another script." >&2
    exit 1
fi

fingrind_require_non_negative_integer() {
    local value=$1
    local name=$2
    [[ "${value}" =~ ^[0-9]+$ ]] || die "${name} must be a non-negative integer, got '${value}'"
}

fingrind_release_workflow_summary_json() {
    local verifier_repo_full_name=$1
    local verifier_target_commit_sha=$2
    local required_workflow_name
    local required_workflow_path
    local workflow_runs_json

    required_workflow_name="$(fingrind_required_ci_workflow_name)"
    required_workflow_path="$(fingrind_required_ci_workflow_path)"
    workflow_runs_json="$(
        gh api \
            "/repos/${verifier_repo_full_name}/actions/runs?head_sha=${verifier_target_commit_sha}&per_page=100"
    )"

    FINGRIND_RELEASE_WORKFLOW_RUNS_JSON="${workflow_runs_json}" \
        FINGRIND_REQUIRED_CI_WORKFLOW_NAME="${required_workflow_name}" \
        FINGRIND_REQUIRED_CI_WORKFLOW_PATH="${required_workflow_path}" \
        python3 - <<'PY'
import json
import os

runs_payload = json.loads(os.environ["FINGRIND_RELEASE_WORKFLOW_RUNS_JSON"])
required_name = os.environ["FINGRIND_REQUIRED_CI_WORKFLOW_NAME"]
required_path = os.environ["FINGRIND_REQUIRED_CI_WORKFLOW_PATH"]
workflow_runs = runs_payload.get("workflow_runs", [])
matching_runs = [
    run
    for run in workflow_runs
    if run.get("name") == required_name and run.get("path") == required_path
]
matching_runs.sort(
    key=lambda run: (
        int(run.get("run_number") or 0),
        int(run.get("run_attempt") or 0),
        run.get("created_at") or "",
    ),
    reverse=True,
)
if not matching_runs:
    print("null")
else:
    selected_run = matching_runs[0]
    print(
        json.dumps(
            {
                "id": selected_run.get("id"),
                "name": selected_run.get("name"),
                "path": selected_run.get("path"),
                "status": selected_run.get("status"),
                "conclusion": selected_run.get("conclusion"),
                "event": selected_run.get("event"),
                "htmlUrl": selected_run.get("html_url"),
                "runNumber": selected_run.get("run_number"),
                "runAttempt": selected_run.get("run_attempt"),
            }
        )
    )
PY
}

fingrind_release_workflow_jobs_json() {
    local verifier_repo_full_name=$1
    local run_id=$2
    gh api "/repos/${verifier_repo_full_name}/actions/runs/${run_id}/jobs?per_page=100" --paginate
}

fingrind_format_observed_release_jobs() {
    local jobs_payload_json=$1
    local workflow_summary_json=$2

    FINGRIND_RELEASE_WORKFLOW_JOBS_JSON="${jobs_payload_json}" \
        FINGRIND_RELEASE_WORKFLOW_SUMMARY_JSON="${workflow_summary_json}" \
        python3 - <<'PY'
import json
import os

workflow = json.loads(os.environ["FINGRIND_RELEASE_WORKFLOW_SUMMARY_JSON"])
jobs_payload = json.loads(os.environ["FINGRIND_RELEASE_WORKFLOW_JOBS_JSON"])
jobs = jobs_payload.get("jobs", [])
job_parts = [
    f'{job.get("name","<unknown>")}[{job.get("status","<missing>")}/{job.get("conclusion","<missing>")}]'
    for job in jobs
]
workflow_identity = (
    f'{workflow.get("name","<unknown>")}#{workflow.get("runNumber","?")}.'
    f'{workflow.get("runAttempt","?")}[{workflow.get("status","<missing>")}/'
    f'{workflow.get("conclusion","<missing>")}]'
)
if job_parts:
    print(workflow_identity + " jobs: " + ", ".join(job_parts))
else:
    print(workflow_identity + " jobs: none")
PY
}

fingrind_release_workflow_state_json() {
    local workflow_summary_json=$1
    local jobs_payload_json=$2

    FINGRIND_RELEASE_WORKFLOW_SUMMARY_JSON="${workflow_summary_json}" \
        FINGRIND_RELEASE_WORKFLOW_JOBS_JSON="${jobs_payload_json}" \
        FINGRIND_REQUIRED_CI_GATE_JOB_NAME="$(fingrind_required_ci_check_name)" \
        FINGRIND_REQUIRED_CI_JOB_NAMES_JSON="$(fingrind_required_ci_job_names_json)" \
        python3 - <<'PY'
import json
import os

workflow = json.loads(os.environ["FINGRIND_RELEASE_WORKFLOW_SUMMARY_JSON"])
jobs_payload = json.loads(os.environ["FINGRIND_RELEASE_WORKFLOW_JOBS_JSON"])
required_gate_job_name = os.environ["FINGRIND_REQUIRED_CI_GATE_JOB_NAME"]
required_job_names = json.loads(os.environ["FINGRIND_REQUIRED_CI_JOB_NAMES_JSON"])
required_job_names = [required_gate_job_name, *required_job_names]
jobs = {job.get("name"): job for job in jobs_payload.get("jobs", [])}

state = "success"
reason = ""

status = workflow.get("status")
conclusion = workflow.get("conclusion")
failed_jobs = []
pending_jobs = []
missing_jobs = []

for job_name in required_job_names:
    job = jobs.get(job_name)
    if job is None:
        missing_jobs.append(job_name)
        continue
    job_status = job.get("status")
    job_conclusion = job.get("conclusion")
    if job_status != "completed":
        pending_jobs.append(job_name)
    elif job_conclusion != "success":
        failed_jobs.append(f"{job_name}={job_conclusion or '<missing>'}")

if failed_jobs:
    state = "failure"
    reason = "required CI jobs did not conclude with success: " + ", ".join(failed_jobs)
elif pending_jobs:
    state = "pending"
    reason = "required CI jobs are not complete yet: " + ", ".join(pending_jobs)
elif missing_jobs:
    if status == "completed":
        if conclusion not in ("success", None):
            state = "failure"
            reason = (
                "required CI workflow concluded "
                f"{conclusion or '<missing>'} before all required jobs materialized: "
                + ", ".join(missing_jobs)
            )
        else:
            state = "failure"
            reason = "required CI workflow omitted expected jobs: " + ", ".join(missing_jobs)
    else:
        state = "pending"
        reason = "required CI jobs are not visible yet: " + ", ".join(missing_jobs)
elif status == "completed" and conclusion not in ("success", None):
    reason = (
        "required CI jobs passed while non-blocking workflow jobs concluded "
        f"{conclusion or '<missing>'}"
    )

print(json.dumps({"state": state, "reason": reason}))
PY
}

fingrind_wait_for_release_blocking_checks() {
    local verifier_repo_full_name=$1
    local verifier_target_commit_sha=$2
    local verifier_blocking_checks_csv=$3
    local verifier_poll_interval_seconds=$4
    local verifier_timeout_seconds=$5
    local verifier_success_scope=$6
    local verifier_wait_scope=$7
    local verifier_failure_scope=$8

    fingrind_require_non_negative_integer \
        "${verifier_poll_interval_seconds}" \
        "FINGRIND_RELEASE_CHECK_POLL_INTERVAL_SECONDS"
    fingrind_require_non_negative_integer \
        "${verifier_timeout_seconds}" \
        "FINGRIND_RELEASE_CHECK_TIMEOUT_SECONDS"
    [[ "${verifier_blocking_checks_csv}" == "$(fingrind_required_ci_check_name)" ]] || die \
        "release-blocking check contract drifted away from the canonical Gate job"

    local deadline_epoch="$((SECONDS + verifier_timeout_seconds))"
    while true; do
        local workflow_summary_json
        workflow_summary_json="$(
            fingrind_release_workflow_summary_json \
                "${verifier_repo_full_name}" \
                "${verifier_target_commit_sha}"
        )"

        if [[ "${workflow_summary_json}" == "null" ]]; then
            if ((SECONDS >= deadline_epoch)); then
                die \
                    "timed out waiting for ${verifier_wait_scope} release-blocking workflow on ${verifier_target_commit_sha}. Observed workflow runs: none"
            fi
            local remaining_seconds="$((deadline_epoch - SECONDS))"
            (( remaining_seconds < 0 )) && remaining_seconds=0
            printf 'Waiting for %s on %s (%ss remaining). Observed workflow runs: none\n' \
                "${verifier_wait_scope}" \
                "${verifier_target_commit_sha}" \
                "${remaining_seconds}"
            sleep "${verifier_poll_interval_seconds}"
            continue
        fi

        local workflow_run_id
        workflow_run_id="$(
            FINGRIND_RELEASE_WORKFLOW_SUMMARY_JSON="${workflow_summary_json}" \
                python3 - <<'PY'
import json
import os

print(json.loads(os.environ["FINGRIND_RELEASE_WORKFLOW_SUMMARY_JSON"])["id"])
PY
        )"

        local jobs_payload_json
        jobs_payload_json="$(
            fingrind_release_workflow_jobs_json \
                "${verifier_repo_full_name}" \
                "${workflow_run_id}"
        )"
        local workflow_state_json
        workflow_state_json="$(
            fingrind_release_workflow_state_json \
                "${workflow_summary_json}" \
                "${jobs_payload_json}"
        )"
        local observed_jobs
        observed_jobs="$(
            fingrind_format_observed_release_jobs \
                "${jobs_payload_json}" \
                "${workflow_summary_json}"
        )"
        local workflow_state
        workflow_state="$(
            FINGRIND_RELEASE_WORKFLOW_STATE_JSON="${workflow_state_json}" \
                python3 - <<'PY'
import json
import os

print(json.loads(os.environ["FINGRIND_RELEASE_WORKFLOW_STATE_JSON"])["state"])
PY
        )"
        local workflow_reason
        workflow_reason="$(
            FINGRIND_RELEASE_WORKFLOW_STATE_JSON="${workflow_state_json}" \
                python3 - <<'PY'
import json
import os

print(json.loads(os.environ["FINGRIND_RELEASE_WORKFLOW_STATE_JSON"])["reason"])
PY
        )"

        case "${workflow_state}" in
            success)
                printf 'Verified %s at %s through %s\n' \
                    "${verifier_success_scope}" \
                    "${verifier_target_commit_sha}" \
                    "${observed_jobs}"
                return 0
                ;;
            failure)
                die \
                    "${verifier_failure_scope} failed for ${verifier_target_commit_sha}; ${workflow_reason}. Observed: ${observed_jobs}"
                ;;
            pending)
                if ((SECONDS >= deadline_epoch)); then
                    die \
                        "timed out waiting for ${verifier_wait_scope} release-blocking workflow on ${verifier_target_commit_sha}; ${workflow_reason}. Observed: ${observed_jobs}"
                fi
                local remaining_seconds="$((deadline_epoch - SECONDS))"
                (( remaining_seconds < 0 )) && remaining_seconds=0
                printf 'Waiting for %s on %s (%ss remaining). %s. Observed: %s\n' \
                    "${verifier_wait_scope}" \
                    "${verifier_target_commit_sha}" \
                    "${remaining_seconds}" \
                    "${workflow_reason}" \
                    "${observed_jobs}"
                sleep "${verifier_poll_interval_seconds}"
                ;;
            *)
                die "unsupported release-blocking workflow state '${workflow_state}'"
                ;;
        esac
    done
}
