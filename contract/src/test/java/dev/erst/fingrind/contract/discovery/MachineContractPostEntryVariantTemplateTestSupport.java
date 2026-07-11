package dev.erst.fingrind.contract.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalLine;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Shared fixtures and assertions for posting-template discovery coverage tests. */
final class MachineContractPostEntryVariantTemplateTestSupport {
  private static final List<ContractTemplates.JournalLineTemplateDescriptor> JOURNAL_LINES =
      List.of(
          new ContractTemplates.JournalLineTemplateDescriptor(
              "cash", JournalLine.EntrySide.DEBIT, new MonetaryAmount("EUR", "1000")),
          new ContractTemplates.JournalLineTemplateDescriptor(
              "service-revenue", JournalLine.EntrySide.CREDIT, new MonetaryAmount("EUR", "1000")));
  private static final List<ContractTemplates.OpeningBalanceTemplateDescriptor> OPENING_BALANCES =
      List.of(
          new ContractTemplates.OpeningBalanceTemplateDescriptor(
              "cash", JournalLine.EntrySide.DEBIT, new MonetaryAmount("EUR", "1000")),
          new ContractTemplates.OpeningBalanceTemplateDescriptor(
              "owner-capital", JournalLine.EntrySide.CREDIT, new MonetaryAmount("EUR", "1000")));

  private MachineContractPostEntryVariantTemplateTestSupport() {}

  static void assertCanonicalTemplate(
      BookkeepingEntryKind entryKind, ContractTemplates.PostingRequestTemplateDescriptor template) {
    assertEquals(entryKind, template.entryKind());
    assertEquals(
        ProtocolCatalog.domain()
            .requestSurface()
            .bookkeepingEntryKind(entryKind)
            .sourceDocumentTypes()
            .scaffoldValue(),
        template.evidence().sourceDocuments().getFirst().sourceDocumentType());
    assertNotNull(template.provenance());
    switch (entryKind) {
      case DIRECT_JOURNAL -> {
        assertNotNull(template.lines());
        assertNull(template.amount());
        assertNull(template.openingBalances());
        assertNull(template.reversal());
      }
      case SALE_SETTLED -> {
        assertEquals("cash", template.cashAccountCode());
        assertEquals("service-revenue", template.revenueAccountCode());
        assertNull(template.inventoryRelief());
        assertAmountTemplateShape(template);
      }
      case SALE_ON_CREDIT -> {
        assertEquals("accounts-receivable", template.receivableAccountCode());
        assertEquals("service-revenue", template.revenueAccountCode());
        assertNull(template.inventoryRelief());
        assertAmountTemplateShape(template);
      }
      case PURCHASE_SETTLED -> {
        assertEquals("inventory", template.inventoryAccountCode());
        assertEquals("cash", template.cashAccountCode());
        assertNull(template.inventoryRelief());
        assertQuantityUnitCostTemplateShape(template);
      }
      case PURCHASE_ON_CREDIT -> {
        assertEquals("inventory", template.inventoryAccountCode());
        assertEquals("accounts-payable", template.payableAccountCode());
        assertNull(template.inventoryRelief());
        assertQuantityUnitCostTemplateShape(template);
      }
      case EXPENSE_SETTLED -> {
        assertEquals("cash", template.cashAccountCode());
        assertEquals("operating-expense", template.expenseAccountCode());
        assertAmountTemplateShape(template);
      }
      case EXPENSE_ON_CREDIT -> {
        assertEquals("accounts-payable", template.payableAccountCode());
        assertEquals("operating-expense", template.expenseAccountCode());
        assertAmountTemplateShape(template);
      }
      case RECEIPT -> {
        assertEquals("cash", template.cashAccountCode());
        assertEquals("accounts-receivable", template.receivableAccountCode());
        assertAmountTemplateShape(template);
      }
      case PAYMENT -> {
        assertEquals("cash", template.cashAccountCode());
        assertEquals("accounts-payable", template.payableAccountCode());
        assertAmountTemplateShape(template);
      }
      case OWNER_CONTRIBUTION, OWNER_WITHDRAWAL -> {
        assertEquals("cash", template.cashAccountCode());
        assertEquals(ownerEquityAccount(entryKind), template.equityAccountCode());
        assertAmountTemplateShape(template);
      }
      case OPENING_POSITION -> {
        assertNull(template.amount());
        assertNull(template.lines());
        assertEquals(2, Objects.requireNonNull(template.openingBalances()).size());
      }
      case REVERSAL -> {
        assertNull(template.lines());
        assertNull(template.amount());
        assertEquals(
            "018f0000-0000-7000-8000-000000000001",
            Objects.requireNonNull(template.reversal()).priorPostingId());
      }
      default -> assertInventoryTemplate(entryKind, template);
    }
  }

