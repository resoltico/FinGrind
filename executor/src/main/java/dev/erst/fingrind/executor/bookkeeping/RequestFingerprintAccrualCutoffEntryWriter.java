package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;

/** Writes canonical caller-authored fingerprint fields for accrual cut-off entries. */
final class RequestFingerprintAccrualCutoffEntryWriter {
  private RequestFingerprintAccrualCutoffEntryWriter() {}

  static void append(StringBuilder canonical, AccrualCutoffBookkeepingEntryVariants entry) {
    switch (entry) {
      case AccrualCutoffBookkeepingEntryVariants.Prepayment prepayment -> {
        appendCutoffId(canonical, prepayment.accrualCutoffId().value());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "prepaymentAssetAccountCode", prepayment.prepaymentAssetAccountCode());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "expenseAccountCode", prepayment.expenseAccountCode());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "cashAccountCode", prepayment.cashAccountCode());
        RequestFingerprintEntryFieldWriter.appendAmount(canonical, prepayment.amount());
        appendRecognitionInterval(canonical, prepayment.recognitionInterval());
      }
      case AccrualCutoffBookkeepingEntryVariants.DeferredRevenue deferredRevenue -> {
        appendCutoffId(canonical, deferredRevenue.accrualCutoffId().value());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "cashAccountCode", deferredRevenue.cashAccountCode());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "deferredRevenueAccountCode", deferredRevenue.deferredRevenueAccountCode());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "revenueAccountCode", deferredRevenue.revenueAccountCode());
        RequestFingerprintEntryFieldWriter.appendAmount(canonical, deferredRevenue.amount());
        appendRecognitionInterval(canonical, deferredRevenue.recognitionInterval());
      }
      case AccrualCutoffBookkeepingEntryVariants.AccruedExpense accruedExpense -> {
        appendCutoffId(canonical, accruedExpense.accrualCutoffId().value());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "expenseAccountCode", accruedExpense.expenseAccountCode());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical,
            "accruedExpenseLiabilityAccountCode",
            accruedExpense.accruedExpenseLiabilityAccountCode());
        RequestFingerprintEntryFieldWriter.appendAmount(canonical, accruedExpense.amount());
      }
      case AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition recognition -> {
        appendCutoffId(canonical, recognition.accrualCutoffId().value());
        RequestFingerprintEntryFieldWriter.appendAmount(canonical, recognition.amount());
      }
      case AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement settlement -> {
        appendCutoffId(canonical, settlement.accrualCutoffId().value());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "cashAccountCode", settlement.cashAccountCode());
        RequestFingerprintEntryFieldWriter.appendAmount(canonical, settlement.amount());
      }
    }
  }

  private static void appendCutoffId(StringBuilder canonical, String cutoffId) {
    RequestFingerprintEntryFieldWriter.appendField(
        canonical, "callerAuthoredEntry.accrualCutoffId", cutoffId);
  }

  private static void appendRecognitionInterval(
      StringBuilder canonical,
      dev.erst.fingrind.contract.bookkeeping.AccrualCutoffRecognitionInterval recognitionInterval) {
    RequestFingerprintEntryFieldWriter.appendField(
        canonical,
        "callerAuthoredEntry.recognitionInterval.startDate",
        recognitionInterval.startDate().toString());
    RequestFingerprintEntryFieldWriter.appendField(
        canonical,
        "callerAuthoredEntry.recognitionInterval.endDate",
        recognitionInterval.endDate().toString());
  }
}
