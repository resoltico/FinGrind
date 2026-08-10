"""Semantic and filesystem checks for report artifacts produced by the field matrix."""

from __future__ import annotations

import unicodedata
from dataclasses import dataclass

from ..artifact_contracts import reported_pdf_artifact_path_matches
from ..models import ReleaseSmokeConfig, ReleaseSmokeFailure, SmokePath
from ..path_support import extract_pdf_artifact_path, extract_pdf_publication_transaction
from ..support import require, require_match
from .artifact_publication_evidence import require_text_publication_transaction_evidence
from .pdf_file_assertions import assert_complete_pdf_file, assert_platform_private_pdf
from .pdf_structure_assertions import assert_resolved_pdf_page_tree


@dataclass(frozen=True)
class _PdfSemanticText:
    """The two pypdf readings needed to prove a rendered report's semantics."""

    layout: str
    default: str


def assert_pdf_artifact(
    config: ReleaseSmokeConfig,
    artifact_path: SmokePath,
    stdout: str,
    purpose: str,
    *,
    expected_document_title: str | None = None,
    expected_text_facts: tuple[str, ...] = (),
) -> None:
    """Prove a requested PDF is complete, private, and semantically its own report.

    Structural PDF markers and a truthful artifact confirmation say nothing
    about which report was rendered.  Report callers therefore pass the
    canonical document title and a scenario fact, both of which are verified
    from the PDF's extracted text layer before artifact coverage is credited.
    """
    _assert_pdf_confirmation(config, artifact_path, stdout, purpose)
    assert_complete_pdf_file(config, artifact_path, purpose)
    assert_resolved_pdf_page_tree(config, artifact_path, purpose)
    assert_platform_private_pdf(config, artifact_path, purpose)
    _assert_pdf_semantic_evidence(
        config,
        artifact_path,
        purpose,
        expected_document_title=expected_document_title,
        expected_text_facts=expected_text_facts,
    )


def _assert_pdf_confirmation(
    config: ReleaseSmokeConfig,
    artifact_path: SmokePath,
    stdout: str,
    purpose: str,
) -> None:
    require_match(
        stdout,
        r"^Artifact$",
        f"{config.label} {purpose} did not emit an artifact confirmation heading",
    )
    require_match(
        stdout,
        r"^Format[[:space:]]+:[[:space:]]+pdf$",
        f"{config.label} {purpose} did not report PDF artifact format",
    )
    reported_path = extract_pdf_artifact_path(stdout)
    require(
        reported_pdf_artifact_path_matches(config, artifact_path, reported_path),
        f"{config.label} {purpose} did not report the canonical physical PDF artifact path",
    )
    require(
        not any(line.strip().startswith("Retained stage") for line in stdout.splitlines()),
        f"{config.label} {purpose} exposed a private retained-stage fact after transaction migration",
    )
    require_text_publication_transaction_evidence(
        config,
        extract_pdf_publication_transaction(stdout),
        purpose,
    )


def _assert_pdf_semantic_evidence(
    config: ReleaseSmokeConfig,
    artifact_path: SmokePath,
    purpose: str,
    *,
    expected_document_title: str | None,
    expected_text_facts: tuple[str, ...],
) -> None:
    """Verify report identity and retained facts in the rendered PDF text layer."""
    require(
        expected_document_title is not None or not expected_text_facts,
        f"{config.label} {purpose} supplied PDF facts without a canonical document title",
    )
    if expected_document_title is None:
        return
    require(
        bool(expected_document_title.strip()),
        f"{config.label} {purpose} supplied a blank canonical PDF document title",
    )
    require(
        all(isinstance(fact, str) and bool(fact.strip()) for fact in expected_text_facts),
        f"{config.label} {purpose} supplied a blank expected PDF fact",
    )
    extracted_text = _extract_pdf_semantic_text(config, artifact_path, purpose)
    layout_pdf_text = _normalize_pdf_semantic_text(extracted_text.layout)
    default_pdf_text = _normalize_pdf_semantic_text(extracted_text.default)
    canonical_document_title = _normalize_pdf_semantic_text(expected_document_title)
    nonblank_lines = tuple(line.strip() for line in layout_pdf_text.splitlines() if line.strip())
    require(
        bool(nonblank_lines) and nonblank_lines[0] == canonical_document_title,
        f"{config.label} {purpose} PDF did not begin with its canonical report title "
        f"{expected_document_title!r}",
    )
    compact_pdf_texts = tuple(
        "".join(pdf_text.split()) for pdf_text in (layout_pdf_text, default_pdf_text)
    )
    missing_facts = [
        fact
        for fact in expected_text_facts
        if not any(
            "".join(_normalize_pdf_semantic_text(fact).split()) in compact_pdf_text
            for compact_pdf_text in compact_pdf_texts
        )
    ]
    require(
        not missing_facts,
        f"{config.label} {purpose} PDF did not retain required report facts: "
        + ", ".join(repr(fact) for fact in missing_facts),
    )


def _normalize_pdf_semantic_text(value: str) -> str:
    """Normalize typographic PDF glyphs before exact logical-text comparison.

    Embedded report fonts may expose a standard ``ff`` ligature as U+FB00 in a
    PDF text extractor even though the report's canonical title and facts use
    ordinary ASCII characters. NFKC removes that presentation-only variance
    without relaxing the subsequent exact semantic comparison.
    """
    return unicodedata.normalize("NFKC", value)


def _extract_pdf_semantic_text(
    config: ReleaseSmokeConfig,
    artifact_path: SmokePath,
    purpose: str,
) -> _PdfSemanticText:
    """Read layout and default text from one strict PDF parse.

    Layout text owns the visible masthead order. Default text preserves a wrapped
    table cell's content-stream order, so a long exact identifier remains one
    logical fact after whitespace compaction. Both readings come from the same
    strictly parsed artifact.
    """
    try:
        from pypdf import PdfReader
        from pypdf.errors import PdfReadError
    except ModuleNotFoundError as exc:
        raise ReleaseSmokeFailure(
            f"{config.label} {purpose} cannot prove PDF report semantics because the repo-owned "
            "pypdf extractor is unavailable. Run the release-smoke workflow through its pinned "
            "uv tool environment."
        ) from exc
    try:
        reader = PdfReader(str(artifact_path.local_path), strict=True)
        layout_pdf_text = "\n".join(
            extracted_text
            for page in reader.pages
            if (extracted_text := page.extract_text(extraction_mode="layout"))
        )
        default_pdf_text = "\n".join(
            extracted_text for page in reader.pages if (extracted_text := page.extract_text())
        )
    except (OSError, PdfReadError, ValueError) as exc:
        raise ReleaseSmokeFailure(
            f"{config.label} {purpose} pypdf rejected the PDF artifact"
        ) from exc
    require(
        bool(layout_pdf_text.strip()),
        f"{config.label} {purpose} PDF did not expose readable layout report text",
    )
    return _PdfSemanticText(layout=layout_pdf_text, default=default_pdf_text)
