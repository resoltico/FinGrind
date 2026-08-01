#!/usr/bin/env bash
# Guard the shared release-smoke workflow wiring so Bash and PowerShell stay delegated.

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
readonly workflow_py="${repo_root}/scripts/release-smoke-workflow.py"
readonly workflow_contract_py="${repo_root}/scripts/test-release-smoke-workflow-contract.py"
readonly workflow_package_dir="${repo_root}/scripts/release_smoke_workflow"
readonly workflow_cli_py="${workflow_package_dir}/cli.py"
readonly field_matrix_package_dir="${workflow_package_dir}/field_matrix"
readonly field_execution_py="${workflow_package_dir}/release_smoke_field_execution.py"
readonly fixture_writers_py="${workflow_package_dir}/fixture_writers.py"
readonly fixture_plan_contract_py="${workflow_package_dir}/fixture_plan_contract.py"
readonly plan_reactivation_checks_py="${workflow_package_dir}/plan_reactivation_checks.py"
readonly plan_posting_checks_py="${workflow_package_dir}/plan_posting_checks.py"
readonly plan_posting_provenance_checks_py="${workflow_package_dir}/plan_posting_provenance_checks.py"
readonly posting_replay_checks_py="${workflow_package_dir}/posting_replay_checks.py"
readonly protected_book_tamper_checks_py="${workflow_package_dir}/protected_book_tamper_checks.py"
readonly maintenance_checks_py="${workflow_package_dir}/maintenance_checks.py"
readonly maintenance_collision_checks_py="${workflow_package_dir}/maintenance_collision_checks.py"
readonly maintenance_source_identity_checks_py="${workflow_package_dir}/maintenance_source_identity_checks.py"
readonly receipt_security_checks_py="${workflow_package_dir}/attestation_receipt_security_checks.py"
readonly receipt_security_positive_py="${workflow_package_dir}/attestation_receipt_security_positive.py"
readonly receipt_security_aliases_py="${workflow_package_dir}/attestation_receipt_security_aliases.py"
readonly receipt_security_output_refusals_py="${workflow_package_dir}/attestation_receipt_security_output_refusals.py"
readonly receipt_security_assertions_py="${workflow_package_dir}/attestation_receipt_security_assertions.py"
readonly receipt_security_commands_py="${workflow_package_dir}/attestation_receipt_security_commands.py"
readonly receipt_security_symlinks_py="${workflow_package_dir}/attestation_receipt_security_symlinks.py"
readonly artifact_assertions_py="${field_matrix_package_dir}/artifact_assertions.py"
readonly attestation_scale_scenario_py="${field_matrix_package_dir}/attestation_scale_scenario.py"
readonly attestation_scale_contract_py="${field_matrix_package_dir}/attestation_scale_contract.py"
readonly attestation_scale_posting_queries_py="${field_matrix_package_dir}/attestation_scale_posting_queries.py"
readonly attestation_scale_account_ledger_py="${field_matrix_package_dir}/attestation_scale_account_ledger.py"
readonly receipt_artifact_assertions_py="${field_matrix_package_dir}/receipt_artifact_assertions.py"
readonly format_boundary_scenarios_py="${field_matrix_package_dir}/format_boundary_scenarios.py"
readonly format_boundary_inspection_py="${field_matrix_package_dir}/format_boundary_inspection.py"
readonly format_boundary_operational_matrix_py="${field_matrix_package_dir}/format_boundary_operational_matrix.py"
readonly format_boundary_probe_execution_py="${field_matrix_package_dir}/format_boundary_probe_execution.py"
readonly format_boundary_refusals_py="${field_matrix_package_dir}/format_boundary_refusals.py"
readonly native_format_boundary_probe_java="${field_matrix_package_dir}/NativeSqliteFormatBoundaryProbe.java"
readonly distribution_plugin="${repo_root}/gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/FinGrindCliDistributionPlugin.kt"
readonly native_format_boundary_probe_registration="${repo_root}/gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/NativeSqliteFormatBoundaryProbeRegistration.kt"
readonly docker_context_registration="${repo_root}/gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/CliDistributionDockerContextRegistration.kt"
readonly docker_source_inventory="${repo_root}/gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/CliDistributionSourceInventory.kt"
readonly dockerfile="${repo_root}/Dockerfile"
readonly docker_entrypoint_sh="${repo_root}/cli/src/docker/docker-entrypoint.sh"
readonly bundle_root_verifier="${repo_root}/scripts/bundle_archive_root_verification.py"
readonly release_smoke_requirements="${repo_root}/requirements-release-smoke-workflow.txt"
readonly python_runtime_support="${repo_root}/scripts/python-runtime-support.sh"
readonly common_support_sh="${repo_root}/scripts/release-smoke-common.sh"
readonly workflow_support_sh="${repo_root}/scripts/release-smoke-workflow-support.sh"
readonly bundle_support_sh="${repo_root}/scripts/release-smoke-support.sh"
readonly bundle_compatibility_floor_support_sh="${repo_root}/scripts/bundle-smoke-compatibility-floor-support.sh"
readonly bundle_office_worker_ps1="${repo_root}/scripts/bundle-smoke-office-worker.ps1"
readonly bundle_command_bridge_ps1="${repo_root}/scripts/bundle-smoke-command-bridge.ps1"
readonly bundle_smoke_sh="${repo_root}/scripts/bundle-smoke.sh"
readonly docker_smoke_sh="${repo_root}/scripts/docker-smoke.sh"

