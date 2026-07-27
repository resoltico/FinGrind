from __future__ import annotations

from .attestation_diagnostic_catalog import (
    admission_diagnostics,
    required_admission_diagnostic,
    required_verification_diagnostic,
    verification_diagnostics,
)
from .support import require


def assert_diagnostic_catalog_contracts() -> None:
    assert_admission_diagnostic_catalog_contract()
    assert_verification_diagnostic_catalog_contract()


def assert_verification_diagnostic_catalog_contract() -> None:
    catalog = verification_diagnostics(
        {
            "attestationVerificationDiagnostics": [
                {
                    "surface": "synthetic-verification-surface",
                    "diagnostics": [
                        {
                            "code": "synthetic-verification-code",
                            "message": "Synthetic verification message.",
                            "hint": "Synthetic verification hint.",
                        }
                    ],
                }
            ]
        },
        "synthetic capabilities output",
    )
    diagnostic = required_verification_diagnostic(
        catalog,
        "synthetic-verification-surface",
        "synthetic-verification-code",
        "synthetic capabilities output",
    )
    require(
        diagnostic.message == "Synthetic verification message."
        and diagnostic.hint == "Synthetic verification hint.",
        "verification diagnostic catalog did not preserve exact diagnostic values",
    )


def assert_admission_diagnostic_catalog_contract() -> None:
    catalog = admission_diagnostics(
        {
            "attestationAdmissionDiagnostics": [
                {
                    "context": "ordinary-live-admission",
                    "diagnostics": [
                        {
                            "code": "synthetic-admission-code",
                            "message": "Synthetic admission message.",
                            "hint": "Synthetic admission hint.",
                        }
                    ],
                },
                {
                    "context": "registry-mutation",
                    "diagnostics": [
                        {
                            "code": "synthetic-registry-code",
                            "message": "Synthetic registry message.",
                            "hint": "Synthetic registry hint.",
                        }
                    ],
                },
                {
                    "context": "backup-acknowledgement",
                    "diagnostics": [
                        {
                            "code": "synthetic-backup-code",
                            "message": "Synthetic backup message.",
                            "hint": "Synthetic backup hint.",
                        }
                    ],
                },
            ]
        },
        "synthetic capabilities output",
    )
    diagnostic = required_admission_diagnostic(
        catalog,
        "ordinary-live-admission",
        "synthetic-admission-code",
        "synthetic capabilities output",
    )
    require(
        diagnostic.message == "Synthetic admission message."
        and diagnostic.hint == "Synthetic admission hint.",
        "admission diagnostic catalog did not preserve exact diagnostic values",
    )
