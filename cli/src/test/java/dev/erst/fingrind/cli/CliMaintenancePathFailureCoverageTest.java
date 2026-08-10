package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliArtifactPathFailureDetails;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliMaintenanceRejectionJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.PublicationPathFailure;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Focused maintenance-path failure coverage extracted from the omnibus maintenance test. */
class CliMaintenancePathFailureCoverageTest extends CliResponseWriterTestSupport {
  private static final Map<PublicationPathFailure, String> EXPECTED_MAINTENANCE_HINTS =
      Map.ofEntries(
          Map.entry(
              PublicationPathFailure.MISSING_PARENT_DIRECTORY,
              "Create and secure the selected parent directory yourself, then choose a path beneath it and rerun the maintenance command."),
          Map.entry(
              PublicationPathFailure.PARENT_PATH_COLLISION,
              "Choose a path whose parent chain is made only of real directories, not existing files or symlinks, then rerun the maintenance command."),
          Map.entry(
              PublicationPathFailure.PARENT_OWNER_ACCESS_REQUIRED,
              "Choose a path beneath a parent directory that the owner can traverse and write, then rerun the maintenance command."),
          Map.entry(
              PublicationPathFailure.PARENT_OWNER_ONLY_REQUIRED,
              "Choose a path beneath an owner-only parent directory, or tighten the existing parent directory first, then rerun the maintenance command."),
          Map.entry(
              PublicationPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE,
              "Choose a regular non-symlink artifact path for this maintenance workflow, then rerun the command."),
          Map.entry(
              PublicationPathFailure.TARGET_OWNER_ONLY_REQUIRED,
              "Tighten the selected artifact to owner-only permissions, then rerun the maintenance command."),
          Map.entry(
              PublicationPathFailure.TARGET_IDENTITY_UNESTABLISHED,
              "Choose protected-book and generated-secret target paths whose distinct filesystem identities can be established, then rerun the maintenance command."),
          Map.entry(
              PublicationPathFailure.SOURCE_ARTIFACT_IDENTITY_DUPLICATED,
              "Choose distinct source artifacts: two selected source roles resolve to the same physical file, then rerun the maintenance command."),
          Map.entry(
              PublicationPathFailure.SOURCE_ARTIFACT_IDENTITY_CHANGED,
              "Keep every selected source stable, restore the trustworthy intended source if it changed, then rerun the complete maintenance command."),
          Map.entry(
              PublicationPathFailure.UNSUPPORTED_SECURE_FILESYSTEM,
              "Choose a path on a filesystem that supports POSIX owner-only permissions or Windows owner-only ACLs, then rerun the maintenance command."),
          Map.entry(
              PublicationPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
              "Choose a path on a filesystem that supports atomically creating owner-only FinGrind protocol files, then rerun the maintenance command."),
          Map.entry(
              PublicationPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED,
              "Choose a path on a filesystem that supports atomic no-replace secret publication, then rerun the maintenance command."),
          Map.entry(
              PublicationPathFailure.ATOMIC_ARTIFACT_PUBLICATION_UNSUPPORTED,
              "Choose a path on a filesystem that supports atomic no-replace protected-book publication, then rerun the maintenance command."),
          Map.entry(
              PublicationPathFailure.ATOMIC_ARTIFACT_REPLACEMENT_UNSUPPORTED,
              "Choose a path on a filesystem that supports atomic protected-book replacement, then rerun the maintenance command."));

