from __future__ import annotations


def assert_canonical_direct_journal_lines(
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


def assert_canonical_sale_fields(
    document: dict[str, object],
    *,
    label: str,
) -> None:
    if document.get("cashAccountCode") != "cash":
        raise SystemExit(f"{label} did not seed the canonical cash account")
    if document.get("revenueAccountCode") != "service-revenue":
        raise SystemExit(f"{label} did not seed the canonical revenue account")
    if document.get("amount") != {"currencyCode": "EUR", "minorUnits": "1000"}:
        raise SystemExit(f"{label} did not seed the canonical sale amount scaffold")
    if document.get("tax") != {
        "taxRegistrationId": "replace-before-commit-tax-registration-id",
        "taxCode": "replace-before-commit-output-tax-code",
    }:
        raise SystemExit(f"{label} did not publish the canonical optional output-tax selector")
    if "foreignExchange" in document:
        raise SystemExit(
            f"{label} leaked optional foreign-exchange facts into the minimal sale scaffold"
        )
    if "lines" in document:
        raise SystemExit(f"{label} leaked raw direct-journal lines into the sale scaffold")


def assert_request_template(
    document: dict[str, object],
    *,
    label: str,
    expected_entry_kind: str,
) -> None:
    if document["entryKind"] != expected_entry_kind:
        raise SystemExit(
            f"{label} did not expose the canonical {expected_entry_kind.lower()} entry kind"
        )
    if "postingKind" in document:
        raise SystemExit(f"{label} leaked retired postingKind")
    if expected_entry_kind == "DIRECT_JOURNAL":
        assert_canonical_direct_journal_lines(document, label=label)
        return
    if expected_entry_kind == "SALE_SETTLED":
        assert_canonical_sale_fields(document, label=label)
        return
    raise SystemExit(f"{label} requested unsupported entry-kind assertion {expected_entry_kind}")


def assert_plan_template(document: dict[str, object]) -> None:
    if document.get("planId") != "general-workflow":
        raise SystemExit(
            "source-checkout launcher plan template did not publish the general-workflow plan"
        )
    steps = document.get("steps")
    if not isinstance(steps, list) or len(steps) != 1:
        raise SystemExit("source-checkout launcher plan template did not publish one workflow step")
    sale = steps[0]
    if (
        not isinstance(sale, dict)
        or sale.get("stepId") != "record-sale-settled"
        or sale.get("kind") != "record-sale-settled"
    ):
        raise SystemExit(
            "source-checkout launcher plan template did not publish the canonical sale step"
        )
    posting = sale.get("posting")
    if not isinstance(posting, dict):
        raise SystemExit("source-checkout launcher plan template omitted the sale posting scaffold")
    assert_request_template(
        posting,
        label="source-checkout launcher plan template sale posting",
        expected_entry_kind="SALE_SETTLED",
    )
    if posting.get("effectiveDate") != "2026-01-15":
        raise SystemExit(
            "source-checkout launcher plan template did not publish the canonical sale date"
        )
    if posting.get("evidence") != {
        "sourceDocuments": [
            {
                "sourceDocumentId": "replace-before-commit-source-document-id",
                "sourceDocumentType": "cash-receipt",
                "documentDate": "2026-01-15",
            }
        ],
        "approvals": [],
    }:
        raise SystemExit(
            "source-checkout launcher plan template did not publish the canonical evidence scaffold"
        )
    if posting.get("provenance") != {
        "commandId": "replace-before-commit-command-id",
        "idempotencyKey": "replace-before-commit-idempotency-key",
        "causationId": "replace-before-commit-causation-id",
    }:
        raise SystemExit(
            "source-checkout launcher plan template did not publish the canonical provenance scaffold"
        )
