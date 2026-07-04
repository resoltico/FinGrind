package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** Canonical owner for entry-semantics violation metadata, ordering, and publication. */
enum EntrySemanticsViolationOwner {
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
      "Add inventoryRelief with declared non-cash inventory, cost-of-sales, and amount facts."),
  INVENTORY_RELIEF_REQUIRES_TRADING_BOOK(
      "inventory-relief-requires-trading-book",
      "trading-sale",
      "inventoryRelief is accepted only on trading-template sale requests.",
      "Remove inventoryRelief, or initialize the book with the trading template when the sale must relieve inventory."),
  EVIDENCE_CLASS_CONFLICT(
      "evidence-class-conflict",
      "evidence-class",
      "The retained evidence class contradicts the event class resolved from the supplied journal.",
      "Use evidence whose source-document types match the resolved event class."),
  RAW_JOURNAL_SHADOWS_TYPED_EVENT(
      "raw-journal-shadows-typed-event",
      "raw-journal-admission",
      "The supplied raw journal resolves to one published typed business event and therefore must not be admitted through the raw direct-journal path.",
      "Submit the matching typed business-event command instead of the raw direct-journal path."),
  RAW_JOURNAL_BUNDLES_OPERATIONAL_EVENTS(
      "raw-journal-bundles-operational-events",
      "raw-journal-admission",
      "The supplied raw journal bundles multiple operational business events into one posting.",
      "Split the request into the separate typed business events named by the violation message."),
  RAW_JOURNAL_REQUIRES_CASH_LINE(
      "raw-journal-requires-cash-line",
      "raw-journal-admission",
      "The supplied raw journal is an adjustment on a cash-basis book, but no journal line resolves to a declared cash account.",
      "Add at least one declared cash account line, or use an accrual-basis book for this adjustment."),
  OPENING_WINDOW_ACCOUNT_NOT_PERMITTED(
      "opening-window-account-not-permitted",
      "opening-window",
      "The supplied opening-position request references an account that is not permitted during the adoption opening window.",
      "Use only opening-window-permitted balance-sheet and equity accounts in openingBalances[].accountCode.");

  private static final Map<String, EntrySemanticsViolationOwner> BY_CODE =
      Arrays.stream(values())
          .collect(
              Collectors.toUnmodifiableMap(
                  EntrySemanticsViolationOwner::code, Function.identity()));

  private static final Map<String, Integer> ORDER_BY_CODE =
      Arrays.stream(values())
          .collect(
              Collectors.toUnmodifiableMap(
                  EntrySemanticsViolationOwner::code,
                  owner -> Arrays.asList(values()).indexOf(owner)));

  private static final Comparator<PostingRejection.EntrySemanticsViolation> CANONICAL_ORDER =
      Comparator.comparingInt(violation -> ORDER_BY_CODE.get(require(violation.code()).code()));

  private static final List<ContractResponse.FieldDescriptor> DETAIL_FIELDS =
      List.of(
          detailField("code", "Stable entry-semantics violation code."),
          detailField("field", "Optional request-field path associated with this violation."),
          detailField("message", "Canonical plain-language explanation for this one violation."),
          detailField("category", "Stable repair category owned by this violation code."),
          detailField("repair", "Canonical action-first repair guidance for this one violation."));

  private final String code;
  private final String category;
  private final String description;
  private final String repair;

  EntrySemanticsViolationOwner(String code, String category, String description, String repair) {
    this.code = ContractDescriptorValidation.requireText(code, "code");
    this.category = ContractDescriptorValidation.requireText(category, "category");
    this.description = ContractDescriptorValidation.requireText(description, "description");
    this.repair = ContractDescriptorValidation.requireText(repair, "repair");
  }

  String code() {
    return code;
  }

  String category() {
    return category;
  }

  String repair() {
    return repair;
  }

  private ContractResponse.RejectionDescriptor descriptor() {
    return new ContractResponse.RejectionDescriptor(code, description, DETAIL_FIELDS, List.of());
  }

  static EntrySemanticsViolationOwner require(String code) {
    String requiredCode = ContractDescriptorValidation.requireText(code, "code");
    EntrySemanticsViolationOwner owner = BY_CODE.get(requiredCode);
    if (owner == null) {
      throw new IllegalArgumentException(
          "Unsupported entry semantics violation code: '%s'.".formatted(requiredCode));
    }
    return owner;
  }

  static void validateKnownMetadata(String code, String category, String repair) {
    @Nullable EntrySemanticsViolationOwner knownOwner = BY_CODE.get(code);
    if (knownOwner == null) {
      return;
    }
    if (!knownOwner.category.equals(category)) {
      throw new IllegalArgumentException(
          "Entry semantics violation category for code '%s' must be '%s'."
              .formatted(code, knownOwner.category));
    }
    if (!knownOwner.repair.equals(repair)) {
      throw new IllegalArgumentException(
          "Entry semantics violation repair for code '%s' must be '%s'."
              .formatted(code, knownOwner.repair));
    }
  }

  static List<PostingRejection.EntrySemanticsViolation> inCanonicalOrder(
      List<PostingRejection.EntrySemanticsViolation> violations) {
    return ContractDescriptorValidation.copyList(violations, "violations").stream()
        .sorted(CANONICAL_ORDER)
        .toList();
  }

  static List<ContractResponse.RejectionDescriptor> descriptors() {
    return Arrays.stream(values()).map(EntrySemanticsViolationOwner::descriptor).toList();
  }

  static String envelopeMessage(List<PostingRejection.EntrySemanticsViolation> violations) {
    int issueCount = inCanonicalOrder(violations).size();
    return issueCount == 1
        ? "Posting rejected with 1 entry-semantics issue."
        : "Posting rejected with %d entry-semantics issues.".formatted(issueCount);
  }

  private static ContractResponse.FieldDescriptor detailField(String name, String description) {
    return new ContractResponse.FieldDescriptor(
        ContractDescriptorValidation.requireText(name, "name"),
        ContractDescriptorValidation.requireText(description, "description"));
  }
}
