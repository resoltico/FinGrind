package dev.erst.fingrind.contract.discovery;

/** Sealed inventory root for the request-template descriptor namespace. */
public sealed interface TemplateDescriptorType
    permits ContractTemplates.PostingRequestTemplateDescriptor,
        ContractTemplates.JournalLineTemplateDescriptor,
        ContractTemplates.OpeningBalanceTemplateDescriptor,
        ContractTemplates.AccountingEvidenceTemplateDescriptor,
        ContractTemplates.SourceDocumentTemplateDescriptor,
        ContractTemplates.ApprovalTemplateDescriptor,
        ContractTemplates.ProvenanceTemplateDescriptor,
        ContractTemplates.ReversalTemplateDescriptor,
        ContractTemplates.LedgerPlanTemplateDescriptor,
        ContractTemplates.LedgerPlanStepTemplateDescriptor,
        ContractTemplates.OpenBookTemplateDescriptor,
        ContractTemplates.LedgerPlanQueryTemplateDescriptor,
        ContractTemplates.DeclareAccountTemplateDescriptor,
        ContractTemplates.LedgerAssertionTemplateDescriptor {}