  @Test
  void everyMaintenanceRejectionPublishesActualTypedPathsAndRedactsOnlyText() {
    Path book = Path.of("books", "live.sqlite");
    Path backup = Path.of("backups", "source.sqlite");
    Path sidecar = Path.of("books", "live.sqlite-wal");
    List<MaintenancePathCase> cases =
        List.of(
            new MaintenancePathCase(
                new BookMaintenanceRejection.BookHasBlockingArtifacts(book, List.of(sidecar)),
                book,
                List.of(sidecar)),
            new MaintenancePathCase(
                new BookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
                    backup, List.of(sidecar)),
                backup,
                List.of(sidecar)),
            new MaintenancePathCase(
                new BookMaintenanceRejection.BackupSourceMatchesLiveBook(book, backup),
                book,
                List.of(backup)),
            new MaintenancePathCase(
                new BookMaintenanceRejection.PairTargetsConflict(
                    Path.of("targets", "Book.sqlite"), Path.of("targets", "book.sqlite")),
                Path.of("targets", "Book.sqlite"),
                List.of(Path.of("targets", "book.sqlite"))),
            new MaintenancePathCase(
                new BookMaintenanceRejection.ArtifactPathInvalid(
                    BookMaintenanceArtifactRole.BACKUP_TARGET,
                    backup,
                    PublicationPathFailure.PARENT_PATH_COLLISION),
                backup,
                List.of()),
            new MaintenancePathCase(
                new BookMaintenanceRejection.ArtifactBusy(
                    BookMaintenanceArtifactRole.LIVE_BOOK, book),
                book,
                List.of()),
            new MaintenancePathCase(
                new BookMaintenanceRejection.BackupDestinationAlreadyExists(backup),
                backup,
                List.of()),
            new MaintenancePathCase(
                new BookMaintenanceRejection.SecretTargetOccupied(Path.of("books", "new.key")),
                Path.of("books", "new.key"),
                List.of()),
            new MaintenancePathCase(
                new BookMaintenanceRejection.BookDestinationOccupied(book), book, List.of()),
            new MaintenancePathCase(
                new BookMaintenanceRejection.RecoveryPending(
                    OperationId.RESTORE_BOOK, book, Path.of("books", "recovered.key")),
                book,
                List.of(Path.of("books", "recovered.key"))),
            new MaintenancePathCase(
                new BookMaintenanceRejection.ArtifactVerificationFailed(
                    BookMaintenanceArtifactRole.BACKUP_TARGET,
                    backup,
                    BookMaintenanceVerificationFailure.MISSING),
                backup,
                List.of()));

