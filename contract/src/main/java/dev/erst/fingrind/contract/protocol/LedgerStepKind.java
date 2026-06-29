package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Canonical wire kinds accepted for top-level ledger-plan steps. */
public enum LedgerStepKind implements WireValue {
  ENSURE_BOOK("ensure-book"),
  DECLARE_ACCOUNT(OperationId.DECLARE_ACCOUNT),
  PREFLIGHT_ENTRY(OperationId.PREFLIGHT_ENTRY),
  RECORD_SALE(OperationId.RECORD_SALE),
  RECORD_EXPENSE(OperationId.RECORD_EXPENSE),
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
    return switch (this) {
      case RECORD_SALE,
          RECORD_EXPENSE,
          RECORD_OWNER_CONTRIBUTION,
          RECORD_OWNER_WITHDRAWAL,
          RECORD_OPENING_POSITION,
          RECORD_REVERSAL,
          POST_ENTRY ->
          true;
      default -> false;
    };
  }

  /**
   * Returns the committed workflow step kind that corresponds to one caller-authored entry kind.
   */
  public static LedgerStepKind forCommittedEntryKind(BookkeepingEntryKind entryKind) {
    return switch (Objects.requireNonNull(entryKind, "entryKind")) {
      case DIRECT_JOURNAL -> POST_ENTRY;
      case SALE -> RECORD_SALE;
      case EXPENSE -> RECORD_EXPENSE;
      case OWNER_CONTRIBUTION -> RECORD_OWNER_CONTRIBUTION;
      case OWNER_WITHDRAWAL -> RECORD_OWNER_WITHDRAWAL;
      case OPENING_POSITION -> RECORD_OPENING_POSITION;
      case REVERSAL -> RECORD_REVERSAL;
    };
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
}
