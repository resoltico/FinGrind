package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingOriginKind;

/** Entry-surface behavior owned by financing bookkeeping-entry variants. */
final class FinancingBookkeepingEntrySurfaceSupport {
  private FinancingBookkeepingEntrySurfaceSupport() {}

  static BookkeepingEntryKind entryKind(FinancingBookkeepingEntryVariants entry) {
    return switch (entry) {
      case FinancingBookkeepingEntryVariants.Borrowing _ ->
          BookkeepingEntryKind.FINANCING_BORROWING;
      case FinancingBookkeepingEntryVariants.PrincipalRepayment _ ->
          BookkeepingEntryKind.FINANCING_PRINCIPAL_REPAYMENT;
      case FinancingBookkeepingEntryVariants.InterestAccrual _ ->
          BookkeepingEntryKind.FINANCING_INTEREST_ACCRUAL;
      case FinancingBookkeepingEntryVariants.InterestPayment _ ->
          BookkeepingEntryKind.FINANCING_INTEREST_PAYMENT;
    };
  }

  static PostingOriginKind postingOriginKind(FinancingBookkeepingEntryVariants entry) {
    return switch (entry) {
      case FinancingBookkeepingEntryVariants.Borrowing _ -> PostingOriginKind.FINANCING_BORROWING;
      case FinancingBookkeepingEntryVariants.PrincipalRepayment _ ->
          PostingOriginKind.FINANCING_PRINCIPAL_REPAYMENT;
      case FinancingBookkeepingEntryVariants.InterestAccrual _ ->
          PostingOriginKind.FINANCING_INTEREST_ACCRUAL;
      case FinancingBookkeepingEntryVariants.InterestPayment _ ->
          PostingOriginKind.FINANCING_INTEREST_PAYMENT;
    };
  }

  static JournalEntry journalEntry(FinancingBookkeepingEntryVariants entry) {
    return switch (entry) {
      case FinancingBookkeepingEntryVariants.Borrowing borrowing ->
          BookkeepingEntrySupport.pairedEntry(
              borrowing.effectiveDate(),
              borrowing.cashAccountCode(),
              borrowing.principalLiabilityAccountCode(),
              borrowing.principalAmount());
      case FinancingBookkeepingEntryVariants.PrincipalRepayment repayment ->
          BookkeepingEntrySupport.pairedEntry(
              repayment.effectiveDate(),
              requiredApplication(repayment.resolvedApplication(), "principalRepayment")
                  .principalLiabilityAccountCode(),
              repayment.cashAccountCode(),
              repayment.principalAmount());
      case FinancingBookkeepingEntryVariants.InterestAccrual interestAccrual ->
          BookkeepingEntrySupport.pairedEntry(
              interestAccrual.effectiveDate(),
              interestAccrual.interestExpenseAccountCode(),
              requiredApplication(interestAccrual.resolvedApplication(), "interestAccrual")
                  .interestPayableAccountCode(),
              interestAccrual.interestAmount());
      case FinancingBookkeepingEntryVariants.InterestPayment interestPayment ->
          BookkeepingEntrySupport.pairedEntry(
              interestPayment.effectiveDate(),
              requiredApplication(interestPayment.resolvedApplication(), "interestPayment")
                  .interestPayableAccountCode(),
              interestPayment.cashAccountCode(),
              interestPayment.interestAmount());
    };
  }

  private static ResolvedFinancingApplication requiredApplication(
      @org.jspecify.annotations.Nullable ResolvedFinancingApplication resolvedApplication,
      String entryName) {
    if (resolvedApplication == null) {
      throw new IllegalStateException(entryName + " requires executor-resolved financing facts.");
    }
    return resolvedApplication;
  }
}