    for (MaintenancePathCase pathCase : cases) {
      CliEnvelopeJsonModels.Envelope<?> envelope =
          CliMaintenanceRejectionPayloadMapper.rejectedEnvelope(pathCase.rejection());
      assertEquals(CliPublicPaths.absoluteValue(pathCase.primary()), envelope.path());
      assertEquals(
          pathCase.related().stream().map(CliPublicPaths::absoluteValue).toList(),
          envelope.relatedPaths());
      String json = CliWireJson.jsonText(envelope);
      assertTrue(json.contains(Objects.requireNonNull(envelope.path())), json);
      assertFalse(json.contains("<redacted>"), json);

      String text =
          CliFailureOutputRenderer.renderRejectedText(
              Objects.requireNonNull(envelope.code()),
              Objects.requireNonNull(envelope.message()),
              envelope.hint(),
              envelope.idempotencyKey(),
              (CliRejectionJsonModels.RejectionDetails) envelope.details());
      assertTrue(text.contains("<redacted>"), text);
      assertFalse(text.contains(Objects.requireNonNull(envelope.path())), text);
    }
  }

  @Test
  void maintenanceRejectionPayloadMapper_rendersArtifactPathFailureHintAndDetails() {
    CliEnvelopeJsonModels.Envelope<?> envelope =
        CliRejectionPayloadMapper.maintenanceRejectedEnvelope(
            new BookMaintenanceRejection.ArtifactPathInvalid(
                dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole.BACKUP_TARGET,
                hint(Path.of("backup/entity.sqlite")),
                PublicationPathFailure.PARENT_PATH_COLLISION));

    assertNotNull(envelope.hint());
    assertTrue(envelope.hint().contains("parent chain is made only of real directories"));
    assertInstanceOf(CliArtifactPathFailureDetails.class, envelope.details());
  }

  @Test
  void maintenanceRejectionPayloadMapper_coversEveryArtifactPathFailureHintVariant() {
    for (PublicationPathFailure pathFailure : PublicationPathFailure.values()) {
      assertMaintenanceHint(pathFailure, expectedMaintenanceHint(pathFailure));
    }
  }

  @Test
  void maintenancePathHints_requireACompleteFailureVocabulary() {
    assertEquals(
        EXPECTED_MAINTENANCE_HINTS,
        CliMaintenancePathFailureHint.requireCompleteHints(EXPECTED_MAINTENANCE_HINTS));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            CliMaintenancePathFailureHint.requireCompleteHints(
                Map.of(
                    PublicationPathFailure.MISSING_PARENT_DIRECTORY,
                    expectedMaintenanceHint(PublicationPathFailure.MISSING_PARENT_DIRECTORY))));
  }

  @Test
  void maintenanceExitCodes_mapArtifactPathInvalidToExitCodeSix() {
    assertEquals(
        6,
        CliAdministrativeExitCodes.exitCodeFor(
            new BackupBookResult.Rejected(
                new BookMaintenanceRejection.ArtifactPathInvalid(
                    dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole
                        .BACKUP_TARGET,
                    hint(Path.of("backup/entity.sqlite")),
                    PublicationPathFailure.PARENT_PATH_COLLISION))));
  }

  @Test
  void pairTargetIdentityFailuresPublishDistinctAliasSpellingsAndTypedPublicDetails() {
    Path bookTarget = hint(Path.of("targets", "Book.sqlite"));
    Path secretTarget = hint(Path.of("targets", "book.sqlite"));

    CliEnvelopeJsonModels.Envelope<?> conflictEnvelope =
        CliMaintenanceRejectionPayloadMapper.rejectedEnvelope(
            new BookMaintenanceRejection.PairTargetsConflict(bookTarget, secretTarget));

    assertEquals("pair-targets-conflict", conflictEnvelope.code());
    assertEquals(CliPublicPaths.absoluteValue(bookTarget), conflictEnvelope.path());
    assertEquals(
        List.of(CliPublicPaths.absoluteValue(secretTarget)), conflictEnvelope.relatedPaths());
    assertEquals(
        2,
        CliMaintenanceExitCodes.exitCodeFor(
            new BookMaintenanceRejection.PairTargetsConflict(bookTarget, secretTarget)));
    CliMaintenanceRejectionJsonModels.PairTargetsConflictDetails conflictDetails =
        assertInstanceOf(
            CliMaintenanceRejectionJsonModels.PairTargetsConflictDetails.class,
            conflictEnvelope.details());
    assertEquals(CliPublicPaths.absoluteValue(bookTarget), conflictDetails.bookTarget());
    assertEquals(
        CliPublicPaths.absoluteValue(secretTarget), conflictDetails.generatedSecretTarget());

    CliEnvelopeJsonModels.Envelope<?> unavailableIdentityEnvelope =
        CliMaintenanceRejectionPayloadMapper.rejectedEnvelope(
            new BookMaintenanceRejection.ArtifactPathInvalid(
                BookMaintenanceArtifactRole.BACKUP_TARGET,
                bookTarget,
                PublicationPathFailure.TARGET_IDENTITY_UNESTABLISHED));

    assertEquals("artifact-path-invalid", unavailableIdentityEnvelope.code());
    assertEquals(CliPublicPaths.absoluteValue(bookTarget), unavailableIdentityEnvelope.path());
    assertEquals(List.of(), unavailableIdentityEnvelope.relatedPaths());
    assertEquals(
        6,
        CliMaintenanceExitCodes.exitCodeFor(
            new BookMaintenanceRejection.ArtifactPathInvalid(
                BookMaintenanceArtifactRole.BACKUP_TARGET,
                bookTarget,
                PublicationPathFailure.TARGET_IDENTITY_UNESTABLISHED)));
    CliArtifactPathFailureDetails unavailableIdentityDetails =
        assertInstanceOf(
            CliArtifactPathFailureDetails.class, unavailableIdentityEnvelope.details());
    assertEquals("target-identity-unestablished", unavailableIdentityDetails.pathFailure());
  }

  @Test
  void failureOutputRenderer_rendersArtifactPathFailureRows() {
    String rendered =
        CliFailureOutputRenderer.renderRejectedText(
            "maintenance-rejected",
            "Maintenance rejected.",
            "Repair it.",
            null,
            new CliArtifactPathFailureDetails(
                "backup-target", "<redacted>/backup.sqlite", "parent-path-collision"));

    assertTrue(rendered.contains("Artifact role"));
    assertTrue(rendered.contains("backup-target"));
    assertTrue(rendered.contains("Artifact path"));
    assertTrue(rendered.contains("<redacted>/backup.sqlite"));
    assertTrue(rendered.contains("Path failure"));
    assertTrue(rendered.contains("parent-path-collision"));
  }

  @Test
  void maintenanceFailureRows_renderEveryClosedDetailShape() {
    List<CliMaintenanceRejectionJsonModels.MaintenanceRejectionDetails> details =
        List.of(
            new CliMaintenanceRejectionJsonModels.BookFileDetails("/tmp/book.sqlite"),
            new CliMaintenanceRejectionJsonModels.BookAndBackupFileDetails(
                "/tmp/book.sqlite", "/tmp/backup.sqlite"),
            new CliMaintenanceRejectionJsonModels.BlockingArtifactsDetails(
                "/tmp/book.sqlite", List.of("/tmp/book.sqlite-wal")),
            new CliArtifactPathFailureDetails(
                "backup-target", "/tmp/backup.sqlite", "parent-path-collision"),
            new CliMaintenanceRejectionJsonModels.ArtifactBusyDetails(
                "live-book", "/tmp/book.sqlite"),
            new CliMaintenanceRejectionJsonModels.BackupAcknowledgementConflictDetails("backup-42"),
            new CliMaintenanceRejectionJsonModels.BackupFileDetails("/tmp/backup.sqlite"),
            new CliMaintenanceRejectionJsonModels.SecretTargetDetails("/tmp/new-book.key"),
            new CliMaintenanceRejectionJsonModels.RecoveryPendingDetails(
                "restore-book", "/tmp/book.sqlite", "/tmp/book.key"),
            new CliMaintenanceRejectionJsonModels.ArtifactVerificationFailureDetails(
                "backup-target", "/tmp/backup.sqlite", "missing"));

    List<List<String>> rows = new java.util.ArrayList<>();
    for (CliMaintenanceRejectionJsonModels.MaintenanceRejectionDetails detail : details) {
      rows.clear();
      CliMaintenanceFailureOutputRenderer.appendRows(rows, detail);
      assertFalse(rows.isEmpty(), detail::toString);
    }

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliMaintenanceRejectionJsonModels.BlockingArtifactsDetails(
                "/tmp/book.sqlite", List.of()));
  }

  @Test
  void standardInputUsage_acceptsKeyFileAndPromptSourcesForRequestStandardInput() {
    assertDoesNotThrow(
        () ->
            CliStandardInputUsageRules.validateStandardInputUsage(
                new BookAccess.PassphraseSource.KeyFile(Path.of("book.key")), Path.of("-")));
    assertDoesNotThrow(
        () ->
            CliStandardInputUsageRules.validateStandardInputUsage(
                BookAccess.PassphraseSource.InteractivePrompt.INSTANCE, Path.of("-")));

    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliStandardInputUsageRules.validateStandardInputUsage(
                    BookAccess.PassphraseSource.StandardInput.INSTANCE, Path.of("-")));

    assertEquals("--book-passphrase-stdin", exception.argument());
  }

  @Test
  void maintenanceRejectionPayloadMapper_rendersBothOccupiedTargetShapes() {
    CliEnvelopeJsonModels.Envelope<?> secretTargetEnvelope =
        CliMaintenanceRejectionPayloadMapper.rejectedEnvelope(
            new BookMaintenanceRejection.SecretTargetOccupied(hint(Path.of("new-book.key"))));
    assertNotNull(secretTargetEnvelope.hint());
    String secretTargetHint = Objects.requireNonNull(secretTargetEnvelope.hint());
    assertTrue(secretTargetHint.contains("absent generated-secret target"));
    assertInstanceOf(
        CliMaintenanceRejectionJsonModels.SecretTargetDetails.class,
        secretTargetEnvelope.details());

    CliEnvelopeJsonModels.Envelope<?> destinationBookEnvelope =
        CliMaintenanceRejectionPayloadMapper.rejectedEnvelope(
            new BookMaintenanceRejection.BookDestinationOccupied(hint(Path.of("book.sqlite"))));
    assertNotNull(destinationBookEnvelope.hint());
    String destinationBookHint = Objects.requireNonNull(destinationBookEnvelope.hint());
    assertTrue(destinationBookHint.contains("absent destination book path"));
    assertInstanceOf(
        CliMaintenanceRejectionJsonModels.BookFileDetails.class, destinationBookEnvelope.details());
  }

  @Test
  void maintenanceRejectionPayloadMapper_rendersRecoveryPendingWithCompleteInputRule() {
    CliEnvelopeJsonModels.Envelope<?> envelope =
        CliMaintenanceRejectionPayloadMapper.rejectedEnvelope(
            new BookMaintenanceRejection.RecoveryPending(
                OperationId.BACKUP_BOOK,
                hint(Path.of("retained", "book.sqlite")),
                hint(Path.of("retained", "book.key"))));

    assertEquals("maintenance-recovery-pending", envelope.code());
    assertNotNull(envelope.hint());
    assertTrue(Objects.requireNonNull(envelope.hint()).contains("complete original inputs"));
    assertTrue(Objects.requireNonNull(envelope.hint()).contains("manually clean"));
    CliMaintenanceRejectionJsonModels.RecoveryPendingDetails details =
        assertInstanceOf(
            CliMaintenanceRejectionJsonModels.RecoveryPendingDetails.class, envelope.details());
    assertEquals("backup-book", details.recoveryOperation());
    assertEquals(
        CliPublicPaths.absoluteValue(Path.of("retained", "book.sqlite")), details.bookTarget());
    assertEquals(
        CliPublicPaths.absoluteValue(Path.of("retained", "book.key")),
        details.generatedSecretTarget());
    assertEquals(details.bookTarget(), envelope.path());
    assertEquals(List.of(details.generatedSecretTarget()), envelope.relatedPaths());
  }

  @Test
  void maintenanceExitCodes_coverEveryClosedRejectionFamily() {
    assertExitCodes(
        7,
        new BookMaintenanceRejection.BookHasBlockingArtifacts(
            hint(Path.of("book.sqlite")), List.of(hint(Path.of("book.sqlite-wal")))),
        new BookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
            hint(Path.of("backup.sqlite")), List.of(hint(Path.of("backup.sqlite-wal")))),
        new BookMaintenanceRejection.ArtifactBusy(
            BookMaintenanceArtifactRole.LIVE_BOOK, hint(Path.of("book.sqlite"))),
        new BookMaintenanceRejection.BackupDestinationAlreadyExists(hint(Path.of("backup.sqlite"))),
        new BookMaintenanceRejection.SecretTargetOccupied(hint(Path.of("new-book.key"))),
        new BookMaintenanceRejection.BookDestinationOccupied(hint(Path.of("book.sqlite"))),
        new BookMaintenanceRejection.RecoveryPending(
            OperationId.REKEY_BOOK, hint(Path.of("book.sqlite")), hint(Path.of("book.rekey.key"))));
    assertExitCodes(
        6,
        new BookMaintenanceRejection.ArtifactPathInvalid(
            BookMaintenanceArtifactRole.BACKUP_TARGET,
            hint(Path.of("backup.sqlite")),
            PublicationPathFailure.PARENT_PATH_COLLISION),
        new BookMaintenanceRejection.ArtifactPathInvalid(
            BookMaintenanceArtifactRole.BACKUP_TARGET,
            hint(Path.of("backup.sqlite")),
            PublicationPathFailure.TARGET_IDENTITY_UNESTABLISHED),
        new BookMaintenanceRejection.ArtifactPathInvalid(
            BookMaintenanceArtifactRole.BACKUP_SOURCE,
            hint(Path.of("backup.sqlite")),
            PublicationPathFailure.SOURCE_ARTIFACT_IDENTITY_DUPLICATED),
        new BookMaintenanceRejection.ArtifactPathInvalid(
            BookMaintenanceArtifactRole.BACKUP_SOURCE,
            hint(Path.of("backup.sqlite")),
            PublicationPathFailure.SOURCE_ARTIFACT_IDENTITY_CHANGED),
        new BookMaintenanceRejection.ArtifactVerificationFailed(
            BookMaintenanceArtifactRole.BACKUP_TARGET,
            hint(Path.of("backup.sqlite")),
            BookMaintenanceVerificationFailure.MISSING));
    assertExitCodes(
        2,
        new BookMaintenanceRejection.BackupSourceMatchesLiveBook(
            hint(Path.of("book.sqlite")), hint(Path.of("backup.sqlite"))),
        new BookMaintenanceRejection.PairTargetsConflict(
            hint(Path.of("targets", "Book.sqlite")), hint(Path.of("targets", "book.sqlite"))));
  }

  private static void assertMaintenanceHint(
      PublicationPathFailure pathFailure, String expectedHint) {
    CliEnvelopeJsonModels.Envelope<?> envelope =
        CliRejectionPayloadMapper.maintenanceRejectedEnvelope(
            new BookMaintenanceRejection.ArtifactPathInvalid(
                dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole.BACKUP_TARGET,
                hint(Path.of("backup/entity.sqlite")),
                pathFailure));
    assertNotNull(envelope.hint());
    assertEquals(expectedHint, envelope.hint());
  }

  private static String expectedMaintenanceHint(PublicationPathFailure pathFailure) {
    return Objects.requireNonNull(EXPECTED_MAINTENANCE_HINTS.get(pathFailure));
  }

  private static void assertExitCodes(
      int expectedExitCode, BookMaintenanceRejection... rejections) {
    for (BookMaintenanceRejection rejection : rejections) {
      assertEquals(expectedExitCode, CliMaintenanceExitCodes.exitCodeFor(rejection));
    }
  }

  private record MaintenancePathCase(
      BookMaintenanceRejection rejection, Path primary, List<Path> related) {}
}