  private static void assertInventoryTemplate(
      BookkeepingEntryKind entryKind, ContractTemplates.PostingRequestTemplateDescriptor template) {
    assertEquals("inventory", template.inventoryAccountCode());
    switch (entryKind) {
      case INVENTORY_CAPITALIZATION_SETTLED -> {
        assertEquals("cash", template.cashAccountCode());
        assertAmountTemplateShape(template);
      }
      case INVENTORY_CAPITALIZATION_ON_CREDIT -> {
        assertEquals("accounts-payable", template.payableAccountCode());
        assertAmountTemplateShape(template);
      }
      case INVENTORY_WRITE_DOWN -> {
        assertEquals("inventory-write-down-loss", template.writeDownLossAccountCode());
        assertAmountTemplateShape(template);
      }
      case INVENTORY_SHRINKAGE -> {
        assertEquals("inventory-shrinkage-loss", template.shrinkageLossAccountCode());
        assertNull(template.amount());
        assertEquals("5", template.quantity());
        assertNull(template.unitCost());
      }
      case INVENTORY_COUNT_INCREASE -> {
        assertEquals("inventory-count-gain", template.countGainAccountCode());
        assertQuantityUnitCostTemplateShape(template);
      }
      default -> throw new IllegalArgumentException("Expected inventory entry kind.");
    }
  }

  static Map<OperationId, BookkeepingEntryKind> scaffoldOperationEntryKinds() {
    return Map.ofEntries(
        Map.entry(OperationId.POST_ENTRY, BookkeepingEntryKind.DIRECT_JOURNAL),
        Map.entry(OperationId.PREFLIGHT_ENTRY, BookkeepingEntryKind.SALE_SETTLED),
        Map.entry(OperationId.RECORD_SALE_SETTLED, BookkeepingEntryKind.SALE_SETTLED),
        Map.entry(OperationId.RECORD_SALE_ON_CREDIT, BookkeepingEntryKind.SALE_ON_CREDIT),
        Map.entry(OperationId.RECORD_PURCHASE_SETTLED, BookkeepingEntryKind.PURCHASE_SETTLED),
        Map.entry(OperationId.RECORD_PURCHASE_ON_CREDIT, BookkeepingEntryKind.PURCHASE_ON_CREDIT),
        Map.entry(
            OperationId.RECORD_INVENTORY_CAPITALIZATION_SETTLED,
            BookkeepingEntryKind.INVENTORY_CAPITALIZATION_SETTLED),
        Map.entry(
            OperationId.RECORD_INVENTORY_CAPITALIZATION_ON_CREDIT,
            BookkeepingEntryKind.INVENTORY_CAPITALIZATION_ON_CREDIT),
        Map.entry(
            OperationId.RECORD_INVENTORY_WRITE_DOWN, BookkeepingEntryKind.INVENTORY_WRITE_DOWN),
        Map.entry(OperationId.RECORD_INVENTORY_SHRINKAGE, BookkeepingEntryKind.INVENTORY_SHRINKAGE),
        Map.entry(
            OperationId.RECORD_INVENTORY_COUNT_INCREASE,
            BookkeepingEntryKind.INVENTORY_COUNT_INCREASE),
        Map.entry(OperationId.RECORD_EXPENSE_SETTLED, BookkeepingEntryKind.EXPENSE_SETTLED),
        Map.entry(OperationId.RECORD_EXPENSE_ON_CREDIT, BookkeepingEntryKind.EXPENSE_ON_CREDIT),
        Map.entry(OperationId.RECORD_RECEIPT, BookkeepingEntryKind.RECEIPT),
        Map.entry(OperationId.RECORD_PAYMENT, BookkeepingEntryKind.PAYMENT),
        Map.entry(OperationId.RECORD_OWNER_CONTRIBUTION, BookkeepingEntryKind.OWNER_CONTRIBUTION),
        Map.entry(OperationId.RECORD_OWNER_WITHDRAWAL, BookkeepingEntryKind.OWNER_WITHDRAWAL),
        Map.entry(OperationId.RECORD_OPENING_POSITION, BookkeepingEntryKind.OPENING_POSITION),
        Map.entry(OperationId.RECORD_REVERSAL, BookkeepingEntryKind.REVERSAL));
  }

