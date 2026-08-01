from __future__ import annotations

from .attestation_authorization_checks import verify_exact_attestation_authorization_diagnostics
from .attestation_checks import verify_attestation_inspection_and_receipt_artifacts
from .attestation_diagnostic_catalog import AttestationDiagnostic
from .field_matrix.capabilities import CapabilityMatrix
from .models import ReleaseSmokeConfig


def verify_attestation_workflow(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    error_exit_codes: dict[str, int],
    attestation_admission_diagnostics: dict[str, dict[str, AttestationDiagnostic]],
    attestation_verification_diagnostics: dict[str, dict[str, AttestationDiagnostic]],
    capability_matrix: CapabilityMatrix,
) -> None:
    verify_exact_attestation_authorization_diagnostics(
        config, operation_ids, attestation_admission_diagnostics
    )
    verify_attestation_inspection_and_receipt_artifacts(
        config,
        operation_ids,
        error_exit_codes,
        attestation_verification_diagnostics,
        capability_matrix,
    )
