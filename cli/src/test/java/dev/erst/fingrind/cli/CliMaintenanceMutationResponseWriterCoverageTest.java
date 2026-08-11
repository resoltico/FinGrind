package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AttestationVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.BackupAcknowledgementState;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublication;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Transport contract for backup publication acknowledgement and restoration outcomes. */
class CliMaintenanceMutationResponseWriterCoverageTest extends CliResponseWriterTestSupport {
  private static final Path BOOK_FILE = Path.of("books", "current.sqlite");
  private static final Path BACKUP_FILE = Path.of("backups", "current.sqlite");
  private static final Path BACKUP_KEY_FILE = Path.of("backups", "current.book-key");
  private static final UUID BACKUP_ID = UUID.fromString("c6547a5e-3404-4d7a-9cb9-2f0ae67e2f63");

  @Test
  void writesAcknowledgementPendingAcrossJsonTextAndUnsupportedCsv() {
    ProtectedBookPairPublication pendingPublicationRetention =
        CliFixtureSupport.pairPublication(BACKUP_FILE, BACKUP_KEY_FILE);
    BackupBookResult.AcknowledgementPending pending =
        new BackupBookResult.AcknowledgementPending(
            BOOK_FILE,
            BACKUP_FILE,
            BACKUP_KEY_FILE,
            BACKUP_ID,
            ProtectedBookPairPublicationCompletion.PUBLISHED,
            pendingPublicationRetention);

    ByteArrayOutputStream json = new ByteArrayOutputStream();
    writer(json).writeBackupBookResult(pending, OutputMode.JSON);
    assertJsonContains(json, "\"acknowledgementState\":\"pending\"");
    assertJsonContains(json, "\"pairPublicationCompletion\":\"published\"");
    assertJsonContains(json, "\"backupId\":\"" + BACKUP_ID + "\"");
    assertJsonContains(
        json,
        "\"bookPublication\":{\"path\":\""
            + CliPublicPaths.absoluteValue(
                pendingPublicationRetention.bookPublication().publishedArtifactPath())
            + "\"}");
    assertJsonContains(
        json,
        "\"generatedSecretPublication\":{\"path\":\""
            + CliPublicPaths.absoluteValue(
                pendingPublicationRetention.generatedSecretPublication().publishedArtifactPath())
            + "\"}");
    assertJsonContains(json, "\"publicationTransaction\"");

    ByteArrayOutputStream text = new ByteArrayOutputStream();
    writer(text).writeBackupBookResult(pending, OutputMode.TEXT);
    String rendered = text.toString(StandardCharsets.UTF_8);
    assertTrue(rendered.contains("Book Backup Published"), rendered);
    assertTrue(rendered.contains("Pair publication completion"), rendered);
    assertTrue(rendered.contains("published"), rendered);
    assertTrue(rendered.contains("Published book file"), rendered);
    assertTrue(rendered.contains("Published generated-secret file"), rendered);
    assertTrue(rendered.contains("--backup-id"), rendered);
    assertTrue(rendered.contains(BACKUP_ID.toString()), rendered);

    assertThrows(
        IllegalArgumentException.class,
        () -> writer(new ByteArrayOutputStream()).writeBackupBookResult(pending, OutputMode.CSV));
    assertEquals(4, CliAdministrativeExitCodes.exitCodeFor(pending));
  }

