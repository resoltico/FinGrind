package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.cli.json.CliForeignExchangeJsonModels;
import dev.erst.fingrind.cli.json.CliOpeningBalancePayload;
import dev.erst.fingrind.cli.json.CliPostingEntryPayload;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryRelief;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.SettlementAdjunct;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxSelection;
import org.jspecify.annotations.Nullable;

/** Maps caller-authored posting entries into public CLI JSON payloads. */
final class CliPostingEntryPayloadMapper {
  private CliPostingEntryPayloadMapper() {}

  static @Nullable CliPostingEntryPayload entryPayload(@Nullable BookkeepingEntry entry) {
    if (entry == null) {
      return null;
    }
    if (entry instanceof BookkeepingEntry.DirectJournal directJournal) {
      return directJournalPayload(directJournal);
    }
    if (entry instanceof BookkeepingEntry.OpeningPosition openingPosition) {
      return openingPositionPayload(openingPosition);
    }
    if (entry instanceof BookkeepingEntry.Reversal reversal) {
      return reversalPayload(reversal);
    }
    return typedEntryPayload(entry);
  }

  private static CliPostingEntryPayload typedEntryPayload(BookkeepingEntry entry) {
    if (entry instanceof BookkeepingEntry.SaleSettled sale) {
      return salePayload(
          sale.entryKind().wireValue(),
          sale.cashAccountCode().value(),
          null,
          sale.revenueAccountCode().value(),
          sale.amount(),
          sale.inventoryRelief(),
          sale.foreignExchangeDetails(),
          new TaxPayloadInput(sale.taxSelection(), sale.appliedTax()));
    }
    if (entry instanceof BookkeepingEntry.SaleOnCredit sale) {
      return salePayload(
          sale.entryKind().wireValue(),
          null,
          sale.receivableAccountCode().value(),
          sale.revenueAccountCode().value(),
          sale.amount(),
          sale.inventoryRelief(),
          null,
          new TaxPayloadInput(sale.taxSelection(), sale.appliedTax()));
    }
    if (entry instanceof BookkeepingEntry.PurchaseSettled purchase) {
      return purchasePayload(
          purchase.entryKind().wireValue(),
          purchase.cashAccountCode().value(),
          null,
          purchase.inventoryAccountCode().value(),
          purchase.amount(),
          purchase.foreignExchangeDetails());
    }
    if (entry instanceof BookkeepingEntry.PurchaseOnCredit purchase) {
      return purchasePayload(
          purchase.entryKind().wireValue(),
          null,
          purchase.payableAccountCode().value(),
          purchase.inventoryAccountCode().value(),
          purchase.amount(),
          null);
    }
    if (entry instanceof BookkeepingEntry.ExpenseSettled expense) {
      return expensePayload(
          expense.entryKind().wireValue(),
          expense.cashAccountCode().value(),
          null,
          expense.expenseAccountCode().value(),
          expense.amount(),
          expense.foreignExchangeDetails(),
          new TaxPayloadInput(expense.taxSelection(), expense.appliedTax()));
    }
    if (entry instanceof BookkeepingEntry.ExpenseOnCredit expense) {
      return expensePayload(
          expense.entryKind().wireValue(),
          null,
          expense.payableAccountCode().value(),
          expense.expenseAccountCode().value(),
          expense.amount(),
          null,
          new TaxPayloadInput(expense.taxSelection(), expense.appliedTax()));
    }
    if (entry instanceof BookkeepingEntry.Receipt receipt) {
      return settlementPayload(
          receipt.entryKind().wireValue(),
          receipt.cashAccountCode().value(),
          receipt.receivableAccountCode().value(),
          null,
          receipt.amount(),
          receipt.settlementAdjunct());
    }
    if (entry instanceof BookkeepingEntry.Payment payment) {
      return settlementPayload(
          payment.entryKind().wireValue(),
          payment.cashAccountCode().value(),
          null,
          payment.payableAccountCode().value(),
          payment.amount(),
          payment.settlementAdjunct());
    }
    if (entry instanceof BookkeepingEntry.OwnerContribution contribution) {
      return ownerEquityPayload(
          contribution.entryKind().wireValue(),
          contribution.cashAccountCode().value(),
          contribution.equityAccountCode().value(),
          contribution.amount(),
          contribution.foreignExchangeDetails());
    }
    BookkeepingEntry.OwnerWithdrawal withdrawal = (BookkeepingEntry.OwnerWithdrawal) entry;
    return ownerEquityPayload(
        withdrawal.entryKind().wireValue(),
        withdrawal.cashAccountCode().value(),
        withdrawal.equityAccountCode().value(),
        withdrawal.amount(),
        withdrawal.foreignExchangeDetails());
  }

