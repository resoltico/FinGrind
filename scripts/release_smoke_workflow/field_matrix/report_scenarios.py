"""Live all-mode and PDF-artifact coverage for every advertised report command."""

from __future__ import annotations

from collections.abc import Mapping

from ..models import ReleaseSmokeConfig
from ..support import require
from .capabilities import CapabilityMatrix, OperationCapability
from .invocation import invoke_all_advertised_modes
from .report_contexts import ReportBookContext, TypedRecordMatrixWorlds
from .report_csv_pdf_refusal import _verify_csv_pdf_refusal
from .report_output_semantics import _assert_report_mode_semantics
from .report_pdf_artifacts import _verify_pdf_artifact, verify_pdf_intermediate_symlink_refusal
from .report_routes import (
    _CSV_PDF_REFUSAL_CODE,
    _REPORT_ROUTES,
    _REPORT_SUBSTANTIVE_FACT_CONTAINERS,
    _TAX_PERIOD_END,
    _TAX_PERIOD_START,
)
from .scenario_matrix import SCENARIO_MATRIX, ScenarioDomain
from .tax_report_setup import TaxReportFact

_INVALID_OUTPUT_DIRECTORY_CODE = "invalid-artifact-output-directory"


def verify_report_matrix(
    config: ReleaseSmokeConfig,
    matrix: CapabilityMatrix,
    typed_worlds: TypedRecordMatrixWorlds,
    tax_fact: TaxReportFact,
    error_exit_codes: Mapping[str, int],
) -> None:
    """Run every report in every live mode against retained substantive facts."""
    print(f"{config.label}: verifying all report capability modes and PDF artifacts")
    live_report_operations = _live_report_operations(matrix)
    _require_exact_report_routing(live_report_operations)
    expected_csv_pdf_refusal_exit_code = error_exit_codes.get(_CSV_PDF_REFUSAL_CODE)
    require(
        isinstance(expected_csv_pdf_refusal_exit_code, int)
        and not isinstance(expected_csv_pdf_refusal_exit_code, bool),
        f"{config.label} capabilities did not publish one exit code for {_CSV_PDF_REFUSAL_CODE}",
    )
    if not isinstance(expected_csv_pdf_refusal_exit_code, int) or isinstance(
        expected_csv_pdf_refusal_exit_code, bool
    ):
        raise TypeError("require must reject a missing CSV/PDF refusal exit code")
    expected_invalid_output_directory_exit_code = error_exit_codes.get(
        _INVALID_OUTPUT_DIRECTORY_CODE
    )
    require(
        isinstance(expected_invalid_output_directory_exit_code, int)
        and not isinstance(expected_invalid_output_directory_exit_code, bool),
        f"{config.label} capabilities did not publish one exit code for "
        f"{_INVALID_OUTPUT_DIRECTORY_CODE}",
    )
    if not isinstance(expected_invalid_output_directory_exit_code, int) or isinstance(
        expected_invalid_output_directory_exit_code, bool
    ):
        raise TypeError("require must reject a missing PDF output-directory refusal exit code")
    report_contexts = _report_contexts(config, typed_worlds, tax_fact)
    for operation in live_report_operations:
        route = _REPORT_ROUTES[operation.operation_id]
        context = report_contexts[route.context_name]
        arguments = route.arguments(context, tax_fact)
        invoke_all_advertised_modes(
            context.config,
            operation,
            lambda _mode, arguments=arguments: arguments,
            "report capability matrix",
            lambda output_mode, output, context=context, operation=operation: (
                _assert_report_mode_semantics(
                    context,
                    operation,
                    output_mode,
                    output,
                    tax_fact,
                )
            ),
        )
        for artifact_index, artifact in enumerate(operation.artifact_outputs, start=1):
            _verify_pdf_artifact(context, operation, arguments, artifact, artifact_index)
            _verify_csv_pdf_refusal(
                context,
                operation,
                arguments,
                artifact,
                artifact_index,
                expected_csv_pdf_refusal_exit_code,
            )
    security_operation = live_report_operations[0]
    security_route = _REPORT_ROUTES[security_operation.operation_id]
    security_context = report_contexts[security_route.context_name]
    verify_pdf_intermediate_symlink_refusal(
        security_context,
        security_operation,
        security_route.arguments(security_context, tax_fact),
        security_operation.artifact_outputs[0],
        expected_invalid_output_directory_exit_code,
    )


