package dev.erst.fingrind.contract.protocol;

/** Canonical open-book request field names shared by parser and public protocol surfaces. */
public final class ProtocolOpenBookFields {
  public static final String ENTITY_NAME = "entityName";
  public static final String ENTITY_FORM = "entityForm";
  public static final String OWNER_MODEL = "ownerModel";
  public static final String REPORTING_OBLIGATION_STATUS = "reportingObligationStatus";
  public static final String TAX_REGISTRATION_STATUS = "taxRegistrationStatus";
  public static final String TAX_PROFILE = "taxProfile";
  public static final String BUSINESS_ACTIVITY_TAGS = "businessActivityTags";
  public static final String FUNCTIONAL_CURRENCY = "functionalCurrency";
  public static final String FISCAL_YEAR_START = "fiscalYearStart";
  public static final String ACCOUNTING_BASIS = "accountingBasis";

  /** Nested tax-profile field names shared by CLI parsers and public request docs. */
  public static final class TaxProfileFields {
    public static final String REGISTRATIONS = "registrations";
    public static final String TAX_CODE_DEFINITIONS = "taxCodeDefinitions";

    private TaxProfileFields() {}
  }

  /** Nested tax-registration field names shared by CLI parsers and public request docs. */
  public static final class TaxRegistrationFields {
    public static final String JURISDICTION_CODE = "jurisdictionCode";
    public static final String REGISTRATION_ID = "registrationId";
    public static final String FILING_FREQUENCY = "filingFrequency";

    private TaxRegistrationFields() {}
  }

  /** Nested tax-code-definition field names shared by CLI parsers and public request docs. */
  public static final class TaxCodeDefinitionFields {
    public static final String TAX_CODE = "taxCode";
    public static final String DISPLAY_NAME = "displayName";
    public static final String JURISDICTION_CODE = "jurisdictionCode";
    public static final String RATE_BASIS_POINTS = "rateBasisPoints";
    public static final String PRICING_MODE = "pricingMode";
    public static final String RECOVERABILITY = "recoverability";
    public static final String LIABILITY_ACCOUNT_CODE = "liabilityAccountCode";
    public static final String RECEIVABLE_ACCOUNT_CODE = "receivableAccountCode";

    private TaxCodeDefinitionFields() {}
  }

  private ProtocolOpenBookFields() {}
}