  private static CliPostingEntryPayload directJournalPayload(
      BookkeepingEntry.DirectJournal directJournal) {
    return payload(directJournal.entryKind().wireValue(), PayloadAccounts.none(), null)
        .withForeignExchange(foreignExchangePayload(directJournal.foreignExchangeDetails()))
        .build();
  }

  private static CliPostingEntryPayload openingPositionPayload(
      BookkeepingEntry.OpeningPosition openingPosition) {
    return payload(openingPosition.entryKind().wireValue(), PayloadAccounts.none(), null)
        .withOpeningBalances(
            openingPosition.balances().stream()
                .map(CliPostingEntryPayloadMapper::openingBalancePayload)
                .toList())
        .build();
  }

  private static CliPostingEntryPayload reversalPayload(BookkeepingEntry.Reversal reversal) {
    return payload(reversal.entryKind().wireValue(), PayloadAccounts.none(), null)
        .withForeignExchange(foreignExchangePayload(reversal.foreignExchangeDetails()))
        .withReversal(
            new CliBookQueryJsonModels.ReversalPayload(
                reversal.reversal().reference().priorPostingId().value(),
                reversal.reversal().reason().value()))
        .build();
  }

  private static CliPostingEntryPayload salePayload(
      String entryKind,
      @Nullable String cashAccountCode,
      @Nullable String receivableAccountCode,
      String revenueAccountCode,
      MonetaryAmount amount,
      @Nullable InventoryRelief inventoryRelief,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      TaxPayloadInput taxPayloadInput) {
    return payload(
            entryKind,
            new PayloadAccounts(
                cashAccountCode, receivableAccountCode, null, revenueAccountCode, null, null, null),
            amount)
        .withInventoryRelief(inventoryReliefPayload(inventoryRelief))
        .withForeignExchange(foreignExchangePayload(foreignExchangeDetails))
        .withTaxSelection(
            taxPayloadInput.selection() == null
                ? null
                : CliTaxPayloadMapper.taxSelectionPayload(taxPayloadInput.selection()))
        .withAppliedTax(
            taxPayloadInput.appliedTax() == null
                ? null
                : CliTaxPayloadMapper.appliedTaxPayload(taxPayloadInput.appliedTax()))
        .build();
  }

  private static CliPostingEntryPayload purchasePayload(
      String entryKind,
      @Nullable String cashAccountCode,
      @Nullable String payableAccountCode,
      String inventoryAccountCode,
      MonetaryAmount amount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    return payload(
            entryKind,
            new PayloadAccounts(
                cashAccountCode, null, payableAccountCode, null, inventoryAccountCode, null, null),
            amount)
        .withForeignExchange(foreignExchangePayload(foreignExchangeDetails))
        .build();
  }

  private static CliPostingEntryPayload expensePayload(
      String entryKind,
      @Nullable String cashAccountCode,
      @Nullable String payableAccountCode,
      String expenseAccountCode,
      MonetaryAmount amount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      TaxPayloadInput taxPayloadInput) {
    return payload(
            entryKind,
            new PayloadAccounts(
                cashAccountCode, null, payableAccountCode, null, null, expenseAccountCode, null),
            amount)
        .withForeignExchange(foreignExchangePayload(foreignExchangeDetails))
        .withTaxSelection(
            taxPayloadInput.selection() == null
                ? null
                : CliTaxPayloadMapper.taxSelectionPayload(taxPayloadInput.selection()))
        .withAppliedTax(
            taxPayloadInput.appliedTax() == null
                ? null
                : CliTaxPayloadMapper.appliedTaxPayload(taxPayloadInput.appliedTax()))
        .build();
  }

