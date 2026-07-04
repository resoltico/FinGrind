package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalLine;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Variant template builders for post-entry request shapes. */
final class MachineContractPostEntryVariantTemplates {
  private static final String SAMPLE_EFFECTIVE_DATE = "2026-01-15";
  private static final String SAMPLE_SOURCE_DOCUMENT_ID = ScaffoldPlaceholders.SOURCE_DOCUMENT_ID;
  private static final String SAMPLE_DOCUMENT_DATE = "2026-01-15";
  private static final String SAMPLE_ACTOR_ID = ScaffoldPlaceholders.ACTOR_ID;
  private static final String SAMPLE_COMMAND_ID = ScaffoldPlaceholders.COMMAND_ID;
  private static final String SAMPLE_IDEMPOTENCY_KEY = ScaffoldPlaceholders.IDEMPOTENCY_KEY;
  private static final String SAMPLE_CAUSATION_ID = ScaffoldPlaceholders.CAUSATION_ID;
  private static final InventoryReliefTemplateDescriptor TRADING_SALE_INVENTORY_RELIEF =
      new InventoryReliefTemplateDescriptor(
          "inventory", "cost-of-sales", new MonetaryAmount("EUR", "600"));
  private static final List<ContractTemplates.JournalLineTemplateDescriptor> DIRECT_JOURNAL_LINES =
      List.of(
          new ContractTemplates.JournalLineTemplateDescriptor(
              "cash", JournalLine.EntrySide.DEBIT, new MonetaryAmount("EUR", "1000")),
          new ContractTemplates.JournalLineTemplateDescriptor(
              "service-revenue", JournalLine.EntrySide.CREDIT, new MonetaryAmount("EUR", "1000")));
  private static final Map<BookkeepingEntryKind, TemplateBuilder> TEMPLATES =
      Map.ofEntries(
          Map.entry(
              BookkeepingEntryKind.DIRECT_JOURNAL,
              ignoredBookTemplateId -> directJournalTemplate()),
          Map.entry(
              BookkeepingEntryKind.SALE_SETTLED,
              MachineContractPostEntryVariantTemplates::saleSettledTemplate),
          Map.entry(
              BookkeepingEntryKind.SALE_ON_CREDIT,
              MachineContractPostEntryVariantTemplates::saleOnCreditTemplate),
          Map.entry(
              BookkeepingEntryKind.PURCHASE_SETTLED,
              ignoredBookTemplateId ->
                  roleAmountTemplate(
                      BookkeepingEntryKind.PURCHASE_SETTLED,
                      "cash",
                      null,
                      null,
                      null,
                      "inventory",
                      null,
                      null,
                      null)),
          Map.entry(
              BookkeepingEntryKind.PURCHASE_ON_CREDIT,
              ignoredBookTemplateId ->
                  roleAmountTemplate(
                      BookkeepingEntryKind.PURCHASE_ON_CREDIT,
                      null,
                      null,
                      "accounts-payable",
                      null,
                      "inventory",
                      null,
                      null,
                      null)),
          Map.entry(
              BookkeepingEntryKind.EXPENSE_SETTLED,
              ignoredBookTemplateId ->
                  roleAmountTemplate(
                      BookkeepingEntryKind.EXPENSE_SETTLED,
                      "cash",
                      null,
                      null,
                      null,
                      null,
                      "operating-expense",
                      null,
                      null)),
          Map.entry(
              BookkeepingEntryKind.EXPENSE_ON_CREDIT,
              ignoredBookTemplateId ->
                  roleAmountTemplate(
                      BookkeepingEntryKind.EXPENSE_ON_CREDIT,
                      null,
                      null,
                      "accounts-payable",
                      null,
                      null,
                      "operating-expense",
                      null,
                      null)),
          Map.entry(
              BookkeepingEntryKind.RECEIPT,
              ignoredBookTemplateId ->
                  roleAmountTemplate(
                      BookkeepingEntryKind.RECEIPT,
                      "cash",
                      "accounts-receivable",
                      null,
                      null,
                      null,
                      null,
                      null,
                      null)),
          Map.entry(
              BookkeepingEntryKind.PAYMENT,
              ignoredBookTemplateId ->
                  roleAmountTemplate(
                      BookkeepingEntryKind.PAYMENT,
                      "cash",
                      null,
                      "accounts-payable",
                      null,
                      null,
                      null,
                      null,
                      null)),
          Map.entry(
              BookkeepingEntryKind.OWNER_CONTRIBUTION,
              ignoredBookTemplateId ->
                  roleAmountTemplate(
                      BookkeepingEntryKind.OWNER_CONTRIBUTION,
                      "cash",
                      null,
                      null,
                      null,
                      null,
                      null,
                      "owner-capital",
                      null)),
          Map.entry(
              BookkeepingEntryKind.OWNER_WITHDRAWAL,
              ignoredBookTemplateId ->
                  roleAmountTemplate(
                      BookkeepingEntryKind.OWNER_WITHDRAWAL,
                      "cash",
                      null,
                      null,
                      null,
                      null,
                      null,
                      "owner-draws",
                      null)),
          Map.entry(
              BookkeepingEntryKind.OPENING_POSITION,
              ignoredBookTemplateId -> openingPositionTemplate()),
          Map.entry(BookkeepingEntryKind.REVERSAL, ignoredBookTemplateId -> reversalTemplate()));

  private MachineContractPostEntryVariantTemplates() {}

  static ContractTemplates.PostingRequestTemplateDescriptor template(
      BookkeepingEntryKind entryKind, @Nullable BookTemplateId bookTemplateId) {
    return Objects.requireNonNull(TEMPLATES.get(entryKind), "entryKind").build(bookTemplateId);
  }

