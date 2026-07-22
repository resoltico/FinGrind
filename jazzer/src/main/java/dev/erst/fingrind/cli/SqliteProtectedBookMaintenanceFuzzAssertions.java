package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.SqliteProtectedBookMaintenanceArtifactAssertions.requireAbsent;
import static dev.erst.fingrind.cli.SqliteProtectedBookMaintenanceArtifactAssertions.requireReadable;
import static dev.erst.fingrind.cli.SqliteProtectedBookMaintenanceArtifactAssertions.requireUnchanged;
import static dev.erst.fingrind.cli.SqliteProtectedBookMaintenanceArtifactAssertions.requireUnreadable;
import static dev.erst.fingrind.cli.SqliteProtectedBookMaintenanceOutcomeAssertions.requireAcceptedResult;
import static dev.erst.fingrind.cli.SqliteProtectedBookMaintenanceOutcomeAssertions.requireArtifactVerificationFailure;
import static dev.erst.fingrind.cli.SqliteProtectedBookMaintenanceOutcomeAssertions.requireDestinationOccupied;
import static dev.erst.fingrind.cli.SqliteProtectedBookMaintenanceOutcomeAssertions.requireSecretTargetOccupied;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.sqlite.SqliteFuzzAssertions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/** Exercises protected-book maintenance isolation and no-clobber contracts through Jazzer. */
final class SqliteProtectedBookMaintenanceFuzzAssertions {
  private static final java.util.UUID BACKUP_ID =
      java.util.UUID.fromString("d2e3518f-6016-40a8-9ae6-3a743fdbe281");

  private SqliteProtectedBookMaintenanceFuzzAssertions() {}

  /** Runs one fuzz-selected maintenance scenario against a freshly initialized protected book. */
  static void exercise(byte[] input, Path root) throws IOException {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(root, "root");
    SqliteFuzzAssertions.prepareSecureArtifactDirectory(root);

    Path sourceBookPath = root.resolve("source").resolve("entity.sqlite");
    Path sourceKeyPath = root.resolve("source").resolve("entity.key");
    SqliteFuzzAssertions.writeDeterministicBookKeyFile(sourceKeyPath);

    CliBookLifecycleWorkflow lifecycleWorkflow =
        SqliteRoundTripWorkflowResources.sqliteLifecycleWorkflow();
    CliBookReadWorkflow readWorkflow = SqliteRoundTripWorkflowResources.sqliteReadWorkflow();
    BookAccess sourceAccess =
        SqliteRoundTripWorkflowResources.keyFileBookAccess(sourceBookPath, sourceKeyPath);
    initializeBook(lifecycleWorkflow, sourceAccess);

    scenario(input)
        .exercise(
            lifecycleWorkflow, readWorkflow, sourceAccess, sourceBookPath, sourceKeyPath, root);
  }

  private static void initializeBook(
      CliBookLifecycleWorkflow lifecycleWorkflow, BookAccess sourceAccess) {
    lifecycleWorkflow
        .openBook(sourceAccess, CliFuzzWorkflowFixtures.openBookCommand())
        .requireAccepted();
  }

  static void exerciseIndependentBackupAndRestore(
      CliBookLifecycleWorkflow lifecycleWorkflow,
      CliBookReadWorkflow readWorkflow,
      BookAccess sourceAccess,
      Path root) {
    Path backupBookPath = root.resolve("backup").resolve("entity.sqlite");
    Path backupKeyPath = root.resolve("backup").resolve("entity.key");
    requireAcceptedResult(
        lifecycleWorkflow
            .backupBook(sourceAccess, backupBookPath, backupKeyPath, BACKUP_ID)
            .requireAccepted(),
        BackupBookResult.BackedUp.class,
        "backup");
    requireReadable(
        readWorkflow,
        SqliteRoundTripWorkflowResources.keyFileBookAccess(backupBookPath, backupKeyPath));
    requireUnreadable(
        readWorkflow,
        SqliteRoundTripWorkflowResources.keyFileBookAccess(
            backupBookPath, sourceAccess.bookFilePath().resolveSibling("entity.key")));

    Path restoredBookPath = root.resolve("restored").resolve("entity.sqlite");
    Path restoredKeyPath = root.resolve("restored").resolve("entity.key");
    requireAcceptedResult(
        lifecycleWorkflow
            .restoreBook(
                restoredBookPath,
                restoredKeyPath,
                backupBookPath,
                backupKeyPath,
                sourceAccess.attestationCredentialSources())
            .requireAccepted(),
        RestoreBookResult.Restored.class,
        "restore");
    requireReadable(
        readWorkflow,
        SqliteRoundTripWorkflowResources.keyFileBookAccess(restoredBookPath, restoredKeyPath));
    requireUnreadable(
        readWorkflow,
        SqliteRoundTripWorkflowResources.keyFileBookAccess(restoredBookPath, backupKeyPath));
  }

