#!/usr/bin/env bash
# Reproduce and guard Jazzer seed-audit and promote-seed wrapper behavior.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

note() {
    printf 'jazzer seed wrapper check: %s\n' "$1"
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
readonly promote_wrapper="${repo_root}/jazzer/bin/promote-seed"
readonly audit_wrapper="${repo_root}/jazzer/bin/seed-audit"
readonly regression_wrapper="${repo_root}/jazzer/bin/regression"
readonly duplicate_seed_source="${repo_root}/jazzer/src/fuzz/resources/dev/erst/fingrind/cli/CliRequestFuzzTestInputs/readPostEntryCommand/basic_valid.json"

[[ -x "${promote_wrapper}" ]] || die "missing promote-seed wrapper at ${promote_wrapper}"
[[ -x "${audit_wrapper}" ]] || die "missing seed-audit wrapper at ${audit_wrapper}"
[[ -x "${regression_wrapper}" ]] || die "missing regression wrapper at ${regression_wrapper}"
[[ -f "${duplicate_seed_source}" ]] || die "missing duplicate-seed fixture at ${duplicate_seed_source}"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT
readonly input_path="${tmp_dir}/input.json"
printf '%s\n' '{}' > "${input_path}"
readonly missing_input_path="${tmp_dir}/missing.json"
readonly missing_input_path_resolved="$(python3 -c 'import pathlib,sys; print(pathlib.Path(sys.argv[1]).resolve())' "${missing_input_path}")"

note 'promote-seed unknown-target fast-fail'
set +e
unknown_target_output="$("${promote_wrapper}" missing-target "${input_path}" --name test_seed --intent 'tmp' 2>&1)"
unknown_target_status=$?
set -e

[[ ${unknown_target_status} -eq 1 ]] || die "promote-seed should fail with exit 1 for an unknown target"
[[ "${unknown_target_output}" == *'Unknown Jazzer run target: missing-target'* ]] ||
    die "promote-seed did not report the unknown target"
[[ "${unknown_target_output}" == *'Supported targets: cli-request, ledger-plan-request, posting-workflow, sqlite-book-roundtrip'* ]] ||
    die "promote-seed did not report the supported target list"
[[ "${unknown_target_output}" != *'Task :'* ]] || die "promote-seed leaked Gradle task output for an unknown target"
[[ "${unknown_target_output}" != *'BUILD FAILED'* ]] || die "promote-seed leaked Gradle failure output for an unknown target"

note 'promote-seed missing-input fast-fail'
set +e
missing_input_output="$("${promote_wrapper}" cli-request "${missing_input_path}" --name test_seed --intent 'tmp' 2>&1)"
missing_input_status=$?
set -e

[[ ${missing_input_status} -eq 1 ]] || die "promote-seed should fail with exit 1 for a missing file"
[[ "${missing_input_output}" == *"Seed promotion input path does not exist: ${missing_input_path_resolved}"* ]] ||
    die "promote-seed did not report the missing file path"
[[ "${missing_input_output}" != *'Task :'* ]] || die "promote-seed leaked Gradle task output for a missing file"

note 'promote-seed required-option guards'
set +e
missing_name_output="$("${promote_wrapper}" cli-request "${input_path}" --intent 'tmp' 2>&1)"
missing_name_status=$?
set -e

[[ ${missing_name_status} -eq 1 ]] || die "promote-seed should fail with exit 1 when --name is missing"
[[ "${missing_name_output}" == *'Missing required promote-seed option: --name'* ]] ||
    die "promote-seed did not report the missing --name option"

set +e
missing_intent_output="$("${promote_wrapper}" cli-request "${input_path}" --name test_seed 2>&1)"
missing_intent_status=$?
set -e

[[ ${missing_intent_status} -eq 1 ]] || die "promote-seed should fail with exit 1 when --intent is missing"
[[ "${missing_intent_output}" == *'Missing required promote-seed option: --intent'* ]] ||
    die "promote-seed did not report the missing --intent option"

set +e
invalid_name_output="$("${promote_wrapper}" cli-request "${input_path}" --name 'Bad Seed' --intent 'tmp' 2>&1)"
invalid_name_status=$?
set -e

[[ ${invalid_name_status} -eq 1 ]] || die "promote-seed should fail with exit 1 for an invalid seed name"
[[ "${invalid_name_output}" == *'Seed name must use lower_snake_case ASCII letters, digits, and underscores. Try: bad_seed'* ]] ||
    die "promote-seed did not report the seed-name grammar or suggestion"
[[ "${invalid_name_output}" != *'Task :'* ]] || die "promote-seed leaked Gradle task output for an invalid seed name"

note 'promote-seed json failure payloads'
set +e
unknown_target_json_output="$("${promote_wrapper}" missing-target "${input_path}" --name test_seed --intent 'tmp' --json --console=plain 2>&1)"
unknown_target_json_status=$?
set -e

[[ ${unknown_target_json_status} -eq 1 ]] || die "promote-seed should fail with exit 1 for an unknown target in JSON mode"
python3 -c 'import json,sys; payload=json.loads(sys.argv[1]); assert payload["status"] == "error"; assert payload["command"] == "promote-seed"; assert "Unknown Jazzer run target: missing-target" in payload["message"]; assert payload["supportedTargetKeys"] == ["cli-request", "ledger-plan-request", "posting-workflow", "sqlite-book-roundtrip"]' \
    "${unknown_target_json_output}" || die "promote-seed unknown-target JSON failure payload was not valid"

set +e
missing_input_json_output="$("${promote_wrapper}" cli-request "${missing_input_path}" --name test_seed --intent 'tmp' --json --console=plain 2>&1)"
missing_input_json_status=$?
set -e

[[ ${missing_input_json_status} -eq 1 ]] || die "promote-seed should fail with exit 1 for a missing file in JSON mode"
python3 -c 'import json,sys; payload=json.loads(sys.argv[1]); assert payload["status"] == "error"; assert payload["command"] == "promote-seed"; assert payload["message"] == f"Seed promotion input path does not exist: {sys.argv[2]}"; assert payload["exitCode"] == 1' \
    "${missing_input_json_output}" "${missing_input_path_resolved}" || die "promote-seed missing-input JSON failure payload was not valid"

readonly duplicate_input_path="${tmp_dir}/duplicate seed.json"
cp "${duplicate_seed_source}" "${duplicate_input_path}"

note 'promote-seed duplicate-byte rejection'
set +e
duplicate_json_output="$("${promote_wrapper}" cli-request "${duplicate_input_path}" --name duplicate_basic_valid --intent 'duplicate byte smoke' --json --console=plain 2>&1)"
duplicate_json_status=$?
set -e

[[ ${duplicate_json_status} -eq 1 ]] || die "promote-seed should fail with exit 1 for duplicate raw bytes"
[[ "${duplicate_json_output}" != *'Task :'* ]] || die "promote-seed leaked Gradle task output for JSON duplicate failure"
[[ "${duplicate_json_output}" != *'BUILD FAILED'* ]] || die "promote-seed leaked Gradle failure output for JSON duplicate failure"
python3 -c 'import json,sys; payload=json.loads(sys.argv[1]); assert payload["status"] == "error"; assert payload["command"] == "promote-seed"; assert "Committed seed content already exists at:" in payload["message"]; assert payload["exitCode"] == 1' \
    "${duplicate_json_output}" || die "promote-seed duplicate JSON failure payload was not valid"

note 'seed-audit json corpus check'
set +e
seed_audit_json_output="$("${audit_wrapper}" --json --console=plain 2>&1)"
seed_audit_json_status=$?
set -e

[[ ${seed_audit_json_status} -eq 0 ]] || die "seed-audit should succeed for the committed corpus"
[[ "${seed_audit_json_output}" != *'Task :'* ]] || die "seed-audit leaked Gradle task output for JSON mode"
[[ "${seed_audit_json_output}" != *'BUILD SUCCESSFUL'* ]] || die "seed-audit leaked Gradle success output for JSON mode"
python3 -c 'import json,sys; payload=json.loads(sys.argv[1]); assert payload["duplicateContentGroups"] == []; assert payload["orphanedInputCount"] == 0; assert payload["unexpectedFailureSeedCount"] == 0; assert payload["integrityProblemCount"] == 0' \
    "${seed_audit_json_output}" || die "seed-audit JSON payload was not valid or reported duplicates"

note 'seed-audit unknown-target rejection'
set +e
seed_audit_unknown_json_output="$("${audit_wrapper}" missing-target --json --console=plain 2>&1)"
seed_audit_unknown_json_status=$?
set -e

[[ ${seed_audit_unknown_json_status} -eq 1 ]] || die "seed-audit should fail with exit 1 for an unknown target in JSON mode"
python3 -c 'import json,sys; payload=json.loads(sys.argv[1]); assert payload["status"] == "error"; assert payload["command"] == "seed-audit"; assert "Unknown Jazzer run target: missing-target" in payload["message"]; assert payload["supportedTargetKeys"] == ["cli-request", "ledger-plan-request", "posting-workflow", "sqlite-book-roundtrip"]' \
    "${seed_audit_unknown_json_output}" || die "seed-audit unknown-target JSON failure payload was not valid"

note 'seed-audit plain summary'
set +e
seed_audit_output="$("${audit_wrapper}" --console=plain 2>&1)"
seed_audit_status=$?
set -e

[[ ${seed_audit_status} -eq 0 ]] || die "seed-audit plain mode should succeed for the committed corpus"
[[ "${seed_audit_output}" == *'Committed seed audit'* ]] || die "seed-audit plain output omitted the audit heading"
[[ "${seed_audit_output}" == *'Orphaned input files: 0'* ]] || die "seed-audit plain output omitted orphaned-input summary"
[[ "${seed_audit_output}" == *'Unexpected-failure expectations: 0'* ]] || die "seed-audit plain output omitted unexpected-failure summary"
[[ "${seed_audit_output}" == *'Integrity problems: 0'* ]] || die "seed-audit plain output omitted integrity summary"
[[ "${seed_audit_output}" == *'Duplicate content groups: 0'* ]] || die "seed-audit plain output omitted duplicate summary"

note 'regression wrapper argument guard'
set +e
regression_extra_output="$("${regression_wrapper}" cli-request 2>&1)"
regression_extra_status=$?
set -e

[[ ${regression_extra_status} -eq 1 ]] || die "regression wrapper should fail with exit 1 for a positional argument"
[[ "${regression_extra_output}" == *'Regression wrapper does not accept positional arguments: cli-request'* ]] ||
    die "regression wrapper did not reject the unexpected positional argument"
[[ "${regression_extra_output}" != *"Task 'cli-request' not found"* ]] ||
    die "regression wrapper leaked the raw Gradle task-not-found failure"

printf 'jazzer seed wrapper regression: success\n'
