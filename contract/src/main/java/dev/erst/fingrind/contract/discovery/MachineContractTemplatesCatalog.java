package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.JournalLine;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Canonical machine-contract templates and scaffold examples. */
final class MachineContractTemplatesCatalog {
  private static final String SAMPLE_EFFECTIVE_DATE = "2026-01-15";
  private static final String SAMPLE_SOURCE_DOCUMENT_ID = ScaffoldPlaceholders.SOURCE_DOCUMENT_ID;
  private static final String SAMPLE_SOURCE_DOCUMENT_TYPE = "cash-receipt";
  private static final String SAMPLE_DOCUMENT_DATE = "2026-01-15";
  private static final String SAMPLE_CAPTURED_AT = "2026-01-15T09:00:00Z";
  private static final String SAMPLE_STORAGE_LOCATOR = ScaffoldPlaceholders.STORAGE_LOCATOR;
  private static final String SAMPLE_CONTENT_SHA256 = ScaffoldPlaceholders.CONTENT_SHA256;
  private static final String SAMPLE_ACTOR_ID = ScaffoldPlaceholders.ACTOR_ID;
  private static final String SAMPLE_COMMAND_ID = ScaffoldPlaceholders.COMMAND_ID;
  private static final String SAMPLE_IDEMPOTENCY_KEY = ScaffoldPlaceholders.IDEMPOTENCY_KEY;
  private static final String SAMPLE_CAUSATION_ID = ScaffoldPlaceholders.CAUSATION_ID;
  private static final String DECLARE_ACCOUNT_CASH_JSON =
      """
      {
        "accountCode": "cash-reserve",
        "accountName": "Cash Reserve",
        "accountType": "ASSET",
        "accountRole": "ORDINARY",
        "accountNodeKind": "POSTABLE",
        "financialPositionLineClassification": "CURRENT_ASSET"
      }
      """;

  private static final String DECLARE_ACCOUNT_REVENUE_JSON =
      """
      {
        "accountCode": "misc-revenue",
        "accountName": "Misc Revenue",
        "accountType": "REVENUE",
        "accountRole": "ORDINARY",
        "accountNodeKind": "POSTABLE",
        "profitAndLossLineClassification": "OTHER_REVENUE"
      }
      """;

  private MachineContractTemplatesCatalog() {}

  static String declareAccountCashJson() {
    return DECLARE_ACCOUNT_CASH_JSON;
  }

  static String declareAccountRevenueJson() {
    return DECLARE_ACCOUNT_REVENUE_JSON;
  }

  static ContractTemplates.PostingRequestTemplateDescriptor requestTemplate() {
    return new ContractTemplates.PostingRequestTemplateDescriptor(
        BookkeepingEntryKind.JOURNAL,
        null,
        SAMPLE_EFFECTIVE_DATE,
        null,
        null,
        null,
        null,
        null,
        directJournalLines(),
        null,
        new ContractTemplates.AccountingEvidenceTemplateDescriptor(
            List.of(
                new ContractTemplates.SourceDocumentTemplateDescriptor(
                    SAMPLE_SOURCE_DOCUMENT_ID,
                    SAMPLE_SOURCE_DOCUMENT_TYPE,
                    SAMPLE_DOCUMENT_DATE,
                    SAMPLE_CAPTURED_AT,
                    SAMPLE_STORAGE_LOCATOR,
                    SAMPLE_CONTENT_SHA256)),
            List.of()),
        new ContractTemplates.ProvenanceTemplateDescriptor(
            SAMPLE_ACTOR_ID,
            ActorType.PERSON,
            SAMPLE_COMMAND_ID,
            SAMPLE_IDEMPOTENCY_KEY,
            SAMPLE_CAUSATION_ID,
            null),
        null);
  }

