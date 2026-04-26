#!/usr/bin/env bash
# Delegates the shared office-worker release acceptance workflow to the Python owner.

readonly release_smoke_workflow_script="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/release-smoke-workflow.py"

release_smoke_run_office_worker_acceptance() {
    [[ -f "${release_smoke_workflow_script}" ]] || die \
        "missing shared release smoke workflow runner at ${release_smoke_workflow_script}"
    python3 "${release_smoke_workflow_script}"
}
