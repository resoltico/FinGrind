package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalLine;
import java.util.List;
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
  private static final String SAMPLE_QUANTITY = "5";
  private static final InventoryReliefTemplateDescriptor TRADING_SALE_INVENTORY_RELIEF =
      new InventoryReliefTemplateDescriptor("inventory", "cost-of-sales", SAMPLE_QUANTITY);
  private static final List<ContractTemplates.JournalLineTemplateDescriptor> DIRECT_JOURNAL_LINES =
      List.of(
          new ContractTemplates.JournalLineTemplateDescriptor(
              "cash", JournalLine.EntrySide.DEBIT, new MonetaryAmount("EUR", "1000")),
          new ContractTemplates.JournalLineTemplateDescriptor(
              "service-revenue", JournalLine.EntrySide.CREDIT, new MonetaryAmount("EUR", "1000")));

  private MachineContractPostEntryVariantTemplates() {}

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor template(
      BookkeepingEntryKind entryKind, @Nullable BookTemplateId bookTemplateId) {
    return MachineContractPostEntryTemplateCatalog.template(entryKind, bookTemplateId);
  }

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor directJournalTemplate() {
    return new ContractPostingRequestTemplates.PostingRequestTemplateDescriptor(
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
        null,
        null,
        null,
        null,
        null,
        DIRECT_JOURNAL_LINES,
        null,
        evidenceTemplate(BookkeepingEntryKind.DIRECT_JOURNAL),
        provenanceTemplate(),
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
        null);
  }

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor
      openingPositionTemplate() {
    return new ContractPostingRequestTemplates.PostingRequestTemplateDescriptor(
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
        null);
  }

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor reversalTemplate() {
    return new ContractPostingRequestTemplates.PostingRequestTemplateDescriptor(
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
        null,
        null,
        null,
        null,
        null,
        evidenceTemplate(BookkeepingEntryKind.REVERSAL),
        provenanceTemplate(),
        new ContractReversalTemplates.ReversalTemplateDescriptor(
            "018f0000-0000-7000-8000-000000000001", "operator-correction"),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor roleAmountTemplate(
      BookkeepingEntryKind entryKind,
      @Nullable String cashAccountCode,
      @Nullable String receivableAccountCode,
      @Nullable String payableAccountCode,
      @Nullable String revenueAccountCode,
      @Nullable String inventoryAccountCode,
      @Nullable String expenseAccountCode,
      @Nullable String equityAccountCode,
      @Nullable InventoryReliefTemplateDescriptor inventoryRelief) {
    return new ContractPostingRequestTemplates.PostingRequestTemplateDescriptor(
        entryKind,
        SAMPLE_EFFECTIVE_DATE,
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
        inventoryRelief,
        null,
        null,
        null,
        null,
        null,
        evidenceTemplate(entryKind),
        provenanceTemplate(),
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
        null);
  }

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor accrualCutoffTemplate(
      BookkeepingEntryKind entryKind,
      @Nullable String cashAccountCode,
      @Nullable String revenueAccountCode,
      @Nullable String expenseAccountCode,
      String accrualCutoffId,
      @Nullable String prepaymentAssetAccountCode,
      @Nullable String deferredRevenueAccountCode,
      @Nullable String accruedExpenseLiabilityAccountCode,
      ContractTemplates.@Nullable RecognitionIntervalTemplateDescriptor recognitionInterval) {
    return new ContractPostingRequestTemplates.PostingRequestTemplateDescriptor(
        entryKind,
        SAMPLE_EFFECTIVE_DATE,
        cashAccountCode,
        null,
        null,
        revenueAccountCode,
        null,
        expenseAccountCode,
        null,
        null,
        null,
        null,
        new MonetaryAmount("EUR", "1000"),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        evidenceTemplate(entryKind),
        provenanceTemplate(),
        null,
        accrualCutoffId,
        prepaymentAssetAccountCode,
        deferredRevenueAccountCode,
        accruedExpenseLiabilityAccountCode,
        recognitionInterval,
        null,
        null,
        null,
        null,
        null);
  }

  static String salesRevenueAccountCode(@Nullable BookTemplateId bookTemplateId) {
    return bookTemplateId == BookTemplateId.OWNER_MANAGED_TRADING
        ? "sales-revenue"
        : "service-revenue";
  }

  static @Nullable InventoryReliefTemplateDescriptor tradingInventoryRelief(
      @Nullable BookTemplateId bookTemplateId) {
    return bookTemplateId == BookTemplateId.OWNER_MANAGED_TRADING
        ? TRADING_SALE_INVENTORY_RELIEF
        : null;
  }

  static ContractTemplates.AccountingEvidenceTemplateDescriptor evidenceTemplate(
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

  static ContractTemplates.ProvenanceTemplateDescriptor provenanceTemplate() {
    return new ContractTemplates.ProvenanceTemplateDescriptor(
        SAMPLE_ACTOR_ID,
        ActorType.PERSON,
        SAMPLE_COMMAND_ID,
        SAMPLE_IDEMPOTENCY_KEY,
        SAMPLE_CAUSATION_ID,
        null);
  }
}
