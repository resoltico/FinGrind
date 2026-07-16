package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Typed write variants owned by the financing context. */
public sealed interface FinancingBookkeepingEntryVariants extends TypedBookkeepingEntry
    permits FinancingBookkeepingEntryVariants.Borrowing,
        FinancingBookkeepingEntryVariants.PrincipalRepayment,
        FinancingBookkeepingEntryVariants.InterestAccrual,
        FinancingBookkeepingEntryVariants.InterestPayment {
  /** Records the initial receipt of one financing arrangement. */
  record Borrowing(
      LocalDate effectiveDate,
      FinancingArrangementId financingArrangementId,
      AccountCode cashAccountCode,
      AccountCode principalLiabilityAccountCode,
      AccountCode interestPayableAccountCode,
      MonetaryAmount principalAmount)
      implements FinancingBookkeepingEntryVariants {
    public Borrowing {
      effectiveDate = BookkeepingEntryScalarValidationSupport.requireEffectiveDate(effectiveDate);
      java.util.Objects.requireNonNull(financingArrangementId, "financingArrangementId");
      cashAccountCode =
          BookkeepingEntryScalarValidationSupport.requireAccountCode(
              cashAccountCode, "cashAccountCode");
      principalLiabilityAccountCode =
          BookkeepingEntryScalarValidationSupport.requireAccountCode(
              principalLiabilityAccountCode, "principalLiabilityAccountCode");
      interestPayableAccountCode =
          BookkeepingEntryScalarValidationSupport.requireAccountCode(
              interestPayableAccountCode, "interestPayableAccountCode");
      principalAmount =
          BookkeepingEntryScalarValidationSupport.requirePositiveAmount(
              principalAmount, "principalAmount");
    }
  }

  /** Repays principal from cash against an admitted financing arrangement. */
  record PrincipalRepayment(
      LocalDate effectiveDate,
      FinancingArrangementId financingArrangementId,
      AccountCode cashAccountCode,
      MonetaryAmount principalAmount,
      @Nullable ResolvedFinancingApplication resolvedApplication)
      implements FinancingBookkeepingEntryVariants {
    public PrincipalRepayment {
      effectiveDate = BookkeepingEntryScalarValidationSupport.requireEffectiveDate(effectiveDate);
      java.util.Objects.requireNonNull(financingArrangementId, "financingArrangementId");
      cashAccountCode =
          BookkeepingEntryScalarValidationSupport.requireAccountCode(
              cashAccountCode, "cashAccountCode");
      principalAmount =
          BookkeepingEntryScalarValidationSupport.requirePositiveAmount(
              principalAmount, "principalAmount");
    }
  }

  /** Accrues interest under an admitted financing arrangement. */
  record InterestAccrual(
      LocalDate effectiveDate,
      FinancingArrangementId financingArrangementId,
      AccountCode interestExpenseAccountCode,
      MonetaryAmount interestAmount,
      @Nullable ResolvedFinancingApplication resolvedApplication)
      implements FinancingBookkeepingEntryVariants {
    public InterestAccrual {
      effectiveDate = BookkeepingEntryScalarValidationSupport.requireEffectiveDate(effectiveDate);
      java.util.Objects.requireNonNull(financingArrangementId, "financingArrangementId");
      interestExpenseAccountCode =
          BookkeepingEntryScalarValidationSupport.requireAccountCode(
              interestExpenseAccountCode, "interestExpenseAccountCode");
      interestAmount =
          BookkeepingEntryScalarValidationSupport.requirePositiveAmount(
              interestAmount, "interestAmount");
    }
  }

  /** Pays already accrued interest from cash against an admitted financing arrangement. */
  record InterestPayment(
      LocalDate effectiveDate,
      FinancingArrangementId financingArrangementId,
      AccountCode cashAccountCode,
      MonetaryAmount interestAmount,
      @Nullable ResolvedFinancingApplication resolvedApplication)
      implements FinancingBookkeepingEntryVariants {
    public InterestPayment {
      effectiveDate = BookkeepingEntryScalarValidationSupport.requireEffectiveDate(effectiveDate);
      java.util.Objects.requireNonNull(financingArrangementId, "financingArrangementId");
      cashAccountCode =
          BookkeepingEntryScalarValidationSupport.requireAccountCode(
              cashAccountCode, "cashAccountCode");
      interestAmount =
          BookkeepingEntryScalarValidationSupport.requirePositiveAmount(
              interestAmount, "interestAmount");
    }
  }
}
