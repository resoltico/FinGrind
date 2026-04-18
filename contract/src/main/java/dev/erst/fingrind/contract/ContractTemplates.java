package dev.erst.fingrind.contract;

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
      @Nullable ReversalTemplateDescriptor reversal) {}

  /** Canonical request-template journal-line descriptor. */
  public record JournalLineTemplateDescriptor(
      String accountCode, String side, String currencyCode, String amount) {}

  /** Canonical request-template provenance descriptor. */
  public record ProvenanceTemplateDescriptor(
      String actorId,
      String actorType,
      String commandId,
      String idempotencyKey,
      String causationId,
      @Nullable String correlationId) {}

  /** Canonical request-template reversal descriptor. */
  public record ReversalTemplateDescriptor(String priorPostingId, String reason) {}

  /** Canonical ledger-plan template document for print-plan-template. */
  public record LedgerPlanTemplateDescriptor(
      String planId, List<LedgerPlanStepTemplateDescriptor> steps) {}

  /** Canonical ledger-plan step template descriptor. */
  public record LedgerPlanStepTemplateDescriptor(
      String stepId,
      String kind,
      @Nullable PostingRequestTemplateDescriptor posting,
      @Nullable DeclareAccountTemplateDescriptor declareAccount,
      @Nullable LedgerAssertionTemplateDescriptor assertion,
      @Nullable String postingId) {}

  /** Canonical declare-account template nested inside a ledger plan. */
  public record DeclareAccountTemplateDescriptor(
      String accountCode, String accountName, String normalBalance) {}

  /** Canonical assertion template nested inside a ledger plan. */
  public record LedgerAssertionTemplateDescriptor(
      String kind,
      String accountCode,
      @Nullable String effectiveDateFrom,
      @Nullable String effectiveDateTo,
      String currencyCode,
      String netAmount,
      String balanceSide,
      @Nullable String postingId) {}
}
