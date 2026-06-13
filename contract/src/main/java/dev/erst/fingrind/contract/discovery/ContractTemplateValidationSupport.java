package dev.erst.fingrind.contract.discovery;

import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** Shared validation helpers for published request and ledger-plan templates. */
final class ContractTemplateValidationSupport {
  private ContractTemplateValidationSupport() {}

  static void validateLiveTextUnlessPlaceholder(String value, Consumer<String> liveValueValidator) {
    if (!ScaffoldPlaceholders.isReserved(value)) {
      liveValueValidator.accept(value);
    }
  }

  static void validateLiveOptionalTextUnlessPlaceholder(
      @Nullable String value, Consumer<String> liveValueValidator) {
    if (value != null && !ScaffoldPlaceholders.isReserved(value)) {
      liveValueValidator.accept(value);
    }
  }

  static boolean containsPlaceholderEvidence(
      List<ContractTemplates.SourceDocumentTemplateDescriptor> sourceDocuments,
      List<ContractTemplates.ApprovalTemplateDescriptor> approvals) {
    return sourceDocuments.stream()
            .anyMatch(ContractTemplateValidationSupport::containsPlaceholderEvidence)
        || approvals.stream()
            .anyMatch(ContractTemplateValidationSupport::containsPlaceholderEvidence);
  }

  private static boolean containsPlaceholderEvidence(
      ContractTemplates.SourceDocumentTemplateDescriptor sourceDocument) {
    return ScaffoldPlaceholders.isReserved(sourceDocument.sourceDocumentId())
        || ScaffoldPlaceholders.isReserved(sourceDocument.sourceDocumentType())
        || ScaffoldPlaceholders.isReserved(sourceDocument.documentDate())
        || ScaffoldPlaceholders.isReserved(sourceDocument.capturedAt())
        || ScaffoldPlaceholders.isReserved(sourceDocument.storageLocator())
        || ScaffoldPlaceholders.isReserved(sourceDocument.contentSha256());
  }

  private static boolean containsPlaceholderEvidence(
      ContractTemplates.ApprovalTemplateDescriptor approval) {
    return ScaffoldPlaceholders.isReserved(approval.approvalId())
        || ScaffoldPlaceholders.isReserved(approval.approvalType())
        || ScaffoldPlaceholders.isReserved(approval.approverId())
        || ScaffoldPlaceholders.isReserved(approval.approvedAt());
  }
}
