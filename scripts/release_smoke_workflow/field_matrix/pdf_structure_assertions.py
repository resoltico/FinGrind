"""Strict parser checks for the structural PDF page tree."""

from __future__ import annotations

from ..models import ReleaseSmokeConfig, ReleaseSmokeFailure, SmokePath
from ..support import require


def assert_resolved_pdf_page_tree(
    config: ReleaseSmokeConfig,
    artifact_path: SmokePath,
    purpose: str,
) -> None:
    """Require pypdf to resolve the catalog and every declared page.

    PDF object streams may validly compress the page-tree dictionary, so raw-byte
    marker checks cannot establish this invariant. The pinned parser must resolve
    the actual catalog and walk every declared page instead.
    """
    try:
        from pypdf import PdfReader
        from pypdf.errors import PdfReadError
    except ModuleNotFoundError as exc:
        raise ReleaseSmokeFailure(
            f"{config.label} {purpose} cannot validate PDF structure because the repo-owned "
            "pypdf extractor is unavailable. Run the release-smoke workflow through its pinned "
            "uv tool environment."
        ) from exc
    try:
        reader = PdfReader(str(artifact_path.local_path), strict=True)
        catalog = resolve_pdf_object(reader.trailer.get("/Root"))
        require(
            isinstance(catalog, dict) and catalog.get("/Type") == "/Catalog",
            f"{config.label} {purpose} PDF artifact did not resolve a catalog root",
        )
        page_tree = resolve_pdf_object(catalog.get("/Pages"))
        require(
            isinstance(page_tree, dict) and page_tree.get("/Type") == "/Pages",
            f"{config.label} {purpose} PDF artifact did not resolve a page-tree root",
        )
        declared_page_count = page_tree.get("/Count")
        require(
            isinstance(declared_page_count, int) and declared_page_count > 0,
            f"{config.label} {purpose} PDF artifact did not declare a positive page-tree count",
        )
        resolved_page_count = len(reader.pages)
        require(
            resolved_page_count == declared_page_count,
            f"{config.label} {purpose} PDF artifact did not resolve every declared page",
        )
    except (OSError, PdfReadError, ValueError, KeyError, TypeError, AttributeError) as exc:
        raise ReleaseSmokeFailure(
            f"{config.label} {purpose} pypdf rejected the PDF artifact"
        ) from exc


def resolve_pdf_object(value: object) -> object:
    """Dereference one pypdf indirect object without imposing its types here."""
    resolver = getattr(value, "get_object", None)
    return resolver() if callable(resolver) else value
