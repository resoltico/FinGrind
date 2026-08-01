package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplates.PostingRequestTemplateDescriptor;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.core.BalanceSide;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Ledger-plan template descriptor namespace for discovery commands. */
public interface ContractPlanTemplates {
  /** Canonical ledger-plan template document for print-plan-template. */
  public record LedgerPlanTemplateDescriptor(
      String planId, List<LedgerPlanStepTemplateDescriptor> steps)
      implements TemplateDescriptorType {
    /** Validates one ledger-plan template descriptor payload. */
    public LedgerPlanTemplateDescriptor {
      planId = ContractDescriptorValidation.requireText(planId, "planId");
      steps = ContractDescriptorValidation.copyList(steps, "steps");
      if (steps.isEmpty()) {
        throw new IllegalArgumentException("steps must not be empty.");
      }
    }
  }

  /** Canonical ledger-plan step template descriptor. */
  public record LedgerPlanStepTemplateDescriptor(
      String stepId,
      LedgerStepKind kind,
      @Nullable PostingRequestTemplateDescriptor posting,
      ContractTemplates.@Nullable DeclareAccountTemplateDescriptor declareAccount,
      ContractTemplates.@Nullable DeclareTaxRegistrationTemplateDescriptor declareTaxRegistration,
      ContractPlanTemplates.@Nullable LedgerPlanQueryTemplateDescriptor query,
      ContractPlanTemplates.@Nullable LedgerAssertionTemplateDescriptor assertion,
      @Nullable String postingId)
      implements TemplateDescriptorType {
    /** Validates one ledger-plan step template descriptor payload. */
    public LedgerPlanStepTemplateDescriptor(
        String stepId,
        LedgerStepKind kind,
        @Nullable PostingRequestTemplateDescriptor posting,
        ContractTemplates.@Nullable DeclareAccountTemplateDescriptor declareAccount,
        ContractTemplates.@Nullable DeclareTaxRegistrationTemplateDescriptor declareTaxRegistration,
        ContractPlanTemplates.@Nullable LedgerPlanQueryTemplateDescriptor query,
        ContractPlanTemplates.@Nullable LedgerAssertionTemplateDescriptor assertion,
        @Nullable String postingId) {
      this.stepId = ContractDescriptorValidation.requireText(stepId, "stepId");
      this.kind = ContractDescriptorValidation.requireValue(kind, "kind");
      this.posting = ContractDescriptorValidation.requireOptionalValue(posting, "posting");
      this.declareAccount =
          ContractDescriptorValidation.requireOptionalValue(declareAccount, "declareAccount");
      this.declareTaxRegistration =
          ContractDescriptorValidation.requireOptionalValue(
              declareTaxRegistration, "declareTaxRegistration");
      this.query = ContractDescriptorValidation.requireOptionalValue(query, "query");
      this.assertion = ContractDescriptorValidation.requireOptionalValue(assertion, "assertion");
      this.postingId = ContractDescriptorValidation.requireOptionalText(postingId, "postingId");
      ContractTemplateShapeValidator.validateStepShape(
          this.kind,
          this.posting,
          this.declareAccount,
          this.declareTaxRegistration,
          this.query,
          this.assertion,
          this.postingId);
    }
  }

  /** Canonical ledger-plan query template nested inside query-oriented steps. */
  public record LedgerPlanQueryTemplateDescriptor(
      @Nullable String accountCode,
      @Nullable String effectiveDateFrom,
      @Nullable String effectiveDateTo,
      @Nullable Integer limit,
      @Nullable String cursor)
      implements TemplateDescriptorType {
    /** Validates one ledger-plan query template descriptor payload. */
    public LedgerPlanQueryTemplateDescriptor {
      accountCode = ContractDescriptorValidation.requireOptionalText(accountCode, "accountCode");
      effectiveDateFrom =
          ContractDescriptorValidation.requireOptionalText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo =
          ContractDescriptorValidation.requireOptionalText(effectiveDateTo, "effectiveDateTo");
      if (limit != null
          && (limit < ProtocolInteractionLimits.PAGE_LIMIT_MIN
              || limit > ProtocolInteractionLimits.PAGE_LIMIT_MAX)) {
        throw new IllegalArgumentException(
            "limit must be between "
                + ProtocolInteractionLimits.PAGE_LIMIT_MIN
                + " and "
                + ProtocolInteractionLimits.PAGE_LIMIT_MAX
                + ".");
      }
      cursor = ContractDescriptorValidation.requireOptionalText(cursor, "cursor");
    }
  }

  /** Canonical assertion template nested inside a ledger plan. */
  public record LedgerAssertionTemplateDescriptor(
      LedgerAssertionKind kind,
      @Nullable String accountCode,
      @Nullable String effectiveDateFrom,
      @Nullable String effectiveDateTo,
      @Nullable MonetaryAmount netAmount,
      @Nullable BalanceSide balanceSide,
      @Nullable String postingId)
      implements TemplateDescriptorType {
    /** Validates one ledger-assertion template descriptor payload. */
    public LedgerAssertionTemplateDescriptor {
      kind = ContractDescriptorValidation.requireValue(kind, "kind");
      accountCode = ContractDescriptorValidation.requireOptionalText(accountCode, "accountCode");
      effectiveDateFrom =
          ContractDescriptorValidation.requireOptionalText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo =
          ContractDescriptorValidation.requireOptionalText(effectiveDateTo, "effectiveDateTo");
      netAmount = ContractDescriptorValidation.requireOptionalValue(netAmount, "netAmount");
      postingId = ContractDescriptorValidation.requireOptionalText(postingId, "postingId");
      ContractTemplateShapeValidator.validateAssertionShape(
          kind, accountCode, effectiveDateFrom, effectiveDateTo, netAmount, balanceSide, postingId);
    }
  }
}
