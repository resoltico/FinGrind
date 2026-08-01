"""Composable fixture constructor for one typed-record posting request."""

from __future__ import annotations

from collections.abc import Mapping

from .typed_record_models import TypedRecordRequest
from .typed_record_payloads import _posting_request


def _record_request(
    operation_key: str,
    request_prefix: str,
    request_label: str,
    entry_kind: str,
    effective_date: str,
    source_document_type: str,
    details: Mapping[str, object],
) -> TypedRecordRequest:
    return TypedRecordRequest(
        operation_key,
        _posting_request(
            request_prefix,
            request_label,
            entry_kind,
            effective_date,
            source_document_type,
            details,
        ),
    )
