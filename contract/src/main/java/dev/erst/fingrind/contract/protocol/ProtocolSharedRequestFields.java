package dev.erst.fingrind.contract.protocol;

/**
 * Canonical request-field names reused across declare-account, posting, and ledger-plan payloads.
 */
public final class ProtocolSharedRequestFields {
  public static final String ACCOUNT_CODE = "accountCode";
  public static final String CURRENCY_CODE = "currencyCode";
  public static final String EFFECTIVE_DATE_FROM = "effectiveDateFrom";
  public static final String EFFECTIVE_DATE_TO = "effectiveDateTo";

  private ProtocolSharedRequestFields() {}
}
