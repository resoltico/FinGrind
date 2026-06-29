package dev.erst.fingrind.contract.discovery;

import java.util.List;

/** Sealed inventory root for published request and ledger-plan template descriptors. */
public sealed interface TemplateDescriptorType
    permits ContractTemplates.PostingRequestTemplateDescriptor,
        ContractTemplates.TaxSelectionTemplateDescriptor,
        ForeignExchangeTemplateDescriptor,
        QuotedExchangeRateTemplateDescriptor,
        ContractTemplates.JournalLineTemplateDescriptor,
        ContractTemplates.OpeningBalanceTemplateDescriptor,
        ContractTemplates.AccountingEvidenceTemplateDescriptor,
        ContractTemplates.SourceDocumentTemplateDescriptor,
        ContractTemplates.ApprovalTemplateDescriptor,
        ContractTemplates.ProvenanceTemplateDescriptor,
        ContractTemplates.ReversalTemplateDescriptor,
        ContractTemplates.DeclareTaxRegistrationTemplateDescriptor,
        ContractTemplates.DeclareTaxCodeTemplateDescriptor,
        ContractPlanTemplates.LedgerPlanTemplateDescriptor,
        ContractPlanTemplates.LedgerPlanStepTemplateDescriptor,
        ContractPlanTemplates.EnsureBookTemplateDescriptor,
        ContractPlanTemplates.LedgerPlanQueryTemplateDescriptor,
        ContractTemplates.DeclareAccountTemplateDescriptor,
        ContractPlanTemplates.LedgerAssertionTemplateDescriptor {
  /** Returns the published descriptor record types for request and ledger-plan templates. */
  static List<Class<?>> descriptorTypes() {
    return DescriptorNamespaceSupport.descriptorTypes(TemplateDescriptorType.class);
  }
}
