package dev.erst.fingrind.executor.maintenance;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.bookkeeping.BackupAcknowledgementState;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRetention;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Covers the published attestation-commit contract for every successful backup state. */
class ProtectedBookBackupOutcomeTest {
  private static final Path BOOK_PATH = Path.of("book.sqlite");
  private static final Path BACKUP_PATH = Path.of("backup.fgba");
  private static final Path BACKUP_KEY_PATH = Path.of("backup.key");
  private static final UUID BACKUP_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");
  private static final AttestationCommit COMMIT =
      new AttestationCommit(BigInteger.ONE, "a".repeat(64));

  @Test
  void backedUp_acceptsEveryStateAndCommitCombinationThePublishedContractAllows() {
    ProtectedBookBackupOutcome.BackedUp acknowledged =
        backedUp(
            ProtectedBookPairPublicationCompletion.PUBLISHED,
            BackupAcknowledgementState.ACKNOWLEDGED,
            COMMIT);
    ProtectedBookBackupOutcome.BackedUp resumedWithoutAppend =
        backedUp(
            ProtectedBookPairPublicationCompletion.RECOVERED,
            BackupAcknowledgementState.RESUMED,
            null);
    ProtectedBookBackupOutcome.BackedUp resumedWithAppend =
        backedUp(
            ProtectedBookPairPublicationCompletion.ALREADY_PUBLISHED,
            BackupAcknowledgementState.RESUMED,
            COMMIT);
    ProtectedBookBackupOutcome.BackedUp alreadyPresent =
        backedUp(
            ProtectedBookPairPublicationCompletion.PUBLISHED,
            BackupAcknowledgementState.ALREADY_PRESENT,
            null);

    assertSame(COMMIT, acknowledged.attestationCommit());
    assertNull(resumedWithoutAppend.attestationCommit());
    assertSame(COMMIT, resumedWithAppend.attestationCommit());
    assertNull(alreadyPresent.attestationCommit());
  }

  @Test
  void backedUp_rejectsCommitCombinationsThatWouldMisstateAcknowledgementHistory() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            backedUp(
                ProtectedBookPairPublicationCompletion.PUBLISHED,
                BackupAcknowledgementState.ACKNOWLEDGED,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            backedUp(
                ProtectedBookPairPublicationCompletion.PUBLISHED,
                BackupAcknowledgementState.ALREADY_PRESENT,
                COMMIT));

    for (CompletionAndAcknowledgement invalid :
        java.util.List.of(
            new CompletionAndAcknowledgement(
                ProtectedBookPairPublicationCompletion.PUBLISHED,
                BackupAcknowledgementState.RESUMED),
            new CompletionAndAcknowledgement(
                ProtectedBookPairPublicationCompletion.RECOVERED,
                BackupAcknowledgementState.ACKNOWLEDGED),
            new CompletionAndAcknowledgement(
                ProtectedBookPairPublicationCompletion.RECOVERED,
                BackupAcknowledgementState.ALREADY_PRESENT),
            new CompletionAndAcknowledgement(
                ProtectedBookPairPublicationCompletion.ALREADY_PUBLISHED,
                BackupAcknowledgementState.ACKNOWLEDGED),
            new CompletionAndAcknowledgement(
                ProtectedBookPairPublicationCompletion.ALREADY_PUBLISHED,
                BackupAcknowledgementState.ALREADY_PRESENT))) {
      assertThrows(
          IllegalArgumentException.class,
          () -> backedUp(invalid.completion(), invalid.acknowledgementState(), null));
    }
  }

  @Test
  @org.jspecify.annotations.NullUnmarked
  void backedUp_rejectsAMissingAcknowledgementState() {
    assertThrows(NullPointerException.class, () -> backedUp(null, null, null));
  }

  private static ProtectedBookBackupOutcome.BackedUp backedUp(
      ProtectedBookPairPublicationCompletion completion,
      BackupAcknowledgementState acknowledgementState,
      @org.jspecify.annotations.Nullable AttestationCommit attestationCommit) {
    return new ProtectedBookBackupOutcome.BackedUp(
        BOOK_PATH,
        BACKUP_PATH,
        BACKUP_KEY_PATH,
        BACKUP_ID,
        completion,
        completion == ProtectedBookPairPublicationCompletion.ALREADY_PUBLISHED ? null : retention(),
        acknowledgementState,
        attestationCommit);
  }

  private static ProtectedBookPairPublicationRetention retention() {
    return new ProtectedBookPairPublicationRetention(
        new ArtifactPublicationResult(
            BACKUP_PATH, new ArtifactPublicationRetention(Path.of("retained-book.stage"))),
        new ArtifactPublicationResult(
            BACKUP_KEY_PATH, new ArtifactPublicationRetention(Path.of("retained-secret.stage"))));
  }

  private record CompletionAndAcknowledgement(
      ProtectedBookPairPublicationCompletion completion,
      BackupAcknowledgementState acknowledgementState) {}
}
