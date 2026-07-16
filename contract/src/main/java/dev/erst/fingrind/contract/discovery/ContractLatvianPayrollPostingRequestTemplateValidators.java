package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.discovery.ContractLatvianPayrollTemplates.MonthlyPayrollTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractLatvianPayrollTemplates.PayrollSettlementTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplateValidators.PostingTemplateFields;
import dev.erst.fingrind.contract.discovery.ContractReversalTemplates.ReversalTemplateDescriptor;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Template validation owned by the Latvian monthly-payroll context. */
final class ContractLatvianPayrollPostingRequestTemplateValidators {
  private ContractLatvianPayrollPostingRequestTemplateValidators() {}

  static void validateMonthlyPayrollTemplate(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    MonthlyPayrollTemplateDescriptor payroll = fields.latvianMonthlyPayroll();
    if (payroll == null) {
      throw new IllegalArgumentException(
          "latvianMonthlyPayroll must be present for LATVIAN_MONTHLY_PAYROLL.");
    }
    ContractPostingRequestTemplateFieldSupport.forbidTextFields(
        fields,
        List.of(
            ContractPostingRequestTemplateFieldSupport.TemplateTextField.CASH,
            ContractPostingRequestTemplateFieldSupport.TemplateTextField.RECEIVABLE,
            ContractPostingRequestTemplateFieldSupport.TemplateTextField.PAYABLE,
            ContractPostingRequestTemplateFieldSupport.TemplateTextField.REVENUE,
            ContractPostingRequestTemplateFieldSupport.TemplateTextField.INVENTORY,
            ContractPostingRequestTemplateFieldSupport.TemplateTextField.EXPENSE,
            ContractPostingRequestTemplateFieldSupport.TemplateTextField.WRITE_DOWN_LOSS,
            ContractPostingRequestTemplateFieldSupport.TemplateTextField.SHRINKAGE_LOSS,
            ContractPostingRequestTemplateFieldSupport.TemplateTextField.COUNT_GAIN,
            ContractPostingRequestTemplateFieldSupport.TemplateTextField.EQUITY));
    ContractPostingTemplateScalarFieldRules.forbidAmount(fields.amount(), "latvianMonthlyPayroll");
    ContractPostingTemplateScalarFieldRules.forbidQuantity(
        fields.quantity(), "latvianMonthlyPayroll");
    ContractPostingTemplateScalarFieldRules.forbidUnitCost(
        fields.unitCost(), "latvianMonthlyPayroll");
    ContractPostingRequestTemplateFieldSupport.validateInventoryRelief(
        fields.inventoryRelief(),
        "latvianMonthlyPayroll",
        ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy.FORBIDDEN);
    ContractPostingRequestTemplateFieldSupport.requireNoSettlementAdjunct(
        fields, "latvianMonthlyPayroll");
    ContractPostingTemplateFieldRules.forbidForeignExchange(
        fields.foreignExchange(), "latvianMonthlyPayroll");
    ContractPostingTemplateFieldRules.forbidTax(fields.tax(), "latvianMonthlyPayroll");
    ContractPostingRequestTemplateFieldSupport.forbidLinesAndOpeningBalances(fields);
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  static void validateSettlementTemplate(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    PayrollSettlementTemplateDescriptor settlement = fields.latvianPayrollSettlement();
    if (settlement == null) {
      throw new IllegalArgumentException(
          "latvianPayrollSettlement must be present for Latvian payroll settlements.");
    }
    ContractPostingRequestTemplateFieldSupport.requireTextFields(
        fields, List.of(ContractPostingRequestTemplateFieldSupport.TemplateTextField.CASH));
    ContractPostingRequestTemplateFieldSupport.forbidTextFields(
        fields,
        List.of(
            ContractPostingRequestTemplateFieldSupport.TemplateTextField.RECEIVABLE,
            ContractPostingRequestTemplateFieldSupport.TemplateTextField.PAYABLE,
            ContractPostingRequestTemplateFieldSupport.TemplateTextField.REVENUE,
            ContractPostingRequestTemplateFieldSupport.TemplateTextField.INVENTORY,
            ContractPostingRequestTemplateFieldSupport.TemplateTextField.EXPENSE,
            ContractPostingRequestTemplateFieldSupport.TemplateTextField.WRITE_DOWN_LOSS,
            ContractPostingRequestTemplateFieldSupport.TemplateTextField.SHRINKAGE_LOSS,
            ContractPostingRequestTemplateFieldSupport.TemplateTextField.COUNT_GAIN,
            ContractPostingRequestTemplateFieldSupport.TemplateTextField.EQUITY));
    ContractPostingRequestTemplateFieldSupport.forbidInventoryMaintenanceFields(
        fields, "latvianPayrollSettlement");
    ContractPostingTemplateScalarFieldRules.forbidAmount(
        fields.amount(), "latvianPayrollSettlement");
    ContractPostingTemplateScalarFieldRules.forbidQuantity(
        fields.quantity(), "latvianPayrollSettlement");
    ContractPostingTemplateScalarFieldRules.forbidUnitCost(
        fields.unitCost(), "latvianPayrollSettlement");
    ContractPostingRequestTemplateFieldSupport.validateInventoryRelief(
        fields.inventoryRelief(),
        "latvianPayrollSettlement",
        ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy.FORBIDDEN);
    ContractPostingRequestTemplateFieldSupport.requireNoSettlementAdjunct(
        fields, "latvianPayrollSettlement");
    ContractPostingTemplateFieldRules.forbidForeignExchange(
        fields.foreignExchange(), "latvianPayrollSettlement");
    ContractPostingTemplateFieldRules.forbidTax(fields.tax(), "latvianPayrollSettlement");
    ContractPostingRequestTemplateFieldSupport.forbidLinesAndOpeningBalances(fields);
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }
}
