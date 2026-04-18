package dev.erst.fingrind.contract.protocol;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Canonical wire kinds accepted for top-level ledger-plan steps. */
public enum LedgerStepKind {
  OPEN_BOOK(OperationId.OPEN_BOOK.wireName()),
  DECLARE_ACCOUNT(OperationId.DECLARE_ACCOUNT.wireName()),
  PREFLIGHT_ENTRY(OperationId.PREFLIGHT_ENTRY.wireName()),
  POST_ENTRY(OperationId.POST_ENTRY.wireName()),
  INSPECT_BOOK(OperationId.INSPECT_BOOK.wireName()),
  LIST_ACCOUNTS(OperationId.LIST_ACCOUNTS.wireName()),
  GET_POSTING(OperationId.GET_POSTING.wireName()),
  LIST_POSTINGS(OperationId.LIST_POSTINGS.wireName()),
  ACCOUNT_BALANCE(OperationId.ACCOUNT_BALANCE.wireName()),
  ASSERT("assert");

  private final String wireValue;

  LedgerStepKind(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  /** Returns the stable wire value for this plan step kind. */
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return Arrays.stream(values()).map(LedgerStepKind::wireValue).toList();
  }

  /** Parses one stable wire step kind. */
  public static LedgerStepKind fromWireValue(String wireValue) {
    Objects.requireNonNull(wireValue, "wireValue");
    return Arrays.stream(values())
        .filter(kind -> kind.wireValue.equals(wireValue))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Unsupported ledger plan step kind: " + wireValue));
  }
}
