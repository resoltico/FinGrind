package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Optional recipe metadata for journal-backed business events on the public write surface.
 *
 * <p>The canonical write boundary is the balanced journal. Recipes remain available only as a
 * higher-level convenience layer that derives that journal.
 */
public sealed interface JournalRecipe
    permits JournalRecipe.CashRevenue,
        JournalRecipe.CashExpense,
        JournalRecipe.EquityContribution,
        JournalRecipe.EquityWithdrawal {
  /** Returns the stable public recipe kind. */
  JournalRecipeKind recipeKind();

  /** Materializes the canonical journal owned by this recipe for one effective date. */
  JournalEntry journalEntry(LocalDate effectiveDate);

  /** Cash-settled revenue recipe that debits cash and credits one revenue account. */
  record CashRevenue(
      AccountCode cashAccountCode, AccountCode revenueAccountCode, MonetaryAmount amount)
      implements JournalRecipe {
    public CashRevenue {
      cashAccountCode = requireAccountCode(cashAccountCode, "cashAccountCode");
      revenueAccountCode = requireAccountCode(revenueAccountCode, "revenueAccountCode");
      amount = requirePositiveAmount(amount, "amount");
    }

    @Override
    public JournalRecipeKind recipeKind() {
      return JournalRecipeKind.CASH_REVENUE;
    }

    @Override
    public JournalEntry journalEntry(LocalDate effectiveDate) {
      return pairedEntry(effectiveDate, cashAccountCode, revenueAccountCode, amount);
    }
  }

  /** Cash-settled expense recipe that debits one expense account and credits cash. */
  record CashExpense(
      AccountCode expenseAccountCode, AccountCode cashAccountCode, MonetaryAmount amount)
      implements JournalRecipe {
    public CashExpense {
      expenseAccountCode = requireAccountCode(expenseAccountCode, "expenseAccountCode");
      cashAccountCode = requireAccountCode(cashAccountCode, "cashAccountCode");
      amount = requirePositiveAmount(amount, "amount");
    }

    @Override
    public JournalRecipeKind recipeKind() {
      return JournalRecipeKind.CASH_EXPENSE;
    }

    @Override
    public JournalEntry journalEntry(LocalDate effectiveDate) {
      return pairedEntry(effectiveDate, expenseAccountCode, cashAccountCode, amount);
    }
  }

  /** Equity contribution recipe that debits cash and credits one equity account. */
  record EquityContribution(
      AccountCode cashAccountCode, AccountCode equityAccountCode, MonetaryAmount amount)
      implements JournalRecipe {
    public EquityContribution {
      cashAccountCode = requireAccountCode(cashAccountCode, "cashAccountCode");
      equityAccountCode = requireAccountCode(equityAccountCode, "equityAccountCode");
      amount = requirePositiveAmount(amount, "amount");
    }

    @Override
    public JournalRecipeKind recipeKind() {
      return JournalRecipeKind.EQUITY_CONTRIBUTION;
    }

    @Override
    public JournalEntry journalEntry(LocalDate effectiveDate) {
      return pairedEntry(effectiveDate, cashAccountCode, equityAccountCode, amount);
    }
  }

  /** Equity withdrawal recipe that debits one equity account and credits cash. */
  record EquityWithdrawal(
      AccountCode equityAccountCode, AccountCode cashAccountCode, MonetaryAmount amount)
      implements JournalRecipe {
    public EquityWithdrawal {
      equityAccountCode = requireAccountCode(equityAccountCode, "equityAccountCode");
      cashAccountCode = requireAccountCode(cashAccountCode, "cashAccountCode");
      amount = requirePositiveAmount(amount, "amount");
    }

    @Override
    public JournalRecipeKind recipeKind() {
      return JournalRecipeKind.EQUITY_WITHDRAWAL;
    }

    @Override
    public JournalEntry journalEntry(LocalDate effectiveDate) {
      return pairedEntry(effectiveDate, equityAccountCode, cashAccountCode, amount);
    }
  }

  private static AccountCode requireAccountCode(AccountCode accountCode, String fieldName) {
    return Objects.requireNonNull(accountCode, fieldName);
  }

  private static MonetaryAmount requirePositiveAmount(MonetaryAmount amount, String fieldName) {
    Objects.requireNonNull(amount, fieldName);
    requirePositive(amount, fieldName);
    return amount;
  }

  private static JournalEntry pairedEntry(
      LocalDate effectiveDate,
      AccountCode debitAccountCode,
      AccountCode creditAccountCode,
      MonetaryAmount amount) {
    return new JournalEntry(
        requireEffectiveDate(effectiveDate),
        List.of(
            new JournalLine(debitAccountCode, JournalLine.EntrySide.DEBIT, amount.toMoney()),
            new JournalLine(creditAccountCode, JournalLine.EntrySide.CREDIT, amount.toMoney())));
  }

  private static LocalDate requireEffectiveDate(LocalDate effectiveDate) {
    return Objects.requireNonNull(effectiveDate, "effectiveDate");
  }

  private static void requirePositive(MonetaryAmount amount, String fieldName) {
    if (!amount.toMoney().isPositive()) {
      throw new IllegalArgumentException(fieldName + " must carry one positive amount.");
    }
  }
}