def _report_contexts(
    config: ReleaseSmokeConfig,
    typed_worlds: TypedRecordMatrixWorlds,
    tax_fact: TaxReportFact,
) -> Mapping[str, ReportBookContext]:
    return {
        "tax": ReportBookContext(
            config=config,
            period_start=_TAX_PERIOD_START,
            period_end=_TAX_PERIOD_END,
            as_of=_TAX_PERIOD_END,
            account_code=config.starter_cash_account_code,
            expected_report_tokens=(("tax-obligation", tax_fact.tax_code),),
        ),
        "commercial": typed_worlds.commercial,
        "inventory": typed_worlds.inventory,
        "accrual": typed_worlds.accrual,
        "payroll": typed_worlds.payroll,
        "fixed_asset": typed_worlds.fixed_asset,
        "financing": typed_worlds.financing,
        "foreign_exchange": typed_worlds.foreign_exchange,
    }


def _require_exact_report_routing(live_operations: tuple[OperationCapability, ...]) -> None:
    live_operation_ids = {operation.operation_id for operation in live_operations}
    configured_operation_ids = set(_REPORT_ROUTES)
    missing_arguments = sorted(live_operation_ids - configured_operation_ids)
    stale_arguments = sorted(configured_operation_ids - live_operation_ids)
    configured_content_ids = set(_REPORT_SUBSTANTIVE_FACT_CONTAINERS)
    missing_content_assertions = sorted(live_operation_ids - configured_content_ids)
    stale_content_assertions = sorted(configured_content_ids - live_operation_ids)
    require(
        not missing_arguments
        and not stale_arguments
        and not missing_content_assertions
        and not stale_content_assertions,
        _report_routing_mismatch_message(
            missing_arguments,
            stale_arguments,
            missing_content_assertions,
            stale_content_assertions,
        ),
    )


def _live_report_operations(matrix: CapabilityMatrix) -> tuple[OperationCapability, ...]:
    routed_reports = tuple(
        operation
        for operation in matrix.operations.values()
        if operation.category == "query"
        and SCENARIO_MATRIX[operation.operation_id].domain == ScenarioDomain.REPORT
    )
    pdf_operations = matrix.operations_with_artifact("pdf")
    require(
        {operation.operation_id for operation in routed_reports}
        == {operation.operation_id for operation in pdf_operations},
        "field-matrix report scenario routing differs from live PDF artifact capabilities",
    )
    for operation in routed_reports:
        artifact_formats = {artifact.format for artifact in operation.artifact_outputs}
        require(
            artifact_formats == {"pdf"},
            f"field-matrix report {operation.operation_id} advertises unsupported artifact formats: "
            + ", ".join(sorted(artifact_formats - {"pdf"})),
        )
    return routed_reports


def _report_routing_mismatch_message(
    missing_arguments: list[str],
    stale_arguments: list[str],
    missing_content_assertions: list[str],
    stale_content_assertions: list[str],
) -> str:
    parts = ["field-matrix report argument routing differs from live PDF report capabilities"]
    if missing_arguments:
        parts.append("missing report routing: " + ", ".join(missing_arguments))
    if stale_arguments:
        parts.append("stale report routing: " + ", ".join(stale_arguments))
    if missing_content_assertions:
        parts.append("missing content assertions: " + ", ".join(missing_content_assertions))
    if stale_content_assertions:
        parts.append("stale content assertions: " + ", ".join(stale_content_assertions))
    return "; ".join(parts)
