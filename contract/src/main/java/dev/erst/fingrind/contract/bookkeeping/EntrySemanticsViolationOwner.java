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
      "Add inventoryRelief with declared non-cash inventory, cost-of-sales, and quantity facts."),
  INVENTORY_RELIEF_REQUIRES_TRADING_BOOK(
      "inventory-relief-requires-trading-book",
      "trading-sale",
      "inventoryRelief is accepted only on trading-template sale requests.",
      "Remove inventoryRelief, or initialize the book with the trading template when the sale must relieve inventory."),
  INVENTORY_QUANTITY_INCOMPATIBLE_WITH_UNIT_OF_MEASURE(
      "inventory-quantity-incompatible-with-unit-of-measure",
      "inventory-quantity",
      "One inventory quantity field is incompatible with the selected inventory account's declared unitOfMeasure and exact quantityScale.",
      "Use quantity text admitted by the selected inventory account's declared unitOfMeasure scale, or declare an inventory account whose unitOfMeasure matches the intended quantity precision."),
  INVENTORY_ACQUISITION_COST_NOT_EXACT(
      "inventory-acquisition-cost-not-exact",
      "inventory-acquisition",
      "One inventory acquisition cannot compose an exact carrying-cost amount at the currency minor-unit boundary from the supplied quantity and unitCost.",
      "Adjust quantity or unitCost so quantity multiplied by unitCost resolves to one exact functional-currency minor-unit amount."),
  INVENTORY_ACQUISITION_BREACHES_MINOR_UNIT_FLOOR(
      "inventory-acquisition-breaches-minor-unit-floor",
      "inventory-acquisition",
      "One inventory acquisition would leave a positive carrying-cost pool below the minimum minor-unit floor required to preserve zero-to-zero disposal truth.",
      "Increase the carrying cost for the selected quantity, or use a coarser inventory unitOfMeasure scale so the resulting positive pool remains above the minor-unit floor."),
  INVENTORY_ACQUISITION_FOREIGN_EXCHANGE_FUNCTIONAL_AMOUNT_MISMATCH(
      "inventory-acquisition-foreign-exchange-functional-amount-mismatch",
      "foreign-exchange",
      "One inventory acquisition foreignExchange.functionalAmount contradicts the exact functional-currency pre-tax acquisition cost resolved from quantity and unitCost.",
      "Use a foreignExchange.functionalAmount equal to the exact pre-tax acquisition cost resolved from the supplied quantity and unitCost."),
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
  RAW_JOURNAL_TOUCHES_INVENTORY(
      "raw-journal-touches-inventory",
      "raw-journal-admission",
      "The supplied raw journal contains an inventory-account line even though raw journals do not own exact inventory quantity truth.",
      "Remove the inventory line, or restate the request as one supported quantity-aware inventory command."),
  OPENING_WINDOW_ACCOUNT_NOT_PERMITTED(
      "opening-window-account-not-permitted",
      "opening-window",
      "The supplied opening-position request references an account that is not permitted during the adoption opening window.",
      "Use only opening-window-permitted balance-sheet and equity accounts in openingBalances[].accountCode."),
  OPENING_INVENTORY_REQUIRES_QUANTITY(
      "opening-inventory-requires-quantity",
      "inventory-opening",
      "One inventory opening balance omits the exact quantity required to establish its carrying-cost pool.",
      "Supply openingBalances[].quantity for every inventory account, using quantity text admitted by that account's unitOfMeasure."),
  OPENING_QUANTITY_REQUIRES_INVENTORY(
      "opening-quantity-requires-inventory",
      "inventory-opening",
      "One non-inventory opening balance carries quantity even though exact quantity belongs only to inventory accounts.",
      "Remove openingBalances[].quantity from non-inventory accounts, or use an inventory account when the opening balance represents stock on hand."),
  INVENTORY_CAPITALIZATION_REQUIRES_QUANTITY_ON_HAND(
      "inventory-capitalization-requires-quantity-on-hand",
      "inventory-capitalization",
      "A cost-only inventory capitalization requires existing inventory quantity so the carrying-cost pool remains exact and non-zero together with quantity.",
      "Record or correct the inventory quantity first, then capitalize the directly attributable carrying cost."),
  INVENTORY_OPENING_CARRYING_COST_INVALID(
      "inventory-opening-carrying-cost-invalid",
      "inventory-opening",
      "One inventory opening quantity and carrying cost cannot establish a valid exact inventory pool.",
      "Use a positive carrying cost that is sufficient for the supplied exact quantity and the inventory account's quantityScale."),
  INVENTORY_OPENING_MUST_BE_FIRST_MOVEMENT(
      "inventory-opening-must-be-first-movement",
      "inventory-opening",
      "An inventory opening balance is valid only as the first durable movement for that inventory account.",
      "Use an opening position before any inventory movement, or correct the established inventory history through a typed compensating event.");

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
