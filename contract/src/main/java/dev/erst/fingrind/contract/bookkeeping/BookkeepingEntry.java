package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.tax.AppliedTax;
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

/** Public bookkeeping write model with one direct-journal path and typed entry variants. */
public sealed interface BookkeepingEntry
    permits BookkeepingEntry.DirectJournal,
        BookkeepingEntry.SaleSettled,
        BookkeepingEntry.SaleOnCredit,
        BookkeepingEntry.PurchaseSettled,
        BookkeepingEntry.PurchaseOnCredit,
        BookkeepingEntry.ExpenseSettled,
        BookkeepingEntry.ExpenseOnCredit,
        BookkeepingEntry.Receipt,
        BookkeepingEntry.Payment,
        BookkeepingEntry.OwnerContribution,
        BookkeepingEntry.OwnerWithdrawal,
        BookkeepingEntry.OpeningPosition,
        BookkeepingEntry.Reversal {
  /** Returns the stable caller-authored entry kind. */
  default BookkeepingEntryKind entryKind() {
    return BookkeepingEntrySurfaceSupport.entryKind(this);
  }

  /** Returns the effective date carried by this caller-authored entry. */
  LocalDate effectiveDate();

  /** Returns the canonical journal entry implied by this entry variant. */
  default JournalEntry journalEntry() {
    return BookkeepingEntrySurfaceSupport.journalEntry(this);
  }

  /** Returns the canonical durable posting kind implied by this entry variant. */
  default PostingKind postingKind() {
    return BookkeepingEntrySurfaceSupport.postingKind(this);
  }

  /** Returns the durable posting-origin vocabulary implied by this entry variant. */
  default PostingOriginKind postingOriginKind() {
    return BookkeepingEntrySurfaceSupport.postingOriginKind(this);
  }

  /** Returns the durable posting lineage implied by this entry variant. */
  default PostingLineage postingLineage() {
    return BookkeepingEntrySurfaceSupport.postingLineage(this);
  }

  /** Returns the caller-authored journal lines carried or implied by this entry variant. */
  default List<JournalLine> lines() {
    return journalEntry().lines();
  }

  /** Returns the optional owned foreign-exchange facts retained for this entry. */
  default @Nullable ForeignExchangeDetails foreignExchangeDetails() {
    return null;
  }

  record DirectJournal(
      JournalEntry journalEntry, @Nullable ForeignExchangeDetails foreignExchangeDetails)
      implements BookkeepingEntry {
    public DirectJournal {
      Objects.requireNonNull(journalEntry, "journalEntry");
      BookkeepingEntryValidationSupport.requireDirectJournalForeignExchange(
          journalEntry, foreignExchangeDetails);
    }

    @Override
    public LocalDate effectiveDate() {
      return journalEntry.effectiveDate();
    }
  }

  record SaleSettled(
      LocalDate effectiveDate,
      AccountCode cashAccountCode,
      AccountCode revenueAccountCode,
      MonetaryAmount amount,
      @Nullable InventoryRelief inventoryRelief,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax)
      implements BookkeepingEntry {
    public SaleSettled {
      var state =
          BookkeepingEntryConstructionSupport.saleSettled(
              effectiveDate,
              cashAccountCode,
              revenueAccountCode,
              amount,
              inventoryRelief,
              foreignExchangeDetails,
              taxSelection,
              appliedTax);
      effectiveDate = state.effectiveDate();
      cashAccountCode = state.cashAccountCode();
      revenueAccountCode = state.revenueAccountCode();
      amount = state.amount();
      inventoryRelief = state.inventoryRelief();
    }
  }

  record SaleOnCredit(
      LocalDate effectiveDate,
      AccountCode receivableAccountCode,
      AccountCode revenueAccountCode,
      MonetaryAmount amount,
      @Nullable InventoryRelief inventoryRelief,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax)
      implements BookkeepingEntry {
    public SaleOnCredit {
      var state =
          BookkeepingEntryConstructionSupport.saleOnCredit(
              effectiveDate,
              receivableAccountCode,
              revenueAccountCode,
              amount,
              inventoryRelief,
              taxSelection,
              appliedTax);
      effectiveDate = state.effectiveDate();
      receivableAccountCode = state.receivableAccountCode();
      revenueAccountCode = state.revenueAccountCode();
      amount = state.amount();
      inventoryRelief = state.inventoryRelief();
    }
  }

  record PurchaseSettled(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails)
      implements BookkeepingEntry {
    public PurchaseSettled {
      var state =
          BookkeepingEntryConstructionSupport.purchaseSettled(
              effectiveDate, inventoryAccountCode, cashAccountCode, amount, foreignExchangeDetails);
      effectiveDate = state.effectiveDate();
      inventoryAccountCode = state.inventoryAccountCode();
      cashAccountCode = state.cashAccountCode();
      amount = state.amount();
    }
  }

  record PurchaseOnCredit(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode payableAccountCode,
      MonetaryAmount amount)
      implements BookkeepingEntry {
    public PurchaseOnCredit {
      var state =
          BookkeepingEntryConstructionSupport.purchaseOnCredit(
              effectiveDate, inventoryAccountCode, payableAccountCode, amount);
      effectiveDate = state.effectiveDate();
      inventoryAccountCode = state.inventoryAccountCode();
      payableAccountCode = state.payableAccountCode();
      amount = state.amount();
    }
  }

  record ExpenseSettled(
      LocalDate effectiveDate,
      AccountCode expenseAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax)
      implements BookkeepingEntry {
    public ExpenseSettled {
      var state =
          BookkeepingEntryConstructionSupport.expenseSettled(
              effectiveDate,
              expenseAccountCode,
              cashAccountCode,
              amount,
              foreignExchangeDetails,
              taxSelection,
              appliedTax);
      effectiveDate = state.effectiveDate();
      expenseAccountCode = state.expenseAccountCode();
      cashAccountCode = state.cashAccountCode();
      amount = state.amount();
    }
  }

  record ExpenseOnCredit(
      LocalDate effectiveDate,
      AccountCode expenseAccountCode,
      AccountCode payableAccountCode,
      MonetaryAmount amount,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax)
      implements BookkeepingEntry {
    public ExpenseOnCredit {
      var state =
          BookkeepingEntryConstructionSupport.expenseOnCredit(
              effectiveDate,
              expenseAccountCode,
              payableAccountCode,
              amount,
              taxSelection,
              appliedTax);
      effectiveDate = state.effectiveDate();
      expenseAccountCode = state.expenseAccountCode();
      payableAccountCode = state.payableAccountCode();
      amount = state.amount();
    }
  }

  record Receipt(
      LocalDate effectiveDate,
      AccountCode cashAccountCode,
      AccountCode receivableAccountCode,
      MonetaryAmount amount,
      @Nullable SettlementAdjunct settlementAdjunct)
      implements BookkeepingEntry {
    public Receipt {
      var state =
          BookkeepingEntryCashMovementConstructionSupport.receipt(
              effectiveDate, cashAccountCode, receivableAccountCode, amount, settlementAdjunct);
      effectiveDate = state.effectiveDate();
      cashAccountCode = state.cashAccountCode();
      receivableAccountCode = state.receivableAccountCode();
      amount = state.amount();
    }
  }

  record Payment(
      LocalDate effectiveDate,
      AccountCode payableAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount,
      @Nullable SettlementAdjunct settlementAdjunct)
      implements BookkeepingEntry {
    public Payment {
      var state =
          BookkeepingEntryCashMovementConstructionSupport.payment(
              effectiveDate, payableAccountCode, cashAccountCode, amount, settlementAdjunct);
      effectiveDate = state.effectiveDate();
      payableAccountCode = state.payableAccountCode();
      cashAccountCode = state.cashAccountCode();
      amount = state.amount();
    }
  }

  record OwnerContribution(
      LocalDate effectiveDate,
      AccountCode cashAccountCode,
      AccountCode equityAccountCode,
      MonetaryAmount amount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails)
      implements BookkeepingEntry {
    public OwnerContribution {
      var state =
          BookkeepingEntryCashMovementConstructionSupport.ownerContribution(
              effectiveDate, cashAccountCode, equityAccountCode, amount, foreignExchangeDetails);
      effectiveDate = state.effectiveDate();
      cashAccountCode = state.cashAccountCode();
      equityAccountCode = state.equityAccountCode();
      amount = state.amount();
    }
  }

  record OwnerWithdrawal(
      LocalDate effectiveDate,
      AccountCode equityAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails)
      implements BookkeepingEntry {
    public OwnerWithdrawal {
      var state =
          BookkeepingEntryCashMovementConstructionSupport.ownerWithdrawal(
              effectiveDate, equityAccountCode, cashAccountCode, amount, foreignExchangeDetails);
      effectiveDate = state.effectiveDate();
      equityAccountCode = state.equityAccountCode();
      cashAccountCode = state.cashAccountCode();
      amount = state.amount();
    }
  }

  record OpeningPosition(LocalDate effectiveDate, List<OpeningAccountBalance> balances)
      implements BookkeepingEntry {
    public OpeningPosition {
      effectiveDate = BookkeepingEntryValidationSupport.requireEffectiveDate(effectiveDate);
      balances = BookkeepingEntryValidationSupport.requireOpeningBalances(balances);
    }

    public record OpeningAccountBalance(
        AccountCode accountCode, JournalLine.EntrySide side, MonetaryAmount amount) {
      public OpeningAccountBalance {
        BookkeepingEntryValidationSupport.requireOpeningAccountBalance(accountCode, side, amount);
      }
    }
  }

  record Reversal(
      LocalDate effectiveDate,
      PostingLineage.Reversal reversal,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      @Nullable JournalEntry resolvedJournalEntry)
      implements BookkeepingEntry {
    public Reversal {
      BookkeepingEntryValidationSupport.requireResolvedReversal(
          effectiveDate, reversal, resolvedJournalEntry, foreignExchangeDetails);
    }

    @Override
    public JournalEntry journalEntry() {
      return BookkeepingEntryValidationSupport.requireResolvedJournalEntry(
          resolvedJournalEntry, "Reversal");
    }
  }
}
