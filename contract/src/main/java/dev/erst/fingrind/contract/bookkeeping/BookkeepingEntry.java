package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Public bookkeeping write-model entry shape with typed business events and manual adjustment. */
public sealed interface BookkeepingEntry
    permits BookkeepingEntry.CashRevenue,
        BookkeepingEntry.CashExpense,
        BookkeepingEntry.OwnerContribution,
        BookkeepingEntry.OwnerDraw,
        BookkeepingEntry.ManualAdjustment {
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

  /** Owner capital introduced into the book through cash. */
  record OwnerContribution(
      LocalDate effectiveDate,
      AccountCode cashAccountCode,
      AccountCode equityAccountCode,
      MonetaryAmount amount)
      implements BookkeepingEntry {
    public OwnerContribution {
      Objects.requireNonNull(effectiveDate, "effectiveDate");
      Objects.requireNonNull(cashAccountCode, "cashAccountCode");
      Objects.requireNonNull(equityAccountCode, "equityAccountCode");
      Objects.requireNonNull(amount, "amount");
      requirePositive(amount, "amount");
    }

    @Override
    public BookkeepingEntryKind entryKind() {
      return BookkeepingEntryKind.OWNER_CONTRIBUTION;
    }
  }

  /** Owner draw taken out of the book through cash. */
  record OwnerDraw(
      LocalDate effectiveDate,
      AccountCode equityAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount)
      implements BookkeepingEntry {
    public OwnerDraw {
      Objects.requireNonNull(effectiveDate, "effectiveDate");
      Objects.requireNonNull(equityAccountCode, "equityAccountCode");
      Objects.requireNonNull(cashAccountCode, "cashAccountCode");
      Objects.requireNonNull(amount, "amount");
      requirePositive(amount, "amount");
    }

    @Override
    public BookkeepingEntryKind entryKind() {
      return BookkeepingEntryKind.OWNER_DRAW;
    }
  }

  /**
   * Explicit privileged raw-journal path reserved for adjustments, openings, and reversals that do
   * not belong to one supported typed business event.
   */
  record ManualAdjustment(
      PostingKind postingKind, JournalEntry journalEntry, PostingLineage postingLineage)
      implements BookkeepingEntry {
    public ManualAdjustment {
      Objects.requireNonNull(postingKind, "postingKind");
      Objects.requireNonNull(journalEntry, "journalEntry");
      Objects.requireNonNull(postingLineage, "postingLineage");
    }

    @Override
    public BookkeepingEntryKind entryKind() {
      return BookkeepingEntryKind.MANUAL_ADJUSTMENT;
    }

    @Override
    public LocalDate effectiveDate() {
      return journalEntry.effectiveDate();
    }

    /** Returns the caller-authored adjustment lines for this explicit manual path. */
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
