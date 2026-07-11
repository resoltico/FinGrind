package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.WireValue;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Canonical wire kinds accepted for top-level ledger-plan steps. */
public enum LedgerStepKind implements WireValue {
  ENSURE_BOOK("ensure-book"),
  DECLARE_ACCOUNT(OperationId.DECLARE_ACCOUNT),
  PREFLIGHT_ENTRY(OperationId.PREFLIGHT_ENTRY),
  RECORD_SALE_SETTLED(OperationId.RECORD_SALE_SETTLED),
  RECORD_SALE_ON_CREDIT(OperationId.RECORD_SALE_ON_CREDIT),
  RECORD_PURCHASE_SETTLED(OperationId.RECORD_PURCHASE_SETTLED),
  RECORD_PURCHASE_ON_CREDIT(OperationId.RECORD_PURCHASE_ON_CREDIT),
  RECORD_INVENTORY_CAPITALIZATION_SETTLED(OperationId.RECORD_INVENTORY_CAPITALIZATION_SETTLED),
  RECORD_INVENTORY_CAPITALIZATION_ON_CREDIT(OperationId.RECORD_INVENTORY_CAPITALIZATION_ON_CREDIT),
  RECORD_INVENTORY_WRITE_DOWN(OperationId.RECORD_INVENTORY_WRITE_DOWN),
  RECORD_INVENTORY_SHRINKAGE(OperationId.RECORD_INVENTORY_SHRINKAGE),
  RECORD_INVENTORY_COUNT_INCREASE(OperationId.RECORD_INVENTORY_COUNT_INCREASE),
  RECORD_EXPENSE_SETTLED(OperationId.RECORD_EXPENSE_SETTLED),
  RECORD_EXPENSE_ON_CREDIT(OperationId.RECORD_EXPENSE_ON_CREDIT),
  RECORD_RECEIPT(OperationId.RECORD_RECEIPT),
  RECORD_PAYMENT(OperationId.RECORD_PAYMENT),
  RECORD_OWNER_CONTRIBUTION(OperationId.RECORD_OWNER_CONTRIBUTION),
  RECORD_OWNER_WITHDRAWAL(OperationId.RECORD_OWNER_WITHDRAWAL),
  RECORD_OPENING_POSITION(OperationId.RECORD_OPENING_POSITION),
  RECORD_REVERSAL(OperationId.RECORD_REVERSAL),
  POST_ENTRY(OperationId.POST_ENTRY),
  INSPECT_BOOK(OperationId.INSPECT_BOOK),
  LIST_ACCOUNTS(OperationId.LIST_ACCOUNTS),
  GET_POSTING(OperationId.GET_POSTING),
  LIST_POSTINGS(OperationId.LIST_POSTINGS),
  ACCOUNT_BALANCE(OperationId.ACCOUNT_BALANCE),
  ASSERT("assert");

  private static final Map<BookkeepingEntryKind, LedgerStepKind> ENTRY_KIND_STEP_KINDS =
      entryKindStepKinds();
  private static final Set<LedgerStepKind> POSTING_COMMIT_STEPS = postingCommitSteps();

  private final String wireValue;

  LedgerStepKind(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  LedgerStepKind(OperationId operationId) {
    this(Objects.requireNonNull(operationId, "operationId").wireName());
  }

  /** Returns the stable wire value for this plan step kind. */
  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns whether this step kind carries a nested posting payload. */
  public boolean carriesPostingPayload() {
    return this == PREFLIGHT_ENTRY || commitsPosting();
  }

  /** Returns whether this step kind commits one posting when it succeeds. */
  public boolean commitsPosting() {
    return POSTING_COMMIT_STEPS.contains(this);
  }

  /**
   * Returns the committed workflow step kind that corresponds to one caller-authored entry kind.
   */
  public static LedgerStepKind forCommittedEntryKind(BookkeepingEntryKind entryKind) {
    return Objects.requireNonNull(ENTRY_KIND_STEP_KINDS.get(entryKind), "entryKind");
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(LedgerStepKind.class);
  }

  /** Parses one stable wire step kind. */
  public static LedgerStepKind fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        LedgerStepKind.class, wireValue, "Unsupported ledger plan step kind");
  }

  private static Map<BookkeepingEntryKind, LedgerStepKind> entryKindStepKinds() {
    var stepKinds =
        new java.util.EnumMap<BookkeepingEntryKind, LedgerStepKind>(BookkeepingEntryKind.class);
    for (BookkeepingEntryKind entryKind : BookkeepingEntryKind.values()) {
      stepKinds.put(
          entryKind,
          entryKind == BookkeepingEntryKind.DIRECT_JOURNAL
              ? POST_ENTRY
              : LedgerStepKind.valueOf("RECORD_" + entryKind.name()));
    }
    return Map.copyOf(stepKinds);
  }

  private static Set<LedgerStepKind> postingCommitSteps() {
    var steps = EnumSet.copyOf(ENTRY_KIND_STEP_KINDS.values());
    steps.add(POST_ENTRY);
    return Set.copyOf(steps);
  }
}
