package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Stable journal-visible kinds emitted by ledger-plan execution. */
public enum LedgerJournalKind implements WireValue {
  OPEN_BOOK("open-book"),
  DECLARE_ACCOUNT("declare-account"),
  PREFLIGHT_ENTRY("preflight-entry"),
  POST_ENTRY("post-entry"),
  INSPECT_BOOK("inspect-book"),
  LIST_ACCOUNTS("list-accounts"),
  GET_POSTING("get-posting"),
  LIST_POSTINGS("list-postings"),
  ACCOUNT_BALANCE("account-balance"),
  ASSERT("assert"),
  PLAN_BOUNDARY("plan-boundary");

  private final String wireValue;

  LedgerJournalKind(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
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
