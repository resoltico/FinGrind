package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.InventoryRelief;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.QuantityText;
import dev.erst.fingrind.contract.bookkeeping.SettlementAdjunct;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Package-private expansion of caller-authored posting entries into workflow facts. */
final class LedgerPlanEntryFactMapper {
  private static final Map<BookkeepingEntryKind, TwoAccountEntryDescriptor>
      TWO_ACCOUNT_ENTRY_FACTS =
          Map.of(
              BookkeepingEntryKind.SALE_SETTLED,
              salesDescriptor(
                  BookkeepingEntry.SaleSettled.class,
                  "cashAccountCode",
                  BookkeepingEntry.SaleSettled::cashAccountCode,
                  "revenueAccountCode",
                  BookkeepingEntry.SaleSettled::revenueAccountCode,
                  BookkeepingEntry.SaleSettled::amount,
                  BookkeepingEntry.SaleSettled::inventoryRelief),
              BookkeepingEntryKind.SALE_ON_CREDIT,
              salesDescriptor(
                  BookkeepingEntry.SaleOnCredit.class,
                  "receivableAccountCode",
                  BookkeepingEntry.SaleOnCredit::receivableAccountCode,
                  "revenueAccountCode",
                  BookkeepingEntry.SaleOnCredit::revenueAccountCode,
                  BookkeepingEntry.SaleOnCredit::amount,
                  BookkeepingEntry.SaleOnCredit::inventoryRelief),
              BookkeepingEntryKind.PURCHASE_SETTLED,
              purchaseDescriptor(
                  BookkeepingEntry.PurchaseSettled.class,
                  "inventoryAccountCode",
                  BookkeepingEntry.PurchaseSettled::inventoryAccountCode,
                  "cashAccountCode",
                  BookkeepingEntry.PurchaseSettled::cashAccountCode,
                  BookkeepingEntry.PurchaseSettled::quantity,
                  BookkeepingEntry.PurchaseSettled::unitCost),
              BookkeepingEntryKind.PURCHASE_ON_CREDIT,
              purchaseDescriptor(
                  BookkeepingEntry.PurchaseOnCredit.class,
                  "inventoryAccountCode",
                  BookkeepingEntry.PurchaseOnCredit::inventoryAccountCode,
                  "payableAccountCode",
                  BookkeepingEntry.PurchaseOnCredit::payableAccountCode,
                  BookkeepingEntry.PurchaseOnCredit::quantity,
                  BookkeepingEntry.PurchaseOnCredit::unitCost),
              BookkeepingEntryKind.EXPENSE_SETTLED,
              pairDescriptor(
                  BookkeepingEntry.ExpenseSettled.class,
                  "expenseAccountCode",
                  BookkeepingEntry.ExpenseSettled::expenseAccountCode,
                  "cashAccountCode",
                  BookkeepingEntry.ExpenseSettled::cashAccountCode,
                  BookkeepingEntry.ExpenseSettled::amount),
              BookkeepingEntryKind.EXPENSE_ON_CREDIT,
              pairDescriptor(
                  BookkeepingEntry.ExpenseOnCredit.class,
                  "expenseAccountCode",
                  BookkeepingEntry.ExpenseOnCredit::expenseAccountCode,
                  "payableAccountCode",
                  BookkeepingEntry.ExpenseOnCredit::payableAccountCode,
                  BookkeepingEntry.ExpenseOnCredit::amount),
              BookkeepingEntryKind.RECEIPT,
              settlementDescriptor(
                  BookkeepingEntry.Receipt.class,
                  "cashAccountCode",
                  BookkeepingEntry.Receipt::cashAccountCode,
                  "receivableAccountCode",
                  BookkeepingEntry.Receipt::receivableAccountCode,
                  BookkeepingEntry.Receipt::amount,
                  BookkeepingEntry.Receipt::settlementAdjunct),
              BookkeepingEntryKind.PAYMENT,
              settlementDescriptor(
                  BookkeepingEntry.Payment.class,
                  "payableAccountCode",
                  BookkeepingEntry.Payment::payableAccountCode,
                  "cashAccountCode",
                  BookkeepingEntry.Payment::cashAccountCode,
                  BookkeepingEntry.Payment::amount,
                  BookkeepingEntry.Payment::settlementAdjunct),
              BookkeepingEntryKind.OWNER_CONTRIBUTION,
              pairDescriptor(
                  BookkeepingEntry.OwnerContribution.class,
                  "cashAccountCode",
                  BookkeepingEntry.OwnerContribution::cashAccountCode,
                  "equityAccountCode",
                  BookkeepingEntry.OwnerContribution::equityAccountCode,
                  BookkeepingEntry.OwnerContribution::amount),
              BookkeepingEntryKind.OWNER_WITHDRAWAL,
              pairDescriptor(
                  BookkeepingEntry.OwnerWithdrawal.class,
                  "equityAccountCode",
                  BookkeepingEntry.OwnerWithdrawal::equityAccountCode,
                  "cashAccountCode",
                  BookkeepingEntry.OwnerWithdrawal::cashAccountCode,
                  BookkeepingEntry.OwnerWithdrawal::amount));

