package dev.erst.fingrind.executor.maintenance;

import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRetention;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
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

  private static ProtectedBookPairPublicationRetention retention() {
    return new ProtectedBookPairPublicationRetention(
        new ArtifactPublicationResult(
            Path.of("book.sqlite"),
            new ArtifactPublicationRetention(Path.of("retained-book.stage"))),
        new ArtifactPublicationResult(
            Path.of("book.key"),
            new ArtifactPublicationRetention(Path.of("retained-secret.stage"))));
  }
}
