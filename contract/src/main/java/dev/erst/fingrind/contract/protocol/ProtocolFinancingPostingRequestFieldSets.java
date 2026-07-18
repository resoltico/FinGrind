package dev.erst.fingrind.contract.protocol;

import java.util.Set;

/** Canonical post-entry field sets owned by Financing. */
public final class ProtocolFinancingPostingRequestFieldSets {
  private static final Set<String> BORROWING_FIELDS =
      ProtocolPostingRequestFieldSetSupport.typedEntryFields(
          ProtocolBusinessEventFields.Financing.FINANCING_ARRANGEMENT_ID,
          ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Financing.PRINCIPAL_LIABILITY_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Financing.INTEREST_PAYABLE_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Financing.PRINCIPAL_AMOUNT);
  private static final Set<String> PRINCIPAL_REPAYMENT_FIELDS =
      ProtocolPostingRequestFieldSetSupport.typedEntryFields(
          ProtocolBusinessEventFields.Financing.FINANCING_ARRANGEMENT_ID,
          ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Financing.PRINCIPAL_AMOUNT);
  private static final Set<String> INTEREST_ACCRUAL_FIELDS =
      ProtocolPostingRequestFieldSetSupport.typedEntryFields(
          ProtocolBusinessEventFields.Financing.FINANCING_ARRANGEMENT_ID,
          ProtocolBusinessEventFields.Financing.INTEREST_EXPENSE_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Financing.INTEREST_AMOUNT);
  private static final Set<String> INTEREST_PAYMENT_FIELDS =
      ProtocolPostingRequestFieldSetSupport.typedEntryFields(
          ProtocolBusinessEventFields.Financing.FINANCING_ARRANGEMENT_ID,
          ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Financing.INTEREST_AMOUNT);

  private ProtocolFinancingPostingRequestFieldSets() {}

  /** Returns accepted fields for a financing borrowing. */
  public static Set<String> borrowingFields() {
    return BORROWING_FIELDS;
  }

  /** Returns accepted fields for a financing principal repayment. */
  public static Set<String> principalRepaymentFields() {
    return PRINCIPAL_REPAYMENT_FIELDS;
  }

  /** Returns accepted fields for a financing interest accrual. */
  public static Set<String> interestAccrualFields() {
    return INTEREST_ACCRUAL_FIELDS;
  }

  /** Returns accepted fields for a financing interest payment. */
  public static Set<String> interestPaymentFields() {
    return INTEREST_PAYMENT_FIELDS;
  }
}
