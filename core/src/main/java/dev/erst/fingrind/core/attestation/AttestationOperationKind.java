package dev.erst.fingrind.core.attestation;

/**
 * Closed operation meaning used to resolve one authorization capability without wire-token parsing.
 */
enum AttestationOperationKind {
  DECLARE_ACCOUNT(AttestationCapability.POST),
  AMEND_ACCOUNT(AttestationCapability.POST),
  RETIRE_ACCOUNT(AttestationCapability.POST),
  DECLARE_TAX_REGISTRATION(AttestationCapability.POST),
  POST_ENTRY(AttestationCapability.POST),
  EXECUTE_PLAN(AttestationCapability.POST),
  RECORD_SALE_SETTLED(AttestationCapability.POST),
  RECORD_SALE_ON_CREDIT(AttestationCapability.POST),
  RECORD_PURCHASE_SETTLED(AttestationCapability.POST),
  RECORD_PURCHASE_ON_CREDIT(AttestationCapability.POST),
  RECORD_INVENTORY_CAPITALIZATION_SETTLED(AttestationCapability.POST),
  RECORD_INVENTORY_CAPITALIZATION_ON_CREDIT(AttestationCapability.POST),
  RECORD_INVENTORY_WRITE_DOWN(AttestationCapability.POST),
  RECORD_INVENTORY_SHRINKAGE(AttestationCapability.POST),
  RECORD_INVENTORY_COUNT_INCREASE(AttestationCapability.POST),
  RECORD_PREPAYMENT(AttestationCapability.POST),
  RECORD_DEFERRED_REVENUE(AttestationCapability.POST),
  RECORD_ACCRUED_EXPENSE(AttestationCapability.POST),
  RECORD_ACCRUAL_CUTOFF_RECOGNITION(AttestationCapability.POST),
  RECORD_ACCRUED_EXPENSE_SETTLEMENT(AttestationCapability.POST),
  RECORD_LATVIAN_MONTHLY_PAYROLL(AttestationCapability.POST),
  RECORD_LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT(AttestationCapability.POST),
  RECORD_LATVIAN_PAYROLL_STATE_REMITTANCE(AttestationCapability.POST),
  RECORD_FIXED_ASSET_CAPITALIZATION(AttestationCapability.POST),
  RECORD_FIXED_ASSET_DEPRECIATION(AttestationCapability.POST),
  RECORD_FIXED_ASSET_DISPOSAL(AttestationCapability.POST),
  RECORD_FINANCING_BORROWING(AttestationCapability.POST),
  RECORD_FINANCING_PRINCIPAL_REPAYMENT(AttestationCapability.POST),
  RECORD_FINANCING_INTEREST_ACCRUAL(AttestationCapability.POST),
  RECORD_FINANCING_INTEREST_PAYMENT(AttestationCapability.POST),
  RECORD_FOREIGN_CURRENCY_OBLIGATION(AttestationCapability.POST),
  RECORD_REALIZED_FOREIGN_EXCHANGE_SETTLEMENT(AttestationCapability.POST),
  RECORD_EXPENSE_SETTLED(AttestationCapability.POST),
  RECORD_EXPENSE_ON_CREDIT(AttestationCapability.POST),
  RECORD_RECEIPT(AttestationCapability.POST),
  RECORD_PAYMENT(AttestationCapability.POST),
  RECORD_OWNER_CONTRIBUTION(AttestationCapability.POST),
  RECORD_OWNER_WITHDRAWAL(AttestationCapability.POST),
  RECORD_OPENING_POSITION(AttestationCapability.POST),
  RECORD_REVERSAL(AttestationCapability.POST),
  ATTACH_POSTING_APPROVAL(AttestationCapability.APPROVE),
  INTERIM_RESULT_SWEEP(AttestationCapability.CLOSE_PERIOD),
  FISCAL_YEAR_CLOSE(AttestationCapability.CLOSE_PERIOD),
  BACKUP_CREATED(AttestationCapability.BACKUP),
  RESTORE_BOOK(AttestationCapability.RESTORE),
  REKEY_BOOK(AttestationCapability.REKEY),
  ENROLL_KEY(AttestationCapability.ENROLL_KEY),
  ROLLOVER_KEY(AttestationCapability.ENROLL_KEY),
  REVOKE_KEY(AttestationCapability.REVOKE_KEY),
  ALTER_POLICY(AttestationCapability.ALTER_POLICY);

  private final AttestationCapability capability;

  AttestationOperationKind(AttestationCapability capability) {
    this.capability = capability;
  }

  AttestationCapability capability() {
    return capability;
  }
}
