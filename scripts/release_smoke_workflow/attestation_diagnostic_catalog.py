from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from .support import require, require_string, required_list

_REQUIRED_ADMISSION_CONTEXTS = (
    "ordinary-live-admission",
    "registry-mutation",
    "backup-acknowledgement",
)


@dataclass(frozen=True)
class AttestationDiagnostic:
    code: str
    message: str
    hint: str


def admission_diagnostics(
    response_model: dict[str, Any],
    label: str,
) -> dict[str, dict[str, AttestationDiagnostic]]:
    diagnostics_by_context: dict[str, dict[str, AttestationDiagnostic]] = {}
    for context_descriptor in required_list(response_model, "attestationAdmissionDiagnostics"):
        require(
            isinstance(context_descriptor, dict),
            f"{label} included a malformed attestation admission diagnostics context",
        )
        context = require_string(context_descriptor, "context")
        require(
            context not in diagnostics_by_context,
            f"{label} repeated attestation admission diagnostics context {context}",
        )
        diagnostics_by_context[context] = _diagnostics_by_code(
            required_list(context_descriptor, "diagnostics"),
            f"{label} {context} attestation admission diagnostics",
        )
    require(
        diagnostics_by_context,
        f"{label} did not publish attestation admission diagnostics",
    )
    for context in _REQUIRED_ADMISSION_CONTEXTS:
        require(
            context in diagnostics_by_context,
            f"{label} did not publish attestation admission diagnostics for {context}",
        )
    return diagnostics_by_context


def verification_diagnostics(
    response_model: dict[str, Any],
    label: str,
) -> dict[str, dict[str, AttestationDiagnostic]]:
    diagnostics_by_surface: dict[str, dict[str, AttestationDiagnostic]] = {}
    for surface_descriptor in required_list(response_model, "attestationVerificationDiagnostics"):
        require(
            isinstance(surface_descriptor, dict),
            f"{label} included a malformed attestation verification diagnostics surface",
        )
        surface = require_string(surface_descriptor, "surface")
        require(
            surface not in diagnostics_by_surface,
            f"{label} repeated attestation verification diagnostics surface {surface}",
        )
        diagnostics_by_surface[surface] = _diagnostics_by_code(
            required_list(surface_descriptor, "diagnostics"),
            f"{label} {surface} attestation verification diagnostics",
        )
    require(
        diagnostics_by_surface,
        f"{label} did not publish attestation verification diagnostics",
    )
    return diagnostics_by_surface


def required_diagnostic(
    diagnostics: dict[str, AttestationDiagnostic],
    code: str,
    label: str,
) -> AttestationDiagnostic:
    diagnostic = diagnostics.get(code)
    require(diagnostic is not None, f"{label} did not publish {code}")
    return diagnostic


def required_verification_diagnostic(
    diagnostics_by_surface: dict[str, dict[str, AttestationDiagnostic]],
    surface: str,
    code: str,
    label: str,
) -> AttestationDiagnostic:
    diagnostics = diagnostics_by_surface.get(surface)
    require(
        diagnostics is not None,
        f"{label} did not publish attestation verification diagnostics for {surface}",
    )
    return required_diagnostic(diagnostics, code, f"{label} {surface}")


def required_admission_diagnostic(
    diagnostics_by_context: dict[str, dict[str, AttestationDiagnostic]],
    context: str,
    code: str,
    label: str,
) -> AttestationDiagnostic:
    diagnostics = diagnostics_by_context.get(context)
    require(
        diagnostics is not None,
        f"{label} did not publish attestation admission diagnostics for {context}",
    )
    return required_diagnostic(diagnostics, code, f"{label} {context}")


def _diagnostics_by_code(
    descriptors: list[Any],
    label: str,
) -> dict[str, AttestationDiagnostic]:
    diagnostics: dict[str, AttestationDiagnostic] = {}
    for descriptor in descriptors:
        require(isinstance(descriptor, dict), f"{label} included a malformed diagnostic")
        code = require_string(descriptor, "code")
        require(code not in diagnostics, f"{label} repeated {code}")
        diagnostics[code] = AttestationDiagnostic(
            code,
            require_string(descriptor, "message"),
            require_string(descriptor, "hint"),
        )
    require(diagnostics, f"{label} was empty")
    return diagnostics
