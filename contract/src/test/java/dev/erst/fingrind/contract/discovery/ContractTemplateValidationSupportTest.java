package dev.erst.fingrind.contract.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.ApprovalDecision;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Focused coverage tests for shared template-validation helper branches. */
class ContractTemplateValidationSupportTest {
  private static final String LIVE_SOURCE_DOCUMENT_ID = "document-idem-1";
  private static final String LIVE_SOURCE_DOCUMENT_TYPE = "cash-receipt";
  private static final String LIVE_DOCUMENT_DATE = "2026-04-25";
  private static final String LIVE_APPROVAL_ID = "approval-1";
  private static final String LIVE_APPROVAL_TYPE = "manager-signoff";
  private static final String LIVE_APPROVER_ID = "manager-1";
  private static final String LIVE_APPROVED_AT = "2026-04-25T10:15:30Z";

  @Test
  void validateLiveOptionalTextUnlessPlaceholder_invokesOnlyForLiveValues() {
    int[] validatorCalls = new int[1];

    ContractTemplateValidationSupport.validateLiveOptionalTextUnlessPlaceholder(
        null, ignored -> validatorCalls[0]++);
    ContractTemplateValidationSupport.validateLiveOptionalTextUnlessPlaceholder(
        ScaffoldPlaceholders.ACTOR_ID, ignored -> validatorCalls[0]++);
    ContractTemplateValidationSupport.validateLiveOptionalTextUnlessPlaceholder(
        "correlation-1", ignored -> validatorCalls[0]++);

    assertEquals(1, validatorCalls[0]);
  }

  @Test
  void containsPlaceholderEvidence_detectsReservedSourceAndApprovalFields() {
    assertFalse(
        ContractTemplateValidationSupport.containsPlaceholderEvidence(
            List.of(
                sourceDocumentTemplate(
                    LIVE_SOURCE_DOCUMENT_ID, LIVE_SOURCE_DOCUMENT_TYPE, LIVE_DOCUMENT_DATE)),
            List.of(
                approvalTemplate(
                    LIVE_APPROVAL_ID, LIVE_APPROVAL_TYPE, LIVE_APPROVER_ID, LIVE_APPROVED_AT))));

    assertTrue(
        ContractTemplateValidationSupport.containsPlaceholderEvidence(
            List.of(
                sourceDocumentTemplate(
                    ScaffoldPlaceholders.SOURCE_DOCUMENT_ID,
                    LIVE_SOURCE_DOCUMENT_TYPE,
                    LIVE_DOCUMENT_DATE)),
            List.of()));
    assertTrue(
        ContractTemplateValidationSupport.containsPlaceholderEvidence(
            List.of(
                sourceDocumentTemplate(
                    LIVE_SOURCE_DOCUMENT_ID,
                    ScaffoldPlaceholders.SOURCE_DOCUMENT_TYPE,
                    LIVE_DOCUMENT_DATE)),
            List.of()));
    assertTrue(
        ContractTemplateValidationSupport.containsPlaceholderEvidence(
            List.of(
                sourceDocumentTemplate(
                    LIVE_SOURCE_DOCUMENT_ID,
                    LIVE_SOURCE_DOCUMENT_TYPE,
                    ScaffoldPlaceholders.EFFECTIVE_DATE)),
            List.of()));

    assertTrue(
        ContractTemplateValidationSupport.containsPlaceholderEvidence(
            List.of(
                sourceDocumentTemplate(
                    LIVE_SOURCE_DOCUMENT_ID, LIVE_SOURCE_DOCUMENT_TYPE, LIVE_DOCUMENT_DATE)),
            List.of(
                approvalTemplate(
                    ScaffoldPlaceholders.APPROVAL_ID,
                    LIVE_APPROVAL_TYPE,
                    LIVE_APPROVER_ID,
                    LIVE_APPROVED_AT))));
    assertTrue(
        ContractTemplateValidationSupport.containsPlaceholderEvidence(
            List.of(
                sourceDocumentTemplate(
                    LIVE_SOURCE_DOCUMENT_ID, LIVE_SOURCE_DOCUMENT_TYPE, LIVE_DOCUMENT_DATE)),
            List.of(
                approvalTemplate(
                    LIVE_APPROVAL_ID,
                    ScaffoldPlaceholders.APPROVAL_TYPE,
                    LIVE_APPROVER_ID,
                    LIVE_APPROVED_AT))));
    assertTrue(
        ContractTemplateValidationSupport.containsPlaceholderEvidence(
            List.of(
                sourceDocumentTemplate(
                    LIVE_SOURCE_DOCUMENT_ID, LIVE_SOURCE_DOCUMENT_TYPE, LIVE_DOCUMENT_DATE)),
            List.of(
                approvalTemplate(
                    LIVE_APPROVAL_ID,
                    LIVE_APPROVAL_TYPE,
                    ScaffoldPlaceholders.APPROVER_ID,
                    LIVE_APPROVED_AT))));
    assertTrue(
        ContractTemplateValidationSupport.containsPlaceholderEvidence(
            List.of(
                sourceDocumentTemplate(
                    LIVE_SOURCE_DOCUMENT_ID, LIVE_SOURCE_DOCUMENT_TYPE, LIVE_DOCUMENT_DATE)),
            List.of(
                approvalTemplate(
                    LIVE_APPROVAL_ID,
                    LIVE_APPROVAL_TYPE,
                    LIVE_APPROVER_ID,
                    ScaffoldPlaceholders.RECORDED_AT))));
  }

  private static ContractTemplates.SourceDocumentTemplateDescriptor sourceDocumentTemplate(
      String sourceDocumentId, String sourceDocumentType, String documentDate) {
    return new ContractTemplates.SourceDocumentTemplateDescriptor(
        sourceDocumentId, sourceDocumentType, documentDate);
  }

  private static ContractTemplates.ApprovalTemplateDescriptor approvalTemplate(
      String approvalId, String approvalType, String approverId, String approvedAt) {
    return new ContractTemplates.ApprovalTemplateDescriptor(
        approvalId,
        approvalType,
        approverId,
        "person",
        ApprovalDecision.APPROVED,
        approvedAt);
  }
}
