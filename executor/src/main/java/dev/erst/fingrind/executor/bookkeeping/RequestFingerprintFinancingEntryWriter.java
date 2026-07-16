package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;

/** Writes canonical caller-authored fingerprint fields for financing entries. */
final class RequestFingerprintFinancingEntryWriter {
  private RequestFingerprintFinancingEntryWriter() {}

  static void append(StringBuilder canonical, FinancingBookkeepingEntryVariants entry) {
    switch (entry) {
      case FinancingBookkeepingEntryVariants.Borrowing borrowing -> {
        appendId(canonical, borrowing.financingArrangementId().value());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "cashAccountCode", borrowing.cashAccountCode());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "principalLiabilityAccountCode", borrowing.principalLiabilityAccountCode());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "interestPayableAccountCode", borrowing.interestPayableAccountCode());
        appendAmount(canonical, "principalAmount", borrowing.principalAmount());
      }
      case FinancingBookkeepingEntryVariants.PrincipalRepayment repayment -> {
        appendId(canonical, repayment.financingArrangementId().value());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "cashAccountCode", repayment.cashAccountCode());
        appendAmount(canonical, "principalAmount", repayment.principalAmount());
      }
      case FinancingBookkeepingEntryVariants.InterestAccrual accrual -> {
        appendId(canonical, accrual.financingArrangementId().value());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "interestExpenseAccountCode", accrual.interestExpenseAccountCode());
        appendAmount(canonical, "interestAmount", accrual.interestAmount());
      }
      case FinancingBookkeepingEntryVariants.InterestPayment payment -> {
        appendId(canonical, payment.financingArrangementId().value());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "cashAccountCode", payment.cashAccountCode());
        appendAmount(canonical, "interestAmount", payment.interestAmount());
      }
    }
  }

  private static void appendId(StringBuilder canonical, String financingArrangementId) {
    RequestFingerprintEntryFieldWriter.appendField(
        canonical, "callerAuthoredEntry.financingArrangementId", financingArrangementId);
  }

  private static void appendAmount(
      StringBuilder canonical, String fieldName, MonetaryAmount amount) {
    RequestFingerprintEntryFieldWriter.appendField(
        canonical, "callerAuthoredEntry." + fieldName + "Currency", amount.currencyCode());
    RequestFingerprintEntryFieldWriter.appendField(
        canonical, "callerAuthoredEntry." + fieldName + "MinorUnits", amount.minorUnits());
  }
}