  @Test
  void writesResumedBackupRestorationAndAcknowledgementConflictWithoutFlatteningTheirMeaning() {
    BackupBookResult.BackedUp resumed =
        new BackupBookResult.BackedUp(
            BOOK_FILE,
            BACKUP_FILE,
            BACKUP_KEY_FILE,
            BACKUP_ID,
            ProtectedBookPairPublicationCompletion.RECOVERED,
            CliFixtureSupport.pairPublication(BACKUP_FILE, BACKUP_KEY_FILE),
            BackupAcknowledgementState.RESUMED,
            attestationCommit());
    ByteArrayOutputStream resumedText = new ByteArrayOutputStream();
    writer(resumedText).writeBackupBookResult(resumed, OutputMode.TEXT);
    assertTrue(resumedText.toString(StandardCharsets.UTF_8).contains("resumed"));
    ByteArrayOutputStream resumedJson = new ByteArrayOutputStream();
    writer(resumedJson).writeBackupBookResult(resumed, OutputMode.JSON);
    assertJsonContains(resumedJson, "\"acknowledgementState\":\"resumed\"");
    assertJsonContains(resumedJson, "\"pairPublicationCompletion\":\"recovered\"");
    assertThrows(
        IllegalArgumentException.class,
        () -> writer(new ByteArrayOutputStream()).writeBackupBookResult(resumed, OutputMode.CSV));

    BackupBookResult.BackedUp acknowledged =
        new BackupBookResult.BackedUp(
            BOOK_FILE,
            BACKUP_FILE,
            BACKUP_KEY_FILE,
            BACKUP_ID,
            ProtectedBookPairPublicationCompletion.PUBLISHED,
            CliFixtureSupport.pairPublication(BACKUP_FILE, BACKUP_KEY_FILE),
            BackupAcknowledgementState.ACKNOWLEDGED,
            attestationCommit());
    ByteArrayOutputStream acknowledgedJson = new ByteArrayOutputStream();
    writer(acknowledgedJson).writeBackupBookResult(acknowledged, OutputMode.JSON);
    assertJsonContains(acknowledgedJson, "\"acknowledgementState\":\"acknowledged\"");

    ProtectedBookPairPublication alreadyPresentPublicationRetention =
        CliFixtureSupport.pairPublication(BACKUP_FILE, BACKUP_KEY_FILE);
    BackupBookResult.BackedUp alreadyPresent =
        new BackupBookResult.BackedUp(
            BOOK_FILE,
            BACKUP_FILE,
            BACKUP_KEY_FILE,
            BACKUP_ID,
            ProtectedBookPairPublicationCompletion.PUBLISHED,
            alreadyPresentPublicationRetention,
            BackupAcknowledgementState.ALREADY_PRESENT,
            null);
    ByteArrayOutputStream alreadyPresentJson = new ByteArrayOutputStream();
    writer(alreadyPresentJson).writeBackupBookResult(alreadyPresent, OutputMode.JSON);
    assertJsonContains(alreadyPresentJson, "\"acknowledgementState\":\"already-present\"");
    assertJsonContains(alreadyPresentJson, "\"pairPublicationCompletion\":\"published\"");
    assertFalse(alreadyPresentJson.toString(StandardCharsets.UTF_8).contains("retainedStage"));
    assertJsonContains(alreadyPresentJson, "\"publicationTransaction\"");
    assertJsonContains(alreadyPresentJson, "\"attestationCommit\":null");
    ByteArrayOutputStream alreadyPresentText = new ByteArrayOutputStream();
    writer(alreadyPresentText).writeBackupBookResult(alreadyPresent, OutputMode.TEXT);
    assertTrue(
        alreadyPresentText
            .toString(StandardCharsets.UTF_8)
            .contains("No operation appended (acknowledgement replay)"));
    assertTrue(
        alreadyPresentText
            .toString(StandardCharsets.UTF_8)
            .contains("Pair publication completion"));
    assertTrue(alreadyPresentText.toString(StandardCharsets.UTF_8).contains("Published book file"));
    assertTrue(
        alreadyPresentText
            .toString(StandardCharsets.UTF_8)
            .contains("Published generated-secret file"));

    RestoreBookResult.Restored restored =
        new RestoreBookResult.Restored(
            BOOK_FILE,
            Path.of("keys", "restored.key"),
            attestationCommit(),
            ProtectedBookPairPublicationCompletion.RECOVERED,
            CliFixtureSupport.pairPublication(BOOK_FILE, Path.of("keys", "restored.key")));
    ByteArrayOutputStream restoredText = new ByteArrayOutputStream();
    writer(restoredText).writeRestoreBookResult(restored, OutputMode.TEXT);
    assertTrue(restoredText.toString(StandardCharsets.UTF_8).contains("Book Restored"));
    assertTrue(restoredText.toString(StandardCharsets.UTF_8).contains("recovered"));
    assertThrows(
        IllegalArgumentException.class,
        () -> writer(new ByteArrayOutputStream()).writeRestoreBookResult(restored, OutputMode.CSV));
    assertEquals(0, CliAdministrativeExitCodes.exitCodeFor(restored));

    BackupBookResult.Rejected conflict =
        new BackupBookResult.Rejected(
            new BookMaintenanceRejection.BackupAcknowledgementConflict(BACKUP_ID));
    ByteArrayOutputStream rejected = new ByteArrayOutputStream();
    writer(rejected).writeBackupBookResult(conflict, OutputMode.JSON);
    assertJsonContains(rejected, "\"code\":\"backup-acknowledgement-conflict\"");
    assertJsonContains(rejected, "\"backupId\":\"" + BACKUP_ID + "\"");
    assertEquals(7, CliAdministrativeExitCodes.exitCodeFor(conflict));
  }

