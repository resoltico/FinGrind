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
    if document.get("planId") != "tax-setup":
        raise SystemExit(
            "source-checkout launcher plan template did not publish the tax-setup plan"
        )
    steps = document.get("steps")
    if not isinstance(steps, list) or len(steps) != 4:
        raise SystemExit("source-checkout launcher plan template did not publish four atomic steps")
    ensure_book = steps[0]["ensureBook"]
    if "businessActivityTags" in ensure_book:
        raise SystemExit(
            "source-checkout launcher plan template leaked retired business activity tags"
        )
    if ensure_book.get("accountingBasis") != "CASH":
        raise SystemExit(
            "source-checkout launcher plan template did not publish the canonical explicit accounting basis"
        )
    payable = steps[1]
    if payable.get("stepId") != "declare-tax-payable" or payable.get("kind") != "declare-account":
        raise SystemExit(
            "source-checkout launcher plan template did not declare the VAT payable account"
        )
    payable_account = payable.get("declareAccount")
    if not isinstance(payable_account, dict):
        raise SystemExit(
            "source-checkout launcher plan template omitted the VAT payable account scaffold"
        )
    if payable_account != {
        "accountCode": "tax-payable-vat",
        "accountName": "VAT Payable",
        "accountType": "LIABILITY",
        "accountNodeKind": "POSTABLE",
        "financialPositionLineClassification": "CURRENT_LIABILITY",
    }:
        raise SystemExit(
            "source-checkout launcher plan template did not publish the canonical VAT payable taxonomy"
        )
    recoverable = steps[2]
    if (
        recoverable.get("stepId") != "declare-tax-recoverable"
        or recoverable.get("kind") != "declare-account"
    ):
        raise SystemExit(
            "source-checkout launcher plan template did not declare the VAT recoverable account"
        )
    recoverable_account = recoverable.get("declareAccount")
    if not isinstance(recoverable_account, dict):
        raise SystemExit(
            "source-checkout launcher plan template omitted the VAT recoverable account scaffold"
        )
    if recoverable_account != {
        "accountCode": "tax-recoverable-vat",
        "accountName": "VAT Recoverable",
        "accountType": "ASSET",
        "accountNodeKind": "POSTABLE",
        "financialPositionLineClassification": "CURRENT_ASSET",
        "cashFlowAssetClassification": "NON_CASH",
    }:
        raise SystemExit(
            "source-checkout launcher plan template did not publish the canonical VAT recoverable taxonomy"
        )
    registration = steps[3]
    if (
        registration.get("stepId") != "declare-tax-registration"
        or registration.get("kind") != "declare-tax-registration"
    ):
        raise SystemExit(
            "source-checkout launcher plan template did not declare the tax registration"
        )
    registration_scaffold = registration.get("declareTaxRegistration")
    if not isinstance(registration_scaffold, dict):
        raise SystemExit(
            "source-checkout launcher plan template omitted the tax-registration scaffold"
        )
    if registration_scaffold.get("payableAccountCode") != "tax-payable-vat":
        raise SystemExit(
            "source-checkout launcher plan template did not bind the payable tax account"
        )
    if registration_scaffold.get("recoverableAccountCode") != "tax-recoverable-vat":
        raise SystemExit(
            "source-checkout launcher plan template did not bind the recoverable tax account"
        )
    if registration_scaffold.get("obligationFrequency") != "MONTHLY":
        raise SystemExit(
            "source-checkout launcher plan template did not publish the tax obligation frequency"
        )
    if registration_scaffold.get("dueDaysAfterPeriodEnd") != 20:
        raise SystemExit(
            "source-checkout launcher plan template did not publish the tax due-date scaffold"
        )
    tax_codes = registration_scaffold.get("taxCodes")
    if not isinstance(tax_codes, list) or [
        tax_code.get("applicationKind") if isinstance(tax_code, dict) else None
        for tax_code in tax_codes
    ] != ["OUTPUT_SALE", "INPUT_EXPENSE_RECOVERABLE"]:
        raise SystemExit(
            "source-checkout launcher plan template did not publish the output and recoverable-input tax scaffolds"
        )
