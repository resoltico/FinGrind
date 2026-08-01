package dev.erst.fingrind.contract.discovery;

import java.util.List;

/** Sealed inventory root for published request and ledger-plan template descriptors. */
public sealed interface TemplateDescriptorType
    permits ContractPostingRequestTemplates.PostingRequestTemplateDescriptor,
        ContractAttestationRegistryTemplates.EnrollKeyTemplateDescriptor,
        ContractAttestationRegistryTemplates.RolloverKeyTemplateDescriptor,
        ContractAttestationRegistryTemplates.RevokeKeyTemplateDescriptor,
        ContractAttestationRegistryTemplates.AlterPolicyTemplateDescriptor,
        ContractAttestationRegistryTemplates.PolicyRuleTemplateDescriptor,
        ContractAttestationRegistryTemplates.CapabilityGrantTemplateDescriptor,
        ContractAttestationRegistryTemplates.SystemWorkflowPolicyTemplateDescriptor,
        ContractAttestationReviewTemplates.AttestationReviewFileTemplateDescriptor,
        ContractAttestationReviewTemplates.CompromiseReviewTemplateDescriptor,
        ContractSettlementTemplates.TaxSelectionTemplateDescriptor,
        ContractSettlementTemplates.SettlementAdjunctTemplateDescriptor,
        InventoryReliefTemplateDescriptor,
        ForeignExchangeTemplateDescriptor,
        QuotedExchangeRateTemplateDescriptor,
        ContractTemplates.JournalLineTemplateDescriptor,
        ContractTemplates.OpeningBalanceTemplateDescriptor,
        ContractTemplates.AccountingEvidenceTemplateDescriptor,
        ContractTemplates.SourceDocumentTemplateDescriptor,
        ContractTemplates.ApprovalTemplateDescriptor,
        ContractTemplates.ProvenanceTemplateDescriptor,
        ContractReversalTemplates.ReversalTemplateDescriptor,
        ContractTemplates.DeclareTaxRegistrationTemplateDescriptor,
        ContractTemplates.DeclareTaxCodeTemplateDescriptor,
        ContractPlanTemplates.LedgerPlanTemplateDescriptor,
        ContractPlanTemplates.LedgerPlanStepTemplateDescriptor,
        ContractPlanTemplates.LedgerPlanQueryTemplateDescriptor,
        ContractTemplates.DeclareAccountTemplateDescriptor,
        ContractTemplates.RetireAccountTemplateDescriptor,
        ContractPlanTemplates.LedgerAssertionTemplateDescriptor,
        ContractFixedAssetTemplates.FixedAssetTemplateDescriptor,
        ContractFixedAssetTemplates.FixedAssetDepreciationScheduleTemplateDescriptor,
        ContractFinancingTemplates.FinancingTemplateDescriptor,
        ContractRealizedForeignExchangeTemplates.RealizedForeignExchangeTemplateDescriptor {
  /** Returns the published descriptor record types for request and ledger-plan templates. */
  static List<Class<?>> descriptorTypes() {
    return DescriptorNamespaceSupport.descriptorTypes(TemplateDescriptorType.class);
  }
}