  static Map<BookkeepingEntryKind, String> settlementAdjunctForbiddenContexts() {
    return Map.ofEntries(
        Map.entry(BookkeepingEntryKind.DIRECT_JOURNAL, "journal"),
        Map.entry(BookkeepingEntryKind.SALE_SETTLED, "saleSettled"),
        Map.entry(BookkeepingEntryKind.SALE_ON_CREDIT, "saleOnCredit"),
        Map.entry(BookkeepingEntryKind.PURCHASE_SETTLED, "purchaseSettled"),
        Map.entry(BookkeepingEntryKind.PURCHASE_ON_CREDIT, "purchaseOnCredit"),
        Map.entry(
            BookkeepingEntryKind.INVENTORY_CAPITALIZATION_SETTLED,
            "inventoryCapitalizationSettled"),
        Map.entry(
            BookkeepingEntryKind.INVENTORY_CAPITALIZATION_ON_CREDIT,
            "inventoryCapitalizationOnCredit"),
        Map.entry(BookkeepingEntryKind.INVENTORY_WRITE_DOWN, "inventoryWriteDown"),
        Map.entry(BookkeepingEntryKind.INVENTORY_SHRINKAGE, "inventoryShrinkage"),
        Map.entry(BookkeepingEntryKind.INVENTORY_COUNT_INCREASE, "inventoryCountIncrease"),
        Map.entry(BookkeepingEntryKind.EXPENSE_SETTLED, "expenseSettled"),
        Map.entry(BookkeepingEntryKind.EXPENSE_ON_CREDIT, "expenseOnCredit"),
        Map.entry(BookkeepingEntryKind.OWNER_CONTRIBUTION, "ownerContribution"),
        Map.entry(BookkeepingEntryKind.OWNER_WITHDRAWAL, "ownerWithdrawal"),
        Map.entry(BookkeepingEntryKind.OPENING_POSITION, "openingPosition"),
        Map.entry(BookkeepingEntryKind.REVERSAL, "reversal"));
  }

