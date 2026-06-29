package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.ActorType;
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

  private MachineContractPostEntryVariantTemplates() {}

  static ContractTemplates.PostingRequestTemplateDescriptor template(
      BookkeepingEntryKind entryKind) {
    return switch (entryKind) {
      case DIRECT_JOURNAL -> directJournalTemplate();
      case SALE -> saleTemplate();
      case EXPENSE -> expenseTemplate();
      case OWNER_CONTRIBUTION -> ownerContributionTemplate();
      case OWNER_WITHDRAWAL -> ownerWithdrawalTemplate();
      case OPENING_POSITION -> openingPositionTemplate();
      case REVERSAL -> reversalTemplate();
    };
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
        directJournalLines(),
        null,
        evidenceTemplate(BookkeepingEntryKind.DIRECT_JOURNAL),
        provenanceTemplate(),
        null);
  }

  private static ContractTemplates.PostingRequestTemplateDescriptor saleTemplate() {
    return roleAmountTemplate(BookkeepingEntryKind.SALE, "cash", "service-revenue", null, null);
  }

  private static ContractTemplates.PostingRequestTemplateDescriptor expenseTemplate() {
    return roleAmountTemplate(
        BookkeepingEntryKind.EXPENSE, "cash", null, "operating-expense", null);
  }

  private static ContractTemplates.PostingRequestTemplateDescriptor ownerContributionTemplate() {
    return roleAmountTemplate(
        BookkeepingEntryKind.OWNER_CONTRIBUTION, "cash", null, null, "owner-capital");
  }

  private static ContractTemplates.PostingRequestTemplateDescriptor ownerWithdrawalTemplate() {
    return roleAmountTemplate(
        BookkeepingEntryKind.OWNER_WITHDRAWAL, "cash", null, null, "owner-draws");
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
        directJournalLines(),
        null,
        evidenceTemplate(BookkeepingEntryKind.REVERSAL),
        provenanceTemplate(),
        new ContractTemplates.ReversalTemplateDescriptor(
            "018f0000-0000-7000-8000-000000000001", "operator-correction"));
  }

  private static ContractTemplates.PostingRequestTemplateDescriptor roleAmountTemplate(
      BookkeepingEntryKind entryKind,
      String cashAccountCode,
      @Nullable String revenueAccountCode,
      @Nullable String expenseAccountCode,
      @Nullable String equityAccountCode) {
    return new ContractTemplates.PostingRequestTemplateDescriptor(
        entryKind,
        SAMPLE_EFFECTIVE_DATE,
        cashAccountCode,
        revenueAccountCode,
        expenseAccountCode,
        equityAccountCode,
        new MonetaryAmount("EUR", "1000"),
        null,
        null,
        null,
        null,
        evidenceTemplate(entryKind),
        provenanceTemplate(),
        null);
  }

  private static List<ContractTemplates.JournalLineTemplateDescriptor> directJournalLines() {
    return List.of(
        new ContractTemplates.JournalLineTemplateDescriptor(
            "cash", JournalLine.EntrySide.DEBIT, new MonetaryAmount("EUR", "1000")),
        new ContractTemplates.JournalLineTemplateDescriptor(
            "service-revenue", JournalLine.EntrySide.CREDIT, new MonetaryAmount("EUR", "1000")));
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
}
