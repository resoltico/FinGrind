package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFinancingApplication;
import org.jspecify.annotations.Nullable;

/** Maps financing entry facts to the scalar provenance columns retained with a posting. */
final class SqliteFinancingOriginatingEntryFactValues {
  private SqliteFinancingOriginatingEntryFactValues() {}

  static SqliteOriginatingEntryFactMapper.OriginatingEntryFactValues originatingEntryFactValues(
      FinancingBookkeepingEntryVariants entry) {
    return switch (entry) {
      case FinancingBookkeepingEntryVariants.Borrowing borrowing ->
          SqliteOriginatingEntryFactMapper.simpleOriginatingEntryFactValues(
              borrowing.cashAccountCode().value(),
              borrowing.principalLiabilityAccountCode().value(),
              borrowing.principalAmount(),
              null);
      case FinancingBookkeepingEntryVariants.PrincipalRepayment repayment ->
          SqliteOriginatingEntryFactMapper.simpleOriginatingEntryFactValues(
              requireResolvedApplication(repayment.resolvedApplication())
                  .principalLiabilityAccountCode()
                  .value(),
              repayment.cashAccountCode().value(),
              repayment.principalAmount(),
              null);
      case FinancingBookkeepingEntryVariants.InterestAccrual accrual ->
          SqliteOriginatingEntryFactMapper.simpleOriginatingEntryFactValues(
              accrual.interestExpenseAccountCode().value(),
              requireResolvedApplication(accrual.resolvedApplication())
                  .interestPayableAccountCode()
                  .value(),
              accrual.interestAmount(),
              null);
      case FinancingBookkeepingEntryVariants.InterestPayment payment ->
          SqliteOriginatingEntryFactMapper.simpleOriginatingEntryFactValues(
              requireResolvedApplication(payment.resolvedApplication())
                  .interestPayableAccountCode()
                  .value(),
              payment.cashAccountCode().value(),
              payment.interestAmount(),
              null);
    };
  }

  private static ResolvedFinancingApplication requireResolvedApplication(
      @Nullable ResolvedFinancingApplication resolved) {
    return java.util.Objects.requireNonNull(
        resolved, "financing application requires executor resolution");
  }
}