  private static List<ContractTemplates.JournalLineTemplateDescriptor> directJournalLines() {
    return List.of(
        new ContractTemplates.JournalLineTemplateDescriptor(
            "cash", JournalLine.EntrySide.DEBIT, new MonetaryAmount("EUR", "1000")),
        new ContractTemplates.JournalLineTemplateDescriptor(
            "service-revenue", JournalLine.EntrySide.CREDIT, new MonetaryAmount("EUR", "1000")));
  }

  static ContractTemplates.DeclareAccountTemplateDescriptor declareAccountTemplate() {
    return new ContractTemplates.DeclareAccountTemplateDescriptor(
        "cash-reserve",
        "Cash Reserve",
        AccountType.ASSET,
        AccountRole.ORDINARY,
        AccountNodeKind.POSTABLE,
        null,
        FinancialPositionLineClassification.CURRENT_ASSET,
        null);
  }

  static ContractPlanTemplates.LedgerPlanTemplateDescriptor planTemplate() {
    return new ContractPlanTemplates.LedgerPlanTemplateDescriptor(
        "plan-1",
        List.of(
            new ContractPlanTemplates.LedgerPlanStepTemplateDescriptor(
                "ensure-book",
                LedgerStepKind.ENSURE_BOOK,
                new ContractPlanTemplates.EnsureBookTemplateDescriptor(
                    "Acme Studio", "EUR", "01-01"),
                null,
                null,
                null,
                null,
                null),
            new ContractPlanTemplates.LedgerPlanStepTemplateDescriptor(
                "post-journal",
                LedgerStepKind.POST_ENTRY,
                null,
                requestTemplate(),
                null,
                null,
                null,
                null),
            new ContractPlanTemplates.LedgerPlanStepTemplateDescriptor(
                "assert-cash-balance",
                LedgerStepKind.ASSERT,
                null,
                null,
                null,
                null,
                new ContractPlanTemplates.LedgerAssertionTemplateDescriptor(
                    LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS,
                    "cash",
                    null,
                    null,
                    new MonetaryAmount("EUR", "1000"),
                    BalanceSide.DEBIT,
                    null),
                null)));
  }

  static ContractRequestShapes.@Nullable RequestShapesDescriptor requestShapesFor(
      @Nullable ProtocolOperation selectedOperation) {
    if (selectedOperation == null) {
      return null;
    }
    ContractRequestShapes.RequestShapesDescriptor canonical =
        MachineContractRequestShapeDescriptors.requestShapes();
    return switch (selectedOperation.id()) {
      case POST_ENTRY, PREFLIGHT_ENTRY ->
          new ContractRequestShapes.RequestShapesDescriptor(
              canonical.schemaDialect(), canonical.postEntry(), null, null);
      case DECLARE_ACCOUNT ->
          new ContractRequestShapes.RequestShapesDescriptor(
              canonical.schemaDialect(), null, canonical.declareAccount(), null);
      case EXECUTE_PLAN ->
          new ContractRequestShapes.RequestShapesDescriptor(
              canonical.schemaDialect(), null, null, canonical.ledgerPlan());
      default -> null;
    };
  }

  static ContractTemplates.@Nullable PostingRequestTemplateDescriptor postingRequestTemplateFor(
      @Nullable ProtocolOperation selectedOperation) {
    if (selectedOperation == null) {
      return null;
    }
    return switch (selectedOperation.id()) {
      case POST_ENTRY, PREFLIGHT_ENTRY -> requestTemplate();
      default -> null;
    };
  }

  static ContractTemplates.@Nullable DeclareAccountTemplateDescriptor declareAccountTemplateFor(
      @Nullable ProtocolOperation selectedOperation) {
    if (selectedOperation == null) {
      return null;
    }
    return selectedOperation.id() == OperationId.DECLARE_ACCOUNT ? declareAccountTemplate() : null;
  }

  static ContractPlanTemplates.@Nullable LedgerPlanTemplateDescriptor ledgerPlanTemplateFor(
      @Nullable ProtocolOperation selectedOperation) {
    if (selectedOperation == null) {
      return null;
    }
    return selectedOperation.id() == OperationId.EXECUTE_PLAN ? planTemplate() : null;
  }
}
