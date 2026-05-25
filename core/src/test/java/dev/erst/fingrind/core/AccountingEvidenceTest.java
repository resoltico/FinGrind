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
  private static final Instant CAPTURED_AT = Instant.parse("2026-04-07T10:15:30Z");
  private static final Instant APPROVED_AT = Instant.parse("2026-04-07T10:18:00Z");
  private static final String DOCUMENT_SHA256 =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

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
    StorageLocator storageLocator = new StorageLocator("evidence://documents/invoice-1.pdf");
    ContentSha256 contentSha256 = new ContentSha256(DOCUMENT_SHA256);
    SourceDocumentReference reference =
        new SourceDocumentReference(
            sourceDocumentId,
            sourceDocumentType,
            DOCUMENT_DATE,
            CAPTURED_AT,
            storageLocator,
            contentSha256);
    assertEquals(sourceDocumentId, reference.sourceDocumentId());
    assertEquals(sourceDocumentType, reference.sourceDocumentType());
    assertEquals(DOCUMENT_DATE, reference.documentDate());
    assertEquals(CAPTURED_AT, reference.capturedAt());
    assertEquals(storageLocator, reference.storageLocator());
    assertEquals(contentSha256, reference.contentSha256());
    assertThrows(
        NullPointerException.class,
        () ->
            new SourceDocumentReference(
                nullOf(),
                sourceDocumentType,
                DOCUMENT_DATE,
                CAPTURED_AT,
                storageLocator,
                contentSha256));
    assertThrows(
        NullPointerException.class,
        () ->
            new SourceDocumentReference(
                sourceDocumentId,
                nullOf(),
                DOCUMENT_DATE,
                CAPTURED_AT,
                storageLocator,
                contentSha256));
    assertThrows(
        NullPointerException.class,
        () ->
            new SourceDocumentReference(
                sourceDocumentId,
                sourceDocumentType,
                nullOf(),
                CAPTURED_AT,
                storageLocator,
                contentSha256));
    assertThrows(
        NullPointerException.class,
        () ->
            new SourceDocumentReference(
                sourceDocumentId,
                sourceDocumentType,
                DOCUMENT_DATE,
                nullOf(),
                storageLocator,
                contentSha256));
    assertThrows(
        NullPointerException.class,
        () ->
            new SourceDocumentReference(
                sourceDocumentId,
                sourceDocumentType,
                DOCUMENT_DATE,
                CAPTURED_AT,
                nullOf(),
                contentSha256));
    assertThrows(
        NullPointerException.class,
        () ->
            new SourceDocumentReference(
                sourceDocumentId,
                sourceDocumentType,
                DOCUMENT_DATE,
                CAPTURED_AT,
                storageLocator,
                nullOf()));
  }

  @Test
  void approvalReference_requiresRetainedApprovalFact() {
    ApprovalId approvalId = new ApprovalId("approval-1");
    ApprovalType approvalType = new ApprovalType("manager-signoff");
    ActorId approverId = new ActorId("manager-1");
    ApprovalReference reference =
        new ApprovalReference(
            approvalId,
            approvalType,
            approverId,
            ActorType.PERSON,
            ApprovalDecision.APPROVED,
            APPROVED_AT);
    assertEquals(approvalId, reference.approvalId());
    assertEquals(approvalType, reference.approvalType());
    assertEquals(approverId, reference.approverId());
    assertEquals(ActorType.PERSON, reference.approverType());
    assertEquals(ApprovalDecision.APPROVED, reference.decision());
    assertEquals(APPROVED_AT, reference.approvedAt());
    assertThrows(
        NullPointerException.class,
        () ->
            new ApprovalReference(
                nullOf(),
                approvalType,
                approverId,
                ActorType.PERSON,
                ApprovalDecision.APPROVED,
                APPROVED_AT));
    assertThrows(
        NullPointerException.class,
        () ->
            new ApprovalReference(
                approvalId,
                nullOf(),
                approverId,
                ActorType.PERSON,
                ApprovalDecision.APPROVED,
                APPROVED_AT));
    assertThrows(
        NullPointerException.class,
        () ->
            new ApprovalReference(
                approvalId,
                approvalType,
                nullOf(),
                ActorType.PERSON,
                ApprovalDecision.APPROVED,
                APPROVED_AT));
    assertThrows(
        NullPointerException.class,
        () ->
            new ApprovalReference(
                approvalId,
                approvalType,
                approverId,
                nullOf(),
                ApprovalDecision.APPROVED,
                APPROVED_AT));
    assertThrows(
        NullPointerException.class,
        () ->
            new ApprovalReference(
                approvalId, approvalType, approverId, ActorType.PERSON, nullOf(), APPROVED_AT));
    assertThrows(
        NullPointerException.class,
        () ->
            new ApprovalReference(
                approvalId,
                approvalType,
                approverId,
                ActorType.PERSON,
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

  private static SourceDocumentReference sourceDocumentReference(String token) {
    return new SourceDocumentReference(
        new SourceDocumentId(token),
        new SourceDocumentType("cash-receipt"),
        DOCUMENT_DATE,
        CAPTURED_AT,
        new StorageLocator("evidence://documents/%s.pdf".formatted(token)),
        new ContentSha256(DOCUMENT_SHA256));
  }

  private static ApprovalReference approvalReference(String token) {
    return new ApprovalReference(
        new ApprovalId(token),
        new ApprovalType("manager-signoff"),
        new ActorId("manager-1"),
        ActorType.PERSON,
        ApprovalDecision.APPROVED,
        APPROVED_AT);
  }
}
