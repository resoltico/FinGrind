package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical declare-tax-registration request field names shared by parser and discovery. */
public final class ProtocolTaxRegistrationFields {
  public static final String TAX_REGISTRATION_ID = "taxRegistrationId";
  public static final String TAX_REGISTRATION_NAME = "taxRegistrationName";
  public static final String JURISDICTION = "jurisdiction";
  public static final String REGISTRATION_NUMBER = "registrationNumber";
  public static final String PAYABLE_ACCOUNT_CODE = "payableAccountCode";
  public static final String RECOVERABLE_ACCOUNT_CODE = "recoverableAccountCode";
  public static final String OBLIGATION_FREQUENCY = "obligationFrequency";
  public static final String DUE_DAYS_AFTER_PERIOD_END = "dueDaysAfterPeriodEnd";
  public static final String TAX_CODES = "taxCodes";

  private ProtocolTaxRegistrationFields() {}

  /** Returns declare-tax-registration top-level fields in stable wire order. */
  public static List<String> topLevelFields() {
    return List.of(
        TAX_REGISTRATION_ID,
        TAX_REGISTRATION_NAME,
        JURISDICTION,
        REGISTRATION_NUMBER,
        PAYABLE_ACCOUNT_CODE,
        RECOVERABLE_ACCOUNT_CODE,
        OBLIGATION_FREQUENCY,
        DUE_DAYS_AFTER_PERIOD_END,
        TAX_CODES);
  }

  /** Nested declared tax-code request fields. */
  public static final class TaxCode {
    public static final String TAX_CODE = "taxCode";
    public static final String TAX_CODE_NAME = "taxCodeName";
    public static final String RATE_PARTS_PER_MILLION = "ratePartsPerMillion";
    public static final String INCLUSION_MODE = "inclusionMode";
    public static final String APPLICATION_KIND = "applicationKind";

    private TaxCode() {}
  }

  /** Returns nested declared tax-code request fields in stable wire order. */
  public static List<String> taxCodeFields() {
    return List.of(
        TaxCode.TAX_CODE,
        TaxCode.TAX_CODE_NAME,
        TaxCode.RATE_PARTS_PER_MILLION,
        TaxCode.INCLUSION_MODE,
        TaxCode.APPLICATION_KIND);
  }
}