  static void exerciseUnattestedBackupRestoreRejection(
      CliBookLifecycleWorkflow lifecycleWorkflow,
      CliBookReadWorkflow readWorkflow,
      Path sourceBookPath,
      Path sourceKeyPath,
      Path root)
      throws IOException {
    Path unattestedBackupBookPath = root.resolve("unattested-backup").resolve("entity.sqlite");
    Path unattestedBackupKeyPath = root.resolve("unattested-backup").resolve("entity.key");
    Path unattestedBackupDirectory =
        Objects.requireNonNull(unattestedBackupKeyPath.getParent(), "unattested backup directory");
    SqliteFuzzAssertions.prepareSecureArtifactDirectory(unattestedBackupDirectory);
    Files.copy(sourceBookPath, unattestedBackupBookPath);
    Files.copy(sourceKeyPath, unattestedBackupKeyPath, StandardCopyOption.COPY_ATTRIBUTES);
    requireUnchanged(
        unattestedBackupKeyPath,
        Files.readAllBytes(sourceKeyPath),
        "The unattested backup fixture must retain the original source key artifact.");
    requireReadable(
        readWorkflow,
        SqliteRoundTripWorkflowResources.keyFileBookAccess(
            unattestedBackupBookPath, unattestedBackupKeyPath));

    Path restoredBookPath = root.resolve("unattested-restored").resolve("entity.sqlite");
    Path restoredKeyPath = root.resolve("unattested-restored").resolve("entity.key");
    RestoreBookResult restoreResult =
        lifecycleWorkflow
            .restoreBook(
                restoredBookPath,
                restoredKeyPath,
                unattestedBackupBookPath,
                unattestedBackupKeyPath,
                CliFuzzWorkflowFixtures.attestationCredentialSources())
            .requireAccepted();
    requireArtifactVerificationFailure(restoreResult);
    requireAbsent(
        restoredBookPath, "An unattested backup pair must not create a restored book artifact.");
    requireAbsent(
        restoredKeyPath, "An unattested backup pair must not create a restored key artifact.");
  }

  static void exerciseGeneratedSecretCollision(
      CliBookLifecycleWorkflow lifecycleWorkflow,
      BookAccess sourceAccess,
      Path sourceBookPath,
      Path root)
      throws IOException {
    Path backupBookPath = root.resolve("collision-backup").resolve("entity.sqlite");
    Path occupiedBackupKeyPath = root.resolve("collision-backup").resolve("entity.key");
    SqliteFuzzAssertions.writeDeterministicBookKeyFile(occupiedBackupKeyPath);
    byte[] sourceBefore = Files.readAllBytes(sourceBookPath);
    byte[] occupiedKeyBefore = Files.readAllBytes(occupiedBackupKeyPath);

    BackupBookResult result =
        lifecycleWorkflow
            .backupBook(sourceAccess, backupBookPath, occupiedBackupKeyPath, BACKUP_ID)
            .requireAccepted();
    requireSecretTargetOccupied(result);
    requireUnchanged(
        sourceBookPath,
        sourceBefore,
        "A generated-secret collision must not mutate the source book.");
    requireUnchanged(
        occupiedBackupKeyPath,
        occupiedKeyBefore,
        "A generated-secret collision must not overwrite the occupied key target.");
    requireAbsent(
        backupBookPath, "A generated-secret collision must not create a backup book artifact.");
  }

