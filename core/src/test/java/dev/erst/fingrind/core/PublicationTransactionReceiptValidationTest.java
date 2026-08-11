package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Verifies public final-artifact values reject incomplete or ambiguous recovery claims. */
class PublicationTransactionReceiptValidationTest {
  private static final PublicationTransactionId TRANSACTION_ID =
      new PublicationTransactionId("0123456789abcdef0123456789abcdef");

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void resolvesOnlyValidFinalArtifactParents(@TempDir Path temporaryDirectory) throws Exception {
    PublicationTransactionResult completed = completed(TRANSACTION_ID);
    Path finalPath = temporaryDirectory.resolve("report.pdf");

    PublicationTransactionArtifact artifact =
        new PublicationTransactionArtifact(finalPath, completed);

    assertEquals(
        Objects.requireNonNull(finalPath.getParent()).toRealPath().resolve("report.pdf"),
        artifact.publishedArtifactPath());
    assertEquals(
        temporaryDirectory.resolve("missing").resolve("report.pdf").toAbsolutePath(),
        new PublicationTransactionArtifact(
                temporaryDirectory.resolve("missing").resolve("report.pdf"), completed)
            .publishedArtifactPath());
    Path brokenParent = temporaryDirectory.resolve("broken-parent");
    Files.createSymbolicLink(brokenParent, temporaryDirectory.resolve("missing-target"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PublicationTransactionArtifact(brokenParent.resolve("report.pdf"), completed));
  }

  @Test
  void rejectsEveryReceiptThatClaimsAnUnauthenticatedFinalArtifact(
      @TempDir Path temporaryDirectory) {
    PublicationTransactionResult completed = completed(TRANSACTION_ID);
    PublicationTransactionArtifact artifact =
        new PublicationTransactionArtifact(temporaryDirectory.resolve("report.pdf"), completed);
    PublicationTransactionMemberArtifact memberArtifact =
        new PublicationTransactionMemberArtifact(
            "pdf-report", PublicationTransactionMemberRole.PDF_REPORT, artifact);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PublicationTransactionMemberArtifact(
                "PDF-report", PublicationTransactionMemberRole.PDF_REPORT, artifact));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PublicationTransactionRecoveryReceipt(
                incomplete(TRANSACTION_ID), List.of(memberArtifact)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PublicationTransactionRecoveryReceipt(completed, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PublicationTransactionRecoveryReceipt(
                completed, List.of(memberArtifact, memberArtifact)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PublicationTransactionRecoveryReceipt(
                completed(new PublicationTransactionId("fedcba9876543210fedcba9876543210")),
                List.of(memberArtifact)));
  }

  private static PublicationTransactionResult completed(PublicationTransactionId transactionId) {
    return new PublicationTransactionResult(
        transactionId,
        PublicationTransactionState.COMPLETE,
        new PublicationTransactionOutcome(
            PublicationCommitOutcome.ALL_COMMITTED, PublicationCleanupOutcome.COMPLETE));
  }

  private static PublicationTransactionResult incomplete(PublicationTransactionId transactionId) {
    return new PublicationTransactionResult(
        transactionId,
        PublicationTransactionState.CLEANING,
        new PublicationTransactionOutcome(
            PublicationCommitOutcome.ALL_COMMITTED, PublicationCleanupOutcome.INCOMPLETE));
  }
}
