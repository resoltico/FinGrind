package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.CliPostingEntryPayloadComponents.PayloadAccounts;
import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.cli.json.CliPostingEntryPayload;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.ResolvedLatvianPayrollSettlement;
import dev.erst.fingrind.contract.bookkeeping.StandardBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.TypedBookkeepingEntry;
import org.jspecify.annotations.Nullable;

/** Maps caller-authored posting entries into public CLI JSON payloads. */
final class CliPostingEntryPayloadMapper {
  private CliPostingEntryPayloadMapper() {}

  static @Nullable CliPostingEntryPayload entryPayload(@Nullable BookkeepingEntry entry) {
    if (entry == null) {
      return null;
    }
    return switch (entry) {
      case BookkeepingEntry.DirectJournal directJournal -> directJournalPayload(directJournal);
      case TypedBookkeepingEntry typedEntry -> typedEntryPayload(typedEntry);
      case BookkeepingEntry.OpeningPosition openingPosition ->
          openingPositionPayload(openingPosition);
      case BookkeepingEntry.Reversal reversal -> reversalPayload(reversal);
    };
  }

  private static CliPostingEntryPayload typedEntryPayload(TypedBookkeepingEntry entry) {
    return switch (entry) {
      case StandardBookkeepingEntryVariants standardEntry ->
          CliStandardPostingEntryPayloadMapper.entryPayload(standardEntry);
      case InventoryBookkeepingEntryVariants inventoryEntry ->
          CliInventoryPostingEntryPayloadMapper.entryPayload(inventoryEntry);
      case AccrualCutoffBookkeepingEntryVariants accrualCutoffEntry ->
          CliAccrualCutoffPostingEntryPayloadMapper.entryPayload(accrualCutoffEntry);
      case LatvianPayrollBookkeepingEntryVariants payrollEntry -> payrollPayload(payrollEntry);
      case FixedAssetBookkeepingEntryVariants fixedAssetEntry ->
          CliFixedAssetPostingEntryPayloadMapper.entryPayload(fixedAssetEntry);
      case FinancingBookkeepingEntryVariants financingEntry -> emptyTypedPayload(financingEntry);
      case RealizedForeignExchangeBookkeepingEntryVariants foreignExchangeEntry ->
          emptyTypedPayload(foreignExchangeEntry);
    };
  }

  private static CliPostingEntryPayload emptyTypedPayload(TypedBookkeepingEntry entry) {
    return CliPostingEntryPayloadComponents.payload(
            entry.entryKind().wireValue(), PayloadAccounts.none(), null)
        .build();
  }

  private static CliPostingEntryPayload directJournalPayload(
      BookkeepingEntry.DirectJournal directJournal) {
    return CliPostingEntryPayloadComponents.payload(
            directJournal.entryKind().wireValue(), PayloadAccounts.none(), null)
        .withForeignExchange(
            CliPostingEntryPayloadComponents.foreignExchangePayload(
                directJournal.foreignExchangeDetails()))
        .build();
  }

  private static CliPostingEntryPayload payrollPayload(
      LatvianPayrollBookkeepingEntryVariants entry) {
    return switch (entry) {
      case LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll payroll ->
          CliPostingEntryPayloadComponents.payload(
                  payroll.entryKind().wireValue(), PayloadAccounts.none(), null)
              .withLatvianMonthlyPayroll(
                  new CliPostingEntryPayload.LatvianMonthlyPayrollPayload(
                      payroll.payrollRunId().value(),
                      payroll.employeeReference().value(),
                      payroll.payrollMonth().wireValue(),
                      payroll.withholdingProfile().taxBookHeldAtEmployer(),
                      payroll.withholdingProfile().dependantCount(),
                      payroll.wageExpenseAccountCode().value(),
                      payroll.employerSocialContributionExpenseAccountCode().value(),
                      payroll.netWagesPayableAccountCode().value(),
                      payroll.employeeSocialContributionPayableAccountCode().value(),
                      payroll.employerSocialContributionPayableAccountCode().value(),
                      payroll.personalIncomeTaxPayableAccountCode().value(),
                      payroll.grossWages(),
                      CliLatvianPayrollPostingEntryPayloadMapper.resolvedCalculationPayload(
                          payroll.resolvedCalculation())))
              .build();
      case LatvianPayrollBookkeepingEntryVariants.NetWageSettlement settlement ->
          settlementPayload(
              settlement.entryKind().wireValue(),
              settlement.payrollRunId().value(),
              settlement.cashAccountCode().value(),
              "NET_WAGES",
              settlement.resolvedSettlement());
      case LatvianPayrollBookkeepingEntryVariants.StateRemittance settlement ->
          settlementPayload(
              settlement.entryKind().wireValue(),
              settlement.payrollRunId().value(),
              settlement.cashAccountCode().value(),
              "STATE_REMITTANCE",
              settlement.resolvedSettlement());
    };
  }

  private static CliPostingEntryPayload settlementPayload(
      String entryKind,
      String payrollRunId,
      String cashAccountCode,
      String settlementKind,
      @Nullable ResolvedLatvianPayrollSettlement resolvedSettlement) {
    return CliPostingEntryPayloadComponents.payload(
            entryKind,
            new PayloadAccounts(
                cashAccountCode, null, null, null, null, null, null, null, null, null),
            null)
        .withLatvianPayrollSettlement(
            new CliPostingEntryPayload.LatvianPayrollSettlementPayload(
                settlementKind,
                payrollRunId,
                cashAccountCode,
                CliLatvianPayrollPostingEntryPayloadMapper.resolvedSettlementPayload(
                    resolvedSettlement)))
        .build();
  }

  private static CliPostingEntryPayload openingPositionPayload(
      BookkeepingEntry.OpeningPosition openingPosition) {
    return CliPostingEntryPayloadComponents.payload(
            openingPosition.entryKind().wireValue(), PayloadAccounts.none(), null)
        .withOpeningBalances(
            openingPosition.balances().stream()
                .map(CliPostingEntryPayloadComponents::openingBalancePayload)
                .toList())
        .build();
  }

  private static CliPostingEntryPayload reversalPayload(BookkeepingEntry.Reversal reversal) {
    return CliPostingEntryPayloadComponents.payload(
            reversal.entryKind().wireValue(), PayloadAccounts.none(), null)
        .withForeignExchange(
            CliPostingEntryPayloadComponents.foreignExchangePayload(
                reversal.foreignExchangeDetails()))
        .withReversal(
            new CliBookQueryJsonModels.ReversalPayload(
                reversal.reversal().reference().priorPostingId().value(),
                reversal.reversal().reason().value()))
        .build();
  }
}