  static void exerciseUnacknowledgedDestinationAndRekeyCollisions(
      CliBookLifecycleWorkflow lifecycleWorkflow,
      BookAccess sourceAccess,
      Path sourceBookPath,
      Path root)
      throws IOException {
    Path backupBookPath = root.resolve("destination-backup").resolve("entity.sqlite");
    Path backupKeyPath = root.resolve("destination-backup").resolve("entity.key");
    requireAcceptedResult(
        lifecycleWorkflow
            .backupBook(sourceAccess, backupBookPath, backupKeyPath, BACKUP_ID)
            .requireAccepted(),
        BackupBookResult.BackedUp.class,
        "backup");

    Path destinationBookPath = root.resolve("occupied-destination").resolve("entity.sqlite");
    SqliteFuzzAssertions.prepareSecureArtifactDirectory(
        Objects.requireNonNull(destinationBookPath.getParent(), "occupied destination parent"));
    Files.copy(sourceBookPath, destinationBookPath);
    byte[] destinationBefore = Files.readAllBytes(destinationBookPath);
    Path destinationKeyPath = root.resolve("occupied-destination").resolve("entity.key");
    RestoreBookResult restoreResult =
        lifecycleWorkflow
            .restoreBook(
                destinationBookPath,
                destinationKeyPath,
                backupBookPath,
                backupKeyPath,
                sourceAccess.attestationCredentialSources())
            .requireAccepted();
    requireDestinationOccupied(restoreResult);
    requireUnchanged(
        destinationBookPath,
        destinationBefore,
        "An unacknowledged restore destination must remain byte-for-byte unchanged.");
    requireAbsent(
        destinationKeyPath,
        "An unacknowledged restore destination must not create a destination key.");

    Path occupiedRekeyPath = root.resolve("rekey-collision").resolve("entity.key");
    SqliteFuzzAssertions.writeDeterministicBookKeyFile(occupiedRekeyPath);
    byte[] sourceBefore = Files.readAllBytes(sourceBookPath);
    RekeyBookResult rekeyResult =
        lifecycleWorkflow.rekeyBook(sourceAccess, occupiedRekeyPath).requireAccepted();
    requireSecretTargetOccupied(rekeyResult);
    requireUnchanged(
        sourceBookPath, sourceBefore, "A rekey target collision must not mutate the source book.");
  }

  static void exerciseRekeyBackupRestoreWithReleasedFormerKeyPath(
      CliBookLifecycleWorkflow lifecycleWorkflow,
      CliBookReadWorkflow readWorkflow,
      BookAccess sourceAccess,
      Path sourceBookPath,
      Path sourceKeyPath,
      Path root)
      throws IOException {
    Path rotatedSourceKeyPath = root.resolve("source").resolve("entity.rotated.key");
    requireAcceptedResult(
        lifecycleWorkflow.rekeyBook(sourceAccess, rotatedSourceKeyPath).requireAccepted(),
        RekeyBookResult.Rekeyed.class,
        "rekey");
    BookAccess rotatedSourceAccess =
        SqliteRoundTripWorkflowResources.keyFileBookAccess(sourceBookPath, rotatedSourceKeyPath);
    requireReadable(readWorkflow, rotatedSourceAccess);
    requireUnreadable(readWorkflow, sourceAccess);

    Files.delete(sourceKeyPath);
    Path backupBookPath = root.resolve("backup").resolve("entity.sqlite");
    Path backupKeyPath = root.resolve("backup").resolve("entity.key");
    requireAcceptedResult(
        lifecycleWorkflow
            .backupBook(rotatedSourceAccess, backupBookPath, backupKeyPath, BACKUP_ID)
            .requireAccepted(),
        BackupBookResult.BackedUp.class,
        "backup after rekey");

    Path restoredBookPath = root.resolve("restored").resolve("entity.sqlite");
    requireAcceptedResult(
        lifecycleWorkflow
            .restoreBook(
                restoredBookPath,
                sourceKeyPath,
                backupBookPath,
                backupKeyPath,
                rotatedSourceAccess.attestationCredentialSources())
            .requireAccepted(),
        RestoreBookResult.Restored.class,
        "restore after rekey");
    BookAccess restoredAccess =
        SqliteRoundTripWorkflowResources.keyFileBookAccess(restoredBookPath, sourceKeyPath);
    requireReadable(readWorkflow, rotatedSourceAccess);
    requireReadable(readWorkflow, restoredAccess);
    requireUnreadable(readWorkflow, sourceAccess);
    requireUnreadable(
        readWorkflow,
        SqliteRoundTripWorkflowResources.keyFileBookAccess(restoredBookPath, rotatedSourceKeyPath));
  }

  private static SqliteProtectedBookMaintenanceScenario scenario(byte[] input) {
    int hash = 1;
    for (byte value : input) {
      hash = 31 * hash + Byte.toUnsignedInt(value);
    }
    SqliteProtectedBookMaintenanceScenario[] scenarios =
        SqliteProtectedBookMaintenanceScenario.values();
    return scenarios[Math.floorMod(hash, scenarios.length)];
  }
}
