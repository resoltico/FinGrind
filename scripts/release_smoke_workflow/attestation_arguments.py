from __future__ import annotations

from .models import ReleaseSmokeConfig


def founder_credential_arguments(config: ReleaseSmokeConfig) -> list[str]:
    return [
        "--attestation-founder-principal-id",
        config.attestation_founder_principal_id,
        "--attestation-founder-key-file",
        config.attestation_founder_key.argument,
        "--attestation-founder-passphrase-file",
        config.attestation_founder_passphrase.argument,
    ]


def signing_credential_arguments(config: ReleaseSmokeConfig) -> list[str]:
    return [
        "--attestation-principal-id",
        config.attestation_founder_principal_id,
        "--attestation-key-file",
        config.attestation_founder_key.argument,
        "--attestation-passphrase-file",
        config.attestation_founder_passphrase.argument,
    ]
