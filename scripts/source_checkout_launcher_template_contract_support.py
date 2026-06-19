from __future__ import annotations


def assert_canonical_journal_lines(
    document: dict[str, object],
    *,
    label: str,
) -> None:
    if document.get("recipeKind") is not None:
        raise SystemExit(f"{label} leaked retired recipe-backed journal scaffolding")
    lines = document.get("lines")
    if not isinstance(lines, list) or len(lines) != 2:
        raise SystemExit(f"{label} did not expose the canonical two-line journal scaffold")
    first_line = lines[0]
    second_line = lines[1]
    if not isinstance(first_line, dict) or not isinstance(second_line, dict):
        raise SystemExit(f"{label} published malformed journal scaffold lines")
    if first_line.get("accountCode") != "cash" or first_line.get("side") != "DEBIT":
        raise SystemExit(f"{label} did not seed the canonical cash debit journal line")
    if second_line.get("accountCode") != "service-revenue" or second_line.get("side") != "CREDIT":
        raise SystemExit(f"{label} did not seed the canonical revenue credit journal line")
    first_amount = first_line.get("amount")
    second_amount = second_line.get("amount")
    if first_amount != {"currencyCode": "EUR", "minorUnits": "1000"}:
        raise SystemExit(f"{label} did not seed the canonical cash amount scaffold")
    if second_amount != {"currencyCode": "EUR", "minorUnits": "1000"}:
        raise SystemExit(f"{label} did not seed the canonical revenue amount scaffold")


def assert_request_template(document: dict[str, object], *, label: str) -> None:
    if document["entryKind"] != "JOURNAL":
        raise SystemExit(f"{label} did not expose the canonical journal entry kind")
    if "postingKind" in document:
        raise SystemExit(f"{label} leaked retired postingKind")
    assert_canonical_journal_lines(document, label=label)


def assert_plan_template(document: dict[str, object]) -> None:
    ensure_book = document["steps"][0]["ensureBook"]
    if "businessActivityTags" in ensure_book:
        raise SystemExit(
            "source-checkout launcher plan template leaked retired business activity tags"
        )
    if "accountingBasis" in ensure_book:
        raise SystemExit(
            "source-checkout launcher plan template leaked doctrine-owned identity fields into open-book input"
        )
    post_entry = document["steps"][1]["posting"]
    if post_entry["entryKind"] != "JOURNAL":
        raise SystemExit(
            "source-checkout launcher plan template did not expose the canonical journal entry kind"
        )
    if "postingKind" in post_entry:
        raise SystemExit("source-checkout launcher plan template leaked retired postingKind")
    assert_canonical_journal_lines(
        post_entry,
        label="source-checkout launcher plan template",
    )
    assertion = document["steps"][2]["assertion"]
    if assertion["accountCode"] != "cash":
        raise SystemExit(
            "source-checkout launcher plan template did not target the seeded cash account"
        )
