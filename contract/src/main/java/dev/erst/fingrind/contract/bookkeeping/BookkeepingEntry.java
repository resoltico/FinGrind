package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Public bookkeeping write-model entry shape with typed business events and named adjustments. */
public sealed interface BookkeepingEntry
    permits BookkeepingEntry.CashRevenue,
        BookkeepingEntry.CashExpense,
        BookkeepingEntry.EquityContribution,
        BookkeepingEntry.EquityWithdrawal,
        BookkeepingEntry.OpenAccountingPosition,
        BookkeepingEntry.ReversalAdjustment {
  /** Returns the stable caller-authored entry kind. */
  BookkeepingEntryKind entryKind();

  /** Returns the effective date carried by this caller-authored entry. */
  LocalDate effectiveDate();

  /** Cash-settled revenue event posted directly from cash into one revenue account. */
  record CashRevenue(
      LocalDate effectiveDate,
      AccountCode cashAccountCode,
      AccountCode revenueAccountCode,
      MonetaryAmount amount)
      implements BookkeepingEntry {
    public CashRevenue {
      Objects.requireNonNull(effectiveDate, "effectiveDate");
      Objects.requireNonNull(cashAccountCode, "cashAccountCode");
      Objects.requireNonNull(revenueAccountCode, "revenueAccountCode");
      Objects.requireNonNull(amount, "amount");
      requirePositive(amount, "amount");
    }

    @Override
    public BookkeepingEntryKind entryKind() {
      return BookkeepingEntryKind.CASH_REVENUE;
    }
  }

  /** Cash-settled expense event posted directly from one expense account into cash. */
  record CashExpense(
      LocalDate effectiveDate,
      AccountCode expenseAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount)
      implements BookkeepingEntry {
    public CashExpense {
      Objects.requireNonNull(effectiveDate, "effectiveDate");
      Objects.requireNonNull(expenseAccountCode, "expenseAccountCode");
      Objects.requireNonNull(cashAccountCode, "cashAccountCode");
      Objects.requireNonNull(amount, "amount");
      requirePositive(amount, "amount");
    }

    @Override
    public BookkeepingEntryKind entryKind() {
      return BookkeepingEntryKind.CASH_EXPENSE;
    }
  }

  /** Equity contribution introduced into the book through cash. */
  record EquityContribution(
      LocalDate effectiveDate,
      AccountCode cashAccountCode,
      AccountCode equityAccountCode,
      MonetaryAmount amount)
      implements BookkeepingEntry {
    public EquityContribution {
      Objects.requireNonNull(effectiveDate, "effectiveDate");
      Objects.requireNonNull(cashAccountCode, "cashAccountCode");
      Objects.requireNonNull(equityAccountCode, "equityAccountCode");
      Objects.requireNonNull(amount, "amount");
      requirePositive(amount, "amount");
    }

    @Override
    public BookkeepingEntryKind entryKind() {
      return BookkeepingEntryKind.EQUITY_CONTRIBUTION;
    }
  }

  /** Equity withdrawal taken out of the book through cash. */
  record EquityWithdrawal(
      LocalDate effectiveDate,
      AccountCode equityAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount)
      implements BookkeepingEntry {
    public EquityWithdrawal {
      Objects.requireNonNull(effectiveDate, "effectiveDate");
      Objects.requireNonNull(equityAccountCode, "equityAccountCode");
      Objects.requireNonNull(cashAccountCode, "cashAccountCode");
      Objects.requireNonNull(amount, "amount");
      requirePositive(amount, "amount");
    }

    @Override
    public BookkeepingEntryKind entryKind() {
      return BookkeepingEntryKind.EQUITY_WITHDRAWAL;
    }
  }

  /**
   * Structured opening accounting position reserved for seeding one book before operating activity
   * begins.
   */
  record OpenAccountingPosition(LocalDate effectiveDate, List<OpeningAccountBalance> balances)
      implements BookkeepingEntry {
    public OpenAccountingPosition {
      Objects.requireNonNull(effectiveDate, "effectiveDate");
      balances = List.copyOf(Objects.requireNonNull(balances, "balances"));
      if (balances.isEmpty()) {
        throw new IllegalArgumentException(
            "Open accounting position requires at least one opening balance.");
      }
    }

    @Override
    public BookkeepingEntryKind entryKind() {
      return BookkeepingEntryKind.OPEN_ACCOUNTING_POSITION;
    }

    /** Returns the caller-authored journal lines implied by this opening accounting position. */
    public List<JournalLine> lines() {
      return balances.stream()
          .map(
              balance ->
                  new JournalLine(
                      balance.accountCode(), balance.side(), balance.amount().toMoney()))
          .toList();
    }

    /** One typed opening balance inside the initial accounting position. */
    public record OpeningAccountBalance(
        AccountCode accountCode, JournalLine.EntrySide side, MonetaryAmount amount) {
      public OpeningAccountBalance {
        Objects.requireNonNull(accountCode, "accountCode");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(amount, "amount");
        requirePositive(amount, "amount");
      }
    }
  }

  /** Explicit administrative reversal entry that negates one previously committed posting. */
  record ReversalAdjustment(JournalEntry journalEntry, PostingLineage.Reversal reversal)
      implements BookkeepingEntry {
    public ReversalAdjustment {
      Objects.requireNonNull(journalEntry, "journalEntry");
      Objects.requireNonNull(reversal, "reversal");
    }

    @Override
    public BookkeepingEntryKind entryKind() {
      return BookkeepingEntryKind.REVERSAL_ADJUSTMENT;
    }

    @Override
    public LocalDate effectiveDate() {
      return journalEntry.effectiveDate();
    }

    /** Returns the caller-authored lines for this reversal entry. */
    public List<JournalLine> lines() {
      return journalEntry.lines();
    }
  }

  private static void requirePositive(MonetaryAmount amount, String fieldName) {
    if (!amount.toMoney().isPositive()) {
      throw new IllegalArgumentException(fieldName + " must carry one positive amount.");
    }
  }
}