  private static CliPostingEntryPayload settlementPayload(
      String entryKind,
      String cashAccountCode,
      @Nullable String receivableAccountCode,
      @Nullable String payableAccountCode,
      MonetaryAmount amount,
      @Nullable SettlementAdjunct settlementAdjunct) {
    return payload(
            entryKind,
            new PayloadAccounts(
                cashAccountCode, receivableAccountCode, payableAccountCode, null, null, null, null),
            amount)
        .withSettlementAdjunct(settlementAdjunctPayload(settlementAdjunct))
        .build();
  }

  private static CliPostingEntryPayload ownerEquityPayload(
      String entryKind,
      String cashAccountCode,
      String equityAccountCode,
      MonetaryAmount amount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    return payload(
            entryKind,
            new PayloadAccounts(cashAccountCode, null, null, null, null, null, equityAccountCode),
            amount)
        .withForeignExchange(foreignExchangePayload(foreignExchangeDetails))
        .build();
  }

  private record PayloadAccounts(
      @Nullable String cashAccountCode,
      @Nullable String receivableAccountCode,
      @Nullable String payableAccountCode,
      @Nullable String revenueAccountCode,
      @Nullable String inventoryAccountCode,
      @Nullable String expenseAccountCode,
      @Nullable String equityAccountCode) {
    private static PayloadAccounts none() {
      return new PayloadAccounts(null, null, null, null, null, null, null);
    }
  }

  private record TaxPayloadInput(
      @Nullable TaxSelection selection, @Nullable AppliedTax appliedTax) {}

  private static CliPostingEntryPayloadBuilder payload(
      String entryKind, PayloadAccounts accounts, @Nullable MonetaryAmount amount) {
    return new CliPostingEntryPayloadBuilder(
        entryKind,
        accounts.cashAccountCode(),
        accounts.receivableAccountCode(),
        accounts.payableAccountCode(),
        accounts.revenueAccountCode(),
        accounts.inventoryAccountCode(),
        accounts.expenseAccountCode(),
        accounts.equityAccountCode(),
        amount);
  }

  private static CliOpeningBalancePayload openingBalancePayload(
      BookkeepingEntry.OpeningPosition.OpeningAccountBalance balance) {
    return new CliOpeningBalancePayload(
        balance.accountCode().value(), balance.side().wireValue(), balance.amount());
  }

  private static CliForeignExchangeJsonModels.@Nullable ForeignExchangePayload
      foreignExchangePayload(@Nullable ForeignExchangeDetails foreignExchangeDetails) {
    if (foreignExchangeDetails == null) {
      return null;
    }
    return new CliForeignExchangeJsonModels.ForeignExchangePayload(
        foreignExchangeDetails.transactionAmount(),
        foreignExchangeDetails.functionalAmount(),
        new CliForeignExchangeJsonModels.QuotedExchangeRatePayload(
            foreignExchangeDetails.quotedExchangeRate().transactionCurrencyAmount(),
            foreignExchangeDetails.quotedExchangeRate().functionalCurrencyAmount(),
            foreignExchangeDetails.quotedExchangeRate().quotedOn().toString(),
            foreignExchangeDetails.quotedExchangeRate().quoteSource()),
        foreignExchangeDetails.treatmentKind().wireValue());
  }

  private static CliPostingEntryPayload.@Nullable SettlementAdjunctPayload settlementAdjunctPayload(
      @Nullable SettlementAdjunct settlementAdjunct) {
    if (settlementAdjunct == null) {
      return null;
    }
    return new CliPostingEntryPayload.SettlementAdjunctPayload(
        settlementAdjunct.accountCode().value(), settlementAdjunct.amount());
  }

  private static CliPostingEntryPayload.@Nullable InventoryReliefPayload inventoryReliefPayload(
      @Nullable InventoryRelief inventoryRelief) {
    if (inventoryRelief == null) {
      return null;
    }
    return new CliPostingEntryPayload.InventoryReliefPayload(
        inventoryRelief.inventoryAccountCode().value(),
        inventoryRelief.costOfSalesAccountCode().value(),
        inventoryRelief.amount());
  }
}
