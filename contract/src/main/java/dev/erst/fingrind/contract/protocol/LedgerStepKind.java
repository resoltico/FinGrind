package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Canonical wire kinds accepted for top-level ledger-plan steps. */
public enum LedgerStepKind implements WireValue {
  OPEN_BOOK("open-book"),
  DECLARE_ACCOUNT("declare-account"),
  PREFLIGHT_ENTRY("preflight-entry"),
  POST_ENTRY("post-entry"),
  INSPECT_BOOK("inspect-book"),
  LIST_ACCOUNTS("list-accounts"),
  GET_POSTING("get-posting"),
  LIST_POSTINGS("list-postings"),
  ACCOUNT_BALANCE("account-balance"),
  ASSERT("assert");

  private final String wireValue;

  LedgerStepKind(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  /** Returns the stable wire value for this plan step kind. */
  @Override
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