  private static ContractTemplates.PostingRequestTemplateDescriptor saleSettledTemplate(
      @Nullable BookTemplateId bookTemplateId) {
    return roleAmountTemplate(
        BookkeepingEntryKind.SALE_SETTLED,
        "cash",
        null,
        null,
        salesRevenueAccountCode(bookTemplateId),
        null,
        null,
        null,
        tradingInventoryRelief(bookTemplateId));
  }

  private static ContractTemplates.PostingRequestTemplateDescriptor saleOnCreditTemplate(
      @Nullable BookTemplateId bookTemplateId) {
    return roleAmountTemplate(
        BookkeepingEntryKind.SALE_ON_CREDIT,
        null,
        "accounts-receivable",
        null,
        salesRevenueAccountCode(bookTemplateId),
        null,
        null,
        null,
        tradingInventoryRelief(bookTemplateId));
  }

  private static ContractTemplates.PostingRequestTemplateDescriptor directJournalTemplate() {
    return new ContractTemplates.PostingRequestTemplateDescriptor(
        BookkeepingEntryKind.DIRECT_JOURNAL,
        SAMPLE_EFFECTIVE_DATE,
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
        DIRECT_JOURNAL_LINES,
        null,
        evidenceTemplate(BookkeepingEntryKind.DIRECT_JOURNAL),
        provenanceTemplate(),
        null);
  }

  private static ContractTemplates.PostingRequestTemplateDescriptor openingPositionTemplate() {
    return new ContractTemplates.PostingRequestTemplateDescriptor(
        BookkeepingEntryKind.OPENING_POSITION,
        SAMPLE_EFFECTIVE_DATE,
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
        List.of(
            new ContractTemplates.OpeningBalanceTemplateDescriptor(
                "cash", JournalLine.EntrySide.DEBIT, new MonetaryAmount("EUR", "1000")),
            new ContractTemplates.OpeningBalanceTemplateDescriptor(
                "owner-capital", JournalLine.EntrySide.CREDIT, new MonetaryAmount("EUR", "1000"))),
        evidenceTemplate(BookkeepingEntryKind.OPENING_POSITION),
        provenanceTemplate(),
        null);
  }

  private static ContractTemplates.PostingRequestTemplateDescriptor reversalTemplate() {
    return new ContractTemplates.PostingRequestTemplateDescriptor(
        BookkeepingEntryKind.REVERSAL,
        SAMPLE_EFFECTIVE_DATE,
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
        evidenceTemplate(BookkeepingEntryKind.REVERSAL),
        provenanceTemplate(),
        new ContractTemplates.ReversalTemplateDescriptor(
            "018f0000-0000-7000-8000-000000000001", "operator-correction"));
  }

  private static ContractTemplates.PostingRequestTemplateDescriptor roleAmountTemplate(
      BookkeepingEntryKind entryKind,
      @Nullable String cashAccountCode,
      @Nullable String receivableAccountCode,
      @Nullable String payableAccountCode,
      @Nullable String revenueAccountCode,
      @Nullable String inventoryAccountCode,
      @Nullable String expenseAccountCode,
      @Nullable String equityAccountCode,
      @Nullable InventoryReliefTemplateDescriptor inventoryRelief) {
    return new ContractTemplates.PostingRequestTemplateDescriptor(
        entryKind,
        SAMPLE_EFFECTIVE_DATE,
        cashAccountCode,
        receivableAccountCode,
        payableAccountCode,
        revenueAccountCode,
        inventoryAccountCode,
        expenseAccountCode,
        equityAccountCode,
        new MonetaryAmount("EUR", "1000"),
        inventoryRelief,
        null,
        null,
        null,
        null,
        null,
        evidenceTemplate(entryKind),
        provenanceTemplate(),
        null);
  }

  private static String salesRevenueAccountCode(@Nullable BookTemplateId bookTemplateId) {
    return bookTemplateId == BookTemplateId.OWNER_MANAGED_TRADING
        ? "sales-revenue"
        : "service-revenue";
  }

  private static @Nullable InventoryReliefTemplateDescriptor tradingInventoryRelief(
      @Nullable BookTemplateId bookTemplateId) {
    return bookTemplateId == BookTemplateId.OWNER_MANAGED_TRADING
        ? TRADING_SALE_INVENTORY_RELIEF
        : null;
  }

  private static ContractTemplates.AccountingEvidenceTemplateDescriptor evidenceTemplate(
      BookkeepingEntryKind entryKind) {
    return new ContractTemplates.AccountingEvidenceTemplateDescriptor(
        List.of(
            new ContractTemplates.SourceDocumentTemplateDescriptor(
                SAMPLE_SOURCE_DOCUMENT_ID,
                ProtocolCatalog.domain()
                    .requestSurface()
                    .bookkeepingEntryKind(entryKind)
                    .sourceDocumentTypes()
                    .scaffoldValue(),
                SAMPLE_DOCUMENT_DATE)),
        List.of());
  }

  private static ContractTemplates.ProvenanceTemplateDescriptor provenanceTemplate() {
    return new ContractTemplates.ProvenanceTemplateDescriptor(
        SAMPLE_ACTOR_ID,
        ActorType.PERSON,
        SAMPLE_COMMAND_ID,
        SAMPLE_IDEMPOTENCY_KEY,
        SAMPLE_CAUSATION_ID,
        null);
  }

  /** Variant-specific template builder for one posting-request kind. */
  @FunctionalInterface
  private interface TemplateBuilder {
    /** Builds one posting-request template for the selected doctrine, when applicable. */
    ContractTemplates.PostingRequestTemplateDescriptor build(
        @Nullable BookTemplateId bookTemplateId);
  }
}
