package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import org.jspecify.annotations.Nullable;

/** Optional tax-scaffold decoration shared by entry templates that admit a tax selection. */
final class MachineContractPostEntryTaxTemplateSupport {
  private MachineContractPostEntryTaxTemplateSupport() {}

  static ContractTemplates.PostingRequestTemplateDescriptor withOptionalTaxSelection(
      ContractTemplates.PostingRequestTemplateDescriptor template) {
    ContractSettlementTemplates.TaxSelectionTemplateDescriptor tax =
        taxSelectionTemplate(template.entryKind());
    if (tax == null) {
      return template;
    }
    return new ContractTemplates.PostingRequestTemplateDescriptor(
        template.entryKind(),
        template.effectiveDate(),
        template.cashAccountCode(),
        template.receivableAccountCode(),
        template.payableAccountCode(),
        template.revenueAccountCode(),
        template.inventoryAccountCode(),
        template.expenseAccountCode(),
        template.writeDownLossAccountCode(),
        template.shrinkageLossAccountCode(),
        template.countGainAccountCode(),
        template.equityAccountCode(),
        template.amount(),
        template.quantity(),
        template.unitCost(),
        template.inventoryRelief(),
        template.settlementAdjunct(),
        template.foreignExchange(),
        tax,
        template.lines(),
        template.openingBalances(),
        template.evidence(),
        template.provenance(),
        template.reversal(),
        template.accrualCutoffId(),
        template.prepaymentAssetAccountCode(),
        template.deferredRevenueAccountCode(),
        template.accruedExpenseLiabilityAccountCode(),
        template.recognitionInterval(),
        template.latvianMonthlyPayroll(),
        template.latvianPayrollSettlement(),
        template.fixedAsset(),
        template.financing(),
        template.realizedForeignExchange());
  }

  private static ContractSettlementTemplates.@Nullable TaxSelectionTemplateDescriptor
      taxSelectionTemplate(BookkeepingEntryKind entryKind) {
    if (!ProtocolCatalog.domain()
        .requestSurface()
        .bookkeepingEntryKind(entryKind)
        .optionalTopLevelFields()
        .contains(ProtocolPostEntryFields.TopLevel.TAX)) {
      return null;
    }
    return new ContractSettlementTemplates.TaxSelectionTemplateDescriptor(
        ScaffoldPlaceholders.TAX_REGISTRATION_ID, taxCodeScaffoldValue(entryKind));
  }

  static String taxCodeScaffoldValue(BookkeepingEntryKind entryKind) {
    return switch (entryKind) {
      case SALE_SETTLED, SALE_ON_CREDIT -> ScaffoldPlaceholders.OUTPUT_TAX_CODE;
      case PURCHASE_SETTLED,
          PURCHASE_ON_CREDIT,
          INVENTORY_CAPITALIZATION_SETTLED,
          INVENTORY_CAPITALIZATION_ON_CREDIT,
          EXPENSE_SETTLED,
          EXPENSE_ON_CREDIT ->
          ScaffoldPlaceholders.INPUT_TAX_CODE;
      default ->
          throw new IllegalStateException(
              "No tax-selector scaffold policy is defined for " + entryKind + ".");
    };
  }
}
