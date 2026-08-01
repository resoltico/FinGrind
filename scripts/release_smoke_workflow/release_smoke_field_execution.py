from __future__ import annotations

from dataclasses import replace

from . import (
    attestation_workflow_checks,
    maintenance_checks,
    maintenance_collision_checks,
    plan_checks,
    protected_book_tamper_checks,
    query_checks,
    raw_journal_checks,
    rekey_failure_checks,
    request_failure_checks,
    setup_checks,
)
from .discovery_checks import verify_help_and_template_surfaces
from .field_matrix import (
    SCENARIO_MATRIX,
    FieldMatrixSession,
    activate_field_matrix,
    administrative_scenarios,
    attestation_scale_scenario,
    discovery_scenarios,
    format_boundary_scenarios,
    query_scenarios,
    report_scenarios,
    tax_report_setup,
    typed_record_scenarios,
)
from .release_smoke_initialization import ReleaseSmokeRunContext


def execute_release_smoke_field_matrix(context: ReleaseSmokeRunContext) -> None:
    config = context.config
    operation_ids = context.operation_ids
    runtime_contract = context.runtime_contract
    field_matrix = FieldMatrixSession(runtime_contract.capability_matrix, SCENARIO_MATRIX)
    with activate_field_matrix(field_matrix):
        discovery_scenarios.verify_discovery_matrix(config, runtime_contract.capability_matrix)
        verify_help_and_template_surfaces(config, operation_ids)
        setup_checks.verify_book_key_generation(config, operation_ids)
        setup_checks.verify_open_book(config, operation_ids)
        format_boundary_scenarios.verify_protected_book_format_boundary_rejections(
            config,
            operation_ids,
            runtime_contract.protected_book_format,
            runtime_contract.error_exit_codes,
        )
        administrative_scenarios.verify_administrative_matrix(
            config, operation_ids, runtime_contract.capability_matrix
        )
        setup_checks.verify_account_registry(config, operation_ids)
        query_checks.verify_preflight_and_commit(config, operation_ids)
        query_checks.verify_operator_queries_and_reports(config, operation_ids)
        raw_journal_checks.verify_raw_journal_commit_and_readback(config, operation_ids)
        plan_checks.verify_attested_administrative_plan_and_read_only_plan(
            config, operation_ids, runtime_contract.error_exit_codes
        )
        tax_fact = tax_report_setup.prepare_tax_report_fact(config, operation_ids)
        query_scenarios.verify_query_matrix(
            config,
            runtime_contract.capability_matrix,
            runtime_contract.protected_book_format,
        )
        typed_worlds = typed_record_scenarios.verify_typed_record_matrix(config, operation_ids)
        report_scenarios.verify_report_matrix(
            config,
            runtime_contract.capability_matrix,
            typed_worlds,
            tax_fact,
            runtime_contract.error_exit_codes,
        )
        attestation_scale_scenario.verify_attestation_scale_scenario(config, operation_ids)
        attestation_workflow_checks.verify_attestation_workflow(
            config,
            operation_ids,
            runtime_contract.error_exit_codes,
            runtime_contract.attestation_admission_diagnostics,
            runtime_contract.attestation_verification_diagnostics,
            runtime_contract.capability_matrix,
        )
        maintenance_checks.verify_backup_restore_surfaces(
            config, operation_ids, runtime_contract.error_exit_codes
        )
        rekey_failure_checks.verify_rekey_and_wrong_key_semantics(
            config, operation_ids, runtime_contract.error_exit_codes
        )
        rekeyed_config = replace(config, book_key=config.replacement_book_key)
        protected_book_tamper_checks.verify_protected_book_byte_tamper_rejection(
            rekeyed_config,
            operation_ids,
            runtime_contract.error_exit_codes,
        )
        maintenance_collision_checks.verify_maintenance_collision_refusals(
            rekeyed_config,
            operation_ids,
        )
        request_failure_checks.verify_deterministic_nonsense_workflows(
            config, operation_ids, runtime_contract.error_exit_codes
        )
        field_matrix.assert_complete()