[[ -f "${workflow_py}" ]] || die "missing shared release smoke workflow runner at ${workflow_py}"
[[ -f "${workflow_contract_py}" ]] || die \
    "missing release smoke workflow contract regression owner at ${workflow_contract_py}"
[[ -d "${workflow_package_dir}" ]] || die "missing release smoke workflow package at ${workflow_package_dir}"
[[ -f "${workflow_cli_py}" ]] || die "missing release smoke CLI transport owner at ${workflow_cli_py}"
[[ -d "${field_matrix_package_dir}" ]] || die "missing release-smoke field-matrix package at ${field_matrix_package_dir}"
[[ -f "${posting_replay_checks_py}" ]] || die "missing direct posting replay release-smoke owner"
[[ -f "${protected_book_tamper_checks_py}" ]] || die \
    "missing protected-book byte tamper release-smoke owner"
[[ -f "${maintenance_checks_py}" ]] || die \
    "missing backup and restore release-smoke owner"
[[ -f "${maintenance_collision_checks_py}" ]] || die \
    "missing maintenance collision release-smoke owner"
[[ -f "${maintenance_source_identity_checks_py}" ]] || die \
    "missing maintenance source-identity release-smoke owner"
[[ -f "${receipt_security_checks_py}" ]] || die "missing receipt security release-smoke owner"
[[ -f "${receipt_security_positive_py}" ]] || die "missing positive receipt security scenario owner"
[[ -f "${receipt_security_aliases_py}" ]] || die "missing receipt alias security scenario owner"
[[ -f "${receipt_security_output_refusals_py}" ]] || die \
    "missing hostile receipt output-path scenario owner"
[[ -f "${receipt_security_assertions_py}" ]] || die "missing receipt security assertion owner"
[[ -f "${receipt_security_commands_py}" ]] || die "missing receipt security CLI transport owner"
[[ -f "${receipt_security_symlinks_py}" ]] || die "missing receipt security symlink owner"
[[ -f "${attestation_scale_scenario_py}" ]] || die \
    "missing attestation provenance scale release-smoke owner"
[[ -f "${attestation_scale_contract_py}" ]] || die \
    "missing attestation provenance scale contract owner"
[[ -f "${attestation_scale_posting_queries_py}" ]] || die \
    "missing attestation provenance scale posting-query owner"
[[ -f "${attestation_scale_account_ledger_py}" ]] || die \
    "missing attestation provenance scale account-ledger owner"
[[ -f "${receipt_artifact_assertions_py}" ]] || die \
    "missing canonical receipt artifact assertion owner"
[[ -f "${format_boundary_scenarios_py}" ]] || die \
    "missing release-smoke protected-book format-boundary scenario"
[[ -f "${format_boundary_inspection_py}" ]] || die \
    "missing release-smoke protected-book format-boundary inspection owner"
[[ -f "${format_boundary_operational_matrix_py}" ]] || die \
    "missing release-smoke protected-book format-boundary operational matrix"
[[ -f "${format_boundary_probe_execution_py}" ]] || die \
    "missing release-smoke protected-book format-boundary native-probe owner"
[[ -f "${docker_entrypoint_sh}" ]] || die "missing Docker runtime entrypoint at ${docker_entrypoint_sh}"
[[ -f "${format_boundary_refusals_py}" ]] || die \
    "missing release-smoke protected-book format-boundary refusal owner"
[[ -f "${native_format_boundary_probe_java}" ]] || die \
    "missing archive-native SQLite format-boundary probe source"
[[ -f "${distribution_plugin}" ]] || die "missing CLI distribution owner at ${distribution_plugin}"
[[ -f "${native_format_boundary_probe_registration}" ]] || die \
    "missing native SQLite format-boundary probe registration owner"
[[ -f "${docker_context_registration}" ]] || die \
    "missing Docker distribution staging owner at ${docker_context_registration}"
[[ -f "${docker_source_inventory}" ]] || die \
    "missing Docker source-inventory owner at ${docker_source_inventory}"
[[ -f "${dockerfile}" ]] || die "missing Dockerfile at ${dockerfile}"
[[ -f "${bundle_root_verifier}" ]] || die \
    "missing bundle-root verifier at ${bundle_root_verifier}"
[[ -f "${release_smoke_requirements}" ]] || die \
    "missing pinned release-smoke Python requirements at ${release_smoke_requirements}"
[[ -f "${python_runtime_support}" ]] || die "missing Python runtime support helper at ${python_runtime_support}"
[[ -f "${common_support_sh}" ]] || die "missing Bash release smoke common helper at ${common_support_sh}"
[[ -f "${workflow_support_sh}" ]] || die "missing Bash release smoke workflow support helper at ${workflow_support_sh}"
[[ -f "${bundle_support_sh}" ]] || die "missing Bash release smoke support wrapper at ${bundle_support_sh}"
[[ -f "${bundle_compatibility_floor_support_sh}" ]] || die \
    "missing compatibility-floor support helper at ${bundle_compatibility_floor_support_sh}"
[[ -f "${bundle_office_worker_ps1}" ]] || die "missing PowerShell office-worker wrapper at ${bundle_office_worker_ps1}"
[[ -f "${bundle_command_bridge_ps1}" ]] || die "missing PowerShell command bridge at ${bundle_command_bridge_ps1}"
[[ -f "${bundle_smoke_sh}" ]] || die "missing Bash bundle smoke entrypoint at ${bundle_smoke_sh}"
[[ -f "${docker_smoke_sh}" ]] || die "missing Bash Docker smoke entrypoint at ${docker_smoke_sh}"
grep -Fq 'release-smoke-workflow-support.sh' "${bundle_support_sh}" || die \
    "release-smoke-support.sh no longer sources the shared workflow support helper"
