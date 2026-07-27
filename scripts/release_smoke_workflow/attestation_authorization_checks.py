from __future__ import annotations

from .attestation_arguments import ATTESTATION_CUSTODIAN, signing_credential_arguments
from .attestation_authorization_commands import mutate_with_request, successful_payload
from .attestation_authorization_probe_files import (
    prepare_authorization_probe,
    write_authorization_probe_requests,
)
from .attestation_diagnostic_catalog import (
    AttestationDiagnostic,
    required_admission_diagnostic,
)
from .attestation_diagnostic_checks import require_exact_rejected_diagnostic
from .attestation_head_checks import verified_attestation_head
from .cli import run_cli
from .models import ReleaseSmokeConfig, SmokePath
from .support import require


def verify_exact_attestation_authorization_diagnostics(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    attestation_admission_diagnostics: dict[str, dict[str, AttestationDiagnostic]],
) -> None:
    print(f"{config.label}: verifying exact attestation authorization diagnostics")
    probe = prepare_authorization_probe(config)
    generated_key = successful_payload(
        run_cli(
            config,
            operation_ids["generateAttestationKeyFile"],
            "--attestation-custodian",
            ATTESTATION_CUSTODIAN,
            "--new-attestation-key-file",
            probe.key.argument,
            "--attestation-passphrase-file",
            probe.passphrase.argument,
            "--output",
            "json",
        ),
        config,
        "generate-attestation-key-file",
    )
    require(
        probe.key.local_path.is_file(),
        f"{config.label} did not create the authorization-probe credential",
    )
    spki = generated_key.get("credentialSpki")
    require(
        isinstance(spki, str) and spki,
        f"{config.label} did not publish the authorization-probe credential SPKI",
    )
    requests = write_authorization_probe_requests(config, probe, spki)
    founder_credentials = signing_credential_arguments(config)
    second_credentials = [
        "--attestation-principal-id",
        probe.principal_id,
        "--attestation-key-file",
        probe.key.argument,
        "--attestation-passphrase-file",
        probe.passphrase.argument,
    ]

    mutate_with_request(
        config, operation_ids["enrollKey"], requests["enroll"], founder_credentials, "enroll-key"
    )
    mutate_with_request(
        config,
        operation_ids["alterPolicy"],
        requests["postQuorumTwo"],
        founder_credentials,
        "alter-policy post quorum two",
    )
    _require_diagnostic(
        config,
        operation_ids,
        operation_ids["recordSaleSettled"],
        requests["saleQuorumTwo"],
        founder_credentials,
        _required_ordinary_admission_diagnostic(
            attestation_admission_diagnostics, "attestation-quorum-below", config
        ),
    )
    mutate_with_request(
        config,
        operation_ids["recordSaleSettled"],
        requests["saleQuorumTwo"],
        [*founder_credentials, *second_credentials],
        "two-signature post quorum",
    )
    mutate_with_request(
        config,
        operation_ids["alterPolicy"],
        requests["postQuorumOne"],
        founder_credentials,
        "alter-policy post quorum one",
    )
    _require_diagnostic(
        config,
        operation_ids,
        operation_ids["recordSaleSettled"],
        requests["saleQuorumExcess"],
        [*founder_credentials, *second_credentials],
        _required_ordinary_admission_diagnostic(
            attestation_admission_diagnostics, "attestation-quorum-excess", config
        ),
    )
    mutate_with_request(
        config,
        operation_ids["alterPolicy"],
        requests["revokeSecondPost"],
        founder_credentials,
        "revoke authorization-probe post grant",
    )
    _require_diagnostic(
        config,
        operation_ids,
        operation_ids["recordSaleSettled"],
        requests["saleCapabilityInvalid"],
        ["--attestation-custodian", ATTESTATION_CUSTODIAN, *second_credentials],
        _required_ordinary_admission_diagnostic(
            attestation_admission_diagnostics, "attestation-capability-invalid", config
        ),
    )
    _require_diagnostic(
        config,
        operation_ids,
        operation_ids["recordSaleSettled"],
        requests["saleKeyPrincipalMismatch"],
        [
            "--attestation-custodian",
            ATTESTATION_CUSTODIAN,
            "--attestation-principal-id",
            config.attestation_founder_principal_id,
            "--attestation-key-file",
            probe.key.argument,
            "--attestation-passphrase-file",
            probe.passphrase.argument,
        ],
        _required_ordinary_admission_diagnostic(
            attestation_admission_diagnostics,
            "attestation-key-principal-mismatch",
            config,
        ),
    )


def _require_diagnostic(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    operation: str,
    request: SmokePath,
    credentials: list[str],
    diagnostic: AttestationDiagnostic,
) -> None:
    head_before = verified_attestation_head(
        config, operation_ids, f"before {diagnostic.code} authorization rejection"
    )
    require_exact_rejected_diagnostic(
        config,
        (
            operation,
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--request-file",
            request.argument,
            *credentials,
        ),
        diagnostic.code,
        diagnostic.message,
        diagnostic.hint,
        f"{config.label} {diagnostic.code} authorization rejection",
    )
    require(
        verified_attestation_head(
            config, operation_ids, f"after {diagnostic.code} authorization rejection"
        )
        == head_before,
        f"{config.label} {diagnostic.code} changed the verified attestation head",
    )


def _required_ordinary_admission_diagnostic(
    attestation_admission_diagnostics: dict[str, dict[str, AttestationDiagnostic]],
    code: str,
    config: ReleaseSmokeConfig,
) -> AttestationDiagnostic:
    return required_admission_diagnostic(
        attestation_admission_diagnostics,
        "ordinary-live-admission",
        code,
        f"{config.label} capabilities output",
    )