  static ContractPostingRequestTemplateValidators.PostingTemplateFields canonicalFields(
      BookkeepingEntryKind entryKind,
      ContractTemplates.@Nullable SettlementAdjunctTemplateDescriptor settlementAdjunct) {
    return switch (entryKind) {
      case DIRECT_JOURNAL -> emptyFields(settlementAdjunct, JOURNAL_LINES, null);
      case SALE_SETTLED ->
          amountFields("cash", null, null, "service-revenue", null, null, null, settlementAdjunct);
      case SALE_ON_CREDIT ->
          amountFields(
              null,
              "accounts-receivable",
              null,
              "service-revenue",
              null,
              null,
              null,
              settlementAdjunct);
      case PURCHASE_SETTLED ->
          quantityUnitCostFields(
              "cash", null, null, null, "inventory", null, null, settlementAdjunct);
      case PURCHASE_ON_CREDIT ->
          quantityUnitCostFields(
              null, null, "accounts-payable", null, "inventory", null, null, settlementAdjunct);
      case EXPENSE_SETTLED ->
          amountFields(
              "cash", null, null, null, null, "operating-expense", null, settlementAdjunct);
      case EXPENSE_ON_CREDIT ->
          amountFields(
              null,
              null,
              "accounts-payable",
              null,
              null,
              "operating-expense",
              null,
              settlementAdjunct);
      case RECEIPT ->
          amountFields(
              "cash", "accounts-receivable", null, null, null, null, null, settlementAdjunct);
      case PAYMENT ->
          amountFields("cash", null, "accounts-payable", null, null, null, null, settlementAdjunct);
      case OWNER_CONTRIBUTION, OWNER_WITHDRAWAL ->
          amountFields(
              "cash",
              null,
              null,
              null,
              null,
              null,
              ownerEquityAccount(entryKind),
              settlementAdjunct);
      case OPENING_POSITION -> emptyFields(settlementAdjunct, null, OPENING_BALANCES);
      case REVERSAL -> emptyFields(settlementAdjunct, null, null);
      default -> inventoryCanonicalFields(entryKind, settlementAdjunct);
    };
  }

  private static ContractPostingRequestTemplateValidators.PostingTemplateFields
      inventoryCanonicalFields(
          BookkeepingEntryKind entryKind,
          ContractTemplates.@Nullable SettlementAdjunctTemplateDescriptor settlementAdjunct) {
    return switch (entryKind) {
      case INVENTORY_CAPITALIZATION_SETTLED ->
          amountFields("cash", null, null, null, "inventory", null, null, settlementAdjunct);
      case INVENTORY_CAPITALIZATION_ON_CREDIT ->
          amountFields(
              null, null, "accounts-payable", null, "inventory", null, null, settlementAdjunct);
      case INVENTORY_WRITE_DOWN ->
          inventoryMaintenanceFields(
              "inventory", "inventory-write-down-loss", null, null, null, settlementAdjunct);
      case INVENTORY_SHRINKAGE ->
          inventoryMaintenanceFields(
              "inventory", null, "inventory-shrinkage-loss", null, "5", settlementAdjunct);
      case INVENTORY_COUNT_INCREASE ->
          inventoryMaintenanceFields(
              "inventory", null, null, "inventory-count-gain", null, settlementAdjunct);
      default -> throw new IllegalArgumentException("Expected inventory entry kind.");
    };
  }

  private static String ownerEquityAccount(BookkeepingEntryKind entryKind) {
    return entryKind == BookkeepingEntryKind.OWNER_CONTRIBUTION ? "owner-capital" : "owner-draws";
  }

  static ContractTemplates.@Nullable SettlementAdjunctTemplateDescriptor settlementAdjunctIfOwned(
      BookkeepingEntryKind entryKind) {
    return entryKind == BookkeepingEntryKind.RECEIPT || entryKind == BookkeepingEntryKind.PAYMENT
        ? settlementAdjunct()
        : null;
  }

  static ContractReversalTemplates.@Nullable ReversalTemplateDescriptor reversalIfOwned(
      BookkeepingEntryKind entryKind) {
    return entryKind == BookkeepingEntryKind.REVERSAL
        ? new ContractReversalTemplates.ReversalTemplateDescriptor("posting-1", "correction")
        : null;
  }

  static ContractTemplates.SettlementAdjunctTemplateDescriptor settlementAdjunct() {
    return new ContractTemplates.SettlementAdjunctTemplateDescriptor(
        "settlement-clearing", new MonetaryAmount("EUR", "250"));
  }