grep -Fq 'release-smoke-workflow.py' "${workflow_support_sh}" || die \
    "release-smoke-workflow-support.sh no longer delegates to the shared Python workflow owner"
grep -Fq 'fingrind_run_python_with_tools' "${workflow_support_sh}" || die \
    "release-smoke-workflow-support.sh no longer provisions its Python dependencies through pinned uv"
grep -Fq -- '--with-requirements' "${python_runtime_support}" || die \
    "Python runtime support no longer resolves release-smoke dependencies through uv"
grep -Fxq 'pypdf==6.14.2' "${release_smoke_requirements}" || die \
    "release-smoke PDF extraction is no longer pinned to the repo-owned pypdf version"
grep -Fq 'from pypdf import PdfReader' "${artifact_assertions_py}" || die \
    "release-smoke PDF extraction no longer uses the repo-owned pypdf reader"
if grep -Fq 'pdftotext' "${artifact_assertions_py}"; then
    die "release-smoke PDF extraction must not fall back to an ambient Poppler utility"
fi
grep -Fq 'fingrind_repo_uv_executable' "${python_runtime_support}" || die \
    "Python runtime support no longer resolves an exact pinned uv executable for release smoke"
grep -Fq 'release_smoke_workflow.runner import main' "${workflow_py}" || die \
    "release-smoke-workflow.py no longer delegates into the release_smoke_workflow package"
grep -Fq 'def emit_command_progress(' "${workflow_cli_py}" || die \
    "release smoke CLI transport no longer emits per-command liveness heartbeats"
python3 - "${workflow_cli_py}" <<'PY' || die \
    "release smoke CLI transport no longer emits liveness heartbeats before both command transport modes"
import ast
import pathlib
import sys

