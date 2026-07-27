"""Named facts shared across query and receipt matrix scenarios."""

from __future__ import annotations

from dataclasses import dataclass

from ..models import SmokePath


@dataclass(frozen=True)
class AttestationHeadFacts:
    book_id: str
    operation_order: str
    operation_head: str
    previous_head: str


@dataclass(frozen=True)
class ReceiptFacts:
    receipt_path: SmokePath
    book_id: str
    operation_order: str
    operation_head: str
