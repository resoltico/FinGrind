#!/usr/bin/env bash
# Delegates the shared office-worker release acceptance workflow to the Python owner.

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    printf 'error: %s\n' "release-smoke-workflow-support.sh is a library and must be sourced by release-smoke-support.sh." >&2
    exit 1
fi

readonly release_smoke_workflow_script="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/release-smoke-workflow.py"
readonly python_runtime_support_script="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/python-runtime-support.sh"

[[ -f "${python_runtime_support_script}" ]] || {
    printf 'error: missing Python runtime support helper at %s\n' "${python_runtime_support_script}" >&2
    exit 1
}
# shellcheck source=/dev/null
source "${python_runtime_support_script}"

prepare_python_runtime_env

release_smoke_run_office_worker_acceptance() {
    [[ -f "${release_smoke_workflow_script}" ]] || die \
        "missing shared release smoke workflow runner at ${release_smoke_workflow_script}"
    fingrind_run_python_with_tools "${release_smoke_workflow_script}"
}
