package dev.erst.fingrind.executor.maintenance;

import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublication;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.executor.PublicationTransactionTestFixtures;
import java.math.BigInteger;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Ensures local lifecycle outcomes cannot mislabel a backup-only replay as a continuation. */
class ProtectedBookLifecycleOutcomeTest {
  private static final AttestationCommit COMMIT =
      new AttestationCommit(BigInteger.ONE, "a".repeat(64));

  @Test
  void restoreAndRekeyOutcomes_reserveAlreadyPublishedForBackupAcknowledgementReplay() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookRestoreOutcome.Restored(
                Path.of("book.sqlite"),
                Path.of("book.key"),
                COMMIT,
                ProtectedBookPairPublicationCompletion.ALREADY_PUBLISHED,
                retention()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookRekeyOutcome.Rekeyed(
                Path.of("book.sqlite"),
                Path.of("book.key"),
                COMMIT,
                ProtectedBookPairPublicationCompletion.ALREADY_PUBLISHED,
                retention()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookRekeyOutcome.Rekeyed(
                Path.of("book.sqlite"),
                Path.of("different.key"),
                COMMIT,
                ProtectedBookPairPublicationCompletion.PUBLISHED,
                retention()));
  }

  private static ProtectedBookPairPublication retention() {
    return new ProtectedBookPairPublication(
        PublicationTransactionTestFixtures.completedArtifact(Path.of("book.sqlite")),
        PublicationTransactionTestFixtures.completedArtifact(Path.of("book.key")));
  }
}
