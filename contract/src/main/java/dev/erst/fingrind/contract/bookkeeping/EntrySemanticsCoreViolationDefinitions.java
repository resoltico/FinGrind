package dev.erst.fingrind.contract.bookkeeping;

import java.util.Arrays;
import java.util.List;

/** Core entry, tax, basis, and raw-journal violation definitions. */
enum EntrySemanticsCoreViolationDefinitions {
  ECONOMIC_NULL_JOURNAL(
      "economic-null-journal",
      "journal-lines",
      "The supplied raw journal reduces every referenced account to zero after debit-credit netting.",
      "Adjust the journal lines so at least one referenced account retains non-zero movement after debit-credit netting."),
  DISTINCT_ROLE_ACCOUNTS_REQUIRED(
      "distinct-role-accounts-required",
      "account-role-assignment",
      "Two semantic role fields point to the same account even though the selected entry kind requires distinct accounts.",
      "Assign distinct accounts to the semantic role fields named in the violation."),
  ACCOUNT_TYPE_MISMATCH(
      "account-type-mismatch",
      "account-type",
      "One referenced account uses a declared account type that the selected entry kind does not accept.",
      "Use accounts whose declared account type matches the violated field requirement."),
  CASH_FLOW_ASSET_CLASSIFICATION_MISMATCH(
      "cash-flow-asset-classification-mismatch",
      "cash-flow-asset-classification",
      "One referenced account uses a declared cashFlowAssetClassification that the selected entry kind does not accept.",
      "Use accounts whose declared cashFlowAssetClassification matches the violated field requirement."),
  FINANCIAL_POSITION_CLASSIFICATION_MISMATCH(
      "financial-position-classification-mismatch",
      "financialPositionClassification",
      "One referenced account uses a declared financialPositionLineClassification that the selected entry kind does not accept.",
      "Use accounts whose declared financialPositionLineClassification matches the violated field requirement."),
  ACCOUNT_ROLE_MISMATCH(
      "account-role-mismatch",
      "account-role",
      "One referenced account resolves to an accountRole that the selected entry kind does not accept.",
      "Use an account whose resolved accountRole matches the violated field requirement."),
  SOURCE_DOCUMENT_TYPE_NOT_ACCEPTED(
      "source-document-type-not-accepted",
      "source-document-type",
      "One evidence source document uses a sourceDocumentType that the selected entry kind does not accept.",
      "Use an accepted source document type for the selected entry kind's source-document policy."),
  UNKNOWN_TAX_REGISTRATION(
      "unknown-tax-registration",
      "tax-registration",
      "One tax selector references a taxRegistrationId that is not declared in this book.",
      "Declare the referenced tax registration first or use an existing taxRegistrationId."),
  UNKNOWN_TAX_CODE(
      "unknown-tax-code",
      "tax-code",
      "One tax selector references a taxCode that the declared tax registration does not define.",
      "Use a taxCode declared on the referenced tax registration."),
  TAX_APPLICATION_KIND_MISMATCH(
      "tax-application-kind-mismatch",
      "tax-application-kind",
      "One tax selector resolves to a tax applicationKind that the selected entry kind does not accept.",
      "Use a taxCode whose declared applicationKind matches the selected entry kind."),
  VERB_REQUIRES_RECEIVABLE_ROLE(
      "verb-requires-receivable-role",
      "accounting-basis",
      "The selected typed entry requires trade-receivable semantics that the current cash-basis book does not admit.",
      "Use an accrual-basis book for this receivable-side event, or restate the business event as a cash-settled entry."),
  VERB_REQUIRES_PAYABLE_ROLE(
      "verb-requires-payable-role",
      "accounting-basis",
      "The selected typed entry requires trade-payable semantics that the current cash-basis book does not admit.",
      "Use an accrual-basis book for this payable-side event, or restate the business event as a cash-settled entry."),
  VERB_REQUIRES_TRADING_TEMPLATE(
      "verb-requires-trading-template",
      "book-template",
      "The selected inventory-purchase verb is admitted only on trading-template books.",
      "Use an OWNER_MANAGED_TRADING book for this inventory-purchase event, or restate the business event through a doctrine the current book admits."),
  TRADING_SALE_REQUIRES_INVENTORY_RELIEF(
      "trading-sale-requires-inventory-relief",
      "trading-sale",
      "A sale on a trading-template book must carry inventory relief so the same committed event recognizes both revenue and cost of sales.",
      "Add inventoryRelief with declared non-cash inventory, cost-of-sales, and quantity facts."),
  INVENTORY_RELIEF_REQUIRES_TRADING_BOOK(
      "inventory-relief-requires-trading-book",
      "trading-sale",
      "inventoryRelief is accepted only on trading-template sale requests.",
      "Remove inventoryRelief, or initialize the book with the trading template when the sale must relieve inventory.");

  private final String code;
  private final String category;
  private final String description;
  private final String repair;

  EntrySemanticsCoreViolationDefinitions(
      String code, String category, String description, String repair) {
    this.code = code;
    this.category = category;
    this.description = description;
    this.repair = repair;
  }

  static List<EntrySemanticsViolationDefinition> definitions() {
    return Arrays.stream(values()).map(EntrySemanticsCoreViolationDefinitions::definition).toList();
  }

  private EntrySemanticsViolationDefinition definition() {
    return new EntrySemanticsViolationDefinition(code, category, description, repair);
  }
}
