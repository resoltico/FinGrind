"""Filesystem and byte-level PDF artifact invariants."""

from __future__ import annotations

import os
import re
import stat

from ..models import ReleaseSmokeConfig, ReleaseSmokeFailure, SmokePath
from ..support import require

_PDF_TRAILING_WHITESPACE = b" \t\r\n\f\x00"
_PDF_TRAILER = re.compile(rb"startxref\s+\d+\s+%%EOF$")


def assert_complete_pdf_file(
    config: ReleaseSmokeConfig,
    artifact_path: SmokePath,
    purpose: str,
) -> None:
    """Require a regular, complete PDF byte stream before parser validation."""
    try:
        artifact_status = artifact_path.local_path.lstat()
    except OSError as exc:
        raise ReleaseSmokeFailure(
            f"{config.label} {purpose} did not create a readable PDF artifact: "
            f"{artifact_path.local_path}"
        ) from exc
    require(
        stat.S_ISREG(artifact_status.st_mode),
        f"{config.label} {purpose} did not create a regular PDF artifact: {artifact_path.local_path}",
    )
    try:
        pdf_bytes = artifact_path.local_path.read_bytes()
    except OSError as exc:
        raise ReleaseSmokeFailure(
            f"{config.label} {purpose} could not read its PDF artifact: {artifact_path.local_path}"
        ) from exc
    require(
        len(pdf_bytes) > len(b"%PDF-\n%%EOF"),
        f"{config.label} {purpose} created an implausibly short PDF artifact",
    )
    require(
        pdf_bytes.startswith(b"%PDF-"),
        f"{config.label} {purpose} PDF artifact did not start with %PDF-",
    )
    trailing_bytes = pdf_bytes.rstrip(_PDF_TRAILING_WHITESPACE)
    require(
        _PDF_TRAILER.search(trailing_bytes) is not None,
        f"{config.label} {purpose} PDF artifact did not contain a complete cross-reference trailer",
    )


def assert_platform_private_pdf(
    config: ReleaseSmokeConfig,
    artifact_path: SmokePath,
    purpose: str,
) -> None:
    """Require platform-specific private access for generated report artifacts."""
    if os.name == "posix":
        permissions = stat.S_IMODE(artifact_path.local_path.stat().st_mode)
        require(
            permissions == 0o600,
            f"{config.label} {purpose} PDF artifact did not use 0600 permissions",
        )
        return
    if os.name == "nt":
        require(
            config.book_key_output_permissions == "owner-only-acl",
            f"{config.label} {purpose} requires the Windows owner-only ACL artifact contract",
        )
        require(
            artifact_path.local_path.parent == config.trial_balance_pdf.local_path.parent,
            f"{config.label} {purpose} escaped the prepared owner-only PDF directory",
        )
        return
    raise ReleaseSmokeFailure(
        f"{config.label} {purpose} cannot verify private PDF permissions on this platform"
    )
