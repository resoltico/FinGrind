package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.sqlite.SqliteFuzzAssertions;
import dev.erst.fingrind.sqlite.SqlitePostingSession;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Objects;

/** Exercises protected-book maintenance isolation and no-clobber contracts through Jazzer. */
final class SqliteProtectedBookMaintenanceFuzzAssertions {
  private SqliteProtectedBookMaintenanceFuzzAssertions() {}

  /** Runs one fuzz-selected maintenance scenario against a freshly initialized protected book. */
  static void exercise(byte[] input, Path root) throws IOException {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(root, "root");
    SqliteFuzzAssertions.prepareSecureArtifactDirectory(root);

    Path sourceBookPath = root.resolve("source").resolve("entity.sqlite");
    Path sourceKeyPath = root.resolve("source").resolve("entity.key");
    SqliteFuzzAssertions.writeDeterministicBookKeyFile(sourceKeyPath);
    initializeBook(sourceBookPath);

    CliBookLifecycleWorkflow lifecycleWorkflow =
        SqliteRoundTripWorkflowResources.sqliteLifecycleWorkflow();
    CliBookReadWorkflow readWorkflow = SqliteRoundTripWorkflowResources.sqliteReadWorkflow();
    BookAccess sourceAccess =
        SqliteRoundTripWorkflowResources.keyFileBookAccess(sourceBookPath, sourceKeyPath);

    scenario(input)
        .exercise(
            lifecycleWorkflow, readWorkflow, sourceAccess, sourceBookPath, sourceKeyPath, root);
  }

  private static void initializeBook(Path bookPath) {
    try (SqlitePostingSession session = SqliteFuzzAssertions.openStore(bookPath)) {
      CliFuzzWorkflowFixtures.openBook(CliFuzzWorkflowFixtures.administrationService(session));
    }
  }

  static void exerciseIndependentBackupAndRestore(
      CliBookLifecycleWorkflow lifecycleWorkflow,
      CliBookReadWorkflow readWorkflow,
      BookAccess sourceAccess,
      Path root) {
    Path backupBookPath = root.resolve("backup").resolve("entity.sqlite");
    Path backupKeyPath = root.resolve("backup").resolve("entity.key");
    requireAcceptedResult(
        lifecycleWorkflow.backupBook(sourceAccess, backupBookPath, backupKeyPath).requireAccepted(),
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
            .restoreBook(restoredBookPath, restoredKeyPath, backupBookPath, backupKeyPath, false)
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

  static void exerciseLegacyBackupRestore(
      CliBookLifecycleWorkflow lifecycleWorkflow,
      CliBookReadWorkflow readWorkflow,
      Path sourceBookPath,
      Path sourceKeyPath,
      Path root)
      throws IOException {
    Path legacyBackupBookPath = root.resolve("legacy-backup").resolve("entity.sqlite");
    Path legacyBackupKeyPath = root.resolve("legacy-backup").resolve("entity.key");
    Path legacyBackupDirectory =
        Objects.requireNonNull(legacyBackupKeyPath.getParent(), "legacy backup directory");
    SqliteFuzzAssertions.prepareSecureArtifactDirectory(legacyBackupDirectory);
    Files.copy(sourceBookPath, legacyBackupBookPath);
    Files.copy(sourceKeyPath, legacyBackupKeyPath, StandardCopyOption.COPY_ATTRIBUTES);
    requireUnchanged(
        legacyBackupKeyPath,
        Files.readAllBytes(sourceKeyPath),
        "The legacy fixture must retain the original source key artifact.");
    requireReadable(
        readWorkflow,
        SqliteRoundTripWorkflowResources.keyFileBookAccess(
            legacyBackupBookPath, legacyBackupKeyPath));

    Path restoredBookPath = root.resolve("legacy-restored").resolve("entity.sqlite");
    Path restoredKeyPath = root.resolve("legacy-restored").resolve("entity.key");
    requireAcceptedResult(
        lifecycleWorkflow
            .restoreBook(
                restoredBookPath, restoredKeyPath, legacyBackupBookPath, legacyBackupKeyPath, false)
            .requireAccepted(),
        RestoreBookResult.Restored.class,
        "legacy restore");
    requireReadable(
        readWorkflow,
        SqliteRoundTripWorkflowResources.keyFileBookAccess(restoredBookPath, restoredKeyPath));
    requireUnreadable(
        readWorkflow,
        SqliteRoundTripWorkflowResources.keyFileBookAccess(restoredBookPath, legacyBackupKeyPath));
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
            .backupBook(sourceAccess, backupBookPath, occupiedBackupKeyPath)
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
        lifecycleWorkflow.backupBook(sourceAccess, backupBookPath, backupKeyPath).requireAccepted(),
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
                destinationBookPath, destinationKeyPath, backupBookPath, backupKeyPath, false)
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
            .backupBook(rotatedSourceAccess, backupBookPath, backupKeyPath)
            .requireAccepted(),
        BackupBookResult.BackedUp.class,
        "backup after rekey");

    Path restoredBookPath = root.resolve("restored").resolve("entity.sqlite");
    requireAcceptedResult(
        lifecycleWorkflow
            .restoreBook(restoredBookPath, sourceKeyPath, backupBookPath, backupKeyPath, false)
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

  private static void requireAcceptedResult(
      Object result, Class<?> expectedResultType, String operation) {
    if (!expectedResultType.isInstance(result)) {
      throw new IllegalStateException(
          "Expected the protected-book " + operation + " scenario to succeed: " + result);
    }
  }

  private static void requireSecretTargetOccupied(BackupBookResult result) {
    if (!(result
        instanceof BackupBookResult.Rejected(BookMaintenanceRejection.SecretTargetOccupied _))) {
      throw new IllegalStateException(
          "Expected the backup key target to be rejected as occupied: " + result);
    }
  }

  private static void requireSecretTargetOccupied(RekeyBookResult result) {
    if (!(result
        instanceof RekeyBookResult.Rejected(BookMaintenanceRejection.SecretTargetOccupied _))) {
      throw new IllegalStateException(
          "Expected the rekey key target to be rejected as occupied: " + result);
    }
  }

  private static void requireDestinationOccupied(RestoreBookResult result) {
    if (!(result
        instanceof
        RestoreBookResult.Rejected(BookMaintenanceRejection.BookDestinationOccupied _))) {
      throw new IllegalStateException(
          "Expected the restore destination to be rejected as occupied: " + result);
    }
  }

  private static void requireUnchanged(Path path, byte[] expectedBytes, String message)
      throws IOException {
    if (!Arrays.equals(expectedBytes, Files.readAllBytes(path))) {
      throw new IllegalStateException(message);
    }
  }

  private static void requireAbsent(Path path, String message) {
    if (Files.exists(path)) {
      throw new IllegalStateException(message);
    }
  }

  private static void requireReadable(CliBookReadWorkflow readWorkflow, BookAccess access) {
    readWorkflow.inspectBook(access).requireAccepted();
  }

  private static void requireUnreadable(CliBookReadWorkflow readWorkflow, BookAccess access) {
    readWorkflow.inspectBook(access).requireRejected();
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