  @Test
  void writesAlreadyPublishedResumeWithoutFinGrindPublicationFacts() {
    BackupBookResult.BackedUp alreadyPublishedResume =
        new BackupBookResult.BackedUp(
            BOOK_FILE,
            BACKUP_FILE,
            BACKUP_KEY_FILE,
            BACKUP_ID,
            ProtectedBookPairPublicationCompletion.ALREADY_PUBLISHED,
            null,
            BackupAcknowledgementState.RESUMED,
            null);

    ByteArrayOutputStream json = new ByteArrayOutputStream();
    writer(json).writeBackupBookResult(alreadyPublishedResume, OutputMode.JSON);
    assertJsonContains(json, "\"pairPublicationCompletion\":\"already-published\"");
    assertJsonContains(json, "\"pairPublication\":null");

    ByteArrayOutputStream text = new ByteArrayOutputStream();
    writer(text).writeBackupBookResult(alreadyPublishedResume, OutputMode.TEXT);
    assertTrue(
        text.toString(StandardCharsets.UTF_8).contains("No FinGrind publication transaction"));
  }

  @Test
  void writesPublishedBackupAcknowledgementAuthorizationRefusalAsAnExactRejection() {
    BackupBookResult.AcknowledgementAuthorizationRejected rejected =
        new BackupBookResult.AcknowledgementAuthorizationRejected(
            BOOK_FILE,
            BACKUP_FILE,
            BACKUP_KEY_FILE,
            BACKUP_ID,
            ProtectedBookPairPublicationCompletion.PUBLISHED,
            CliFixtureSupport.pairPublication(BACKUP_FILE, BACKUP_KEY_FILE),
            AttestationVerificationFailure.QUORUM_BELOW);

    ByteArrayOutputStream json = new ByteArrayOutputStream();
    writer(json).writeBackupBookResult(rejected, OutputMode.JSON);
    assertJsonContains(json, "\"status\":\"rejected\"");
    assertJsonContains(json, "\"code\":\"attestation-quorum-below\"");
    assertJsonContains(json, "\"pairPublicationCompletion\":\"published\"");

    ByteArrayOutputStream text = new ByteArrayOutputStream();
    writer(text).writeBackupBookResult(rejected, OutputMode.TEXT);
    assertTrue(text.toString(StandardCharsets.UTF_8).contains("published backup pair"));
    assertTrue(text.toString(StandardCharsets.UTF_8).contains("Pair publication completion"));
    assertEquals(2, CliAdministrativeExitCodes.exitCodeFor(rejected));
  }

  private static CliMaintenanceMutationResponseWriter writer(ByteArrayOutputStream outputStream) {
    return new CliMaintenanceMutationResponseWriter(outputChannel(outputStream));
  }
}
