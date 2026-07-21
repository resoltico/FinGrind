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
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenancePathFailure;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceVerificationFailure;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Focused maintenance-path failure coverage extracted from the omnibus maintenance test. */
class CliMaintenancePathFailureCoverageTest extends CliResponseWriterTestSupport {
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
                new BookMaintenanceRejection.ArtifactPathInvalid(
                    BookMaintenanceArtifactRole.BACKUP_TARGET,
                    backup,
                    BookMaintenancePathFailure.PARENT_PATH_COLLISION),
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
                BookMaintenancePathFailure.PARENT_PATH_COLLISION));

    assertNotNull(envelope.hint());
    assertTrue(envelope.hint().contains("parent chain is made only of real directories"));
    assertInstanceOf(CliArtifactPathFailureDetails.class, envelope.details());
  }

  @Test
  void maintenanceRejectionPayloadMapper_coversEveryArtifactPathFailureHintVariant() {
    assertMaintenanceHint(
        BookMaintenancePathFailure.MISSING_PARENT_DIRECTORY,
        "already exists or whose missing parent chain");
    assertMaintenanceHint(
        BookMaintenancePathFailure.PARENT_PATH_COLLISION, "made only of real directories");
    assertMaintenanceHint(
        BookMaintenancePathFailure.PARENT_OWNER_ACCESS_REQUIRED, "owner can traverse and write");
    assertMaintenanceHint(
        BookMaintenancePathFailure.PARENT_OWNER_ONLY_REQUIRED, "tighten the existing parent");
    assertMaintenanceHint(
        BookMaintenancePathFailure.TARGET_MUST_BE_REGULAR_NON_SYMLINK_FILE,
        "regular non-symlink artifact path");
    assertMaintenanceHint(
        BookMaintenancePathFailure.UNSUPPORTED_SECURE_FILESYSTEM,
        "supports POSIX owner-only permissions or Windows owner-only ACLs");
    assertMaintenanceHint(
        BookMaintenancePathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED,
        "supports atomic no-replace secret publication");
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
                    BookMaintenancePathFailure.PARENT_PATH_COLLISION))));
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
    List<CliRejectionJsonModels.MaintenanceRejectionDetails> details =
        List.of(
            new CliRejectionJsonModels.BookFileDetails("/tmp/book.sqlite"),
            new CliRejectionJsonModels.BookAndBackupFileDetails(
                "/tmp/book.sqlite", "/tmp/backup.sqlite"),
            new CliRejectionJsonModels.BlockingArtifactsDetails(
                "/tmp/book.sqlite", List.of("/tmp/book.sqlite-wal")),
            new CliArtifactPathFailureDetails(
                "backup-target", "/tmp/backup.sqlite", "parent-path-collision"),
            new CliRejectionJsonModels.ArtifactBusyDetails("live-book", "/tmp/book.sqlite"),
            new CliRejectionJsonModels.BackupAcknowledgementConflictDetails("backup-42"),
            new CliRejectionJsonModels.BackupFileDetails("/tmp/backup.sqlite"),
            new CliRejectionJsonModels.SecretTargetDetails("/tmp/new-book.key"),
            new CliRejectionJsonModels.ArtifactVerificationFailureDetails(
                "backup-target", "/tmp/backup.sqlite", "missing"));

    for (CliRejectionJsonModels.MaintenanceRejectionDetails detail : details) {
      List<List<String>> rows = new java.util.ArrayList<>();
      CliMaintenanceFailureOutputRenderer.appendRows(rows, detail);
      assertFalse(rows.isEmpty(), detail::toString);
    }

    assertThrows(
        IllegalArgumentException.class,
        () -> new CliRejectionJsonModels.BlockingArtifactsDetails("/tmp/book.sqlite", List.of()));
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
        CliRejectionJsonModels.SecretTargetDetails.class, secretTargetEnvelope.details());

    CliEnvelopeJsonModels.Envelope<?> destinationBookEnvelope =
        CliMaintenanceRejectionPayloadMapper.rejectedEnvelope(
            new BookMaintenanceRejection.BookDestinationOccupied(hint(Path.of("book.sqlite"))));
    assertNotNull(destinationBookEnvelope.hint());
    String destinationBookHint = Objects.requireNonNull(destinationBookEnvelope.hint());
    assertTrue(destinationBookHint.contains("absent destination book path"));
    assertInstanceOf(
        CliRejectionJsonModels.BookFileDetails.class, destinationBookEnvelope.details());
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
        new BookMaintenanceRejection.BookDestinationOccupied(hint(Path.of("book.sqlite"))));
    assertExitCodes(
        6,
        new BookMaintenanceRejection.ArtifactPathInvalid(
            BookMaintenanceArtifactRole.BACKUP_TARGET,
            hint(Path.of("backup.sqlite")),
            BookMaintenancePathFailure.PARENT_PATH_COLLISION),
        new BookMaintenanceRejection.ArtifactVerificationFailed(
            BookMaintenanceArtifactRole.BACKUP_TARGET,
            hint(Path.of("backup.sqlite")),
            BookMaintenanceVerificationFailure.MISSING));
    assertExitCodes(
        2,
        new BookMaintenanceRejection.BackupSourceMatchesLiveBook(
            hint(Path.of("book.sqlite")), hint(Path.of("backup.sqlite"))));
  }

  private static void assertMaintenanceHint(
      BookMaintenancePathFailure pathFailure, String expectedHintFragment) {
    CliEnvelopeJsonModels.Envelope<?> envelope =
        CliRejectionPayloadMapper.maintenanceRejectedEnvelope(
            new BookMaintenanceRejection.ArtifactPathInvalid(
                dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole.BACKUP_TARGET,
                hint(Path.of("backup/entity.sqlite")),
                pathFailure));
    assertNotNull(envelope.hint());
    assertTrue(envelope.hint().contains(expectedHintFragment), envelope.hint());
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
