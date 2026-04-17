package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical non-operation step kinds accepted inside ledger-plan request documents. */
public final class ProtocolPlanKinds {
  /** Assertion step kind requiring a declared account. */
  public static final String ASSERT_ACCOUNT_DECLARED = "assert-account-declared";

  /** Assertion step kind requiring an active account. */
  public static final String ASSERT_ACCOUNT_ACTIVE = "assert-account-active";

  /** Assertion step kind requiring an existing posting. */
  public static final String ASSERT_POSTING_EXISTS = "assert-posting-exists";

  /** Assertion step kind requiring an exact account balance. */
  public static final String ASSERT_ACCOUNT_BALANCE = "assert-account-balance";

  private static final List<String> ALL =
      List.of(
          ASSERT_ACCOUNT_DECLARED,
          ASSERT_ACCOUNT_ACTIVE,
          ASSERT_POSTING_EXISTS,
          ASSERT_ACCOUNT_BALANCE);

  private ProtocolPlanKinds() {}

  /** Returns the stable list of supported non-operation ledger-plan step kinds. */
  public static List<String> all() {
    return ALL;
  }
}
