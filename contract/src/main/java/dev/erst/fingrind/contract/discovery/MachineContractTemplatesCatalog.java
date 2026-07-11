package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import dev.erst.fingrind.contract.protocol.ProtocolPostingRequestTopics;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
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

  static ContractTemplates.PostingRequestTemplateDescriptor requestTemplate(
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
        FinancialPositionLineClassification.CURRENT_ASSET,
        null,
        CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT);
  }

  static ContractTemplates.DeclareTaxRegistrationTemplateDescriptor
      declareTaxRegistrationTemplate() {
    return new ContractTemplates.DeclareTaxRegistrationTemplateDescriptor(
        "replace-before-commit-tax-registration-id",
        "Replace Before Commit Tax Registration",
        new dev.erst.fingrind.contract.tax.TaxJurisdiction("<ISO-3166-alpha-2>"),
        "replace-before-commit-registration-number",
        "tax-payable-vat",
        "tax-recoverable-vat",
        dev.erst.fingrind.contract.tax.TaxObligationFrequency.MONTHLY,
        20,
        List.of(
            new ContractTemplates.DeclareTaxCodeTemplateDescriptor(
                "replace-before-commit-output-tax-code",
                "Replace Before Commit Output Tax Code",
                0,
                dev.erst.fingrind.contract.tax.TaxInclusionMode.EXCLUSIVE,
                dev.erst.fingrind.contract.tax.TaxApplicationKind.OUTPUT_SALE),
            new ContractTemplates.DeclareTaxCodeTemplateDescriptor(
                "replace-before-commit-input-tax-code",
                "Replace Before Commit Input Tax Code",
                0,
                dev.erst.fingrind.contract.tax.TaxInclusionMode.EXCLUSIVE,
                dev.erst.fingrind.contract.tax.TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE)));
  }

  static ContractPlanTemplates.LedgerPlanTemplateDescriptor planTemplate() {
    return new ContractPlanTemplates.LedgerPlanTemplateDescriptor(
        "plan-1",
        List.of(
            new ContractPlanTemplates.LedgerPlanStepTemplateDescriptor(
                "ensure-book",
                LedgerStepKind.ENSURE_BOOK,
                new ContractPlanTemplates.EnsureBookTemplateDescriptor(
                    "Acme Studio", "OWNER_MANAGED_SERVICE", "CASH", null, "EUR", "01-01"),
                null,
                null,
                null,
                null,
                null),
            new ContractPlanTemplates.LedgerPlanStepTemplateDescriptor(
                OperationId.RECORD_SALE_SETTLED.wireName(),
                LedgerStepKind.RECORD_SALE_SETTLED,
                null,
                MachineContractPostEntryVariantSchemas.template(BookkeepingEntryKind.SALE_SETTLED),
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
    OperationId operationId = selectedOperation.id();
    if (operationId == OperationId.PREFLIGHT_ENTRY) {
      return MachineContractRequestShapesCatalog.preflightRequestShapes();
    }
    if (ProtocolPostingRequestTopics.requiredEntryKind(operationId).isPresent()) {
      return MachineContractRequestShapesCatalog.postingRequestShapes(
          requiredPostingEntryKind(selectedOperation));
    }
    return switch (operationId) {
      case DECLARE_ACCOUNT -> MachineContractRequestShapesCatalog.declareAccountRequestShapes();
      case DECLARE_TAX_REGISTRATION ->
          MachineContractRequestShapesCatalog.declareTaxRegistrationRequestShapes();
      case EXECUTE_PLAN -> MachineContractRequestShapesCatalog.ledgerPlanRequestShapes();
      default -> null;
    };
  }

  static ContractTemplates.@Nullable PostingRequestTemplateDescriptor postingRequestTemplateFor(
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
    return selectedOperation.id() == OperationId.DECLARE_ACCOUNT ? declareAccountTemplate() : null;
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
