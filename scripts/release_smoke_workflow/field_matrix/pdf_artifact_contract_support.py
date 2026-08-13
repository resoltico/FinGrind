"""Synthetic PDF fixtures and shared rejection proof for artifact contracts."""

from __future__ import annotations

import zlib
from collections.abc import Callable

from ..models import ReleaseSmokeFailure


def classic_pdf(objects: tuple[bytes, ...]) -> bytes:
    """Build one small valid classic-XRef PDF from ordered indirect objects."""
    prefix = b"%PDF-1.7\n%\xe2\xe3\xcf\xd3\n"
    indirect_objects = tuple(
        f"{number} 0 obj\n".encode("ascii") + body + b"\nendobj\n"
        for number, body in enumerate(objects, start=1)
    )
    offsets: list[int] = []
    current_offset = len(prefix)
    for indirect_object in indirect_objects:
        offsets.append(current_offset)
        current_offset += len(indirect_object)
    xref_offset = current_offset
    xref = (
        f"xref\n0 {len(objects) + 1}\n".encode("ascii")
        + b"0000000000 65535 f \n"
        + b"".join(f"{offset:010d} 00000 n \n".encode("ascii") for offset in offsets)
        + f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\n".encode("ascii")
        + f"startxref\n{xref_offset}\n%%EOF\n".encode("ascii")
    )
    return prefix + b"".join(indirect_objects) + xref


def object_stream_page_tree_pdf() -> bytes:
    """Build a valid PDF whose page-tree dictionary is compressed in an object stream."""
    prefix = b"%PDF-1.5\n%\xe2\xe3\xcf\xd3\n"
    catalog = b"1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
    page_tree = b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>"
    page = b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] /Resources << >> >>"
    object_stream_header = b"2 0 3 " + str(len(page_tree) + 1).encode("ascii") + b" "
    compressed_object_stream = zlib.compress(object_stream_header + page_tree + b" " + page)
    object_stream = (
        b"4 0 obj\n<< /Type /ObjStm /N 2 /First "
        + str(len(object_stream_header)).encode("ascii")
        + b" /Filter /FlateDecode /Length "
        + str(len(compressed_object_stream)).encode("ascii")
        + b" >>\nstream\n"
        + compressed_object_stream
        + b"\nendstream\nendobj\n"
    )
    catalog_offset = len(prefix)
    object_stream_offset = catalog_offset + len(catalog)
    xref_offset = object_stream_offset + len(object_stream)
    xref_entries = b"".join(
        (
            xref_stream_entry(0, 0, 65535),
            xref_stream_entry(1, catalog_offset, 0),
            xref_stream_entry(2, 4, 0),
            xref_stream_entry(2, 4, 1),
            xref_stream_entry(1, object_stream_offset, 0),
            xref_stream_entry(1, xref_offset, 0),
        )
    )
    xref_stream = (
        b"5 0 obj\n<< /Type /XRef /Size 6 /W [1 4 2] /Index [0 6] /Root 1 0 R /Length "
        + str(len(xref_entries)).encode("ascii")
        + b" >>\nstream\n"
        + xref_entries
        + b"\nendstream\nendobj\nstartxref\n"
        + str(xref_offset).encode("ascii")
        + b"\n%%EOF\n"
    )
    return prefix + catalog + object_stream + xref_stream


def complete_pdf() -> bytes:
    """Return the smallest complete single-page PDF used by artifact contracts."""
    return classic_pdf(
        (
            b"<< /Type /Catalog /Pages 2 0 R >>",
            b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] /Resources << >> >>",
        )
    )


def artifact_confirmation(path: str, publication_transaction: str) -> str:
    """Return the native text confirmation for one PDF path and public transaction."""
    return (
        f"Artifact\n========\n\nFormat : pdf\nPath   : {path}\n"
        f"Publication transaction : {publication_transaction}\n"
    )


def require_rejected(action: Callable[[], None], expected_message: str) -> None:
    """Require a release-smoke contract action to reject with its stable diagnostic."""
    try:
        action()
    except ReleaseSmokeFailure as exc:
        assert expected_message in str(exc)
        return
    raise AssertionError(f"PDF artifact validation accepted {expected_message!r}")


def xref_stream_entry(kind: int, first_field: int, second_field: int) -> bytes:
    """Encode one fixed-width cross-reference stream entry."""
    return bytes((kind,)) + first_field.to_bytes(4, "big") + second_field.to_bytes(2, "big")
