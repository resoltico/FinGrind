package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Typed write variants owned by the accrual cut-off context. */
public sealed interface AccrualCutoffBookkeepingEntryVariants extends TypedBookkeepingEntry
    permits AccrualCutoffBookkeepingEntryVariants.Prepayment,
        AccrualCutoffBookkeepingEntryVariants.DeferredRevenue,
        AccrualCutoffBookkeepingEntryVariants.AccruedExpense,
        AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition,
        AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement {
  /** Records one cash-funded prepayment and its permitted recognition interval. */
  record Prepayment(
      LocalDate effectiveDate,
      AccrualCutoffId accrualCutoffId,
      AccountCode prepaymentAssetAccountCode,
      AccountCode expenseAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount,
      AccrualCutoffRecognitionInterval recognitionInterval)
      implements AccrualCutoffBookkeepingEntryVariants {
    public Prepayment {
      var state =
          AccrualCutoffEntryConstructionSupport.prepayment(
              effectiveDate,
              accrualCutoffId,
              prepaymentAssetAccountCode,
              expenseAccountCode,
              cashAccountCode,
              amount,
              recognitionInterval);
      effectiveDate = state.effectiveDate();
      accrualCutoffId = state.accrualCutoffId();
      prepaymentAssetAccountCode = state.prepaymentAssetAccountCode();
      expenseAccountCode = state.expenseAccountCode();
      cashAccountCode = state.cashAccountCode();
      amount = state.amount();
      recognitionInterval = state.recognitionInterval();
    }
  }

  /** Records one cash-funded deferred-revenue liability and its recognition interval. */
  record DeferredRevenue(
      LocalDate effectiveDate,
      AccrualCutoffId accrualCutoffId,
      AccountCode cashAccountCode,
      AccountCode deferredRevenueAccountCode,
      AccountCode revenueAccountCode,
      MonetaryAmount amount,
      AccrualCutoffRecognitionInterval recognitionInterval)
      implements AccrualCutoffBookkeepingEntryVariants {
    public DeferredRevenue {
      var state =
          AccrualCutoffEntryConstructionSupport.deferredRevenue(
              effectiveDate,
              accrualCutoffId,
              cashAccountCode,
              deferredRevenueAccountCode,
              revenueAccountCode,
              amount,
              recognitionInterval);
      effectiveDate = state.effectiveDate();
      accrualCutoffId = state.accrualCutoffId();
      cashAccountCode = state.cashAccountCode();
      deferredRevenueAccountCode = state.deferredRevenueAccountCode();
      revenueAccountCode = state.revenueAccountCode();
      amount = state.amount();
      recognitionInterval = state.recognitionInterval();
    }
  }

  /** Recognizes one expense and records its outstanding accrued-expense liability. */
  record AccruedExpense(
      LocalDate effectiveDate,
      AccrualCutoffId accrualCutoffId,
      AccountCode expenseAccountCode,
      AccountCode accruedExpenseLiabilityAccountCode,
      MonetaryAmount amount)
      implements AccrualCutoffBookkeepingEntryVariants {
    public AccruedExpense {
      var state =
          AccrualCutoffEntryConstructionSupport.accruedExpense(
              effectiveDate,
              accrualCutoffId,
              expenseAccountCode,
              accruedExpenseLiabilityAccountCode,
              amount);
      effectiveDate = state.effectiveDate();
      accrualCutoffId = state.accrualCutoffId();
      expenseAccountCode = state.expenseAccountCode();
      accruedExpenseLiabilityAccountCode = state.accruedExpenseLiabilityAccountCode();
      amount = state.amount();
    }
  }

  /** Releases part of one admitted prepayment or deferred-revenue cut-off. */
  record AccrualCutoffRecognition(
      LocalDate effectiveDate,
      AccrualCutoffId accrualCutoffId,
      MonetaryAmount amount,
      @Nullable ResolvedAccrualCutoffApplication resolvedApplication)
      implements AccrualCutoffBookkeepingEntryVariants {
    public AccrualCutoffRecognition {
      var state =
          AccrualCutoffEntryConstructionSupport.recognition(
              effectiveDate, accrualCutoffId, amount, resolvedApplication);
      effectiveDate = state.effectiveDate();
      accrualCutoffId = state.accrualCutoffId();
      amount = state.amount();
      resolvedApplication = state.resolvedApplication();
    }
  }

  /** Pays part of one admitted accrued-expense liability. */
  record AccruedExpenseSettlement(
      LocalDate effectiveDate,
      AccrualCutoffId accrualCutoffId,
      AccountCode cashAccountCode,
      MonetaryAmount amount,
      @Nullable ResolvedAccrualCutoffApplication resolvedApplication)
      implements AccrualCutoffBookkeepingEntryVariants {
    public AccruedExpenseSettlement {
      var state =
          AccrualCutoffEntryConstructionSupport.accruedExpenseSettlement(
              effectiveDate, accrualCutoffId, cashAccountCode, amount, resolvedApplication);
      effectiveDate = state.effectiveDate();
      accrualCutoffId = state.accrualCutoffId();
      cashAccountCode = state.cashAccountCode();
      amount = state.amount();
      resolvedApplication = state.resolvedApplication();
    }
  }
}
