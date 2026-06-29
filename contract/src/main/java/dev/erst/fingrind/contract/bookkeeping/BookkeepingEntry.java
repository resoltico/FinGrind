package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Public bookkeeping write model with one direct-journal path and six typed business-entry
 * variants.
 */
public sealed interface BookkeepingEntry
    permits BookkeepingEntry.DirectJournal,
        BookkeepingEntry.Sale,
        BookkeepingEntry.Expense,
        BookkeepingEntry.OwnerContribution,
        BookkeepingEntry.OwnerWithdrawal,
        BookkeepingEntry.OpeningPosition,
        BookkeepingEntry.Reversal {
  /** Returns the stable caller-authored entry kind. */
  BookkeepingEntryKind entryKind();

  /** Returns the effective date carried by this caller-authored entry. */
  LocalDate effectiveDate();

  /** Returns the canonical journal entry implied by this entry variant. */
  JournalEntry journalEntry();

  /** Returns the canonical durable posting kind implied by this entry variant. */
  default PostingKind postingKind() {
    if (this instanceof OpeningPosition) {
      return PostingKind.OPENING_BALANCE;
    }
    return PostingKind.STANDARD;
  }

  /** Returns the durable posting-origin vocabulary implied by this entry variant. */
  default PostingOriginKind postingOriginKind() {
    return switch (this) {
      case DirectJournal _ -> PostingOriginKind.DIRECT_JOURNAL;
      case Sale _ -> PostingOriginKind.SALE;
      case Expense _ -> PostingOriginKind.EXPENSE;
      case OwnerContribution _ -> PostingOriginKind.OWNER_CONTRIBUTION;
      case OwnerWithdrawal _ -> PostingOriginKind.OWNER_WITHDRAWAL;
      case OpeningPosition _ -> PostingOriginKind.OPENING_POSITION;
      case Reversal _ -> PostingOriginKind.REVERSAL;
    };
  }

  /** Returns the durable posting lineage implied by this entry variant. */
  default PostingLineage postingLineage() {
    if (this instanceof Reversal reversal) {
      return reversal.reversal();
    }
    return PostingLineage.direct();
  }

  /** Returns the caller-authored journal lines carried or implied by this entry variant. */
  default List<JournalLine> lines() {
    return journalEntry().lines();
  }

  /** Returns the optional owned foreign-exchange facts retained for this entry. */
  default @Nullable ForeignExchangeDetails foreignExchangeDetails() {
    return null;
  }

  /** Raw balanced journal that bypasses the typed business-entry helpers. */
  record DirectJournal(
      JournalEntry journalEntry, @Nullable ForeignExchangeDetails foreignExchangeDetails)
      implements BookkeepingEntry {
    public DirectJournal {
      Objects.requireNonNull(journalEntry, "journalEntry");
      BookkeepingEntrySupport.requireDirectJournalForeignExchange(
          journalEntry, foreignExchangeDetails);
    }

    @Override
    public BookkeepingEntryKind entryKind() {
      return BookkeepingEntryKind.DIRECT_JOURNAL;
    }

    @Override
    public LocalDate effectiveDate() {
      return journalEntry.effectiveDate();
    }
  }

  /** Cash-settled sale entry that debits cash and credits revenue. */
  record Sale(
      LocalDate effectiveDate,
      AccountCode cashAccountCode,
      AccountCode revenueAccountCode,
      MonetaryAmount amount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax)
      implements BookkeepingEntry {
    public Sale {
      effectiveDate = BookkeepingEntrySupport.requireEffectiveDate(effectiveDate);
      cashAccountCode =
          BookkeepingEntrySupport.requireAccountCode(cashAccountCode, "cashAccountCode");
      revenueAccountCode =
          BookkeepingEntrySupport.requireAccountCode(revenueAccountCode, "revenueAccountCode");
      amount = BookkeepingEntrySupport.requirePositiveAmount(amount, "amount");
      BookkeepingEntrySupport.requireTypedEntryForeignExchange(
          amount,
          foreignExchangeDetails,
          BookkeepingEntrySupport.ForeignExchangeAllowance.SPOT_SETTLEMENT_ONLY,
          "sale");
      BookkeepingEntrySupport.requireTaxSelectionState(
          amount, taxSelection, appliedTax, TaxApplicationKind.OUTPUT_SALE);
    }

    @Override
    public BookkeepingEntryKind entryKind() {
      return BookkeepingEntryKind.SALE;
    }

    @Override
    public JournalEntry journalEntry() {
      if (taxSelection == null) {
        return BookkeepingEntrySupport.pairedEntry(
            effectiveDate, cashAccountCode, revenueAccountCode, amount);
      }
      AppliedTax resolvedTax =
          BookkeepingEntrySupport.requireResolvedAppliedTax(appliedTax, "sale");
      return BookkeepingEntrySupport.saleEntry(
          effectiveDate, cashAccountCode, revenueAccountCode, amount, resolvedTax);
    }
  }

  /** Cash-settled expense entry that debits expense and credits cash. */
  record Expense(
      LocalDate effectiveDate,
      AccountCode expenseAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax)
      implements BookkeepingEntry {
    public Expense {
      effectiveDate = BookkeepingEntrySupport.requireEffectiveDate(effectiveDate);
      expenseAccountCode =
          BookkeepingEntrySupport.requireAccountCode(expenseAccountCode, "expenseAccountCode");
      cashAccountCode =
          BookkeepingEntrySupport.requireAccountCode(cashAccountCode, "cashAccountCode");
      amount = BookkeepingEntrySupport.requirePositiveAmount(amount, "amount");
      BookkeepingEntrySupport.requireTypedEntryForeignExchange(
          amount,
          foreignExchangeDetails,
          BookkeepingEntrySupport.ForeignExchangeAllowance.SPOT_SETTLEMENT_ONLY,
          "expense");
      BookkeepingEntrySupport.requireTaxSelectionState(
          amount,
          taxSelection,
          appliedTax,
          TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
          TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE);
    }

    @Override
    public BookkeepingEntryKind entryKind() {
      return BookkeepingEntryKind.EXPENSE;
    }

    @Override
    public JournalEntry journalEntry() {
      if (taxSelection == null) {
        return BookkeepingEntrySupport.pairedEntry(
            effectiveDate, expenseAccountCode, cashAccountCode, amount);
      }
      AppliedTax resolvedTax =
          BookkeepingEntrySupport.requireResolvedAppliedTax(appliedTax, "expense");
      return BookkeepingEntrySupport.expenseEntry(
          effectiveDate, expenseAccountCode, cashAccountCode, amount, resolvedTax);
    }
  }

  /** Owner-contribution entry that debits cash and credits equity. */
  record OwnerContribution(
      LocalDate effectiveDate,
      AccountCode cashAccountCode,
      AccountCode equityAccountCode,
      MonetaryAmount amount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails)
      implements BookkeepingEntry {
    public OwnerContribution {
      effectiveDate = BookkeepingEntrySupport.requireEffectiveDate(effectiveDate);
      cashAccountCode =
          BookkeepingEntrySupport.requireAccountCode(cashAccountCode, "cashAccountCode");
      equityAccountCode =
          BookkeepingEntrySupport.requireAccountCode(equityAccountCode, "equityAccountCode");
      amount = BookkeepingEntrySupport.requirePositiveAmount(amount, "amount");
      BookkeepingEntrySupport.requireTypedEntryForeignExchange(
          amount,
          foreignExchangeDetails,
          BookkeepingEntrySupport.ForeignExchangeAllowance.SPOT_SETTLEMENT_ONLY,
          "ownerContribution");
    }

    @Override
    public BookkeepingEntryKind entryKind() {
      return BookkeepingEntryKind.OWNER_CONTRIBUTION;
    }

    @Override
    public JournalEntry journalEntry() {
      return BookkeepingEntrySupport.pairedEntry(
          effectiveDate, cashAccountCode, equityAccountCode, amount);
    }
  }

  /** Owner-withdrawal entry that debits equity and credits cash. */
  record OwnerWithdrawal(
      LocalDate effectiveDate,
      AccountCode equityAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails)
      implements BookkeepingEntry {
    public OwnerWithdrawal {
      effectiveDate = BookkeepingEntrySupport.requireEffectiveDate(effectiveDate);
      equityAccountCode =
          BookkeepingEntrySupport.requireAccountCode(equityAccountCode, "equityAccountCode");
      cashAccountCode =
          BookkeepingEntrySupport.requireAccountCode(cashAccountCode, "cashAccountCode");
      amount = BookkeepingEntrySupport.requirePositiveAmount(amount, "amount");
      BookkeepingEntrySupport.requireTypedEntryForeignExchange(
          amount,
          foreignExchangeDetails,
          BookkeepingEntrySupport.ForeignExchangeAllowance.SPOT_SETTLEMENT_ONLY,
          "ownerWithdrawal");
    }

    @Override
    public BookkeepingEntryKind entryKind() {
      return BookkeepingEntryKind.OWNER_WITHDRAWAL;
    }

    @Override
    public JournalEntry journalEntry() {
      return BookkeepingEntrySupport.pairedEntry(
          effectiveDate, equityAccountCode, cashAccountCode, amount);
    }
  }

  /** Structured opening position reserved for seeding one book before ordinary activity begins. */
  record OpeningPosition(LocalDate effectiveDate, List<OpeningAccountBalance> balances)
      implements BookkeepingEntry {
    public OpeningPosition {
      Objects.requireNonNull(effectiveDate, "effectiveDate");
      balances = List.copyOf(Objects.requireNonNull(balances, "balances"));
      if (balances.isEmpty()) {
        throw new IllegalArgumentException(
            "Opening position requires at least one opening balance.");
      }
    }

    @Override
    public BookkeepingEntryKind entryKind() {
      return BookkeepingEntryKind.OPENING_POSITION;
    }

    @Override
    public JournalEntry journalEntry() {
      return new JournalEntry(effectiveDate, lines());
    }

    @Override
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

  /** Explicit reversal entry that negates one previously committed posting. */
  record Reversal(
      JournalEntry journalEntry,
      PostingLineage.Reversal reversal,
      @Nullable ForeignExchangeDetails foreignExchangeDetails)
      implements BookkeepingEntry {
    public Reversal {
      Objects.requireNonNull(journalEntry, "journalEntry");
      Objects.requireNonNull(reversal, "reversal");
      BookkeepingEntrySupport.requireDirectJournalForeignExchange(
          journalEntry, foreignExchangeDetails);
    }

    @Override
    public BookkeepingEntryKind entryKind() {
      return BookkeepingEntryKind.REVERSAL;
    }

    @Override
    public LocalDate effectiveDate() {
      return journalEntry.effectiveDate();
    }
  }
}
