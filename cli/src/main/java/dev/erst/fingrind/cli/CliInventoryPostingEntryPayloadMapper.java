package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.CliPostingEntryPayloadComponents.PayloadAccounts;
import dev.erst.fingrind.cli.CliPostingEntryPayloadComponents.TaxPayloadInput;
import dev.erst.fingrind.cli.json.CliPostingEntryPayload;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.QuantityText;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import org.jspecify.annotations.Nullable;

/** Maps caller-authored inventory entries into their public CLI JSON payloads. */
final class CliInventoryPostingEntryPayloadMapper {
  private CliInventoryPostingEntryPayloadMapper() {}

  static CliPostingEntryPayload entryPayload(InventoryBookkeepingEntryVariants entry) {
    return switch (entry) {
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled capitalization ->
          capitalizationPayload(
              capitalization.entryKind().wireValue(),
              capitalization.cashAccountCode().value(),
              null,
              capitalization.inventoryAccountCode().value(),
              capitalization.amount(),
              capitalization.foreignExchangeDetails(),
              new TaxPayloadInput(capitalization.taxSelection(), capitalization.appliedTax()));
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit capitalization ->
          capitalizationPayload(
              capitalization.entryKind().wireValue(),
              null,
              capitalization.payableAccountCode().value(),
              capitalization.inventoryAccountCode().value(),
              capitalization.amount(),
              capitalization.foreignExchangeDetails(),
              new TaxPayloadInput(capitalization.taxSelection(), capitalization.appliedTax()));
      case InventoryBookkeepingEntryVariants.InventoryWriteDown writeDown ->
          CliPostingEntryPayloadComponents.payload(
                  writeDown.entryKind().wireValue(),
                  accounts(
                      writeDown.inventoryAccountCode().value(),
                      writeDown.writeDownLossAccountCode().value(),
                      null),
                  writeDown.amount())
              .build();
      case InventoryBookkeepingEntryVariants.InventoryShrinkage shrinkage ->
          CliPostingEntryPayloadComponents.payload(
                  shrinkage.entryKind().wireValue(),
                  accounts(
                      shrinkage.inventoryAccountCode().value(),
                      null,
                      shrinkage.shrinkageLossAccountCode().value()),
                  null,
                  shrinkage.quantity().value(),
                  null)
              .build();
      case InventoryBookkeepingEntryVariants.InventoryCountIncrease countIncrease ->
          CliPostingEntryPayloadComponents.payload(
                  countIncrease.entryKind().wireValue(),
                  accounts(
                      countIncrease.inventoryAccountCode().value(),
                      null,
                      null,
                      countIncrease.countGainAccountCode().value()),
                  null,
                  countIncrease.quantity().value(),
                  countIncrease.unitCost())
              .build();
    };
  }

  static CliPostingEntryPayload purchasePayload(BookkeepingEntry.PurchaseSettled purchase) {
    return purchasePayload(
        purchase.entryKind().wireValue(),
        purchase.cashAccountCode().value(),
        null,
        purchase.inventoryAccountCode().value(),
        purchase.quantity(),
        purchase.unitCost(),
        purchase.foreignExchangeDetails(),
        new TaxPayloadInput(purchase.taxSelection(), purchase.appliedTax()));
  }

  static CliPostingEntryPayload purchasePayload(BookkeepingEntry.PurchaseOnCredit purchase) {
    return purchasePayload(
        purchase.entryKind().wireValue(),
        null,
        purchase.payableAccountCode().value(),
        purchase.inventoryAccountCode().value(),
        purchase.quantity(),
        purchase.unitCost(),
        purchase.foreignExchangeDetails(),
        new TaxPayloadInput(purchase.taxSelection(), purchase.appliedTax()));
  }

  private static CliPostingEntryPayload purchasePayload(
      String entryKind,
      @Nullable String cashAccountCode,
      @Nullable String payableAccountCode,
      String inventoryAccountCode,
      QuantityText quantity,
      MonetaryAmount unitCost,
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
                inventoryAccountCode,
                null,
                null,
                null,
                null,
                null),
            null,
            quantity.value(),
            unitCost);
    CliPostingEntryPayloadComponents.addTaxAndForeignExchange(
        builder, foreignExchangeDetails, taxPayloadInput);
    return builder.build();
  }

  private static CliPostingEntryPayload capitalizationPayload(
      String entryKind,
      @Nullable String cashAccountCode,
      @Nullable String payableAccountCode,
      String inventoryAccountCode,
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
                inventoryAccountCode,
                null,
                null,
                null,
                null,
                null),
            amount);
    CliPostingEntryPayloadComponents.addTaxAndForeignExchange(
        builder, foreignExchangeDetails, taxPayloadInput);
    return builder.build();
  }

  private static PayloadAccounts accounts(
      String inventoryAccountCode,
      @Nullable String writeDownLossAccountCode,
      @Nullable String shrinkageLossAccountCode) {
    return accounts(inventoryAccountCode, writeDownLossAccountCode, shrinkageLossAccountCode, null);
  }

  private static PayloadAccounts accounts(
      String inventoryAccountCode,
      @Nullable String writeDownLossAccountCode,
      @Nullable String shrinkageLossAccountCode,
      @Nullable String countGainAccountCode) {
    return new PayloadAccounts(
        null,
        null,
        null,
        null,
        inventoryAccountCode,
        null,
        writeDownLossAccountCode,
        shrinkageLossAccountCode,
        countGainAccountCode,
        null);
  }
}
