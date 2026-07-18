package dev.erst.fingrind.contract.protocol;

import java.util.Set;

/** Canonical book-lifecycle and account-declaration request-field sets. */
public final class ProtocolBookRequestFieldSets {
  private static final Set<String> DECLARE_ACCOUNT_FIELDS =
      Set.of(
          ProtocolDeclareAccountFields.ACCOUNT_CODE,
          ProtocolDeclareAccountFields.ACCOUNT_NAME,
          ProtocolDeclareAccountFields.ACCOUNT_TYPE,
          ProtocolDeclareAccountFields.ACCOUNT_NODE_KIND,
          ProtocolDeclareAccountFields.PARENT_ACCOUNT_CODE,
          ProtocolDeclareAccountFields.CONTRA_OF_ACCOUNT_CODE,
          ProtocolDeclareAccountFields.FINANCIAL_POSITION_LINE_CLASSIFICATION,
          ProtocolDeclareAccountFields.PROFIT_AND_LOSS_LINE_CLASSIFICATION,
          ProtocolDeclareAccountFields.CASH_FLOW_ASSET_CLASSIFICATION,
          ProtocolDeclareAccountFields.UNIT_OF_MEASURE);
  private static final Set<String> DECLARE_TAX_REGISTRATION_FIELDS =
      Set.of(
          ProtocolTaxRegistrationFields.TAX_REGISTRATION_ID,
          ProtocolTaxRegistrationFields.TAX_REGISTRATION_NAME,
          ProtocolTaxRegistrationFields.JURISDICTION,
          ProtocolTaxRegistrationFields.REGISTRATION_NUMBER,
          ProtocolTaxRegistrationFields.PAYABLE_ACCOUNT_CODE,
          ProtocolTaxRegistrationFields.RECOVERABLE_ACCOUNT_CODE,
          ProtocolTaxRegistrationFields.OBLIGATION_FREQUENCY,
          ProtocolTaxRegistrationFields.DUE_DAYS_AFTER_PERIOD_END,
          ProtocolTaxRegistrationFields.TAX_CODES);
  private static final Set<String> OPEN_BOOK_FIELDS =
      Set.of(
          ProtocolOpenBookFields.ENTITY_NAME,
          ProtocolOpenBookFields.BOOK_TEMPLATE_ID,
          ProtocolOpenBookFields.ACCOUNTING_BASIS,
          ProtocolOpenBookFields.INVENTORY_COSTING,
          ProtocolOpenBookFields.FUNCTIONAL_CURRENCY,
          ProtocolOpenBookFields.FISCAL_YEAR_START,
          ProtocolOpenBookFields.BOOK_START_EFFECTIVE_DATE);

  private ProtocolBookRequestFieldSets() {}

  /** Returns the accepted top-level fields for {@code declare-account} requests. */
  public static Set<String> declareAccountFields() {
    return DECLARE_ACCOUNT_FIELDS;
  }

  /** Returns the accepted top-level fields for {@code declare-tax-registration} requests. */
  public static Set<String> declareTaxRegistrationFields() {
    return DECLARE_TAX_REGISTRATION_FIELDS;
  }

  /** Returns the accepted top-level fields for {@code open-book} requests. */
  public static Set<String> openBookFields() {
    return OPEN_BOOK_FIELDS;
  }
}