tree = ast.parse(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
functions = {
    node.name: node
    for node in tree.body
    if isinstance(node, ast.FunctionDef)
}
for function_name in (
    "run_cli_allow_failure",
    "run_cli_allow_failure_with_split_streams",
):
    calls = [
        node
        for node in ast.walk(functions[function_name])
        if isinstance(node, ast.Call)
        and isinstance(node.func, ast.Name)
        and node.func.id == "emit_command_progress"
    ]
    assert len(calls) == 1, function_name
PY
grep -Fq 'verify_discovery_matrix' "${field_execution_py}" || die \
    "release smoke runner no longer executes live discovery matrix coverage"
grep -Fq 'verify_query_matrix' "${field_execution_py}" || die \
    "release smoke runner no longer executes live generic query matrix coverage"
grep -Fq 'verify_direct_posting_replay(config, operation_ids, commit_sale_output)' \
    "${workflow_package_dir}/query_checks.py" || die \
    "release smoke runner no longer proves direct idempotent replay before later writes"
grep -Fq 'verify_attestation_scale_scenario(config, operation_ids)' "${field_execution_py}" || die \
    "release smoke runner no longer executes isolated attestation provenance scale coverage"
grep -Fq 'verify_protected_book_byte_tamper_rejection(' "${field_execution_py}" || die \
    "release smoke runner no longer proves protected-book raw-byte tamper rejection"
grep -Fq 'verify_maintenance_collision_refusals(' "${field_execution_py}" || die \
    "release smoke runner no longer proves maintenance no-clobber target collisions"
grep -Fq 'maintenance_checks.verify_backup_restore_surfaces(' "${field_execution_py}" || die \
    "release smoke runner no longer executes backup and restore acceptance coverage"
grep -Fq 'verify_source_artifact_identity_duplicate_refusal(' "${maintenance_source_identity_checks_py}" || die \
    "release smoke backup coverage no longer proves duplicate source-identity rejection"
grep -Fq 'os.link(' "${maintenance_source_identity_checks_py}" || die \
    "release smoke duplicate-source coverage no longer uses a real filesystem hard link"
grep -Fq 'source-artifact-identity-duplicated' "${maintenance_source_identity_checks_py}" || die \
    "release smoke duplicate-source coverage no longer requires the typed physical-identity refusal"
grep -Fq 'root_entry_names == {aliased_book_source.local_path.name}' "${maintenance_source_identity_checks_py}" || die \
    "release smoke duplicate-source coverage no longer proves refusal side-effect freedom"
grep -Fq 'after source-artifact identity duplicate refusal' "${maintenance_source_identity_checks_py}" || die \
    "release smoke duplicate-source coverage no longer proves attestation-head immutability"
grep -Fq 'verify_receipt_trust_and_path_security(' "${workflow_package_dir}/attestation_checks.py" || die \
    "release smoke attestation workflow no longer proves receipt trust and hostile-path handling"
grep -Fq 'canonical_receipt_reported_path' "${receipt_artifact_assertions_py}" || die \
    "release smoke receipt artifact assertions no longer require canonical physical paths"
grep -Fq 'receipt intermediate-alias output refusal' "${receipt_security_aliases_py}" || die \
    "release smoke receipt security no longer rejects an intermediate output-path symlink"
grep -Fq 'receipt output-parent symlink refusal' "${receipt_security_output_refusals_py}" || die \
    "release smoke receipt security no longer rejects a final output-parent symlink"
grep -Fq 'invalid-artifact-output-directory' "${receipt_security_assertions_py}" || die \
    "release smoke receipt security no longer requires the public private-output-directory refusal"
grep -Fq 'unsafe_parent.chmod(0o755)' "${receipt_security_output_refusals_py}" || die \
    "release smoke receipt security no longer exercises a nonprivate receipt output parent"
grep -Fq 'created a staged or final artifact' "${receipt_security_output_refusals_py}" || die \
    "release smoke receipt security no longer proves unsafe output-parent refusal leaves no artifact"
grep -Fq 'receipt-artifact-invalid' "${receipt_security_aliases_py}" || die \
    "release smoke receipt security no longer rejects a final input symlink"
grep -Fq 'receipt-not-independent' "${receipt_security_positive_py}" || die \
    "release smoke receipt security no longer proves in-boundary trust warnings"
grep -Fq 'SCALE_POSTING_COUNT = 40' "${attestation_scale_contract_py}" || die \
    "release smoke provenance scale no longer exercises forty fresh posting writes"
grep -Fq 'verify_paginated_list_postings' "${attestation_scale_posting_queries_py}" || die \
    "release smoke provenance scale no longer verifies paginated list-postings commitments"
grep -Fq 'verify_list_postings_csv' "${attestation_scale_posting_queries_py}" || die \
    "release smoke provenance scale no longer verifies list-postings CSV commitments"
grep -Fq 'get-posting deliberately advertises JSON and text only' \
    "${attestation_scale_posting_queries_py}" || die \
    "release smoke provenance scale no longer records get-posting's intentionally non-CSV contract"
grep -Fq 'account-ledger CSV' "${attestation_scale_account_ledger_py}" || die \
    "release smoke provenance scale no longer verifies account-ledger CSV commitments"
grep -Fq 'verify_protected_book_format_boundary_rejections' "${field_execution_py}" || die \
    "release smoke runner no longer proves protected-book format boundary rejection"
grep -Fq 'assert_inspection_rejection(' "${format_boundary_scenarios_py}" || die \
    "release smoke format-boundary scenario no longer invokes its public inspection proof"
grep -Fq 'unsupported-format-version' "${format_boundary_inspection_py}" || die \
    "release smoke format-boundary inspection no longer requires the public unsupported-format state"
grep -Fq '("future", future_format)' "${format_boundary_scenarios_py}" || die \
    "release smoke format-boundary scenario no longer proves future-format rejection"
grep -Fq 'require_persisted_user_version(' "${format_boundary_scenarios_py}" || die \
    "release smoke format-boundary scenario no longer proves public refusals do not rewrite the marker"
grep -Fq 'run_native_sqlite_probe(' "${format_boundary_probe_execution_py}" || die \
    "release smoke format-boundary probe owner no longer executes its marker probe through the archive runtime"
grep -Fq '_require_open_book_does_not_replace_boundary(' "${format_boundary_scenarios_py}" || die \
    "release smoke format-boundary scenario no longer proves open-book cannot replace an existing boundary book"
grep -Fq 'require_operational_format_refusals(' "${format_boundary_scenarios_py}" || die \
    "release smoke format-boundary scenario no longer proves operational format refusals"
python3 - "${format_boundary_scenarios_py}" <<'PY' || die \
    "release smoke format-boundary scenario no longer passes the operation-id contract to operational refusals"
import ast
import pathlib
import sys

source = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
tree = ast.parse(source)
calls = [
    node
    for node in ast.walk(tree)
    if isinstance(node, ast.Call)
    and isinstance(node.func, ast.Name)
    and node.func.id
    in {"require_operational_format_refusals", "_require_open_book_does_not_replace_boundary"}
]
assert len(calls) == 2
operational_call = next(
    call for call in calls if isinstance(call.func, ast.Name) and call.func.id == "require_operational_format_refusals"
)
open_book_call = next(
    call for call in calls if isinstance(call.func, ast.Name) and call.func.id == "_require_open_book_does_not_replace_boundary"
)
assert len(operational_call.args) == 8
assert isinstance(operational_call.args[1], ast.Name)
assert operational_call.args[1].id == "operation_ids"
assert len(open_book_call.args) == 6
assert isinstance(open_book_call.args[1], ast.Name)
assert open_book_call.args[1].id == "boundary_book"
PY
grep -Fq '"preflight-entry"' "${format_boundary_operational_matrix_py}" || die \
    "release smoke format-boundary scenario no longer proves direct posting preflight refusal"
grep -Fq '"record-sale-settled"' "${format_boundary_operational_matrix_py}" || die \
    "release smoke format-boundary scenario no longer proves signed direct posting refusal"
grep -Fq '"execute-plan read-only"' "${format_boundary_operational_matrix_py}" || die \
    "release smoke format-boundary scenario no longer proves read-only plan refusal"
grep -Fq '"execute-plan mutating"' "${format_boundary_operational_matrix_py}" || die \
    "release smoke format-boundary scenario no longer proves mutating plan refusal"
grep -Fq '"unsupported-book-format-version"' "${format_boundary_refusals_py}" || die \
    "release smoke format-boundary refusal owner no longer requires the exact public format-boundary error"
grep -Fq '_require_file_digest(' "${format_boundary_scenarios_py}" || die \
    "release smoke format-boundary scenario no longer proves public boundary checks preserve both artifacts"
grep -Fq 'native_sqlite_java_prefix' "${format_boundary_probe_execution_py}" || die \
    "release smoke format-boundary probe owner no longer supports an archive-native execution prefix"
grep -Fq 'native_sqlite_probe_classpath' "${format_boundary_probe_execution_py}" || die \
    "release smoke format-boundary probe owner no longer consumes the packaged probe artifact"
if grep -n -E 'ctypes|_ensure_native_probe_compiled|_native_probe_javac|javac' \
    "${format_boundary_scenarios_py}" \
    "${format_boundary_probe_execution_py}" >/dev/null; then
    die "release smoke format-boundary scenario still depends on a workflow-host native compiler or library loader"
fi
grep -Fq 'field_matrix.assert_complete()' "${field_execution_py}" || die \
    "release smoke runner no longer fails closed on uncovered live capabilities"
grep -Fq 'record_new_attestation_append' "${field_matrix_package_dir}/coverage.py" || die \
    "release smoke field matrix no longer requires new mutable-operation append evidence"
grep -Fq 'record_verified_artifact' "${field_matrix_package_dir}/coverage.py" || die \
    "release smoke field matrix no longer distinguishes artifact verification from invocation"
if grep -Fq 'StrEnum' "${field_matrix_package_dir}"/*.py; then
    die "release smoke field matrix uses StrEnum, which is unavailable to the compatibility-floor runtime"
fi
grep -Fq 'verify_attested_administrative_plan_and_read_only_plan(' \
    "${field_execution_py}" || die \
    "release smoke runner no longer executes the aggregate-plan attestation check"
grep -Fq 'config, operation_ids, runtime_contract.error_exit_codes' \
    "${field_execution_py}" || die \
    "release smoke runner no longer supplies the published error exits to aggregate-plan verification"
grep -Fq 'require_no_attestation_commit(read_only_payload' \
    "${workflow_package_dir}/plan_checks.py" || die \
    "release smoke aggregate-plan verification no longer requires explicit null attestationCommit for read-only plans"
grep -Fq 'run_cli_allow_failure' "${workflow_package_dir}/plan_checks.py" || die \
    "release smoke aggregate-plan verification no longer exercises a signed read-only refusal"
grep -Fq 'error_exit_codes["attestation-credentials-not-allowed"]' \
    "${workflow_package_dir}/plan_checks.py" || die \
    "release smoke signed read-only refusal no longer uses the published credential-policy exit code"
grep -Fq 'verify_reactivate_rename_plan(' "${workflow_package_dir}/plan_checks.py" || die \
    "release smoke no longer exercises same-account aggregate-plan reactivation and rename"
grep -Fq 'reactivate_rename_ledger_plan_request' "${fixture_writers_py}" || die \
    "release smoke no longer generates the same-account aggregate-plan fixture"
grep -Fq 'head_after_plan.operation_order == str(int(head_before_plan.operation_order) + 1)' \
    "${plan_reactivation_checks_py}" || die \
    "release smoke same-account aggregate-plan verification no longer requires one aggregate append"
grep -Fq 'verify_posting_plan(' "${workflow_package_dir}/plan_checks.py" || die \
    "release smoke no longer executes the aggregate plan containing a post-entry child"
grep -Fq 'assert_posting_plan_provenance(' "${plan_posting_checks_py}" || die \
    "release smoke aggregate posting-plan verification no longer checks query provenance"
grep -Fq '_assert_posting_plan_account_ledger_provenance(' \
    "${plan_posting_provenance_checks_py}" || die \
    "release smoke aggregate posting-plan verification no longer checks account-ledger provenance"
grep -Fq 'plans.posting.argument' "${plan_posting_checks_py}" || die \
    "release smoke aggregate posting-plan verification no longer executes its dedicated fixture"
grep -Fq 'posting_ledger_plan_request' "${fixture_writers_py}" || die \
    "release smoke no longer generates the aggregate post-entry plan fixture"
grep -Fq 'post-plan-bank-transfer' "${fixture_plan_contract_py}" || die \
    "release smoke fixture contract no longer protects the aggregate post-entry step"
grep -Fq 'operation_ids["capabilities"], "--output", "json", "--detail", "full"' \
    "${workflow_package_dir}/discovery_checks.py" || die \
    "release smoke runtime verification no longer requests the full capabilities contract"
grep -Fq 'payload_field(plan_template, "planId") == "general-workflow"' \
    "${workflow_package_dir}/discovery_checks.py" || die \
    "release smoke template verification no longer requires the canonical general workflow"
grep -Fq 'required_mapping(payload, "fullContract")' \
    "${workflow_package_dir}/discovery_assertions.py" || die \
    "release smoke assertions no longer require the full capabilities contract envelope"
grep -Fq 'required_mapping(full_contract, "responseModel")' \
    "${workflow_package_dir}/discovery_assertions.py" || die \
    "release smoke assertions no longer read responseModel from the full capabilities contract"
grep -Fq 'error_descriptor_exit_codes' "${workflow_package_dir}/discovery_assertions.py" || die \
    "release smoke assertions no longer derive published exit-code mappings from error descriptors"
grep -Fq 'error_exit_codes["protected-book-verification-failed"]' \
    "${workflow_package_dir}/rekey_failure_checks.py" || die \
    "release smoke wrong-key verification no longer uses the published protected-book verification exit code"
grep -Fq 'machine_prompt_failure_status == error_exit_codes["unsupported-output-selection"]' \
    "${workflow_package_dir}/request_failure_checks.py" || die \
    "release smoke machine-output prompt verification no longer uses the published unsupported-output-selection exit code"
grep -Fq 'terminal_prompt_failure_status == error_exit_codes["interactive-prompt-unavailable"]' \
    "${workflow_package_dir}/request_failure_checks.py" || die \
    "release smoke prompt verification no longer uses the published interactive prompt exit code"
grep -Fq 'error_exit_codes["invalid-request"]' \
    "${workflow_package_dir}/request_failure_checks.py" || die \
    "release smoke invalid-request verification no longer uses the published invalid-request exit code"
if grep -Fq 'required_mapping(payload, "responseModel")' \
    "${workflow_package_dir}/discovery_assertions.py"; then
    die "release smoke assertions still read responseModel from the compact capabilities payload"
fi
grep -Fq '"--effective-date-as-of"' "${workflow_package_dir}/query_checks.py" || die \
    "release smoke query verification no longer uses the canonical trial-balance as-of flag"
grep -Fq 'list-postings continuation page did not preserve the accepted cursor' \
    "${workflow_package_dir}/pagination_checks.py" || die \
    "release smoke query verification no longer distinguishes accepted and continuation cursors"
grep -Fq 'list-postings terminal page unexpectedly emitted payload.nextCursor' \
    "${workflow_package_dir}/pagination_checks.py" || die \
    "release smoke query verification no longer checks terminal pagination shape"
if grep -Fq 'instead of 2' "${workflow_package_dir}/request_failure_checks.py"; then
    die "release smoke failure verification still hardcodes retired exit-code expectations"
fi
python3 - <<'PY' "${workflow_package_dir}/query_checks.py"
from pathlib import Path
import sys

for raw_path in sys.argv[1:]:
    path = Path(raw_path)
    text = path.read_text(encoding="utf-8")
    cursor = 0
    found = False
    marker = 'operation_ids["trialBalance"]'
    while True:
        index = text.find(marker, cursor)
        if index < 0:
            break
        found = True
        window = text[index : index + 400]
        if '"--effective-date-as-of"' not in window:
            raise SystemExit(
                f"error: {path.name} no longer uses the canonical trial-balance as-of flag"
            )
        if '"--effective-date-to"' in window:
            raise SystemExit(
                f"error: {path.name} uses the retired effective-date-to flag for trial-balance verification"
            )
        cursor = index + len(marker)
    if not found:
        raise SystemExit(f"error: {path.name} no longer exercises the trial-balance command")
PY
grep -Fq 'release-smoke-workflow.py' "${bundle_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer delegates to the shared Python workflow owner"
grep -Fq 'bundle-smoke-command-bridge.ps1' "${bundle_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer delegates Windows command execution through the bridge owner"
grep -Fq 'FINGRIND_RELEASE_SMOKE_COMMAND_BRIDGE_PREFIX_JSON' "${bundle_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer publishes the Windows bridge command contract"
grep -Fq 'Get-RepoUvExecutable' "${bundle_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer resolves the pinned uv launcher"
grep -Fq -- '--with-requirements' "${bundle_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer provisions release-smoke dependencies through uv"
grep -Fq 'release-smoke-common.sh' "${bundle_support_sh}" || die \
    "release-smoke-support.sh no longer sources the shared common helper owner"
if grep -Fq 'release-smoke-fixtures.sh' "${bundle_support_sh}"; then
    die "release-smoke-support.sh still sources the deleted Bash fixture owner"
fi
if grep -Fq 'release-smoke-assertions.sh' "${bundle_support_sh}"; then
    die "release-smoke-support.sh still sources the deleted Bash assertion owner"
fi

assert_source_only_guard() {
    local script_path=$1
    local expected_fragment=$2
    local output
    local status

    set +e
    output="$(bash "${script_path}" 2>&1)"
    status=$?
    set -e

    [[ ${status} -ne 0 ]] || die "${script_path} unexpectedly succeeded when executed directly"
    [[ "${output}" == *"${expected_fragment}"* ]] || die \
        "${script_path} did not explain that it must be sourced"
}

assert_source_only_guard \
    "${common_support_sh}" \
    "release-smoke-common.sh is a library and must be sourced by a release-smoke support script."
assert_source_only_guard \
    "${bundle_support_sh}" \
    "release-smoke-support.sh is a library and must be sourced by a release-smoke entrypoint."
assert_source_only_guard \
    "${workflow_support_sh}" \
    "release-smoke-workflow-support.sh is a library and must be sourced by release-smoke-support.sh."
assert_source_only_guard \
    "${bundle_compatibility_floor_support_sh}" \
    "bundle-smoke-compatibility-floor-support.sh is a library and must be sourced by bundle-smoke.sh."
grep -Fq 'bundle-smoke-compatibility-floor-support.sh' "${bundle_smoke_sh}" || die \
    "bundle-smoke.sh no longer delegates the compatibility-floor rerun to its dedicated helper"
grep -Fq 'verify-bundle-archive-contract.py' "${bundle_smoke_sh}" || die \
    "bundle-smoke.sh no longer verifies extracted host bundles through the canonical bundle-contract owner"
grep -Fq 'FINGRIND_RELEASE_SMOKE_WORK_ROOT' "${bundle_smoke_sh}" || die \
    "bundle-smoke.sh no longer publishes the compact shared work-root contract"
grep -Fq 'Bundle acceptance: using archive' "${bundle_smoke_sh}" || die \
    "bundle-smoke.sh no longer reports which archive the acceptance run selected"
grep -Fq 'FINGRIND_RELEASE_SMOKE_ARGUMENT_PATH_MODE' "${bundle_smoke_sh}" || die \
    "bundle-smoke.sh no longer publishes the shared argument-path-mode contract"
grep -Fq 'FINGRIND_RELEASE_SMOKE_SCENARIO_ID' "${bundle_smoke_sh}" || die \
    "bundle-smoke.sh no longer publishes the shared scenario-id contract"
grep -Fq 'compatibility-floor' "${bundle_smoke_sh}" || die \
    "bundle-smoke.sh no longer exposes the compatibility-floor execution surface"
grep -Fq 'compatibilitySmokeContainerImage' "${bundle_compatibility_floor_support_sh}" || die \
    "compatibility-floor support no longer reads the contract-owned compatibility-floor image"
grep -Fq 'verify-bundle-archive-contract.py' "${bundle_compatibility_floor_support_sh}" || die \
    "compatibility-floor support no longer verifies extracted target bundles inside the compatibility container"
grep -Fq 'FINGRIND_RELEASE_SMOKE_COMMAND_CWD=/work' "${bundle_compatibility_floor_support_sh}" || die \
    "compatibility-floor support no longer binds relative-path CLI execution to the mounted work root"
if grep -Fq -- '--user "${compatibility_docker_run_user}"' "${bundle_compatibility_floor_support_sh}"; then
    die "compatibility-floor support must not attempt dependency provisioning as the invoking host identity"
fi
grep -Fq 'FINGRIND_COMPATIBILITY_CALLER_UID' "${bundle_compatibility_floor_support_sh}" || die \
    "compatibility-floor support no longer carries the invoking caller UID into the container"
grep -Fq 'FINGRIND_COMPATIBILITY_CALLER_GID' "${bundle_compatibility_floor_support_sh}" || die \
    "compatibility-floor support no longer carries the invoking caller GID into the container"
grep -Fq 'exec setpriv' "${bundle_compatibility_floor_support_sh}" || die \
    "compatibility-floor support no longer drops from dependency provisioning to the invoking identity"
grep -Fq -- '--clear-groups' "${bundle_compatibility_floor_support_sh}" || die \
    "compatibility-floor support no longer clears root group membership before acceptance"
grep -Fq -- '-e HOME=/home/fingrind' "${bundle_compatibility_floor_support_sh}" || die \
    "compatibility-floor support no longer gives its unprivileged tooling a dedicated writable home"
grep -Fq -- '-v "${compatibility_home_root}:/home/fingrind"' "${bundle_compatibility_floor_support_sh}" || die \
    "compatibility-floor support no longer mounts its dedicated tooling home"
grep -Fq 'python3 -m pip install --user --disable-pip-version-check' \
    "${bundle_compatibility_floor_support_sh}" || die \
    "compatibility-floor support no longer bootstraps the pinned uv launcher"
grep -Fq 'fingrindUvVersion' "${bundle_compatibility_floor_support_sh}" || die \
    "compatibility-floor support no longer derives the pinned uv version from repository metadata"
grep -Fq 'release_smoke_run_office_worker_acceptance' "${bundle_compatibility_floor_support_sh}" || die \
    "compatibility-floor support no longer routes release smoke through the uv-backed workflow owner"
if grep -n -E 'compile_format_boundary_probe|javac|NativeSqliteFormatBoundaryProbe\.java' \
    "${bundle_compatibility_floor_support_sh}" >/dev/null; then
    die "compatibility-floor support still compiles the archive-native SQLite probe from an ambient JDK"
fi
if grep -n -E '_contains_only_precompiled_native_format_boundary_probe|\.fingrind-format-boundary-probe' \
    "${workflow_package_dir}/fixtures.py" >/dev/null; then
    die "release smoke fresh-root guard still carries a private compiler-output exception"
fi
grep -Fq 'FINGRIND_RELEASE_SMOKE_NATIVE_SQLITE_PROBE_CLASSPATH' "${bundle_smoke_sh}" || die \
    "bundle smoke no longer publishes the packaged native SQLite probe classpath"
grep -Fq 'FINGRIND_RELEASE_SMOKE_NATIVE_SQLITE_PROBE_CLASSPATH' \
    "${bundle_compatibility_floor_support_sh}" || die \
    "compatibility-floor smoke no longer publishes the packaged native SQLite probe classpath"
grep -Fq 'FINGRIND_RELEASE_SMOKE_WORK_ROOT' "${docker_smoke_sh}" || die \
    "docker-smoke.sh no longer publishes the compact shared work-root contract"
grep -Fq 'FINGRIND_RELEASE_SMOKE_REPORTED_WORK_ROOT' "${docker_smoke_sh}" || die \
    "docker-smoke.sh no longer publishes the shared reported-work-root contract"
grep -Fq 'FINGRIND_RELEASE_SMOKE_NATIVE_SQLITE_JAVA_PREFIX_JSON' "${docker_smoke_sh}" || die \
    "docker-smoke.sh no longer publishes the archive-native SQLite probe execution prefix"
grep -Fq 'FINGRIND_RELEASE_SMOKE_NATIVE_SQLITE_PROBE_CLASSPATH' "${docker_smoke_sh}" || die \
    "docker-smoke.sh no longer publishes the packaged native SQLite probe classpath"
grep -Fq -- '--entrypoint /opt/fingrind/runtime/bin/java' "${docker_smoke_sh}" || die \
    "docker-smoke.sh no longer runs the SQLite format probe through the container runtime"
grep -Fq "container runtime omitted jdk.crypto.ec, which Ed25519 attestation credentials require" \
    "${docker_smoke_sh}" || die \
    "docker-smoke.sh no longer requires the Ed25519 JCA provider in the linked runtime"
grep -Fq 'readonly working_directory="$(pwd -P)"' "${docker_entrypoint_sh}" || die \
    "Docker entrypoint no longer resolves a physical mounted working directory for its private user home"
grep -Fq -- '-Duser.home="${working_directory}"' "${docker_entrypoint_sh}" || die \
    "Docker entrypoint no longer binds Java user.home to its mounted working directory"
grep -Fq 'FINGRIND_RELEASE_SMOKE_ARGUMENT_PATH_MODE' "${docker_smoke_sh}" || die \
    "docker-smoke.sh no longer publishes the shared argument-path-mode contract"
grep -Fq 'FINGRIND_RELEASE_SMOKE_SCENARIO_ID' "${docker_smoke_sh}" || die \
    "docker-smoke.sh no longer publishes the shared scenario-id contract"
if grep -Fq 'FINGRIND_RELEASE_SMOKE_REQUEST_SALE_ARG' "${bundle_smoke_sh}"; then
    die "bundle-smoke.sh still exports legacy per-path release-smoke arguments"
fi
if grep -Fq 'FINGRIND_RELEASE_SMOKE_REQUEST_SALE_ARG' "${docker_smoke_sh}"; then
    die "docker-smoke.sh still exports legacy per-path release-smoke arguments"
fi
grep -Fq 'registerNativeSqliteFormatBoundaryProbe' "${distribution_plugin}" || die \
    "CLI distribution no longer registers the native SQLite format-boundary probe through Gradle"
grep -Fq 'compileNativeSqliteFormatBoundaryProbe' "${native_format_boundary_probe_registration}" || die \
    "native SQLite format-boundary probe registration no longer compiles the probe through Gradle"
grep -Fq 'packageNativeSqliteFormatBoundaryProbe' "${native_format_boundary_probe_registration}" || die \
    "native SQLite format-boundary probe registration no longer packages the probe"
grep -Fq 'sourceCheckoutJavaCompiler' "${distribution_plugin}" || die \
    "CLI distribution no longer selects the Gradle-owned Java toolchain compiler for the probe"
grep -Fq 'javaCompiler.set(javaCompilerExecutable)' "${native_format_boundary_probe_registration}" || die \
    "native SQLite format-boundary probe registration no longer receives the Gradle-owned compiler"
grep -Fq 'nativeFormatBoundaryProbePath' "${distribution_plugin}" || die \
    "CLI bundle no longer stages the packaged native SQLite format-boundary probe"
grep -Fq 'nativeSqliteFormatBoundaryProbeJar' "${docker_context_registration}" || die \
    "Docker context no longer stages the packaged native SQLite format-boundary probe"
grep -Fq 'native-sqlite-format-boundary-probe.jar' "${docker_source_inventory}" || die \
    "Docker source inventory no longer declares the packaged native SQLite format-boundary probe"
grep -Fq 'NativeSqliteFormatBoundaryProbe.java' "${docker_source_inventory}" || die \
    "Docker source inventory no longer fingerprints native SQLite probe source changes"
grep -Fq 'native-sqlite-format-boundary-probe.jar' "${dockerfile}" || die \
    "Docker image no longer installs the packaged native SQLite format-boundary probe"
grep -Fq 'sha256sum -c -s -' "${dockerfile}" || die \
    "Docker image no longer verifies the downloaded Zulu archive with BusyBox-portable sha256sum options"
if grep -Fq 'sha256sum --check --status' "${dockerfile}"; then
    die "Docker image still uses GNU-only sha256sum long options on Alpine"
fi
grep -Fq 'test -x /opt/zulu/bin/jlink' "${dockerfile}" || die \
    "Docker image no longer verifies that the downloaded Zulu archive contains jlink"
grep -Fq 'RUN "${JAVA_HOME}/bin/jlink"' "${dockerfile}" || die \
    "Docker image no longer invokes jlink through its explicit Zulu toolchain path"
grep -Fq '_verify_native_format_boundary_probe' "${bundle_root_verifier}" || die \
    "bundle verifier no longer validates the packaged native SQLite format-boundary probe"
if grep -Fq 'scratch directory' "${native_format_boundary_probe_java}"; then
    die "native SQLite format-boundary probe documentation still describes field-time scratch compilation"
fi

# shellcheck source=/dev/null
source "${python_runtime_support}"

prepare_python_runtime_env

python3 -m py_compile "${workflow_py}" >/dev/null
find "${workflow_package_dir}" -name '*.py' -exec python3 -m py_compile {} + >/dev/null
python3 -m py_compile "${workflow_contract_py}" >/dev/null
fingrind_run_python_with_tools "${workflow_contract_py}" "${repo_root}"

set +e
missing_env_output="$(fingrind_run_python_with_tools "${workflow_py}" 2>&1)"
missing_env_status=$?
set -e
[[ "${missing_env_status}" -ne 0 ]] || die \
    "shared release smoke workflow unexpectedly succeeded without required environment wiring"
printf '%s\n' "${missing_env_output}" | grep -Fq 'FINGRIND_RELEASE_SMOKE_COMMAND_PREFIX_JSON' || die \
    "shared release smoke workflow did not fail through its required-environment guard"

printf 'release smoke workflow wiring regression: success\n'
