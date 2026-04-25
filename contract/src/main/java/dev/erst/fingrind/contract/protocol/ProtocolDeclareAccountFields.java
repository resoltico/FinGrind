package dev.erst.fingrind.contract.protocol;

/** Canonical declare-account request field names shared by parser and machine contract surfaces. */
public final class ProtocolDeclareAccountFields {
  public static final String ACCOUNT_CODE = ProtocolSharedRequestFields.ACCOUNT_CODE;
  public static final String ACCOUNT_NAME = "accountName";
  public static final String NORMAL_BALANCE = "normalBalance";

  private ProtocolDeclareAccountFields() {}
}
