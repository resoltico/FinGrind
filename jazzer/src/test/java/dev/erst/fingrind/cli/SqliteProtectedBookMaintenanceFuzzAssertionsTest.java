package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.SqliteProtectedBookMaintenanceArtifactAssertions.requireAbsent;
import static dev.erst.fingrind.cli.SqliteProtectedBookMaintenanceArtifactAssertions.requireUnchanged;
import static dev.erst.fingrind.cli.SqliteProtectedBookMaintenanceOutcomeAssertions.requireAcceptedResult;
import static dev.erst.fingrind.cli.SqliteProtectedBookMaintenanceOutcomeAssertions.requireArtifactVerificationFailure;
import static dev.erst.fingrind.cli.SqliteProtectedBookMaintenanceOutcomeAssertions.requireDestinationOccupied;
import static dev.erst.fingrind.cli.SqliteProtectedBookMaintenanceOutcomeAssertions.requireSecretTargetOccupied;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.sqlite.SqliteFuzzAssertions;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Locks every fuzz-selected protected-book maintenance scenario into the regular test suite. */
class SqliteProtectedBookMaintenanceFuzzAssertionsTest {
  @TempDir Path tempDirectory;

  @Test
  void exercise_coversEveryProtectedBookMaintenanceScenario() throws Exception {
    SqliteFuzzAssertions.prepareSecureArtifactDirectory(tempDirectory);
    assertDoesNotThrow(
        () ->
            SqliteProtectedBookMaintenanceFuzzAssertions.exercise(
                new byte[] {2}, tempDirectory.resolve("scenario-destination-collision")));
    assertDoesNotThrow(
        () ->
            SqliteProtectedBookMaintenanceFuzzAssertions.exercise(
                new byte[] {4}, tempDirectory.resolve("scenario-independent")));
    assertDoesNotThrow(
        () ->
            SqliteProtectedBookMaintenanceFuzzAssertions.exercise(
                new byte[] {0}, tempDirectory.resolve("scenario-unattested-backup")));
    assertDoesNotThrow(
        () ->
            SqliteProtectedBookMaintenanceFuzzAssertions.exercise(
                new byte[] {1}, tempDirectory.resolve("scenario-secret-collision")));
    assertDoesNotThrow(
        () ->
            SqliteProtectedBookMaintenanceFuzzAssertions.exercise(
                new byte[] {3}, tempDirectory.resolve("scenario-rekey-backup-restore")));
  }

  @Test
  void defensiveAssertions_detectMismatchedMaintenanceOutcomesAndArtifacts() throws Exception {
    Path path = tempDirectory.resolve("artifact");
    Files.writeString(path, "actual");

    assertThrows(
        IllegalStateException.class,
        () ->
            requireAcceptedResult(
                new BackupBookResult.Rejected(
                    new BookMaintenanceRejection.SecretTargetOccupied(path)),
                BackupBookResult.BackedUp.class,
                "backup"));
    assertThrows(
        IllegalStateException.class,
        () ->
            requireAcceptedResult(
                new RestoreBookResult.Rejected(
                    new BookMaintenanceRejection.BookDestinationOccupied(path)),
                RestoreBookResult.Restored.class,
                "restore"));
    assertThrows(
        IllegalStateException.class,
        () ->
            requireSecretTargetOccupied(
                new BackupBookResult.BackedUp(
                    path,
                    path.resolveSibling("backup"),
                    path.resolveSibling("key"),
                    java.util.UUID.fromString("b89812f3-5389-4b9a-8d67-1d60bd41a8ce"),
                    false)));
    assertThrows(
        IllegalStateException.class,
        () ->
            requireSecretTargetOccupied(
                new BackupBookResult.Rejected(
                    new BookMaintenanceRejection.BackupDestinationAlreadyExists(path))));
    assertThrows(
        IllegalStateException.class,
        () -> requireSecretTargetOccupied(new RekeyBookResult.Rekeyed(path)));
    assertThrows(
        IllegalStateException.class,
        () ->
            requireSecretTargetOccupied(
                new RekeyBookResult.Rejected(
                    new BookMaintenanceRejection.BookDestinationOccupied(path))));
    assertThrows(
        IllegalStateException.class,
        () ->
            requireDestinationOccupied(
                new RestoreBookResult.Restored(path, path.resolveSibling("key"))));
    assertThrows(
        IllegalStateException.class,
        () ->
            requireDestinationOccupied(
                new RestoreBookResult.Rejected(
                    new BookMaintenanceRejection.SecretTargetOccupied(path))));
    assertThrows(
        IllegalStateException.class,
        () ->
            requireArtifactVerificationFailure(
                new RestoreBookResult.Restored(path, path.resolveSibling("key"))));
    assertThrows(
        IllegalStateException.class,
        () ->
            requireArtifactVerificationFailure(
                new RestoreBookResult.Rejected(
                    new BookMaintenanceRejection.SecretTargetOccupied(path))));
    assertDoesNotThrow(
        () ->
            requireArtifactVerificationFailure(
                new RestoreBookResult.Rejected(
                    new BookMaintenanceRejection.ArtifactVerificationFailed(
                        BookMaintenanceArtifactRole.BACKUP_SOURCE,
                        path,
                        BookMaintenanceVerificationFailure.MISSING))));
    assertThrows(
        IllegalStateException.class,
        () ->
            requireUnchanged(
                path,
                "expected".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "expected drift rejection"));
    assertThrows(
        IllegalStateException.class, () -> requireAbsent(path, "expected occupied rejection"));
  }
}
