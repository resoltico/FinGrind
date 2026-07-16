package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.CliPostingEntryPayloadComponents.PayloadAccounts;
import dev.erst.fingrind.cli.json.CliPostingEntryPayload;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffRecognitionInterval;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.ResolvedAccrualCutoffApplication;
import org.jspecify.annotations.Nullable;

/** Maps caller-authored accrual cut-off entries into public CLI JSON payloads. */
final class CliAccrualCutoffPostingEntryPayloadMapper {
  private CliAccrualCutoffPostingEntryPayloadMapper() {}

  static CliPostingEntryPayload entryPayload(AccrualCutoffBookkeepingEntryVariants entry) {
    return switch (entry) {
      case AccrualCutoffBookkeepingEntryVariants.Prepayment prepayment ->
          payload(
              prepayment.entryKind().wireValue(),
              new AccrualCutoffPayloadDetails(
                  prepayment.accrualCutoffId().value(),
                  "PREPAYMENT",
                  prepayment.amount(),
                  new AccrualCutoffAccountCodes(
                      prepayment.prepaymentAssetAccountCode().value(),
                      null,
                      null,
                      prepayment.expenseAccountCode().value(),
                      prepayment.cashAccountCode().value()),
                  prepayment.recognitionInterval(),
                  null));
      case AccrualCutoffBookkeepingEntryVariants.DeferredRevenue deferredRevenue ->
          payload(
              deferredRevenue.entryKind().wireValue(),
              new AccrualCutoffPayloadDetails(
                  deferredRevenue.accrualCutoffId().value(),
                  "DEFERRED_REVENUE",
                  deferredRevenue.amount(),
                  new AccrualCutoffAccountCodes(
                      null,
                      deferredRevenue.deferredRevenueAccountCode().value(),
                      null,
                      deferredRevenue.revenueAccountCode().value(),
                      deferredRevenue.cashAccountCode().value()),
                  deferredRevenue.recognitionInterval(),
                  null));
      case AccrualCutoffBookkeepingEntryVariants.AccruedExpense accruedExpense ->
          payload(
              accruedExpense.entryKind().wireValue(),
              new AccrualCutoffPayloadDetails(
                  accruedExpense.accrualCutoffId().value(),
                  "ACCRUED_EXPENSE",
                  accruedExpense.amount(),
                  new AccrualCutoffAccountCodes(
                      null,
                      null,
                      accruedExpense.accruedExpenseLiabilityAccountCode().value(),
                      accruedExpense.expenseAccountCode().value(),
                      null),
                  null,
                  null));
      case AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition recognition ->
          payload(
              recognition.entryKind().wireValue(),
              new AccrualCutoffPayloadDetails(
                  recognition.accrualCutoffId().value(),
                  null,
                  recognition.amount(),
                  AccrualCutoffAccountCodes.none(),
                  null,
                  recognition.resolvedApplication()));
      case AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement settlement ->
          payload(
              settlement.entryKind().wireValue(),
              new AccrualCutoffPayloadDetails(
                  settlement.accrualCutoffId().value(),
                  null,
                  settlement.amount(),
                  new AccrualCutoffAccountCodes(
                      null, null, null, null, settlement.cashAccountCode().value()),
                  null,
                  settlement.resolvedApplication()));
    };
  }

  private static CliPostingEntryPayload payload(
      String entryKind, AccrualCutoffPayloadDetails details) {
    return CliPostingEntryPayloadComponents.payload(
            entryKind,
            new PayloadAccounts(
                details.accountCodes().cashAccountCode(),
                null,
                null,
                null,
                null,
                details.accountCodes().expenseAccountCode(),
                null,
                null,
                null,
                null),
            details.amount())
        .withAccrualCutoff(
            new CliPostingEntryPayload.AccrualCutoffPayload(
                details.accrualCutoffId(),
                details.kind(),
                details.accountCodes().prepaymentAssetAccountCode(),
                details.accountCodes().deferredRevenueAccountCode(),
                details.accountCodes().accruedExpenseLiabilityAccountCode(),
                details.recognitionInterval() == null
                    ? null
                    : new CliPostingEntryPayload.RecognitionIntervalPayload(
                        details.recognitionInterval().startDate().toString(),
                        details.recognitionInterval().endDate().toString()),
                details.resolvedApplication() == null
                    ? null
                    : new CliPostingEntryPayload.ResolvedApplicationPayload(
                        details.resolvedApplication().applicationKind().wireValue(),
                        details.resolvedApplication().debitAccountCode().value(),
                        details.resolvedApplication().creditAccountCode().value())))
        .build();
  }

  private record AccrualCutoffPayloadDetails(
      String accrualCutoffId,
      @Nullable String kind,
      MonetaryAmount amount,
      AccrualCutoffAccountCodes accountCodes,
      @Nullable AccrualCutoffRecognitionInterval recognitionInterval,
      @Nullable ResolvedAccrualCutoffApplication resolvedApplication) {}

  private record AccrualCutoffAccountCodes(
      @Nullable String prepaymentAssetAccountCode,
      @Nullable String deferredRevenueAccountCode,
      @Nullable String accruedExpenseLiabilityAccountCode,
      @Nullable String expenseAccountCode,
      @Nullable String cashAccountCode) {
    private static AccrualCutoffAccountCodes none() {
      return new AccrualCutoffAccountCodes(null, null, null, null, null);
    }
  }
}
