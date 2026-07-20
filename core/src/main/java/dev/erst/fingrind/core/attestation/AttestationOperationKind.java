package dev.erst.fingrind.core.attestation;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Closed operation meaning and its canonical payload token. */
enum AttestationOperationKind {
  BOOK_GENESIS,
  DECLARE_ACCOUNT,
  AMEND_ACCOUNT,
  RETIRE_ACCOUNT,
  DECLARE_TAX_REGISTRATION,
  POST_ENTRY,
  EXECUTE_PLAN,
  RECORD_SALE_SETTLED,
  RECORD_SALE_ON_CREDIT,
  RECORD_PURCHASE_SETTLED,
  RECORD_PURCHASE_ON_CREDIT,
  RECORD_INVENTORY_CAPITALIZATION_SETTLED,
  RECORD_INVENTORY_CAPITALIZATION_ON_CREDIT,
  RECORD_INVENTORY_WRITE_DOWN,
  RECORD_INVENTORY_SHRINKAGE,
  RECORD_INVENTORY_COUNT_INCREASE,
  RECORD_PREPAYMENT,
  RECORD_DEFERRED_REVENUE,
  RECORD_ACCRUED_EXPENSE,
  RECORD_ACCRUAL_CUTOFF_RECOGNITION,
  RECORD_ACCRUED_EXPENSE_SETTLEMENT,
  RECORD_LATVIAN_MONTHLY_PAYROLL,
  RECORD_LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
  RECORD_LATVIAN_PAYROLL_STATE_REMITTANCE,
  RECORD_FIXED_ASSET_CAPITALIZATION,
  RECORD_FIXED_ASSET_DEPRECIATION,
  RECORD_FIXED_ASSET_DISPOSAL,
  RECORD_FINANCING_BORROWING,
  RECORD_FINANCING_PRINCIPAL_REPAYMENT,
  RECORD_FINANCING_INTEREST_ACCRUAL,
  RECORD_FINANCING_INTEREST_PAYMENT,
  RECORD_FOREIGN_CURRENCY_OBLIGATION,
  RECORD_REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
  RECORD_EXPENSE_SETTLED,
  RECORD_EXPENSE_ON_CREDIT,
  RECORD_RECEIPT,
  RECORD_PAYMENT,
  RECORD_OWNER_CONTRIBUTION,
  RECORD_OWNER_WITHDRAWAL,
  RECORD_OPENING_POSITION,
  RECORD_REVERSAL,
  ATTACH_POSTING_APPROVAL,
  INTERIM_RESULT_SWEEP,
  FISCAL_YEAR_CLOSE,
  BACKUP_CREATED,
  RESTORE_BOOK,
  REKEY_BOOK,
  ENROLL_KEY,
  ROLLOVER_KEY,
  REVOKE_KEY,
  ALTER_POLICY;

  private static final Map<String, AttestationOperationKind> BY_WIRE_TOKEN =
      java.util.Arrays.stream(values())
          .collect(
              Collectors.toUnmodifiableMap(
                  AttestationOperationKind::wireToken, Function.identity()));
  private static final Map<AttestationOperationKind, AttestationCapability> NON_POST_CAPABILITIES =
      Map.ofEntries(
          Map.entry(ATTACH_POSTING_APPROVAL, AttestationCapability.APPROVE),
          Map.entry(INTERIM_RESULT_SWEEP, AttestationCapability.CLOSE_PERIOD),
          Map.entry(FISCAL_YEAR_CLOSE, AttestationCapability.CLOSE_PERIOD),
          Map.entry(BACKUP_CREATED, AttestationCapability.BACKUP),
          Map.entry(RESTORE_BOOK, AttestationCapability.RESTORE),
          Map.entry(REKEY_BOOK, AttestationCapability.REKEY),
          Map.entry(ENROLL_KEY, AttestationCapability.ENROLL_KEY),
          Map.entry(ROLLOVER_KEY, AttestationCapability.ENROLL_KEY),
          Map.entry(REVOKE_KEY, AttestationCapability.REVOKE_KEY),
          Map.entry(ALTER_POLICY, AttestationCapability.ALTER_POLICY));

  static AttestationOperationKind forWireToken(String wireToken) {
    AttestationOperationKind operationKind = BY_WIRE_TOKEN.get(wireToken);
    if (operationKind == null) {
      throw AttestationCapability.unknownOperation();
    }
    return operationKind;
  }

  String wireToken() {
    return name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

  AttestationCapability capability() {
    return NON_POST_CAPABILITIES.getOrDefault(this, AttestationCapability.POST);
  }

  boolean isGenesis() {
    return this == BOOK_GENESIS;
  }
}
