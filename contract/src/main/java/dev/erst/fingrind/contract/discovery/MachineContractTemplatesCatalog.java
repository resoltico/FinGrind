package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplates.PostingRequestTemplateDescriptor;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import dev.erst.fingrind.contract.protocol.ProtocolPostingRequestTopics;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Canonical machine-contract templates and scaffold examples. */
final class MachineContractTemplatesCatalog {
  private static final String DECLARE_ACCOUNT_CASH_JSON =
      """
      {
        "accountCode": "cash-reserve",
        "accountName": "Cash Reserve",
        "accountType": "ASSET",
        "accountNodeKind": "POSTABLE",
        "financialPositionLineClassification": "CURRENT_ASSET",
        "cashFlowAssetClassification": "CASH_AND_CASH_EQUIVALENT"
      }
      """;

  private static final String DECLARE_ACCOUNT_REVENUE_JSON =
      """
      {
        "accountCode": "misc-revenue",
        "accountName": "Misc Revenue",
        "accountType": "REVENUE",
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

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor requestTemplate(
      @Nullable BookTemplateId bookTemplateId) {
    return MachineContractPostEntryVariantSchemas.template(
        BookkeepingEntryKind.SALE_SETTLED, bookTemplateId);
  }

  static ContractTemplates.DeclareAccountTemplateDescriptor declareAccountTemplate() {
    return new ContractTemplates.DeclareAccountTemplateDescriptor(
        "cash-reserve",
        "Cash Reserve",
        AccountType.ASSET,
        AccountNodeKind.POSTABLE,
        null,
        null,
        FinancialPositionLineClassification.CURRENT_ASSET,
        null,
        CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT,
        null);
  }

  static ContractTemplates.DeclareTaxRegistrationTemplateDescriptor
      declareTaxRegistrationTemplate() {
    return new ContractTemplates.DeclareTaxRegistrationTemplateDescriptor(
        ScaffoldPlaceholders.TAX_REGISTRATION_ID,
        "Replace Before Commit Tax Registration",
        new dev.erst.fingrind.contract.tax.TaxJurisdiction(ScaffoldPlaceholders.TAX_JURISDICTION),
        ScaffoldPlaceholders.TAX_REGISTRATION_NUMBER,
        "tax-payable-vat",
        "tax-recoverable-vat",
        dev.erst.fingrind.contract.tax.TaxObligationFrequency.MONTHLY,
        20,
        List.of(
            new ContractTemplates.DeclareTaxCodeTemplateDescriptor(
                ScaffoldPlaceholders.OUTPUT_TAX_CODE,
                "Replace Before Commit Output Tax Code",
                0,
                dev.erst.fingrind.contract.tax.TaxInclusionMode.EXCLUSIVE,
                dev.erst.fingrind.contract.tax.TaxApplicationKind.OUTPUT_SALE),
            new ContractTemplates.DeclareTaxCodeTemplateDescriptor(
                ScaffoldPlaceholders.INPUT_TAX_CODE,
                "Replace Before Commit Input Tax Code",
                0,
                dev.erst.fingrind.contract.tax.TaxInclusionMode.EXCLUSIVE,
                dev.erst.fingrind.contract.tax.TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE)));
  }

  static ContractTemplates.RetireAccountTemplateDescriptor retireAccountTemplate() {
    return new ContractTemplates.RetireAccountTemplateDescriptor("obsolete-account");
  }

  static TemplateDescriptorType attestationRegistryTemplate(OperationId operationId) {
    return switch (operationId) {
      case ENROLL_KEY ->
          new ContractAttestationRegistryTemplates.EnrollKeyTemplateDescriptor(
              ContractAttestationRegistryTemplates.EXAMPLE_PRINCIPAL_ID,
              ContractAttestationRegistryTemplates.EXAMPLE_CREDENTIAL_SPKI,
              "operator");
      case ROLLOVER_KEY ->
          new ContractAttestationRegistryTemplates.RolloverKeyTemplateDescriptor(
              ContractAttestationRegistryTemplates.EXAMPLE_PRINCIPAL_ID,
              ContractAttestationRegistryTemplates.EXAMPLE_REPLACEMENT_CREDENTIAL_SPKI,
              "operator",
              ContractAttestationRegistryTemplates.EXAMPLE_CREDENTIAL_SPKI);
      case REVOKE_KEY ->
          new ContractAttestationRegistryTemplates.RevokeKeyTemplateDescriptor(
              ContractAttestationRegistryTemplates.EXAMPLE_PRINCIPAL_ID,
              ContractAttestationRegistryTemplates.EXAMPLE_CREDENTIAL_SPKI,
              "credential-retired-by-authorized-policy");
      case ALTER_POLICY ->
          new ContractAttestationRegistryTemplates.AlterPolicyTemplateDescriptor(
              List.of(
                  new ContractAttestationRegistryTemplates.PolicyRuleTemplateDescriptor("post", 1)),
              List.of(),
              List.of());
      default ->
          throw new IllegalArgumentException(
              "Operation " + operationId.wireName() + " has no attestation registry template.");
    };
  }

  static ContractPlanTemplates.LedgerPlanTemplateDescriptor planTemplate() {
    return MachineContractPlanTemplates.template(PlanTemplateTopic.GENERAL);
  }

  static ContractPlanTemplates.LedgerPlanTemplateDescriptor planTemplate(PlanTemplateTopic topic) {
    return MachineContractPlanTemplates.template(topic);
  }

  static ContractRequestShapes.@Nullable RequestShapesDescriptor requestShapesFor(
      @Nullable ProtocolOperation selectedOperation) {
    if (selectedOperation == null) {
      return null;
    }
    OperationId operationId = selectedOperation.id();
    if (operationId == OperationId.PREFLIGHT_ENTRY) {
      return MachineContractRequestShapesCatalog.preflightRequestShapes();
    }
    if (ProtocolPostingRequestTopics.requiredEntryKind(operationId).isPresent()) {
      return MachineContractRequestShapesCatalog.postingRequestShapes(
          requiredPostingEntryKind(selectedOperation));
    }
    return switch (operationId) {
      case DECLARE_ACCOUNT, AMEND_ACCOUNT ->
          MachineContractRequestShapesCatalog.declareAccountRequestShapes();
      case RETIRE_ACCOUNT -> MachineContractRequestShapesCatalog.retireAccountRequestShapes();
      case DECLARE_TAX_REGISTRATION ->
          MachineContractRequestShapesCatalog.declareTaxRegistrationRequestShapes();
      case EXECUTE_PLAN -> MachineContractRequestShapesCatalog.ledgerPlanRequestShapes();
      default -> null;
    };
  }

  static @Nullable PostingRequestTemplateDescriptor postingRequestTemplateFor(
      @Nullable ProtocolOperation selectedOperation, @Nullable BookTemplateId bookTemplateId) {
    if (selectedOperation == null) {
      return null;
    }
    OperationId operationId = selectedOperation.id();
    if (operationId == OperationId.PREFLIGHT_ENTRY) {
      return requestTemplate(bookTemplateId);
    }
    if (!ProtocolPostingRequestTopics.requiredEntryKind(operationId).isPresent()) {
      return null;
    }
    return MachineContractPostEntryVariantSchemas.template(
        ProtocolPostingRequestTopics.scaffoldEntryKind(operationId), bookTemplateId);
  }

  static ContractTemplates.@Nullable DeclareAccountTemplateDescriptor declareAccountTemplateFor(
      @Nullable ProtocolOperation selectedOperation) {
    if (selectedOperation == null) {
      return null;
    }
    return switch (selectedOperation.id()) {
      case DECLARE_ACCOUNT, AMEND_ACCOUNT -> declareAccountTemplate();
      default -> null;
    };
  }

  static ContractTemplates.@Nullable RetireAccountTemplateDescriptor retireAccountTemplateFor(
      @Nullable ProtocolOperation selectedOperation) {
    return selectedOperation != null && selectedOperation.id() == OperationId.RETIRE_ACCOUNT
        ? retireAccountTemplate()
        : null;
  }

  static ContractTemplates.@Nullable DeclareTaxRegistrationTemplateDescriptor
      declareTaxRegistrationTemplateFor(@Nullable ProtocolOperation selectedOperation) {
    if (selectedOperation == null) {
      return null;
    }
    return selectedOperation.id() == OperationId.DECLARE_TAX_REGISTRATION
        ? declareTaxRegistrationTemplate()
        : null;
  }

  static ContractPlanTemplates.@Nullable LedgerPlanTemplateDescriptor ledgerPlanTemplateFor(
      @Nullable ProtocolOperation selectedOperation) {
    if (selectedOperation == null) {
      return null;
    }
    return selectedOperation.id() == OperationId.EXECUTE_PLAN ? planTemplate() : null;
  }

  static BookkeepingEntryKind requiredPostingEntryKind(ProtocolOperation selectedOperation) {
    return ProtocolPostingRequestTopics.requiredEntryKind(selectedOperation.id())
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Operation "
                        + selectedOperation.id().wireName()
                        + " does not own a typed posting template."));
  }
}