  private LedgerPlanEntryFactMapper() {}

  static List<BookWorkflowFact> entryFacts(BookkeepingEntry entry) {
    List<BookWorkflowFact> facts = new ArrayList<>();
    facts.add(BookWorkflowFact.text("entryKind", entry.entryKind().wireValue()));
    appendVariantFacts(facts, entry);
    return List.copyOf(facts);
  }

  private static void appendVariantFacts(List<BookWorkflowFact> facts, BookkeepingEntry entry) {
    if (entry instanceof BookkeepingEntry.DirectJournal) {
      return;
    }
    if (entry instanceof BookkeepingEntry.OpeningPosition openingPosition) {
      appendOpeningBalanceFacts(facts, openingPosition);
      return;
    }
    if (entry instanceof BookkeepingEntry.Reversal reversal) {
      appendReversalFacts(facts, reversal);
      return;
    }
    if (entry instanceof InventoryBookkeepingEntryVariants inventoryEntry) {
      LedgerPlanInventoryEntryFactMapper.append(facts, inventoryEntry);
      return;
    }
    java.util.Objects.requireNonNull(TWO_ACCOUNT_ENTRY_FACTS.get(entry.entryKind()))
        .append(facts, entry);
  }

  private static <ENTRY extends BookkeepingEntry> TwoAccountEntryDescriptor salesDescriptor(
      Class<ENTRY> entryType,
      String firstFieldName,
      Function<ENTRY, AccountCode> firstAccount,
      String secondFieldName,
      Function<ENTRY, AccountCode> secondAccount,
      Function<ENTRY, MonetaryAmount> amount,
      Function<ENTRY, @Nullable InventoryRelief> inventoryRelief) {
    return pairDescriptor(
        entryType,
        firstFieldName,
        firstAccount,
        secondFieldName,
        secondAccount,
        amount,
        (facts, entry) -> appendInventoryRelief(facts, inventoryRelief.apply(entry)));
  }

  private static <ENTRY extends BookkeepingEntry> TwoAccountEntryDescriptor settlementDescriptor(
      Class<ENTRY> entryType,
      String firstFieldName,
      Function<ENTRY, AccountCode> firstAccount,
      String secondFieldName,
      Function<ENTRY, AccountCode> secondAccount,
      Function<ENTRY, MonetaryAmount> amount,
      Function<ENTRY, @Nullable SettlementAdjunct> settlementAdjunct) {
    return pairDescriptor(
        entryType,
        firstFieldName,
        firstAccount,
        secondFieldName,
        secondAccount,
        amount,
        (facts, entry) -> appendSettlementAdjunct(facts, settlementAdjunct.apply(entry)));
  }

  private static <ENTRY extends BookkeepingEntry> TwoAccountEntryDescriptor pairDescriptor(
      Class<ENTRY> entryType,
      String firstFieldName,
      Function<ENTRY, AccountCode> firstAccount,
      String secondFieldName,
      Function<ENTRY, AccountCode> secondAccount,
      Function<ENTRY, MonetaryAmount> amount) {
    return pairDescriptor(
        entryType,
        firstFieldName,
        firstAccount,
        secondFieldName,
        secondAccount,
        amount,
        (facts, entry) -> {});
  }

  private static <ENTRY extends BookkeepingEntry> TwoAccountEntryDescriptor purchaseDescriptor(
      Class<ENTRY> entryType,
      String firstFieldName,
      Function<ENTRY, AccountCode> firstAccount,
      String secondFieldName,
      Function<ENTRY, AccountCode> secondAccount,
      Function<ENTRY, QuantityText> quantity,
      Function<ENTRY, MonetaryAmount> unitCost) {
    return new TwoAccountEntryDescriptor(
        entryType,
        firstFieldName,
        entry -> firstAccount.apply(entryType.cast(entry)).value(),
        secondFieldName,
        entry -> secondAccount.apply(entryType.cast(entry)).value(),
        entry -> unitCost.apply(entryType.cast(entry)),
        (facts, entry) -> {
          ENTRY typedEntry = entryType.cast(entry);
          facts.add(BookWorkflowFact.text("quantity", quantity.apply(typedEntry).value()));
          facts.add(BookWorkflowFact.money("unitCost", unitCost.apply(typedEntry)));
        });
  }

