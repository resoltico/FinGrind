package dev.erst.fingrind.contract.protocol;

import java.util.Set;

/** Canonical book-lifecycle and account-declaration request-field sets. */
public final class ProtocolBookRequestFieldSets {
  private static final Set<String> DECLARE_ACCOUNT_FIELDS =
      Set.of(
          ProtocolDeclareAccountFields.ACCOUNT_CODE,
          ProtocolDeclareAccountFields.ACCOUNT_NAME,
          ProtocolDeclareAccountFields.ACCOUNT_TYPE,
          ProtocolDeclareAccountFields.ACCOUNT_ROLE,
          ProtocolDeclareAccountFields.ACCOUNT_NODE_KIND,
          ProtocolDeclareAccountFields.PARENT_ACCOUNT_CODE,
          ProtocolDeclareAccountFields.FINANCIAL_POSITION_LINE_CLASSIFICATION,
          ProtocolDeclareAccountFields.PROFIT_AND_LOSS_LINE_CLASSIFICATION);
  private static final Set<String> OPEN_BOOK_FIELDS =
      Set.of(
          ProtocolOpenBookFields.ENTITY_NAME,
          ProtocolOpenBookFields.BUSINESS_ACTIVITY_TAGS,
          ProtocolOpenBookFields.FUNCTIONAL_CURRENCY,
          ProtocolOpenBookFields.FISCAL_YEAR_START);

  private ProtocolBookRequestFieldSets() {}

  /** Returns the accepted top-level fields for {@code declare-account} requests. */
  public static Set<String> declareAccountFields() {
    return DECLARE_ACCOUNT_FIELDS;
  }

  /** Returns the accepted top-level fields for {@code open-book} requests. */
  public static Set<String> openBookFields() {
    return OPEN_BOOK_FIELDS;
  }
}
