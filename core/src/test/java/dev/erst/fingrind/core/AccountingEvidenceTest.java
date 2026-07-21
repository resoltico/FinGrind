package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for first-class evidence value objects. */
class AccountingEvidenceTest {
  private static final LocalDate DOCUMENT_DATE = LocalDate.parse("2026-04-07");
  private static final Instant APPROVED_AT = Instant.parse("2026-04-07T10:18:00Z");

  @Test
  void sourceDocumentId_publishesBoundaryContractAndNormalizesValues() {
    assertEquals(255, SourceDocumentId.maxLength());
    assertEquals("^[A-Za-z0-9](?:[A-Za-z0-9._:/-]{0,254})?$", SourceDocumentId.pattern());
    assertEquals(
        "invoice/2026-05-20:001", new SourceDocumentId("  invoice/2026-05-20:001  ").value());
  }

  @Test
  void sourceDocumentId_rejectsNullBlankOversizedAndInvalidValues() {
    assertThrows(NullPointerException.class, () -> new SourceDocumentId(nullOf()));
    assertThrows(IllegalArgumentException.class, () -> new SourceDocumentId("   "));
    assertThrows(IllegalArgumentException.class, () -> new SourceDocumentId("x".repeat(256)));
    assertThrows(IllegalArgumentException.class, () -> new SourceDocumentId("invoice 001"));
  }

  @Test
  void sourceDocumentType_publishesBoundaryContractAndNormalizesValues() {
    assertEquals(64, SourceDocumentType.maxLength());
    assertEquals("^[A-Za-z0-9](?:[A-Za-z0-9._:/-]{0,63})?$", SourceDocumentType.pattern());
    assertEquals("invoice", new SourceDocumentType("  invoice  ").value());
  }

  @Test
  void sourceDocumentType_rejectsNullBlankOversizedAndInvalidValues() {
    assertThrows(NullPointerException.class, () -> new SourceDocumentType(nullOf()));
    assertThrows(IllegalArgumentException.class, () -> new SourceDocumentType("   "));
    assertThrows(IllegalArgumentException.class, () -> new SourceDocumentType("x".repeat(65)));
    assertThrows(IllegalArgumentException.class, () -> new SourceDocumentType("invoice type"));
  }

  @Test
  void approvalId_publishesBoundaryContractAndNormalizesValues() {
    assertEquals(255, ApprovalId.maxLength());
    assertEquals("^[A-Za-z0-9](?:[A-Za-z0-9._:/-]{0,254})?$", ApprovalId.pattern());
    assertEquals("approval/2026-05-20:001", new ApprovalId("  approval/2026-05-20:001  ").value());
  }

  @Test
  void approvalId_rejectsNullBlankOversizedAndInvalidValues() {
    assertThrows(NullPointerException.class, () -> new ApprovalId(nullOf()));
    assertThrows(IllegalArgumentException.class, () -> new ApprovalId("   "));
    assertThrows(IllegalArgumentException.class, () -> new ApprovalId("x".repeat(256)));
    assertThrows(IllegalArgumentException.class, () -> new ApprovalId("approval 001"));
  }

  @Test
  void approvalType_publishesBoundaryContractAndNormalizesValues() {
    assertEquals(64, ApprovalType.maxLength());
    assertEquals("^[A-Za-z0-9](?:[A-Za-z0-9._:/-]{0,63})?$", ApprovalType.pattern());
    assertEquals("manager-signoff", new ApprovalType("  manager-signoff  ").value());
  }

  @Test
  void approvalType_rejectsNullBlankOversizedAndInvalidValues() {
    assertThrows(NullPointerException.class, () -> new ApprovalType(nullOf()));
    assertThrows(IllegalArgumentException.class, () -> new ApprovalType("   "));
    assertThrows(IllegalArgumentException.class, () -> new ApprovalType("x".repeat(65)));
    assertThrows(IllegalArgumentException.class, () -> new ApprovalType("manager signoff"));
  }

  @Test
  void sourceDocumentReference_requiresRetainedDocumentFact() {
    SourceDocumentId sourceDocumentId = new SourceDocumentId("invoice-1");
    SourceDocumentType sourceDocumentType = new SourceDocumentType("cash-receipt");
    SourceDocumentReference reference =
        new SourceDocumentReference(sourceDocumentId, sourceDocumentType, DOCUMENT_DATE);
    assertEquals(sourceDocumentId, reference.sourceDocumentId());
    assertEquals(sourceDocumentType, reference.sourceDocumentType());
    assertEquals(DOCUMENT_DATE, reference.documentDate());
    assertThrows(
        NullPointerException.class,
        () -> new SourceDocumentReference(nullOf(), sourceDocumentType, DOCUMENT_DATE));
    assertThrows(
        NullPointerException.class,
        () -> new SourceDocumentReference(sourceDocumentId, nullOf(), DOCUMENT_DATE));
    assertThrows(
        NullPointerException.class,
        () -> new SourceDocumentReference(sourceDocumentId, sourceDocumentType, nullOf()));
  }

