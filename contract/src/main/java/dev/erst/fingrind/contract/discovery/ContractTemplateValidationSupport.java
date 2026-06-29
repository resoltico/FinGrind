package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.discovery.ContractTemplates.ApprovalTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.SourceDocumentTemplateDescriptor;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.ApprovalDecision;
import dev.erst.fingrind.core.ApprovalId;
import dev.erst.fingrind.core.ApprovalReference;
import dev.erst.fingrind.core.ApprovalType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** Shared validation and normalization owner for discovery template descriptors. */
final class ContractTemplateValidationSupport {
  private ContractTemplateValidationSupport() {}

  static void validateLiveTextUnlessPlaceholder(String value, Consumer<String> validator) {
    Objects.requireNonNull(value, "value");
    Consumer<String> requiredValidator = Objects.requireNonNull(validator, "validator");
    if (!ScaffoldPlaceholders.isReserved(value)) {
      requiredValidator.accept(value);
    }
  }

  static void validateLiveOptionalTextUnlessPlaceholder(
      @Nullable String value, Consumer<String> validator) {
    Objects.requireNonNull(validator, "validator");
    if (value != null) {
      validateLiveTextUnlessPlaceholder(value, validator);
    }
  }

  static ProvenanceTemplateValues validateProvenanceTemplate(
      String actorId,
      ActorType actorType,
      String commandId,
      String idempotencyKey,
      String causationId,
      @Nullable String correlationId) {
    String validatedActorId = ContractDescriptorValidation.requireText(actorId, "actorId");
    ActorType validatedActorType =
        ContractDescriptorValidation.requireValue(actorType, "actorType");
    String validatedCommandId = ContractDescriptorValidation.requireText(commandId, "commandId");
    String validatedIdempotencyKey =
        ContractDescriptorValidation.requireText(idempotencyKey, "idempotencyKey");
    String validatedCausationId =
        ContractDescriptorValidation.requireText(causationId, "causationId");
    @Nullable String validatedCorrelationId =
        ContractDescriptorValidation.requireOptionalText(correlationId, "correlationId");

    validateLiveTextUnlessPlaceholder(validatedActorId, ActorId::new);
    validateLiveTextUnlessPlaceholder(validatedCommandId, CommandId::new);
    validateLiveTextUnlessPlaceholder(validatedIdempotencyKey, IdempotencyKey::new);
    validateLiveTextUnlessPlaceholder(validatedCausationId, CausationId::new);
    validateLiveOptionalTextUnlessPlaceholder(validatedCorrelationId, CorrelationId::new);
    if (!containsPlaceholderProvenance(
        validatedActorId,
        validatedCommandId,
        validatedIdempotencyKey,
        validatedCausationId,
        validatedCorrelationId)) {
      new RequestProvenance(
          new ActorId(validatedActorId),
          validatedActorType,
          new CommandId(validatedCommandId),
          new IdempotencyKey(validatedIdempotencyKey),
          new CausationId(validatedCausationId),
          Optional.ofNullable(validatedCorrelationId).map(CorrelationId::new));
    }
    return new ProvenanceTemplateValues(
        validatedActorId,
        validatedActorType,
        validatedCommandId,
        validatedIdempotencyKey,
        validatedCausationId,
        validatedCorrelationId);
  }

  static AccountingEvidenceTemplateValues validateAccountingEvidenceTemplate(
      List<SourceDocumentTemplateDescriptor> sourceDocuments,
      List<ApprovalTemplateDescriptor> approvals) {
    List<SourceDocumentTemplateDescriptor> validatedSourceDocuments =
        ContractDescriptorValidation.copyList(sourceDocuments, "sourceDocuments");
    List<ApprovalTemplateDescriptor> validatedApprovals =
        ContractDescriptorValidation.copyList(approvals, "approvals");
    if (validatedSourceDocuments.isEmpty()) {
      throw new IllegalArgumentException(
          "Accounting evidence must contain at least one source document.");
    }
    if (!containsPlaceholderEvidence(validatedSourceDocuments, validatedApprovals)) {
      new AccountingEvidence(
          validatedSourceDocuments.stream()
              .map(ContractTemplateValidationSupport::toSourceDocumentReference)
              .toList(),
          validatedApprovals.stream()
              .map(ContractTemplateValidationSupport::toApprovalReference)
              .toList());
    }
    return new AccountingEvidenceTemplateValues(validatedSourceDocuments, validatedApprovals);
  }

  static SourceDocumentTemplateValues validateSourceDocumentTemplate(
      String sourceDocumentId, String sourceDocumentType, String documentDate) {
    String validatedSourceDocumentId =
        ContractDescriptorValidation.requireText(sourceDocumentId, "sourceDocumentId");
    String validatedSourceDocumentType =
        ContractDescriptorValidation.requireText(sourceDocumentType, "sourceDocumentType");
    String validatedDocumentDate =
        ContractDescriptorValidation.requireText(documentDate, "documentDate");

    validateLiveTextUnlessPlaceholder(validatedSourceDocumentId, SourceDocumentId::new);
    validateLiveTextUnlessPlaceholder(validatedSourceDocumentType, SourceDocumentType::new);
    validateLiveTextUnlessPlaceholder(validatedDocumentDate, value -> LocalDate.parse(value));
    return new SourceDocumentTemplateValues(
        validatedSourceDocumentId, validatedSourceDocumentType, validatedDocumentDate);
  }

