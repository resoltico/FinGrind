package dev.erst.fingrind.contract.workflow;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Stable journal-visible kinds emitted by ledger-plan execution. */
public enum LedgerJournalKind implements WireValue {
  OPEN_BOOK(OperationId.OPEN_BOOK),
  DECLARE_ACCOUNT(OperationId.DECLARE_ACCOUNT),
  PREFLIGHT_ENTRY(OperationId.PREFLIGHT_ENTRY),
  POST_ENTRY(OperationId.POST_ENTRY),
  INSPECT_BOOK(OperationId.INSPECT_BOOK),
  LIST_ACCOUNTS(OperationId.LIST_ACCOUNTS),
  GET_POSTING(OperationId.GET_POSTING),
  LIST_POSTINGS(OperationId.LIST_POSTINGS),
  ACCOUNT_BALANCE(OperationId.ACCOUNT_BALANCE),
  ASSERT("assert"),
  PLAN_BOUNDARY("plan-boundary");

  private final String wireValue;

  LedgerJournalKind(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  LedgerJournalKind(OperationId operationId) {
    this(Objects.requireNonNull(operationId, "operationId").wireName());
  }

  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable public journal-kind wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(LedgerJournalKind.class);
  }

  /** Parses one stable public journal-kind wire value. */
  public static LedgerJournalKind fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        LedgerJournalKind.class, wireValue, "Unsupported ledger journal kind");
  }
}
