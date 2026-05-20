package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for first-class evidence value objects. */
class AccountingEvidenceTest {
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
  void sourceDocumentReference_requiresBothConstituents() {
    SourceDocumentId sourceDocumentId = new SourceDocumentId("invoice-1");
    SourceDocumentType sourceDocumentType = new SourceDocumentType("invoice");
    SourceDocumentReference reference =
        new SourceDocumentReference(sourceDocumentId, sourceDocumentType);
    assertEquals(sourceDocumentId, reference.sourceDocumentId());
    assertEquals(sourceDocumentType, reference.sourceDocumentType());
    assertThrows(
        NullPointerException.class,
        () -> new SourceDocumentReference(nullOf(), sourceDocumentType));
    assertThrows(
        NullPointerException.class, () -> new SourceDocumentReference(sourceDocumentId, nullOf()));
  }

  @Test
  void approvalReference_requiresBothConstituents() {
    ApprovalId approvalId = new ApprovalId("approval-1");
    ApprovalType approvalType = new ApprovalType("manager-signoff");
    ApprovalReference reference = new ApprovalReference(approvalId, approvalType);
    assertEquals(approvalId, reference.approvalId());
    assertEquals(approvalType, reference.approvalType());
    assertThrows(NullPointerException.class, () -> new ApprovalReference(nullOf(), approvalType));
    assertThrows(NullPointerException.class, () -> new ApprovalReference(approvalId, nullOf()));
  }

  @Test
  void accountingEvidence_defensivelyCopiesCollectionsAndTracksApprovalPresence() {
    List<SourceDocumentReference> sourceDocuments =
        new ArrayList<>(
            List.of(
                new SourceDocumentReference(
                    new SourceDocumentId("invoice-1"), new SourceDocumentType("invoice"))));
    List<ApprovalReference> approvals =
        new ArrayList<>(
            List.of(
                new ApprovalReference(new ApprovalId("approval-1"), new ApprovalType("signoff"))));

    AccountingEvidence accountingEvidence = new AccountingEvidence(sourceDocuments, approvals);

    assertTrue(accountingEvidence.hasApprovals());
    sourceDocuments.add(
        new SourceDocumentReference(
            new SourceDocumentId("invoice-2"), new SourceDocumentType("invoice")));
    approvals.add(new ApprovalReference(new ApprovalId("approval-2"), new ApprovalType("review")));
    assertEquals(1, accountingEvidence.sourceDocuments().size());
    assertEquals(1, accountingEvidence.approvals().size());
    assertThrows(
        UnsupportedOperationException.class,
        () ->
            accountingEvidence
                .sourceDocuments()
                .add(
                    new SourceDocumentReference(
                        new SourceDocumentId("invoice-3"), new SourceDocumentType("invoice"))));
    assertThrows(
        UnsupportedOperationException.class,
        () ->
            accountingEvidence
                .approvals()
                .add(
                    new ApprovalReference(
                        new ApprovalId("approval-3"), new ApprovalType("audit"))));
  }

  @Test
  void accountingEvidence_requiresSourceDocumentsAndRejectsNullCollections() {
    SourceDocumentReference sourceDocument =
        new SourceDocumentReference(
            new SourceDocumentId("invoice-1"), new SourceDocumentType("invoice"));

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
}
