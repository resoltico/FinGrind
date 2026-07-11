package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.CliPostingEntryPayloadComponents.PayloadAccounts;
import dev.erst.fingrind.cli.CliPostingEntryPayloadComponents.SalePayloadInput;
import dev.erst.fingrind.cli.CliPostingEntryPayloadComponents.TaxPayloadInput;
import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.cli.json.CliPostingEntryPayload;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.SettlementAdjunct;
import dev.erst.fingrind.contract.bookkeeping.TypedBookkeepingEntry;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import org.jspecify.annotations.Nullable;

/** Maps caller-authored posting entries into public CLI JSON payloads. */
final class CliPostingEntryPayloadMapper {
  private CliPostingEntryPayloadMapper() {}

  static @Nullable CliPostingEntryPayload entryPayload(@Nullable BookkeepingEntry entry) {
    if (entry == null) {
      return null;
    }
    return switch (entry) {
      case BookkeepingEntry.DirectJournal directJournal -> directJournalPayload(directJournal);
      case TypedBookkeepingEntry typedEntry -> typedEntryPayload(typedEntry);
      case BookkeepingEntry.OpeningPosition openingPosition ->
          openingPositionPayload(openingPosition);
      case BookkeepingEntry.Reversal reversal -> reversalPayload(reversal);
    };
  }

  private static CliPostingEntryPayload typedEntryPayload(TypedBookkeepingEntry entry) {
    return switch (entry) {
      case BookkeepingEntry.SaleSettled sale ->
          salePayload(
              new SalePayloadInput(
                  sale.entryKind().wireValue(),
                  saleAccounts(
                      sale.cashAccountCode().value(), null, sale.revenueAccountCode().value()),
                  sale.amount(),
                  sale.inventoryRelief(),
                  sale.resolvedInventoryCosting(),
                  sale.foreignExchangeDetails(),
                  new TaxPayloadInput(sale.taxSelection(), sale.appliedTax())));
      case BookkeepingEntry.SaleOnCredit sale ->
          salePayload(
              new SalePayloadInput(
                  sale.entryKind().wireValue(),
                  saleAccounts(
                      null,
                      sale.receivableAccountCode().value(),
                      sale.revenueAccountCode().value()),
                  sale.amount(),
                  sale.inventoryRelief(),
                  sale.resolvedInventoryCosting(),
                  sale.foreignExchangeDetails(),
                  new TaxPayloadInput(sale.taxSelection(), sale.appliedTax())));
      case BookkeepingEntry.PurchaseSettled purchase ->
          CliInventoryPostingEntryPayloadMapper.purchasePayload(purchase);
      case BookkeepingEntry.PurchaseOnCredit purchase ->
          CliInventoryPostingEntryPayloadMapper.purchasePayload(purchase);
      case InventoryBookkeepingEntryVariants inventoryEntry ->
          CliInventoryPostingEntryPayloadMapper.entryPayload(inventoryEntry);
      case BookkeepingEntry.ExpenseSettled expense ->
          expensePayload(
              expense.entryKind().wireValue(),
              expense.cashAccountCode().value(),
              null,
              expense.expenseAccountCode().value(),
              expense.amount(),
              expense.foreignExchangeDetails(),
              new TaxPayloadInput(expense.taxSelection(), expense.appliedTax()));
      case BookkeepingEntry.ExpenseOnCredit expense ->
          expensePayload(
              expense.entryKind().wireValue(),
              null,
              expense.payableAccountCode().value(),
              expense.expenseAccountCode().value(),
              expense.amount(),
              expense.foreignExchangeDetails(),
              new TaxPayloadInput(expense.taxSelection(), expense.appliedTax()));
      case BookkeepingEntry.Receipt receipt ->
          settlementPayload(
              receipt.entryKind().wireValue(),
              receipt.cashAccountCode().value(),
              receipt.receivableAccountCode().value(),
              null,
              receipt.amount(),
              receipt.settlementAdjunct());
      case BookkeepingEntry.Payment payment ->
          settlementPayload(
              payment.entryKind().wireValue(),
              payment.cashAccountCode().value(),
              null,
              payment.payableAccountCode().value(),
              payment.amount(),
              payment.settlementAdjunct());
      case BookkeepingEntry.OwnerContribution contribution ->
          ownerEquityPayload(
              contribution.entryKind().wireValue(),
              contribution.cashAccountCode().value(),
              contribution.equityAccountCode().value(),
              contribution.amount(),
              contribution.foreignExchangeDetails());
      case BookkeepingEntry.OwnerWithdrawal withdrawal ->
          ownerEquityPayload(
              withdrawal.entryKind().wireValue(),
              withdrawal.cashAccountCode().value(),
              withdrawal.equityAccountCode().value(),
              withdrawal.amount(),
              withdrawal.foreignExchangeDetails());
    };
  }

