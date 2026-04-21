package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Request and ledger-plan template descriptor namespace for discovery commands. */
public final class ContractTemplates {
  private ContractTemplates() {}

  /** Returns the descriptor record types owned by this namespace. */
  public static List<Class<?>> descriptorTypes() {
    return List.of(
        PostingRequestTemplateDescriptor.class,
        JournalLineTemplateDescriptor.class,
        ProvenanceTemplateDescriptor.class,
        ReversalTemplateDescriptor.class,
        LedgerPlanTemplateDescriptor.class,
        LedgerPlanStepTemplateDescriptor.class,
        DeclareAccountTemplateDescriptor.class,
        LedgerAssertionTemplateDescriptor.class);
  }

  /** Canonical request-template document for print-request-template. */
  public record PostingRequestTemplateDescriptor(
      String effectiveDate,
      List<JournalLineTemplateDescriptor> lines,
      ProvenanceTemplateDescriptor provenance,
      @Nullable ReversalTemplateDescriptor reversal) {
    /** Validates one posting-request template descriptor payload. */
    public PostingRequestTemplateDescriptor {
      effectiveDate = ContractDescriptorValidation.requireText(effectiveDate, "effectiveDate");
      lines = ContractDescriptorValidation.copyList(lines, "lines");
      provenance = ContractDescriptorValidation.requireValue(provenance, "provenance");
    }
  }

  /** Canonical request-template journal-line descriptor. */
  public record JournalLineTemplateDescriptor(
      String accountCode, String side, String currencyCode, String amount) {
    /** Validates one journal-line template descriptor payload. */
    public JournalLineTemplateDescriptor {
      accountCode = ContractDescriptorValidation.requireText(accountCode, "accountCode");
      side = ContractDescriptorValidation.requireText(side, "side");
      currencyCode = ContractDescriptorValidation.requireText(currencyCode, "currencyCode");
      amount = ContractDescriptorValidation.requireText(amount, "amount");
    }
  }

  /** Canonical request-template provenance descriptor. */
  public record ProvenanceTemplateDescriptor(
      String actorId,
      String actorType,
      String commandId,
      String idempotencyKey,
      String causationId,
      @Nullable String correlationId) {
    /** Validates one provenance template descriptor payload. */
    public ProvenanceTemplateDescriptor {
      actorId = ContractDescriptorValidation.requireText(actorId, "actorId");
      actorType = ContractDescriptorValidation.requireText(actorType, "actorType");
      commandId = ContractDescriptorValidation.requireText(commandId, "commandId");
      idempotencyKey = ContractDescriptorValidation.requireText(idempotencyKey, "idempotencyKey");
      causationId = ContractDescriptorValidation.requireText(causationId, "causationId");
      correlationId =
          ContractDescriptorValidation.requireOptionalText(correlationId, "correlationId");
    }
  }

  /** Canonical request-template reversal descriptor. */
  public record ReversalTemplateDescriptor(String priorPostingId, String reason) {
    /** Validates one reversal template descriptor payload. */
    public ReversalTemplateDescriptor {
      priorPostingId = ContractDescriptorValidation.requireText(priorPostingId, "priorPostingId");
      reason = ContractDescriptorValidation.requireText(reason, "reason");
    }
  }

  /** Canonical ledger-plan template document for print-plan-template. */
  public record LedgerPlanTemplateDescriptor(
      String planId, List<LedgerPlanStepTemplateDescriptor> steps) {
    /** Validates one ledger-plan template descriptor payload. */
    public LedgerPlanTemplateDescriptor {
      planId = ContractDescriptorValidation.requireText(planId, "planId");
      steps = ContractDescriptorValidation.copyList(steps, "steps");
    }
  }

  /** Canonical ledger-plan step template descriptor. */
  public record LedgerPlanStepTemplateDescriptor(
      String stepId,
      String kind,
      @Nullable PostingRequestTemplateDescriptor posting,
      @Nullable DeclareAccountTemplateDescriptor declareAccount,
      @Nullable LedgerAssertionTemplateDescriptor assertion,
      @Nullable String postingId) {
    /** Validates one ledger-plan step template descriptor payload. */
    public LedgerPlanStepTemplateDescriptor {
      stepId = ContractDescriptorValidation.requireText(stepId, "stepId");
      kind = ContractDescriptorValidation.requireText(kind, "kind");
      postingId = ContractDescriptorValidation.requireOptionalText(postingId, "postingId");
    }
  }

  /** Canonical declare-account template nested inside a ledger plan. */
  public record DeclareAccountTemplateDescriptor(
      String accountCode, String accountName, String normalBalance) {
    /** Validates one declare-account template descriptor payload. */
    public DeclareAccountTemplateDescriptor {
      accountCode = ContractDescriptorValidation.requireText(accountCode, "accountCode");
      accountName = ContractDescriptorValidation.requireText(accountName, "accountName");
      normalBalance = ContractDescriptorValidation.requireText(normalBalance, "normalBalance");
    }
  }

  /** Canonical assertion template nested inside a ledger plan. */
  public record LedgerAssertionTemplateDescriptor(
      String kind,
      String accountCode,
      @Nullable String effectiveDateFrom,
      @Nullable String effectiveDateTo,
      String currencyCode,
      String netAmount,
      String balanceSide,
      @Nullable String postingId) {
    /** Validates one ledger-assertion template descriptor payload. */
    public LedgerAssertionTemplateDescriptor {
      kind = ContractDescriptorValidation.requireText(kind, "kind");
      accountCode = ContractDescriptorValidation.requireText(accountCode, "accountCode");
      effectiveDateFrom =
          ContractDescriptorValidation.requireOptionalText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo =
          ContractDescriptorValidation.requireOptionalText(effectiveDateTo, "effectiveDateTo");
      currencyCode = ContractDescriptorValidation.requireText(currencyCode, "currencyCode");
      netAmount = ContractDescriptorValidation.requireText(netAmount, "netAmount");
      balanceSide = ContractDescriptorValidation.requireText(balanceSide, "balanceSide");
      postingId = ContractDescriptorValidation.requireOptionalText(postingId, "postingId");
    }
  }
}
