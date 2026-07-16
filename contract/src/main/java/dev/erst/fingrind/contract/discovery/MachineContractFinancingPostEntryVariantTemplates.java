package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ContractFinancingTemplates.FinancingTemplateDescriptor;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import org.jspecify.annotations.Nullable;

/** Request scaffolds owned by the Financing context. */
final class MachineContractFinancingPostEntryVariantTemplates {
  private static final String SAMPLE_EFFECTIVE_DATE = "2026-01-15";
  private static final String SAMPLE_ARRANGEMENT_ID = "term-loan-001";

  private MachineContractFinancingPostEntryVariantTemplates() {}

  static ContractTemplates.PostingRequestTemplateDescriptor borrowingTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return template(
        BookkeepingEntryKind.FINANCING_BORROWING,
        "cash",
        new FinancingTemplateDescriptor(
            SAMPLE_ARRANGEMENT_ID,
            "term-loan-principal",
            "term-loan-interest-payable",
            null,
            new MonetaryAmount("EUR", "1000000"),
            null));
  }

  static ContractTemplates.PostingRequestTemplateDescriptor principalRepaymentTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return template(
        BookkeepingEntryKind.FINANCING_PRINCIPAL_REPAYMENT,
        "cash",
        new FinancingTemplateDescriptor(
            SAMPLE_ARRANGEMENT_ID, null, null, null, new MonetaryAmount("EUR", "100000"), null));
  }

  static ContractTemplates.PostingRequestTemplateDescriptor interestAccrualTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return template(
        BookkeepingEntryKind.FINANCING_INTEREST_ACCRUAL,
        null,
        new FinancingTemplateDescriptor(
            SAMPLE_ARRANGEMENT_ID,
            null,
            null,
            "interest-expense",
            null,
            new MonetaryAmount("EUR", "12000")));
  }

  static ContractTemplates.PostingRequestTemplateDescriptor interestPaymentTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return template(
        BookkeepingEntryKind.FINANCING_INTEREST_PAYMENT,
        "cash",
        new FinancingTemplateDescriptor(
            SAMPLE_ARRANGEMENT_ID, null, null, null, null, new MonetaryAmount("EUR", "12000")));
  }

  private static ContractTemplates.PostingRequestTemplateDescriptor template(
      BookkeepingEntryKind entryKind,
      @Nullable String cashAccountCode,
      FinancingTemplateDescriptor financing) {
    return new ContractTemplates.PostingRequestTemplateDescriptor(
        entryKind,
        SAMPLE_EFFECTIVE_DATE,
        cashAccountCode,
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
        null,
        null,
        financing,
        null);
  }
}
