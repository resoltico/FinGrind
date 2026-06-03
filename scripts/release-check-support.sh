#!/usr/bin/env bash
# Shared canonical owner for the release-publication CI workflow and Gate contract.

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    printf 'error: %s\n' "release-check-support.sh is a library and must be sourced by another script." >&2
    exit 1
fi

readonly release_check_support_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly release_publication_reader="${release_check_support_dir}/read-release-publication-contract.py"

[[ -f "${release_publication_reader}" ]] || {
    printf 'error: %s\n' "missing release-publication contract reader at ${release_publication_reader}" >&2
    exit 1
}

fingrind_release_publication_contract_json() {
    python3 "${release_publication_reader}"
}

fingrind_release_publication_field() {
    local field_name=$1
    FINGRIND_RELEASE_PUBLICATION_CONTRACT_JSON="$(fingrind_release_publication_contract_json)" \
        python3 - "${field_name}" <<'PY'
import json
import os
import sys

document = json.loads(os.environ["FINGRIND_RELEASE_PUBLICATION_CONTRACT_JSON"])
value = document[sys.argv[1]]
if isinstance(value, str):
    print(value)
else:
    raise SystemExit(f"expected string release-publication field for {sys.argv[1]}")
PY
}

fingrind_release_publication_string_array_json() {
    local field_name=$1
    FINGRIND_RELEASE_PUBLICATION_CONTRACT_JSON="$(fingrind_release_publication_contract_json)" \
        python3 - "${field_name}" <<'PY'
import json
import os
import sys

document = json.loads(os.environ["FINGRIND_RELEASE_PUBLICATION_CONTRACT_JSON"])
value = document[sys.argv[1]]
if not isinstance(value, list) or not all(isinstance(element, str) for element in value):
    raise SystemExit(f"expected string-array release-publication field for {sys.argv[1]}")
print(json.dumps(value))
PY
}

fingrind_required_ci_workflow_name() {
    fingrind_release_publication_field requiredCiWorkflowName
}

fingrind_required_ci_workflow_path() {
    fingrind_release_publication_field requiredCiWorkflowPath
}

fingrind_required_ci_check_name() {
    fingrind_release_publication_field requiredCiGateJobName
}

fingrind_required_ci_checks_csv() {
    fingrind_required_ci_check_name
}

fingrind_required_ci_check_contexts_json() {
    printf '["%s"]\n' "$(fingrind_required_ci_check_name)"
}

fingrind_required_ci_job_names_json() {
    fingrind_release_publication_string_array_json requiredCiJobNames
}
