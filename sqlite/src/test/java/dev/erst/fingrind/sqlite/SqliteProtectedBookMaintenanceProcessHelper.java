package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookPassphraseSource;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import dev.erst.fingrind.executor.spi.StagedBookReplacement;
import dev.erst.fingrind.executor.spi.StagedRollbackArtifactDeletion;
import dev.erst.fingrind.sqlite.secret.SqliteBookKeyFile;
import dev.erst.fingrind.sqlite.secret.SqlitePassphraseResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Subprocess helper that parks inside one staged maintenance phase until the parent kills it. */
public final class SqliteProtectedBookMaintenanceProcessHelper {
  private static final SqlitePassphraseResolver KEY_FILE_RESOLVER =
      (resolvedBookPath, passphraseSource, intent) ->
          switch (passphraseSource) {
            case BookAccess.PassphraseSource.KeyFile keyFile ->
                SqliteBookKeyFile.loadDecision(keyFile.bookKeyFilePath());
            case BookAccess.PassphraseSource.StandardInput _ ->
                throw new AssertionError("Crash helper expects key-file-backed access only.");
            case BookAccess.PassphraseSource.InteractivePrompt _ ->
                throw new AssertionError("Crash helper expects key-file-backed access only.");
          };

  private SqliteProtectedBookMaintenanceProcessHelper() {}

  public static void main(String[] args) throws IOException, InterruptedException {
    if (args.length == 0) {
      throw new IllegalArgumentException("maintenance stage mode is required.");
    }
    switch (args[0]) {
      case "stage-backup" -> stageBackup(args);
      case "stage-replacement" -> stageReplacement(args);
      case "stage-rollback-delete" -> stageRollbackDeletion(args);
      default ->
          throw new IllegalArgumentException("Unsupported maintenance stage mode: " + args[0]);
    }
  }

  private static void stageBackup(String[] args) throws IOException, InterruptedException {
    requireArgumentCount(args, 6);
    Path bookPath = normalizedPath(args[1]);
    Path bookKeyPath = normalizedPath(args[2]);
    Path backupPath = normalizedPath(args[3]);
    Path backupKeyPath = normalizedPath(args[4]);
    Path signalPath = normalizedPath(args[5]);
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    ProtectedBookAccess sourceAccess =
        new ProtectedBookAccess(bookPath, new ProtectedBookPassphraseSource.KeyFile(bookKeyPath));
    try (StagedBackupPair ignored =
        acceptedValue(store.stageBackupPair(sourceAccess, backupPath, backupKeyPath))) {
      signalReady(signalPath);
      sleepUntilKilled();
    }
  }

  private static void stageReplacement(String[] args) throws IOException, InterruptedException {
    requireArgumentCount(args, 4);
    Path sourcePath = normalizedPath(args[1]);
    Path targetPath = normalizedPath(args[2]);
    Path signalPath = normalizedPath(args[3]);
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    try (StagedBookReplacement ignored = store.stageReplacement(sourcePath, targetPath)) {
      signalReady(signalPath);
      sleepUntilKilled();
    }
  }

  private static void stageRollbackDeletion(String[] args)
      throws IOException, InterruptedException {
    requireArgumentCount(args, 3);
    Path rollbackArtifactPath = normalizedPath(args[1]);
    Path signalPath = normalizedPath(args[2]);
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    try (StagedRollbackArtifactDeletion ignored =
        store.stageRollbackArtifactDeletion(rollbackArtifactPath)) {
      signalReady(signalPath);
      sleepUntilKilled();
    }
  }

  private static SqliteProtectedBookMaintenanceStore maintenanceStore() {
    return new SqliteProtectedBookMaintenanceStore(KEY_FILE_RESOLVER);
  }

  private static Path normalizedPath(String rawPath) {
    return Path.of(rawPath).toAbsolutePath().normalize();
  }

  private static void signalReady(Path signalPath) throws IOException {
    Path parentDirectory = signalPath.getParent();
    if (parentDirectory != null) {
      Files.createDirectories(parentDirectory);
    }
    Files.writeString(
        signalPath, "ready", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }

  private static void sleepUntilKilled() throws InterruptedException {
    while (true) {
      Thread.sleep(1_000L);
    }
  }

  private static void requireArgumentCount(String[] args, int expectedCount) {
    if (args.length != expectedCount) {
      throw new IllegalArgumentException(
          "Expected " + expectedCount + " arguments but received " + args.length + ".");
    }
  }

  private static <T> T acceptedValue(MaintenanceDecision<T> decision) {
    return switch (decision) {
      case MaintenanceDecision.Accepted<T>(T value) -> value;
      case MaintenanceDecision.Failed<T>(MaintenanceFailure failure) ->
          throw new IllegalStateException(
              "Expected accepted maintenance decision but got " + failure);
    };
  }
}
