package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingPolicyProfile;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.EntityForm;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.OwnerModel;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Canonical machine-contract templates and scaffold examples. */
final class MachineContractTemplatesCatalog {
  private static final String SAMPLE_EFFECTIVE_DATE = "2026-01-15";
  private static final String SAMPLE_SOURCE_DOCUMENT_ID = "invoice-1001";
  private static final String SAMPLE_SOURCE_DOCUMENT_TYPE = "invoice";
  private static final String SAMPLE_DOCUMENT_DATE = "2026-01-15";
  private static final String SAMPLE_CAPTURED_AT = "2026-01-15T09:00:00Z";
  private static final String SAMPLE_STORAGE_LOCATOR = "vault://documents/invoice-1001";
  private static final String SAMPLE_CONTENT_SHA256 =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
  private static final String SAMPLE_ACTOR_ID = "operator-demo-1";
  private static final String SAMPLE_COMMAND_ID = "command-demo-1";
  private static final String SAMPLE_IDEMPOTENCY_KEY = "idem-demo-1";
  private static final String SAMPLE_CAUSATION_ID = "cause-demo-1";
  private static final String DECLARE_ACCOUNT_CASH_JSON =
      """
      {
        "accountCode": "1000",
        "accountName": "Cash",
        "accountType": "ASSET",
        "accountRole": "ORDINARY",
        "accountNodeKind": "POSTABLE",
        "financialPositionLineClassification": "CURRENT_ASSET"
      }
      """;

  private static final String DECLARE_ACCOUNT_REVENUE_JSON =
      """
      {
        "accountCode": "2000",
        "accountName": "Revenue",
        "accountType": "REVENUE",
        "accountRole": "ORDINARY",
        "accountNodeKind": "POSTABLE",
        "profitAndLossLineClassification": "OPERATING_REVENUE"
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
        BookkeepingEntryKind.CASH_REVENUE,
        SAMPLE_EFFECTIVE_DATE,
        "1000",
        "2000",
        null,
        null,
        new MonetaryAmount("EUR", "1000"),
        null,
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
            ActorType.AGENT,
            SAMPLE_COMMAND_ID,
            SAMPLE_IDEMPOTENCY_KEY,
            SAMPLE_CAUSATION_ID,
            null),
        null);
  }

  static ContractTemplates.DeclareAccountTemplateDescriptor declareAccountTemplate() {
    return new ContractTemplates.DeclareAccountTemplateDescriptor(
        "1000",
        "Cash",
        AccountType.ASSET,
        AccountRole.ORDINARY,
        AccountNodeKind.POSTABLE,
        null,
        FinancialPositionLineClassification.CURRENT_ASSET,
        null);
  }

  static ContractTemplates.LedgerPlanTemplateDescriptor planTemplate() {
    return new ContractTemplates.LedgerPlanTemplateDescriptor(
        "plan-1",
        List.of(
            new ContractTemplates.LedgerPlanStepTemplateDescriptor(
                "initialize-book",
                LedgerStepKind.OPEN_BOOK,
                new ContractTemplates.OpenBookTemplateDescriptor(
                    "Acme Studio",
                    EntityForm.FREELANCER,
                    OwnerModel.SOLE_OWNER,
                    List.of("translation-services"),
                    "EUR",
                    "01-01",
                    AccountingPolicyProfile.INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1),
                null,
                null,
                null,
                null,
                null),
            new ContractTemplates.LedgerPlanStepTemplateDescriptor(
                "declare-cash",
                LedgerStepKind.DECLARE_ACCOUNT,
                null,
                null,
                new ContractTemplates.DeclareAccountTemplateDescriptor(
                    "1000",
                    "Cash",
                    AccountType.ASSET,
                    AccountRole.ORDINARY,
                    AccountNodeKind.POSTABLE,
                    null,
                    FinancialPositionLineClassification.CURRENT_ASSET,
                    null),
                null,
                null,
                null),
            new ContractTemplates.LedgerPlanStepTemplateDescriptor(
                "declare-revenue",
                LedgerStepKind.DECLARE_ACCOUNT,
                null,
                null,
                new ContractTemplates.DeclareAccountTemplateDescriptor(
                    "2000",
                    "Revenue",
                    AccountType.REVENUE,
                    AccountRole.ORDINARY,
                    AccountNodeKind.POSTABLE,
                    null,
                    null,
                    ProfitAndLossLineClassification.OPERATING_REVENUE),
                null,
                null,
                null),
            new ContractTemplates.LedgerPlanStepTemplateDescriptor(
                "post-journal",
                LedgerStepKind.POST_ENTRY,
                null,
                requestTemplate(),
                null,
                null,
                null,
                null),
            new ContractTemplates.LedgerPlanStepTemplateDescriptor(
                "assert-cash-balance",
                LedgerStepKind.ASSERT,
                null,
                null,
                null,
                null,
                new ContractTemplates.LedgerAssertionTemplateDescriptor(
                    LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS,
                    "1000",
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

  static ContractTemplates.@Nullable LedgerPlanTemplateDescriptor ledgerPlanTemplateFor(
      @Nullable ProtocolOperation selectedOperation) {
    if (selectedOperation == null) {
      return null;
    }
    return selectedOperation.id() == OperationId.EXECUTE_PLAN ? planTemplate() : null;
  }
}
