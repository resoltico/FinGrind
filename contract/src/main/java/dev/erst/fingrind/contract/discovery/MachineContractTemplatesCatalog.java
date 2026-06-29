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

  static ContractTemplates.PostingRequestTemplateDescriptor requestTemplate() {
    return MachineContractPostEntryVariantSchemas.template(BookkeepingEntryKind.SALE);
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
        "vat-lv",
        "Latvia VAT",
        new dev.erst.fingrind.contract.tax.TaxJurisdiction("LV"),
        "LV40000000000",
        "tax-payable-vat",
        "tax-recoverable-vat",
        dev.erst.fingrind.contract.tax.TaxObligationFrequency.MONTHLY,
        20,
        List.of(
            new ContractTemplates.DeclareTaxCodeTemplateDescriptor(
                "vat-standard-sale",
                "VAT Standard Sale",
                210_000,
                dev.erst.fingrind.contract.tax.TaxInclusionMode.EXCLUSIVE,
                dev.erst.fingrind.contract.tax.TaxApplicationKind.OUTPUT_SALE),
            new ContractTemplates.DeclareTaxCodeTemplateDescriptor(
                "vat-standard-expense",
                "VAT Standard Expense",
                210_000,
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
                    "Acme Studio", "EUR", "01-01"),
                null,
                null,
                null,
                null,
                null),
            new ContractPlanTemplates.LedgerPlanStepTemplateDescriptor(
                OperationId.RECORD_SALE.wireName(),
                LedgerStepKind.RECORD_SALE,
                null,
                MachineContractPostEntryVariantSchemas.template(BookkeepingEntryKind.SALE),
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
    return switch (selectedOperation.id()) {
      case POST_ENTRY ->
          new ContractRequestShapes.RequestShapesDescriptor(
              MachineContractRequestSchemas.JSON_SCHEMA_DIALECT,
              MachineContractPostEntrySchemas.descriptor(
                  ProtocolPostingRequestTopics.requiredEntryKind(selectedOperation.id())
                      .orElseThrow()),
              null,
              null,
              null);
      case PREFLIGHT_ENTRY ->
          new ContractRequestShapes.RequestShapesDescriptor(
              MachineContractRequestSchemas.JSON_SCHEMA_DIALECT,
              MachineContractPostEntrySchemas.descriptor(),
              null,
              null,
              null);
      case RECORD_SALE,
          RECORD_EXPENSE,
          RECORD_OWNER_CONTRIBUTION,
          RECORD_OWNER_WITHDRAWAL,
          RECORD_OPENING_POSITION,
          RECORD_REVERSAL ->
          new ContractRequestShapes.RequestShapesDescriptor(
              MachineContractRequestSchemas.JSON_SCHEMA_DIALECT,
              MachineContractPostEntrySchemas.descriptor(
                  requiredPostingEntryKind(selectedOperation)),
              null,
              null,
              null);
      case DECLARE_ACCOUNT ->
          new ContractRequestShapes.RequestShapesDescriptor(
              MachineContractRequestSchemas.JSON_SCHEMA_DIALECT,
              null,
              MachineContractDeclareAccountSchemas.descriptor(),
              null,
              null);
      case DECLARE_TAX_REGISTRATION ->
          new ContractRequestShapes.RequestShapesDescriptor(
              MachineContractRequestSchemas.JSON_SCHEMA_DIALECT,
              null,
              null,
              MachineContractDeclareTaxRegistrationSchemas.descriptor(),
              null);
      case EXECUTE_PLAN ->
          new ContractRequestShapes.RequestShapesDescriptor(
              MachineContractRequestSchemas.JSON_SCHEMA_DIALECT,
              null,
              null,
              null,
              MachineContractLedgerPlanSchemas.descriptor(
                  planTemplate().canonicalPostingTemplate().entryKind()));
      default -> null;
    };
  }

  static ContractTemplates.@Nullable PostingRequestTemplateDescriptor postingRequestTemplateFor(
      @Nullable ProtocolOperation selectedOperation) {
    if (selectedOperation == null) {
      return null;
    }
    return switch (selectedOperation.id()) {
      case POST_ENTRY ->
          MachineContractPostEntryVariantSchemas.template(
              ProtocolPostingRequestTopics.scaffoldEntryKind(selectedOperation.id()));
      case PREFLIGHT_ENTRY -> requestTemplate();
      case RECORD_SALE,
          RECORD_EXPENSE,
          RECORD_OWNER_CONTRIBUTION,
          RECORD_OWNER_WITHDRAWAL,
          RECORD_OPENING_POSITION,
          RECORD_REVERSAL ->
          MachineContractPostEntryVariantSchemas.template(
              ProtocolPostingRequestTopics.scaffoldEntryKind(selectedOperation.id()));
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
                        + " does not own one typed posting template."));
  }
}