  private static CliPostingEntryPayload directJournalPayload(
      BookkeepingEntry.DirectJournal directJournal) {
    return CliPostingEntryPayloadComponents.payload(
            directJournal.entryKind().wireValue(), PayloadAccounts.none(), null)
        .withForeignExchange(
            CliPostingEntryPayloadComponents.foreignExchangePayload(
                directJournal.foreignExchangeDetails()))
        .build();
  }

  private static CliPostingEntryPayload openingPositionPayload(
      BookkeepingEntry.OpeningPosition openingPosition) {
    return CliPostingEntryPayloadComponents.payload(
            openingPosition.entryKind().wireValue(), PayloadAccounts.none(), null)
        .withOpeningBalances(
            openingPosition.balances().stream()
                .map(CliPostingEntryPayloadComponents::openingBalancePayload)
                .toList())
        .build();
  }

  private static CliPostingEntryPayload reversalPayload(BookkeepingEntry.Reversal reversal) {
    return CliPostingEntryPayloadComponents.payload(
            reversal.entryKind().wireValue(), PayloadAccounts.none(), null)
        .withForeignExchange(
            CliPostingEntryPayloadComponents.foreignExchangePayload(
                reversal.foreignExchangeDetails()))
        .withReversal(
            new CliBookQueryJsonModels.ReversalPayload(
                reversal.reversal().reference().priorPostingId().value(),
                reversal.reversal().reason().value()))
        .build();
  }

  private static CliPostingEntryPayload salePayload(SalePayloadInput input) {
    var builder =
        CliPostingEntryPayloadComponents.payload(
                input.entryKind(), input.accounts(), input.amount())
            .withInventoryRelief(
                CliPostingEntryPayloadComponents.inventoryReliefPayload(input.inventoryRelief()))
            .withResolvedInventoryCosting(
                CliPostingEntryPayloadComponents.resolvedInventoryCostingPayload(
                    input.resolvedInventoryCosting()));
    CliPostingEntryPayloadComponents.addTaxAndForeignExchange(
        builder, input.foreignExchangeDetails(), input.taxPayloadInput());
    return builder.build();
  }

  private static CliPostingEntryPayload expensePayload(
      String entryKind,
      @Nullable String cashAccountCode,
      @Nullable String payableAccountCode,
      String expenseAccountCode,
      MonetaryAmount amount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      TaxPayloadInput taxPayloadInput) {
    var builder =
        CliPostingEntryPayloadComponents.payload(
            entryKind,
            new PayloadAccounts(
                cashAccountCode,
                null,
                payableAccountCode,
                null,
                null,
                expenseAccountCode,
                null,
                null,
                null,
                null),
            amount);
    CliPostingEntryPayloadComponents.addTaxAndForeignExchange(
        builder, foreignExchangeDetails, taxPayloadInput);
    return builder.build();
  }

  private static PayloadAccounts saleAccounts(
      @Nullable String cashAccountCode,
      @Nullable String receivableAccountCode,
      String revenueAccountCode) {
    return new PayloadAccounts(
        cashAccountCode,
        receivableAccountCode,
        null,
        revenueAccountCode,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private static CliPostingEntryPayload settlementPayload(
      String entryKind,
      String cashAccountCode,
      @Nullable String receivableAccountCode,
      @Nullable String payableAccountCode,
      MonetaryAmount amount,
      @Nullable SettlementAdjunct settlementAdjunct) {
    return CliPostingEntryPayloadComponents.payload(
            entryKind,
            new PayloadAccounts(
                cashAccountCode,
                receivableAccountCode,
                payableAccountCode,
                null,
                null,
                null,
                null,
                null,
                null,
                null),
            amount)
        .withSettlementAdjunct(
            CliPostingEntryPayloadComponents.settlementAdjunctPayload(settlementAdjunct))
        .build();
  }

  private static CliPostingEntryPayload ownerEquityPayload(
      String entryKind,
      String cashAccountCode,
      String equityAccountCode,
      MonetaryAmount amount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    return CliPostingEntryPayloadComponents.payload(
            entryKind,
            new PayloadAccounts(
                cashAccountCode, null, null, null, null, null, null, null, null, equityAccountCode),
            amount)
        .withForeignExchange(
            CliPostingEntryPayloadComponents.foreignExchangePayload(foreignExchangeDetails))
        .build();
  }
}
