package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.SqliteProtectedBookMaintenanceArtifactAssertions.requireAbsent;
import static dev.erst.fingrind.cli.SqliteProtectedBookMaintenanceArtifactAssertions.requireUnchanged;
import static dev.erst.fingrind.cli.SqliteProtectedBookMaintenanceOutcomeAssertions.requireAcceptedResult;
import static dev.erst.fingrind.cli.SqliteProtectedBookMaintenanceOutcomeAssertions.requireArtifactVerificationFailure;
import static dev.erst.fingrind.cli.SqliteProtectedBookMaintenanceOutcomeAssertions.requireDestinationOccupied;
import static dev.erst.fingrind.cli.SqliteProtectedBookMaintenanceOutcomeAssertions.requireSecretTargetOccupied;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BackupAcknowledgementState;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRetention;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.jazzer.support.JazzerTestFixturePaths;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Locks every fuzz-selected protected-book maintenance scenario into the regular test suite. */
class SqliteProtectedBookMaintenanceFuzzAssertionsTest {
  @TempDir Path tempDirectory;

  @Test
  void exercise_coversEveryProtectedBookMaintenanceScenario() throws Exception {
    Path fixtureDirectory = JazzerTestFixturePaths.canonicalExistingDirectory(tempDirectory);
    assertDoesNotThrow(
        () ->
            SqliteProtectedBookMaintenanceFuzzAssertions.exercise(
                new byte[] {2}, fixtureDirectory.resolve("scenario-destination-collision")));
    assertDoesNotThrow(
        () ->
            SqliteProtectedBookMaintenanceFuzzAssertions.exercise(
                new byte[] {4}, fixtureDirectory.resolve("scenario-independent")));
    assertDoesNotThrow(
        () ->
            SqliteProtectedBookMaintenanceFuzzAssertions.exercise(
                new byte[] {0}, fixtureDirectory.resolve("scenario-unattested-backup")));
    assertDoesNotThrow(
        () ->
            SqliteProtectedBookMaintenanceFuzzAssertions.exercise(
                new byte[] {1}, fixtureDirectory.resolve("scenario-secret-collision")));
    assertDoesNotThrow(
        () ->
            SqliteProtectedBookMaintenanceFuzzAssertions.exercise(
                new byte[] {3}, fixtureDirectory.resolve("scenario-rekey-backup-restore")));
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
                    ProtectedBookPairPublicationCompletion.PUBLISHED,
                    retention(path.resolveSibling("backup"), path.resolveSibling("key")),
                    BackupAcknowledgementState.ACKNOWLEDGED,
                    CliFuzzAttestationFixtures.syntheticTrustRootCommitment())));
    assertThrows(
        IllegalStateException.class,
        () ->
            requireSecretTargetOccupied(
                new BackupBookResult.Rejected(
                    new BookMaintenanceRejection.BackupDestinationAlreadyExists(path))));
    assertThrows(
        IllegalStateException.class,
        () ->
            requireSecretTargetOccupied(
                new RekeyBookResult.Rekeyed(
                    path,
                    path.resolveSibling("key"),
                    CliFuzzAttestationFixtures.syntheticTrustRootCommitment(),
                    ProtectedBookPairPublicationCompletion.PUBLISHED,
                    retention(path, path.resolveSibling("key")))));
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
                new RestoreBookResult.Restored(
                    path,
                    path.resolveSibling("key"),
                    CliFuzzAttestationFixtures.syntheticTrustRootCommitment(),
                    ProtectedBookPairPublicationCompletion.PUBLISHED,
                    retention(path, path.resolveSibling("key")))));
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
                new RestoreBookResult.Restored(
                    path,
                    path.resolveSibling("key"),
                    CliFuzzAttestationFixtures.syntheticTrustRootCommitment(),
                    ProtectedBookPairPublicationCompletion.PUBLISHED,
                    retention(path, path.resolveSibling("key")))));
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

  private static ProtectedBookPairPublicationRetention retention(
      Path bookFinalArtifactPath, Path generatedSecretFinalArtifactPath) {
    return new ProtectedBookPairPublicationRetention(
        new ArtifactPublicationResult(
            bookFinalArtifactPath,
            new ArtifactPublicationRetention(
                bookFinalArtifactPath
                    .toAbsolutePath()
                    .normalize()
                    .resolveSibling(".fingrind-fuzz-retained-book.stage"))),
        new ArtifactPublicationResult(
            generatedSecretFinalArtifactPath,
            new ArtifactPublicationRetention(
                generatedSecretFinalArtifactPath
                    .toAbsolutePath()
                    .normalize()
                    .resolveSibling(".fingrind-fuzz-retained-secret.stage"))));
  }
}