  @Test
  void approvalReference_requiresRetainedApprovalFact() {
    ApprovalId approvalId = new ApprovalId("approval-1");
    ApprovalType approvalType = new ApprovalType("manager-signoff");
    String approverReference = "manager-1";
    String approverType = "person";
    ApprovalReference reference =
        new ApprovalReference(
            approvalId,
            approvalType,
            approverReference,
            approverType,
            ApprovalDecision.APPROVED,
            APPROVED_AT);
    assertEquals(approvalId, reference.approvalId());
    assertEquals(approvalType, reference.approvalType());
    assertEquals(approverReference, reference.approverReference());
    assertEquals(approverType, reference.approverType());
    assertEquals(ApprovalDecision.APPROVED, reference.decision());
    assertEquals(APPROVED_AT, reference.approvedAt());
    assertThrows(
        NullPointerException.class,
        () ->
            new ApprovalReference(
                nullOf(),
                approvalType,
                approverReference,
                approverType,
                ApprovalDecision.APPROVED,
                APPROVED_AT));
    assertThrows(
        NullPointerException.class,
        () ->
            new ApprovalReference(
                approvalId,
                nullOf(),
                approverReference,
                approverType,
                ApprovalDecision.APPROVED,
                APPROVED_AT));
    assertThrows(
        NullPointerException.class,
        () ->
            new ApprovalReference(
                approvalId,
                approvalType,
                nullOf(),
                approverType,
                ApprovalDecision.APPROVED,
                APPROVED_AT));
    assertThrows(
        NullPointerException.class,
        () ->
            new ApprovalReference(
                approvalId,
                approvalType,
                approverReference,
                nullOf(),
                ApprovalDecision.APPROVED,
                APPROVED_AT));
    assertThrows(
        NullPointerException.class,
        () ->
            new ApprovalReference(
                approvalId, approvalType, approverReference, approverType, nullOf(), APPROVED_AT));
    assertThrows(
        NullPointerException.class,
        () ->
            new ApprovalReference(
                approvalId,
                approvalType,
                approverReference,
                approverType,
                ApprovalDecision.APPROVED,
                nullOf()));
  }

  @Test
  void accountingEvidence_defensivelyCopiesCollectionsAndTracksApprovalPresence() {
    List<SourceDocumentReference> sourceDocuments =
        new ArrayList<>(List.of(sourceDocumentReference("invoice-1")));
    List<ApprovalReference> approvals = new ArrayList<>(List.of(approvalReference("approval-1")));

    AccountingEvidence accountingEvidence = new AccountingEvidence(sourceDocuments, approvals);

    assertTrue(accountingEvidence.hasApprovals());
    sourceDocuments.add(sourceDocumentReference("invoice-2"));
    approvals.add(approvalReference("approval-2"));
    assertEquals(1, accountingEvidence.sourceDocuments().size());
    assertEquals(1, accountingEvidence.approvals().size());
    assertThrows(
        UnsupportedOperationException.class,
        () -> accountingEvidence.sourceDocuments().add(sourceDocumentReference("invoice-3")));
    assertThrows(
        UnsupportedOperationException.class,
        () -> accountingEvidence.approvals().add(approvalReference("approval-3")));
  }

  @Test
  void accountingEvidence_requiresSourceDocumentsAndRejectsNullCollections() {
    SourceDocumentReference sourceDocument = sourceDocumentReference("invoice-1");

    assertThrows(NullPointerException.class, () -> new AccountingEvidence(nullOf(), List.of()));
    assertThrows(
        NullPointerException.class,
        () -> new AccountingEvidence(List.of(sourceDocument), nullOf()));
    assertThrows(
        IllegalArgumentException.class, () -> new AccountingEvidence(List.of(), List.of()));

    AccountingEvidence accountingEvidence =
        new AccountingEvidence(List.of(sourceDocument), List.of());
    assertFalse(accountingEvidence.hasApprovals());
  }

  @Test
  void accountingEvidence_preservesDuplicateIdentifiersUntilDurableStoreBoundary() {
    AccountingEvidence accountingEvidence =
        new AccountingEvidence(
            List.of(sourceDocumentReference("invoice-1"), sourceDocumentReference("invoice-1")),
            List.of(approvalReference("approval-1"), approvalReference("approval-1")));

    assertEquals(2, accountingEvidence.sourceDocuments().size());
    assertEquals(
        "invoice-1", accountingEvidence.sourceDocuments().get(0).sourceDocumentId().value());
    assertEquals(
        "invoice-1", accountingEvidence.sourceDocuments().get(1).sourceDocumentId().value());
    assertEquals(2, accountingEvidence.approvals().size());
    assertEquals("approval-1", accountingEvidence.approvals().get(0).approvalId().value());
    assertEquals("approval-1", accountingEvidence.approvals().get(1).approvalId().value());
  }

  private static SourceDocumentReference sourceDocumentReference(String token) {
    return new SourceDocumentReference(
        new SourceDocumentId(token), new SourceDocumentType("cash-receipt"), DOCUMENT_DATE);
  }

  private static ApprovalReference approvalReference(String token) {
    return new ApprovalReference(
        new ApprovalId(token),
        new ApprovalType("manager-signoff"),
        "manager-1",
        "person",
        ApprovalDecision.APPROVED,
        APPROVED_AT);
  }
}