  private static <ENTRY extends BookkeepingEntry> TwoAccountEntryDescriptor pairDescriptor(
      Class<ENTRY> entryType,
      String firstFieldName,
      Function<ENTRY, AccountCode> firstAccount,
      String secondFieldName,
      Function<ENTRY, AccountCode> secondAccount,
      Function<ENTRY, MonetaryAmount> amount,
      BiConsumer<List<BookWorkflowFact>, ENTRY> trailingFactsAppender) {
    return new TwoAccountEntryDescriptor(
        entryType,
        firstFieldName,
        entry -> firstAccount.apply(entryType.cast(entry)).value(),
        secondFieldName,
        entry -> secondAccount.apply(entryType.cast(entry)).value(),
        entry -> amount.apply(entryType.cast(entry)),
        (facts, entry) -> trailingFactsAppender.accept(facts, entryType.cast(entry)));
  }

  private static void appendOpeningBalanceFacts(
      List<BookWorkflowFact> facts, BookkeepingEntry.OpeningPosition openingPosition) {
    openingPosition
        .balances()
        .forEach(
            balance -> {
              List<BookWorkflowFact> balanceFacts = new ArrayList<>();
              balanceFacts.add(BookWorkflowFact.text("accountCode", balance.accountCode().value()));
              balanceFacts.add(BookWorkflowFact.text("side", balance.side().wireValue()));
              balanceFacts.add(BookWorkflowFact.money("amount", balance.amount()));
              if (balance.quantity() != null) {
                balanceFacts.add(BookWorkflowFact.text("quantity", balance.quantity().value()));
              }
              facts.add(BookWorkflowFact.group("openingBalance", List.copyOf(balanceFacts)));
            });
  }

  private static void appendReversalFacts(
      List<BookWorkflowFact> facts, BookkeepingEntry.Reversal reversal) {
    facts.add(
        BookWorkflowFact.group(
            "reversal",
            List.of(
                BookWorkflowFact.text(
                    "priorPostingId", reversal.reversal().reference().priorPostingId().value()),
                BookWorkflowFact.text("reason", reversal.reversal().reason().value()))));
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

  private static void appendSettlementAdjunct(
      List<BookWorkflowFact> facts, @Nullable SettlementAdjunct settlementAdjunct) {
    if (settlementAdjunct == null) {
      return;
    }
    facts.add(
        BookWorkflowFact.group(
            "settlementAdjunct",
            List.of(
                BookWorkflowFact.text("accountCode", settlementAdjunct.accountCode().value()),
                BookWorkflowFact.money("amount", settlementAdjunct.amount()))));
  }

  private static void appendInventoryRelief(
      List<BookWorkflowFact> facts, @Nullable InventoryRelief inventoryRelief) {
    if (inventoryRelief == null) {
      return;
    }
    facts.add(
        BookWorkflowFact.group(
            "inventoryRelief",
            List.of(
                BookWorkflowFact.text(
                    "inventoryAccountCode", inventoryRelief.inventoryAccountCode().value()),
                BookWorkflowFact.text(
                    "costOfSalesAccountCode", inventoryRelief.costOfSalesAccountCode().value()),
                BookWorkflowFact.text("quantity", inventoryRelief.quantity().value()))));
  }

  private record TwoAccountEntryDescriptor(
      Class<? extends BookkeepingEntry> entryType,
      String firstFieldName,
      Function<BookkeepingEntry, String> firstFieldValue,
      String secondFieldName,
      Function<BookkeepingEntry, String> secondFieldValue,
      Function<BookkeepingEntry, MonetaryAmount> amount,
      BiConsumer<List<BookWorkflowFact>, BookkeepingEntry> trailingFactsAppender) {
    void append(List<BookWorkflowFact> facts, BookkeepingEntry entry) {
      BookkeepingEntry typedEntry = entryType.cast(entry);
      appendTwoAccountAmountFacts(
          facts,
          firstFieldName,
          firstFieldValue.apply(typedEntry),
          secondFieldName,
          secondFieldValue.apply(typedEntry),
          amount.apply(typedEntry));
      trailingFactsAppender.accept(facts, typedEntry);
    }
  }
}