  private static ContractPostingRequestTemplateValidators.PostingTemplateFields amountFields(
      @Nullable String cashAccountCode,
      @Nullable String receivableAccountCode,
      @Nullable String payableAccountCode,
      @Nullable String revenueAccountCode,
      @Nullable String inventoryAccountCode,
      @Nullable String expenseAccountCode,
      @Nullable String equityAccountCode,
      ContractTemplates.@Nullable SettlementAdjunctTemplateDescriptor settlementAdjunct) {
    return new ContractPostingRequestTemplateValidators.PostingTemplateFields(
        cashAccountCode,
        receivableAccountCode,
        payableAccountCode,
        revenueAccountCode,
        inventoryAccountCode,
        expenseAccountCode,
        null,
        null,
        null,
        equityAccountCode,
        new MonetaryAmount("EUR", "1000"),
        null,
        null,
        null,
        settlementAdjunct,
        null,
        null,
        null,
        null);
  }

  private static ContractPostingRequestTemplateValidators.PostingTemplateFields
      quantityUnitCostFields(
          @Nullable String cashAccountCode,
          @Nullable String receivableAccountCode,
          @Nullable String payableAccountCode,
          @Nullable String revenueAccountCode,
          @Nullable String inventoryAccountCode,
          @Nullable String expenseAccountCode,
          @Nullable String equityAccountCode,
          ContractTemplates.@Nullable SettlementAdjunctTemplateDescriptor settlementAdjunct) {
    return new ContractPostingRequestTemplateValidators.PostingTemplateFields(
        cashAccountCode,
        receivableAccountCode,
        payableAccountCode,
        revenueAccountCode,
        inventoryAccountCode,
        expenseAccountCode,
        null,
        null,
        null,
        equityAccountCode,
        null,
        "5",
        new MonetaryAmount("EUR", "120"),
        null,
        settlementAdjunct,
        null,
        null,
        null,
        null);
  }

  private static ContractPostingRequestTemplateValidators.PostingTemplateFields
      inventoryMaintenanceFields(
          String inventoryAccountCode,
          @Nullable String writeDownLossAccountCode,
          @Nullable String shrinkageLossAccountCode,
          @Nullable String countGainAccountCode,
          @Nullable String quantity,
          ContractTemplates.@Nullable SettlementAdjunctTemplateDescriptor settlementAdjunct) {
    return new ContractPostingRequestTemplateValidators.PostingTemplateFields(
        null,
        null,
        null,
        null,
        inventoryAccountCode,
        null,
        writeDownLossAccountCode,
        shrinkageLossAccountCode,
        countGainAccountCode,
        null,
        countGainAccountCode == null && quantity == null ? new MonetaryAmount("EUR", "1000") : null,
        countGainAccountCode == null ? quantity : "5",
        countGainAccountCode == null ? null : new MonetaryAmount("EUR", "120"),
        null,
        settlementAdjunct,
        null,
        null,
        null,
        null);
  }

  private static ContractPostingRequestTemplateValidators.PostingTemplateFields emptyFields(
      ContractTemplates.@Nullable SettlementAdjunctTemplateDescriptor settlementAdjunct,
      @Nullable List<ContractTemplates.JournalLineTemplateDescriptor> lines,
      @Nullable List<ContractTemplates.OpeningBalanceTemplateDescriptor> openingBalances) {
    return new ContractPostingRequestTemplateValidators.PostingTemplateFields(
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        settlementAdjunct,
        null,
        null,
        lines,
        openingBalances);
  }

  private static void assertAmountTemplateShape(
      ContractTemplates.PostingRequestTemplateDescriptor template) {
    assertNotNull(template.amount());
    assertNull(template.quantity());
    assertNull(template.unitCost());
    assertNull(template.lines());
    assertNull(template.openingBalances());
    assertNull(template.reversal());
  }

  private static void assertQuantityUnitCostTemplateShape(
      ContractTemplates.PostingRequestTemplateDescriptor template) {
    assertNull(template.amount());
    assertEquals("5", template.quantity());
    assertEquals(new MonetaryAmount("EUR", "120"), template.unitCost());
    assertNull(template.lines());
    assertNull(template.openingBalances());
    assertNull(template.reversal());
  }
}
