package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Public bookkeeping write model with one direct-journal path and typed entry variants. */
public sealed interface BookkeepingEntry extends BookkeepingEntrySurface
    permits BookkeepingEntry.ScalarFactFree, TypedBookkeepingEntry {
  /**
   * Entry form whose durable projection has no generic account, amount, quantity, or unit-cost
   * facts.
   */
  sealed interface ScalarFactFree extends BookkeepingEntry
      permits BookkeepingEntry.DirectJournal,
          BookkeepingEntry.OpeningPosition,
          BookkeepingEntry.Reversal {}

  record DirectJournal(
      JournalEntry journalEntry, @Nullable ForeignExchangeDetails foreignExchangeDetails)
      implements ScalarFactFree {
    public DirectJournal {
      Objects.requireNonNull(journalEntry, "journalEntry");
      BookkeepingEntryForeignExchangeValidationSupport.requireDirectJournalForeignExchange(
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
      @Nullable ResolvedInventoryCosting resolvedInventoryCosting,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax)
      implements StandardBookkeepingEntryVariants {
    public SaleSettled {
      var state =
          BookkeepingEntryConstructionSupport.saleSettled(
              effectiveDate,
              cashAccountCode,
              revenueAccountCode,
              amount,
              inventoryRelief,
              resolvedInventoryCosting,
              foreignExchangeDetails,
              taxSelection,
              appliedTax);
      effectiveDate = state.effectiveDate();
      cashAccountCode = state.cashAccountCode();
      revenueAccountCode = state.revenueAccountCode();
      amount = state.amount();
      inventoryRelief = state.inventoryRelief();
      resolvedInventoryCosting = state.resolvedInventoryCosting();
    }
  }

  record SaleOnCredit(
      LocalDate effectiveDate,
      AccountCode receivableAccountCode,
      AccountCode revenueAccountCode,
      MonetaryAmount amount,
      @Nullable InventoryRelief inventoryRelief,
      @Nullable ResolvedInventoryCosting resolvedInventoryCosting,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax)
      implements StandardBookkeepingEntryVariants {
    public SaleOnCredit {
      var state =
          BookkeepingEntryConstructionSupport.saleOnCredit(
              effectiveDate,
              receivableAccountCode,
              revenueAccountCode,
              amount,
              inventoryRelief,
              resolvedInventoryCosting,
              foreignExchangeDetails,
              taxSelection,
              appliedTax);
      effectiveDate = state.effectiveDate();
      receivableAccountCode = state.receivableAccountCode();
      revenueAccountCode = state.revenueAccountCode();
      amount = state.amount();
      inventoryRelief = state.inventoryRelief();
      resolvedInventoryCosting = state.resolvedInventoryCosting();
      foreignExchangeDetails = state.foreignExchangeDetails();
    }
  }

  record PurchaseSettled(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode cashAccountCode,
      QuantityText quantity,
      MonetaryAmount unitCost,
      @Nullable ResolvedInventoryAcquisition resolvedInventoryAcquisition,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax)
      implements StandardBookkeepingEntryVariants {
    public PurchaseSettled {
      var state =
          BookkeepingEntryConstructionSupport.purchaseSettled(
              effectiveDate,
              inventoryAccountCode,
              cashAccountCode,
              quantity,
              unitCost,
              resolvedInventoryAcquisition,
              foreignExchangeDetails,
              taxSelection,
              appliedTax);
      effectiveDate = state.effectiveDate();
      inventoryAccountCode = state.inventoryAccountCode();
      cashAccountCode = state.cashAccountCode();
      quantity = state.quantity();
      unitCost = state.unitCost();
      resolvedInventoryAcquisition = state.resolvedInventoryAcquisition();
    }
  }

  record PurchaseOnCredit(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode payableAccountCode,
      QuantityText quantity,
      MonetaryAmount unitCost,
      @Nullable ResolvedInventoryAcquisition resolvedInventoryAcquisition,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax)
      implements StandardBookkeepingEntryVariants {
    public PurchaseOnCredit {
      var state =
          BookkeepingEntryConstructionSupport.purchaseOnCredit(
              effectiveDate,
              inventoryAccountCode,
              payableAccountCode,
              quantity,
              unitCost,
              resolvedInventoryAcquisition,
              foreignExchangeDetails,
              taxSelection,
              appliedTax);
      effectiveDate = state.effectiveDate();
      inventoryAccountCode = state.inventoryAccountCode();
      payableAccountCode = state.payableAccountCode();
      quantity = state.quantity();
      unitCost = state.unitCost();
      resolvedInventoryAcquisition = state.resolvedInventoryAcquisition();
      foreignExchangeDetails = state.foreignExchangeDetails();
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
      implements StandardBookkeepingEntryVariants {
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
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax)
      implements StandardBookkeepingEntryVariants {
    public ExpenseOnCredit {
      var state =
          BookkeepingEntryConstructionSupport.expenseOnCredit(
              effectiveDate,
              expenseAccountCode,
              payableAccountCode,
              amount,
              foreignExchangeDetails,
              taxSelection,
              appliedTax);
      effectiveDate = state.effectiveDate();
      expenseAccountCode = state.expenseAccountCode();
      payableAccountCode = state.payableAccountCode();
      amount = state.amount();
      foreignExchangeDetails = state.foreignExchangeDetails();
    }
  }

  record Receipt(
      LocalDate effectiveDate,
      AccountCode cashAccountCode,
      AccountCode receivableAccountCode,
      MonetaryAmount amount,
      @Nullable SettlementAdjunct settlementAdjunct)
      implements StandardBookkeepingEntryVariants {
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
      implements StandardBookkeepingEntryVariants {
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
      implements StandardBookkeepingEntryVariants {
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
      implements StandardBookkeepingEntryVariants {
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
      implements ScalarFactFree {
    public OpeningPosition {
      effectiveDate = BookkeepingEntryScalarValidationSupport.requireEffectiveDate(effectiveDate);
      balances = BookkeepingEntryScalarValidationSupport.requireOpeningBalances(balances);
    }

    public record OpeningAccountBalance(
        AccountCode accountCode,
        JournalLine.EntrySide side,
        MonetaryAmount amount,
        @Nullable QuantityText quantity) {
      public OpeningAccountBalance {
        BookkeepingEntryScalarValidationSupport.requireOpeningAccountBalance(
            accountCode, side, amount);
      }
    }
  }

  record Reversal(
      LocalDate effectiveDate,
      PostingLineage.Reversal reversal,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      @Nullable JournalEntry resolvedJournalEntry)
      implements ScalarFactFree {
    public Reversal {
      BookkeepingEntryReversalValidationSupport.requireResolvedReversal(
          effectiveDate, reversal, resolvedJournalEntry, foreignExchangeDetails);
    }

    @Override
    public JournalEntry journalEntry() {
      return BookkeepingEntryReversalValidationSupport.requireResolvedJournalEntry(
          resolvedJournalEntry, "Reversal");
    }
  }
}
