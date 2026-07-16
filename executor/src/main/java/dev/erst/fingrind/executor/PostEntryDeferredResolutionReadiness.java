package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import java.util.Optional;

/** Resolution-readiness policy for reversal, accrual cut-off, and payroll entries. */
final class PostEntryDeferredResolutionReadiness {
  private PostEntryDeferredResolutionReadiness() {}

  static Optional<Boolean> readiness(BookkeepingEntry entry) {
    return switch (entry) {
      case BookkeepingEntry.Reversal reversal ->
          Optional.of(reversal.resolvedJournalEntry() != null);
      case AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition recognition ->
          Optional.of(recognition.resolvedApplication() != null);
      case AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement settlement ->
          Optional.of(settlement.resolvedApplication() != null);
      case LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll payroll ->
          Optional.of(payroll.resolvedCalculation() != null);
      case LatvianPayrollBookkeepingEntryVariants.NetWageSettlement settlement ->
          Optional.of(settlement.resolvedSettlement() != null);
      case LatvianPayrollBookkeepingEntryVariants.StateRemittance settlement ->
          Optional.of(settlement.resolvedSettlement() != null);
      case FixedAssetBookkeepingEntryVariants.Depreciation depreciation ->
          Optional.of(depreciation.resolvedDepreciation() != null);
      case FixedAssetBookkeepingEntryVariants.Disposal disposal ->
          Optional.of(disposal.resolvedDisposal() != null);
      case FinancingBookkeepingEntryVariants.PrincipalRepayment repayment ->
          Optional.of(repayment.resolvedApplication() != null);
      case FinancingBookkeepingEntryVariants.InterestAccrual interestAccrual ->
          Optional.of(interestAccrual.resolvedApplication() != null);
      case FinancingBookkeepingEntryVariants.InterestPayment interestPayment ->
          Optional.of(interestPayment.resolvedApplication() != null);
      case RealizedForeignExchangeBookkeepingEntryVariants.Settlement settlement ->
          Optional.of(settlement.resolvedSettlement() != null);
      default -> Optional.empty();
    };
  }
}
