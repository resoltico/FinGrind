package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import java.util.List;

/** Expands inventory-maintenance entries into the workflow facts used by ledger plans. */
final class LedgerPlanInventoryEntryFactMapper {
  private LedgerPlanInventoryEntryFactMapper() {}

  static void append(
      List<BookWorkflowFact> facts, InventoryBookkeepingEntryVariants inventoryEntry) {
    switch (inventoryEntry) {
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled capitalization ->
          appendTwoAccountAmountFacts(
              facts,
              "inventoryAccountCode",
              capitalization.inventoryAccountCode().value(),
              "cashAccountCode",
              capitalization.cashAccountCode().value(),
              capitalization.amount());
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit capitalization ->
          appendTwoAccountAmountFacts(
              facts,
              "inventoryAccountCode",
              capitalization.inventoryAccountCode().value(),
              "payableAccountCode",
              capitalization.payableAccountCode().value(),
              capitalization.amount());
      case InventoryBookkeepingEntryVariants.InventoryWriteDown writeDown ->
          appendTwoAccountAmountFacts(
              facts,
              "writeDownLossAccountCode",
              writeDown.writeDownLossAccountCode().value(),
              "inventoryAccountCode",
              writeDown.inventoryAccountCode().value(),
              writeDown.amount());
      case InventoryBookkeepingEntryVariants.InventoryShrinkage shrinkage -> {
        facts.add(
            BookWorkflowFact.text(
                "inventoryAccountCode", shrinkage.inventoryAccountCode().value()));
        facts.add(
            BookWorkflowFact.text(
                "shrinkageLossAccountCode", shrinkage.shrinkageLossAccountCode().value()));
        facts.add(BookWorkflowFact.text("quantity", shrinkage.quantity().value()));
      }
      case InventoryBookkeepingEntryVariants.InventoryCountIncrease countIncrease -> {
        facts.add(
            BookWorkflowFact.text(
                "inventoryAccountCode", countIncrease.inventoryAccountCode().value()));
        facts.add(
            BookWorkflowFact.text(
                "countGainAccountCode", countIncrease.countGainAccountCode().value()));
        facts.add(BookWorkflowFact.text("quantity", countIncrease.quantity().value()));
        facts.add(BookWorkflowFact.money("unitCost", countIncrease.unitCost()));
      }
    }
  }

  private static void appendTwoAccountAmountFacts(
      List<BookWorkflowFact> facts,
      String firstFieldName,
      String firstFieldValue,
      String secondFieldName,
      String secondFieldValue,
      MonetaryAmount amount) {
    facts.add(BookWorkflowFact.text(firstFieldName, firstFieldValue));
    facts.add(BookWorkflowFact.text(secondFieldName, secondFieldValue));
    facts.add(BookWorkflowFact.money("amount", amount));
  }
}
