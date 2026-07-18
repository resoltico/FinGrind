from __future__ import annotations

from .cli import run_cli
from .models import ReleaseSmokeConfig
from .support import parse_json_output, payload_field, require


def verify_list_postings_pagination(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
) -> None:
    first_page = parse_json_output(
        run_cli(
            config,
            operation_ids["listPostings"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--limit",
            "1",
            "--output",
            "json",
        ),
        f"{config.label} list-postings first page was not valid JSON",
    )
    first_page_payload = payload_field(first_page, "payload")
    require(
        isinstance(first_page_payload, dict),
        f"{config.label} list-postings first page did not publish an object payload",
    )
    first_page_query = payload_field(first_page_payload, "resolvedQuery")
    require(
        isinstance(first_page_query, dict) and first_page_query.get("cursor") is None,
        f"{config.label} list-postings first page did not preserve the accepted empty cursor",
    )
    next_cursor = payload_field(first_page_payload, "nextCursor")
    require(
        isinstance(next_cursor, str) and next_cursor,
        f"{config.label} list-postings did not report payload.nextCursor for the first page",
    )

    continuation_page = parse_json_output(
        run_cli(
            config,
            operation_ids["listPostings"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--cursor",
            next_cursor,
            "--limit",
            "25",
            "--output",
            "json",
        ),
        f"{config.label} list-postings continuation page was not valid JSON",
    )
    continuation_payload = payload_field(continuation_page, "payload")
    require(
        isinstance(continuation_payload, dict),
        f"{config.label} list-postings continuation page did not publish an object payload",
    )
    continuation_query = payload_field(continuation_payload, "resolvedQuery")
    require(
        isinstance(continuation_query, dict) and continuation_query.get("cursor") == next_cursor,
        f"{config.label} list-postings continuation page did not preserve the accepted cursor",
    )
    require(
        "nextCursor" not in continuation_payload,
        f"{config.label} list-postings terminal page unexpectedly emitted payload.nextCursor",
    )
