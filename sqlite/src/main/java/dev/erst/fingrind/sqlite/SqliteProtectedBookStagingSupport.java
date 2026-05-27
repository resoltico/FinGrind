package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.PublicPathHint;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import dev.erst.fingrind.sqlite.secret.SqliteBookKeyFileGenerator;
import dev.erst.fingrind.sqlite.secret.SqliteBookPassphrase;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.jspecify.annotations.Nullable;

/** Shared SQLite staging helpers for protected-book maintenance flows. */
final class SqliteProtectedBookStagingSupport {
  private SqliteProtectedBookStagingSupport() {}

  static MaintenanceDecision<StagedBackupPair> stageResolvedBackupPair(
      Path normalizedBookPath,
      Path normalizedBackupFilePath,
      Path normalizedBackupBookKeyFilePath,
      SqliteBookPassphrase sourcePassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport) {
    try (SqliteBookPassphrase ignoredSource = sourcePassphrase;
        SqliteBookPassphrase stagedBackupPassphrase = sourcePassphrase.copy();
        SqliteBookPassphrase exportPassphrase = sourcePassphrase.copy()) {
      ensureSecureParentDirectory(normalizedBackupFilePath);
      ensureSecureParentDirectory(normalizedBackupBookKeyFilePath);
      SqliteBookMaintenanceFiles.cleanupAbandonedStageArtifacts(normalizedBackupFilePath);
      SqliteBookMaintenanceFiles.cleanupAbandonedStageArtifacts(normalizedBackupBookKeyFilePath);
      Path stagedBackupFilePath =
          createStagedSibling(normalizedBackupFilePath, ".backup-", ".sqlite");
      try {
        exportBackupUsingSqlite(normalizedBookPath, stagedBackupFilePath, exportPassphrase);
        return MaintenanceDecision.accepted(
            new SqliteStagedBackupPair(
                stagedBackupFilePath,
                normalizedBackupFilePath,
                normalizedBackupBookKeyFilePath,
                stagedBackupPassphrase.copy(),
                verificationSupport));
      } catch (RuntimeException exception) {
        deleteQuietlyIfPresent(stagedBackupFilePath);
        throw exception;
      }
    }
  }

  static void requireRegularNonSymlinkFile(Path normalizedPath) {
    if (!Files.isRegularFile(normalizedPath, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalStateException(
          "The FinGrind protected book path must resolve to one regular non-symlink file: "
              + PublicPathHint.fromPath(normalizedPath).value());
    }
  }

  static Path createStagedSibling(Path finalPath, String infix, String suffix) {
    try {
      Path parent = java.util.Objects.requireNonNull(finalPath.getParent(), "finalPath parent");
      String baseName =
          java.util.Objects.requireNonNull(finalPath.getFileName(), "finalPath fileName")
              .toString();
      return Files.createTempFile(parent, baseName + infix, suffix);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to create one staged maintenance artifact beside "
              + PublicPathHint.fromPath(finalPath).value()
              + ".",
          exception);
    }
  }

  static void exportBackupUsingSqlite(
      Path normalizedBookPath, Path stagedBackupFilePath, SqliteBookPassphrase sourcePassphrase) {
    try (SqliteBookPassphrase ignored = sourcePassphrase;
        SqliteNativeDatabase sourceDatabase =
            SqliteNativeConnections.openWithoutRollbackArtifactWarning(
                normalizedBookPath, sourcePassphrase, SqliteNativeOpenMode.READ_WRITE_EXISTING)) {
      sourceDatabase.executeStatement(
          "vacuum into '" + escapeSqlLiteral(stagedBackupFilePath.toString()) + "'");
      hardenBookArtifacts(stagedBackupFilePath);
    }
  }

  static void ensureSecureParentDirectory(Path artifactPath) {
    try {
      SqliteBookFileSecurity.ensureSecureParentDirectory(artifactPath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to secure the parent directory for "
              + PublicPathHint.fromPath(artifactPath).value()
              + ".",
          exception);
    }
  }

  static void hardenBookArtifacts(Path artifactPath) {
    try {
      SqliteBookFileSecurity.hardenBookArtifacts(artifactPath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to harden the FinGrind protected-book artifacts for "
              + PublicPathHint.fromPath(artifactPath).value()
              + ".",
          exception);
    }
  }

  static String escapeSqlLiteral(String value) {
    return value.replace("'", "''");
  }

  static void deleteQuietlyIfPresent(@Nullable Path path) {
    if (path != null) {
      SqliteBookKeyFileGenerator.deleteQuietly(path);
    }
  }

  static void moveReplacing(Path source, Path target) throws IOException {
    try {
      Files.move(
          source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException exception) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
