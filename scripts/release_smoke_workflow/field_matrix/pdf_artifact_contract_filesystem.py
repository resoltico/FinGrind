"""Path, truncation, symlink, and privacy contract checks for PDF artifacts."""

from __future__ import annotations

import os
from dataclasses import replace

from ..artifact_contracts import (
    expected_public_pdf_artifact_path_hint,
    reported_pdf_artifact_path_matches,
)
from ..models import ReleaseSmokeConfig, SmokePath
from ..path_support import extract_pdf_publication_transaction
from .artifact_assertions import assert_pdf_artifact
from .pdf_artifact_contract_support import artifact_confirmation, complete_pdf, require_rejected


def assert_path_mismatch_is_rejected(
    config: ReleaseSmokeConfig,
    artifact_path: SmokePath,
) -> None:
    """Require the rendered confirmation to name the requested artifact path."""
    require_rejected(
        lambda: assert_pdf_artifact(
            config,
            artifact_path,
            artifact_confirmation("<redacted>/different.pdf", "0123456789abcdef0123456789abcdef"),
            "mismatched artifact path",
        ),
        "canonical physical PDF artifact path",
    )


def assert_missing_publication_transaction_is_rejected(
    config: ReleaseSmokeConfig,
    artifact_path: SmokePath,
) -> None:
    """Require every successful PDF confirmation to disclose transaction evidence."""
    require_rejected(
        lambda: assert_pdf_artifact(
            config,
            artifact_path,
            "Artifact\n========\n\nFormat : pdf\nPath   : "
            + expected_public_pdf_artifact_path_hint(config, artifact_path)
            + "\n",
            "missing PDF publication transaction",
        ),
        "missing publication-transaction confirmation",
    )


def assert_private_retained_stage_is_rejected(
    config: ReleaseSmokeConfig,
    artifact_path: SmokePath,
) -> None:
    """Reject a report confirmation that leaks the private legacy stage after migration."""
    require_rejected(
        lambda: assert_pdf_artifact(
            config,
            artifact_path,
            artifact_confirmation(
                expected_public_pdf_artifact_path_hint(config, artifact_path),
                "0123456789abcdef0123456789abcdef",
            )
            + "Retained stage : <redacted>/.legacy-stage\n",
            "private retained PDF stage",
        ),
        "private retained-stage fact",
    )


def assert_public_hint_preserves_the_cli_visible_suffix(
    config: ReleaseSmokeConfig,
    artifact_path: SmokePath,
) -> None:
    """Require a redacted path to retain the physical artifact's three-segment suffix."""
    expected_hint = expected_public_pdf_artifact_path_hint(config, artifact_path)
    assert reported_pdf_artifact_path_matches(config, artifact_path, expected_hint)
    filename_only_hint = f"<redacted>/{artifact_path.local_path.name}"
    assert not reported_pdf_artifact_path_matches(config, artifact_path, filename_only_hint)


def assert_truncated_pdf_is_rejected(
    config: ReleaseSmokeConfig,
    artifact_path: SmokePath,
    stdout: str,
) -> None:
    """Reject an artifact that looks like PDF text but lacks a full trailer."""
    artifact_path.local_path.write_bytes(b"%PDF-1.7\n/Type /Catalog\n/Type /Pages\nstartxref\n0\n")
    if os.name == "posix":
        artifact_path.local_path.chmod(0o600)
    require_rejected(
        lambda: assert_pdf_artifact(config, artifact_path, stdout, "truncated PDF export"),
        "complete cross-reference trailer",
    )
    artifact_path.local_path.write_bytes(complete_pdf())
    if os.name == "posix":
        artifact_path.local_path.chmod(0o600)


def assert_symlink_is_rejected(
    config: ReleaseSmokeConfig,
    artifact_path: SmokePath,
    stdout: str,
) -> None:
    """Reject an existing symlink at the final artifact target instead of a new regular PDF."""
    if os.name != "posix":
        return
    target = artifact_path.local_path.with_name("symlink-target.pdf")
    target.write_bytes(complete_pdf())
    target.chmod(0o600)
    artifact_path.local_path.unlink()
    artifact_path.local_path.symlink_to(target.name)
    symlink_stdout = artifact_confirmation(
        expected_public_pdf_artifact_path_hint(config, artifact_path),
        extract_pdf_publication_transaction(stdout),
    )
    require_rejected(
        lambda: assert_pdf_artifact(
            config,
            artifact_path,
            symlink_stdout,
            "symlink PDF export",
        ),
        "regular PDF artifact",
    )
    artifact_path.local_path.unlink()
    artifact_path.local_path.write_bytes(complete_pdf())
    artifact_path.local_path.chmod(0o600)


def assert_platform_privacy_is_rejected(
    config: ReleaseSmokeConfig,
    artifact_path: SmokePath,
    stdout: str,
) -> None:
    """Reject an otherwise valid PDF that lacks the platform's private-access contract."""
    if os.name == "posix":
        artifact_path.local_path.chmod(0o644)
        require_rejected(
            lambda: assert_pdf_artifact(config, artifact_path, stdout, "shared PDF export"),
            "0600 permissions",
        )
        artifact_path.local_path.chmod(0o600)
        return
    if os.name == "nt":
        require_rejected(
            lambda: assert_pdf_artifact(
                replace(config, book_key_output_permissions="0600"),
                artifact_path,
                stdout,
                "non-ACL PDF export",
            ),
            "owner-only ACL artifact contract",
        )
