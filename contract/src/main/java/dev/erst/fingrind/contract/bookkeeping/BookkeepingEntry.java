package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Public bookkeeping write model with one journal-first boundary plus opening and reversal
 * profiles.
 */
public sealed interface BookkeepingEntry
    permits BookkeepingEntry.Journal,
        BookkeepingEntry.OpenAccountingPosition,
        BookkeepingEntry.ReversalAdjustment {
  /** Returns one journal entry backed by the cash-revenue convenience recipe. */
  static Journal cashRevenue(
      LocalDate effectiveDate,
      AccountCode cashAccountCode,
      AccountCode revenueAccountCode,
      MonetaryAmount amount) {
    return new Journal(
        effectiveDate, new JournalRecipe.CashRevenue(cashAccountCode, revenueAccountCode, amount));
  }

  /** Returns one journal entry backed by the cash-expense convenience recipe. */
  static Journal cashExpense(
      LocalDate effectiveDate,
      AccountCode expenseAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount) {
    return new Journal(
        effectiveDate, new JournalRecipe.CashExpense(expenseAccountCode, cashAccountCode, amount));
  }

  /** Returns one journal entry backed by the equity-contribution convenience recipe. */
  static Journal equityContribution(
      LocalDate effectiveDate,
      AccountCode cashAccountCode,
      AccountCode equityAccountCode,
      MonetaryAmount amount) {
    return new Journal(
        effectiveDate,
        new JournalRecipe.EquityContribution(cashAccountCode, equityAccountCode, amount));
  }

  /** Returns one journal entry backed by the equity-withdrawal convenience recipe. */
  static Journal equityWithdrawal(
      LocalDate effectiveDate,
      AccountCode equityAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount) {
    return new Journal(
        effectiveDate,
        new JournalRecipe.EquityWithdrawal(equityAccountCode, cashAccountCode, amount));
  }

  /** Returns the stable caller-authored entry kind. */
  BookkeepingEntryKind entryKind();

  /** Returns the effective date carried by this caller-authored entry. */
  LocalDate effectiveDate();

  /**
   * Canonical operational journal entry, optionally annotated with one higher-level business-event
   * recipe that derives the same journal.
   */
  record Journal(JournalEntry journalEntry, @Nullable JournalRecipe recipe)
      implements BookkeepingEntry {
    public Journal {
      Objects.requireNonNull(journalEntry, "journalEntry");
      if (recipe != null
          && !recipe.journalEntry(journalEntry.effectiveDate()).equals(journalEntry)) {
        throw new IllegalArgumentException(
            "journalEntry must equal the journal derived from the selected recipe.");
      }
    }

    /** Builds one canonical journal from the supplied effective date and higher-level recipe. */
    public Journal(LocalDate effectiveDate, JournalRecipe recipe) {
      this(Objects.requireNonNull(recipe, "recipe").journalEntry(effectiveDate), recipe);
    }

    @Override
    public BookkeepingEntryKind entryKind() {
      return BookkeepingEntryKind.JOURNAL;
    }

    @Override
    public LocalDate effectiveDate() {
      return journalEntry.effectiveDate();
    }

    /** Returns the caller-authored journal lines after any optional recipe expansion. */
    public List<JournalLine> lines() {
      return journalEntry.lines();
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
        if (!amount.toMoney().isPositive()) {
          throw new IllegalArgumentException("amount must carry one positive amount.");
        }
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
}
