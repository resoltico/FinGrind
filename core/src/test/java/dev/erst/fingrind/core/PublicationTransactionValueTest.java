package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies public transaction values preserve the exact identifier, secret, and outcome contracts.
 */
class PublicationTransactionValueTest {
  private static final PublicationTransactionId TRANSACTION_ID =
      new PublicationTransactionId("0123456789abcdef0123456789abcdef");

  @Test
  void rejectsInvalidOrAmbiguousPublicRequestMembers(@TempDir Path temporaryDirectory) {
    Path finalPath = temporaryDirectory.resolve("report.pdf");
    PublicationTransactionMemberRequest first = member("pdf-report", finalPath, new byte[] {1});
    PublicationTransactionMemberRequest sameFinal =
        member("backup-report", finalPath, new byte[] {2});

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PublicationTransactionMemberRequest(
                "PDF",
                PublicationTransactionMemberRole.PDF_REPORT,
                finalPath,
                PublicationMode.REPLACE,
                new byte[] {1}));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PublicationTransactionRequest(List.of(first, sameFinal)));
  }

  @Test
  void comparesSecretBearingRequestsWithoutLeakingTheirBytes(@TempDir Path temporaryDirectory) {
    Path finalPath = temporaryDirectory.resolve("report.pdf");
    PublicationTransactionMemberRequest original = member("pdf-report", finalPath, new byte[] {1});
    PublicationTransactionMemberRequest equal = member("pdf-report", finalPath, new byte[] {1});

    assertEquals(original, original);
    assertEquals(original, equal);
    assertEquals(original.hashCode(), equal.hashCode());
    assertNotEquals(original, member("book-report", finalPath, new byte[] {1}));
    assertNotEquals(
        original,
        new PublicationTransactionMemberRequest(
            "pdf-report",
            PublicationTransactionMemberRole.PROTECTED_BOOK,
            finalPath,
            PublicationMode.NO_REPLACE_LINK,
            new byte[] {1}));
    assertNotEquals(
        original, member("pdf-report", temporaryDirectory.resolve("other.pdf"), new byte[] {1}));
    assertNotEquals(
        original,
        new PublicationTransactionMemberRequest(
            "pdf-report",
            PublicationTransactionMemberRole.PDF_REPORT,
            finalPath,
            PublicationMode.REPLACE,
            new byte[] {1}));
    assertNotEquals(original, member("pdf-report", finalPath, new byte[] {2}));
    assertNotEquals(original, "pdf-report");
  }

  @Test
  void distinguishesPrivateSourceRequestsWithoutExposingTheirSourcePath(
      @TempDir Path temporaryDirectory) {
    Path finalPath = temporaryDirectory.resolve("report.pdf");
    Path sourcePath = temporaryDirectory.resolve("source.bin");
    PublicationTransactionMemberRequest source =
        PublicationTransactionMemberRequest.fromPrivateSource(
            "pdf-report",
            PublicationTransactionMemberRole.PDF_REPORT,
            finalPath,
            PublicationMode.NO_REPLACE_LINK,
            sourcePath);
    PublicationTransactionMemberRequest sameSource =
        PublicationTransactionMemberRequest.fromPrivateSource(
            "pdf-report",
            PublicationTransactionMemberRole.PDF_REPORT,
            finalPath,
            PublicationMode.NO_REPLACE_LINK,
            sourcePath);
    PublicationTransactionMemberRequest differentSource =
        PublicationTransactionMemberRequest.fromPrivateSource(
            "pdf-report",
            PublicationTransactionMemberRole.PDF_REPORT,
            finalPath,
            PublicationMode.NO_REPLACE_LINK,
            temporaryDirectory.resolve("other-source.bin"));

    assertEquals(source, sameSource);
    assertEquals(source.hashCode(), sameSource.hashCode());
    assertNotEquals(source, differentSource);
    assertTrue(source.hasPrivateSource());
    assertEquals(sourcePath.toAbsolutePath(), source.privateSourcePathForStaging());
    assertFalse(source.toString().contains(sourcePath.toString()));
    assertThrows(IllegalStateException.class, source::secretBytesForStaging);
    assertNotEquals(
        source,
        member(
            "pdf-report",
            finalPath,
            sourcePath.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            PublicationTransactionMemberRequest.fromPrivateSource(
                "pdf-report",
                PublicationTransactionMemberRole.PDF_REPORT,
                finalPath,
                PublicationMode.NO_REPLACE_LINK,
                finalPath));
  }

  @Test
  void requiresACompleteResultToHaveBothSuccessfulAxes() {
    PublicationTransactionOutcome successful =
        new PublicationTransactionOutcome(
            PublicationCommitOutcome.ALL_COMMITTED, PublicationCleanupOutcome.COMPLETE);

    assertTrue(
        new PublicationTransactionResult(
                TRANSACTION_ID, PublicationTransactionState.COMPLETE, successful)
            .successful());
    assertFalse(
        new PublicationTransactionResult(
                TRANSACTION_ID, PublicationTransactionState.STAGED, successful)
            .successful());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PublicationTransactionResult(
                TRANSACTION_ID,
                PublicationTransactionState.COMPLETE,
                new PublicationTransactionOutcome(
                    PublicationCommitOutcome.ALL_COMMITTED, PublicationCleanupOutcome.INCOMPLETE)));
  }

  @Test
  void exposesFinalArtifactOnlyAfterItsTransactionCompleted(@TempDir Path temporaryDirectory) {
    Path expectedFinalPath = temporaryDirectory.resolve("report.pdf");
    PublicationTransactionResult completed =
        new PublicationTransactionResult(
            TRANSACTION_ID,
            PublicationTransactionState.COMPLETE,
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.ALL_COMMITTED, PublicationCleanupOutcome.COMPLETE));
    PublicationTransactionResult incomplete =
        new PublicationTransactionResult(
            TRANSACTION_ID,
            PublicationTransactionState.CLEANING,
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.ALL_COMMITTED, PublicationCleanupOutcome.INCOMPLETE));

    PublicationTransactionArtifact artifact =
        new PublicationTransactionArtifact(expectedFinalPath, completed);

    assertEquals(expectedFinalPath.toAbsolutePath(), artifact.publishedArtifactPath());
    assertEquals(completed, artifact.transactionResult());
    assertThrows(
        IllegalArgumentException.class,
        () -> new PublicationTransactionArtifact(expectedFinalPath, incomplete));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PublicationTransactionArtifact(temporaryDirectory.getRoot(), completed));
  }

  @Test
  void restoresTheSafeRecoveryHandleAfterExceptionSerialization() throws Exception {
    PublicationTransactionResult expected =
        new PublicationTransactionResult(
            TRANSACTION_ID,
            PublicationTransactionState.COMMIT_UNCERTAIN,
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.COMMIT_UNCERTAIN, PublicationCleanupOutcome.INCOMPLETE));
    PublicationTransactionExecutionException original =
        new PublicationTransactionExecutionException(
            expected, new IOException("commit interrupted"));

    PublicationTransactionExecutionException restored = roundTrip(original);

    assertEquals(expected, restored.result());
    assertTrue(
        Objects.requireNonNull(restored.getMessage()).contains(expected.transactionId().value()));
  }

  private static PublicationTransactionMemberRequest member(
      String memberId, Path finalPath, byte[] bytes) {
    return new PublicationTransactionMemberRequest(
        memberId,
        PublicationTransactionMemberRole.PDF_REPORT,
        finalPath,
        PublicationMode.NO_REPLACE_LINK,
        bytes);
  }

  private static PublicationTransactionExecutionException roundTrip(
      PublicationTransactionExecutionException exception) throws IOException {
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(exception);
      try (ObjectInputStream input =
          new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
        try {
          return (PublicationTransactionExecutionException) input.readObject();
        } catch (ClassNotFoundException failure) {
          throw new IOException(
              "Transaction exception serialization could not restore its type.", failure);
        }
      }
    }
  }
}
