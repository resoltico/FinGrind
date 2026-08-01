package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import org.jspecify.annotations.Nullable;

/** Request scaffold owned by the deliberately narrow Latvian monthly-payroll context. */
final class MachineContractLatvianPayrollPostEntryVariantTemplates {
  private MachineContractLatvianPayrollPostEntryVariantTemplates() {}

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor monthlyPayrollTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return new ContractPostingRequestTemplates.PostingRequestTemplateDescriptor(
        BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL,
        "2026-01-31",
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
        MachineContractPostEntryVariantTemplates.evidenceTemplate(
            BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL),
        MachineContractPostEntryVariantTemplates.provenanceTemplate(),
        null,
        null,
        null,
        null,
        null,
        null,
        new ContractLatvianPayrollTemplates.MonthlyPayrollTemplateDescriptor(
            "payroll-lv-2026-01-employee-001",
            "employee-001",
            "2026-01",
            true,
            0,
            "wage-expense",
            "employer-social-expense",
            "net-wages-payable",
            "employee-social-payable",
            "employer-social-payable",
            "personal-income-tax-payable",
            new MonetaryAmount("EUR", "200000")),
        null,
        null,
        null,
        null);
  }

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor netWageSettlementTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return settlementTemplate(BookkeepingEntryKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT);
  }

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor stateRemittanceTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return settlementTemplate(BookkeepingEntryKind.LATVIAN_PAYROLL_STATE_REMITTANCE);
  }

  private static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor
      settlementTemplate(BookkeepingEntryKind entryKind) {
    return new ContractPostingRequestTemplates.PostingRequestTemplateDescriptor(
        entryKind,
        "2026-02-23",
        "cash",
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
        MachineContractPostEntryVariantTemplates.evidenceTemplate(entryKind),
        MachineContractPostEntryVariantTemplates.provenanceTemplate(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        new ContractLatvianPayrollTemplates.PayrollSettlementTemplateDescriptor(
            "payroll-lv-2026-01-employee-001"),
        null,
        null,
        null);
  }
}