  static ApprovalTemplateValues validateApprovalTemplate(
      String approvalId,
      String approvalType,
      String approverId,
      ActorType approverType,
      ApprovalDecision decision,
      String approvedAt) {
    String validatedApprovalId = ContractDescriptorValidation.requireText(approvalId, "approvalId");
    String validatedApprovalType =
        ContractDescriptorValidation.requireText(approvalType, "approvalType");
    String validatedApproverId = ContractDescriptorValidation.requireText(approverId, "approverId");
    ActorType validatedApproverType =
        ContractDescriptorValidation.requireValue(approverType, "approverType");
    ApprovalDecision validatedDecision =
        ContractDescriptorValidation.requireValue(decision, "decision");
    String validatedApprovedAt = ContractDescriptorValidation.requireText(approvedAt, "approvedAt");

    validateLiveTextUnlessPlaceholder(validatedApprovalId, ApprovalId::new);
    validateLiveTextUnlessPlaceholder(validatedApprovalType, ApprovalType::new);
    validateLiveTextUnlessPlaceholder(validatedApproverId, ActorId::new);
    validateLiveTextUnlessPlaceholder(validatedApprovedAt, value -> Instant.parse(value));
    return new ApprovalTemplateValues(
        validatedApprovalId,
        validatedApprovalType,
        validatedApproverId,
        validatedApproverType,
        validatedDecision,
        validatedApprovedAt);
  }

  static boolean containsPlaceholderEvidence(
      List<SourceDocumentTemplateDescriptor> sourceDocuments,
      List<ApprovalTemplateDescriptor> approvals) {
    List<SourceDocumentTemplateDescriptor> validatedSourceDocuments =
        ContractDescriptorValidation.copyList(sourceDocuments, "sourceDocuments");
    List<ApprovalTemplateDescriptor> validatedApprovals =
        ContractDescriptorValidation.copyList(approvals, "approvals");
    return validatedSourceDocuments.stream()
            .anyMatch(ContractTemplateValidationSupport::hasPlaceholder)
        || validatedApprovals.stream().anyMatch(ContractTemplateValidationSupport::hasPlaceholder);
  }

  private static boolean containsPlaceholderProvenance(
      String actorId,
      String commandId,
      String idempotencyKey,
      String causationId,
      @Nullable String correlationId) {
    return ScaffoldPlaceholders.isReserved(actorId)
        || ScaffoldPlaceholders.isReserved(commandId)
        || ScaffoldPlaceholders.isReserved(idempotencyKey)
        || ScaffoldPlaceholders.isReserved(causationId)
        || (correlationId != null && ScaffoldPlaceholders.isReserved(correlationId));
  }

  private static SourceDocumentReference toSourceDocumentReference(
      SourceDocumentTemplateDescriptor sourceDocument) {
    return new SourceDocumentReference(
        new SourceDocumentId(sourceDocument.sourceDocumentId()),
        new SourceDocumentType(sourceDocument.sourceDocumentType()),
        LocalDate.parse(sourceDocument.documentDate()));
  }

  private static ApprovalReference toApprovalReference(ApprovalTemplateDescriptor approval) {
    return new ApprovalReference(
        new ApprovalId(approval.approvalId()),
        new ApprovalType(approval.approvalType()),
        new ActorId(approval.approverId()),
        approval.approverType(),
        approval.decision(),
        Instant.parse(approval.approvedAt()));
  }

  private static boolean hasPlaceholder(SourceDocumentTemplateDescriptor sourceDocument) {
    return ScaffoldPlaceholders.isReserved(sourceDocument.sourceDocumentId())
        || ScaffoldPlaceholders.isReserved(sourceDocument.sourceDocumentType())
        || ScaffoldPlaceholders.isReserved(sourceDocument.documentDate());
  }

  private static boolean hasPlaceholder(ApprovalTemplateDescriptor approval) {
    return ScaffoldPlaceholders.isReserved(approval.approvalId())
        || ScaffoldPlaceholders.isReserved(approval.approvalType())
        || ScaffoldPlaceholders.isReserved(approval.approverId())
        || ScaffoldPlaceholders.isReserved(approval.approvedAt());
  }

  /** Validated values for one provenance template descriptor. */
  record ProvenanceTemplateValues(
      String actorId,
      ActorType actorType,
      String commandId,
      String idempotencyKey,
      String causationId,
      @Nullable String correlationId) {}

  /** Validated values for one evidence template descriptor. */
  record AccountingEvidenceTemplateValues(
      List<SourceDocumentTemplateDescriptor> sourceDocuments,
      List<ApprovalTemplateDescriptor> approvals) {}

  /** Validated values for one source-document template descriptor. */
  record SourceDocumentTemplateValues(
      String sourceDocumentId, String sourceDocumentType, String documentDate) {}

  /** Validated values for one approval template descriptor. */
  record ApprovalTemplateValues(
      String approvalId,
      String approvalType,
      String approverId,
      ActorType approverType,
      ApprovalDecision decision,
      String approvedAt) {}
}
