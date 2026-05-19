package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceEvent;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceVerificationFailure;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** SQLite-backed maintenance store for protected-book verification and closed-copy file work. */
public final class SqliteProtectedBookMaintenanceStore implements ProtectedBookMaintenanceStore {
  private final SqlitePassphraseResolver passphraseResolver;

  /** Creates the SQLite maintenance store with one passphrase-resolution seam. */
  public SqliteProtectedBookMaintenanceStore(SqlitePassphraseResolver passphraseResolver) {
    this.passphraseResolver = Objects.requireNonNull(passphraseResolver, "passphraseResolver");
  }

  @Override
  public Path normalize(Path path, String argumentName) {
    return SqliteBookMaintenanceFiles.normalize(path, argumentName);
  }

  @Override
  public List<Path> blockingArtifactsForBook(Path normalizedBookPath) {
    return SqliteBookMaintenanceFiles.blockingArtifactsForBook(normalizedBookPath);
  }

  @Override
  public List<Path> blockingArtifactsForBackupSource(Path normalizedBackupFilePath) {
    return SqliteBookMaintenanceFiles.blockingArtifactsForBackupSource(normalizedBackupFilePath);
  }

  @Override
  public LeaseAcquisition acquireExclusiveLease(Path normalizedArtifactPath) {
    return switch (SqliteBookMaintenanceLease.acquire(normalizedArtifactPath)) {
      case SqliteBookMaintenanceLease.HeldLease heldLease -> heldLease;
      case SqliteBookMaintenanceLease.LeaseBusy leaseBusy ->
          new LeaseBusy(leaseBusy.artifactPath());
    };
  }

  @Override
  public ContractDecision<BookVerification> verifyInitializedBook(BookAccess bookAccess) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Path normalizedBookPath = normalize(bookAccess.bookFilePath(), "bookFilePath");
    BookAccess normalizedAccess = new BookAccess(normalizedBookPath, bookAccess.passphraseSource());
    if (!Files.exists(normalizedBookPath, LinkOption.NOFOLLOW_LINKS)) {
      return ContractDecision.accepted(
          new VerificationFailure(
              normalizedBookPath, ProtectedBookMaintenanceVerificationFailure.MISSING));
    }
    requireRegularNonSymlinkFile(normalizedBookPath);
    return passphraseResolver
        .resolve(normalizedAccess, SqlitePassphraseIntent.EXISTING_SECRET)
        .fold(
            bookPassphrase -> verifyResolvedBook(normalizedBookPath, bookPassphrase),
            ContractDecision::rejected);
  }

  @Override
  public ContractDecision<Path> publishBackupPair(
      BookAccess sourceAccess,
      Path normalizedBackupFilePath,
      Path normalizedBackupBookKeyFilePath) {
    Objects.requireNonNull(sourceAccess, "sourceAccess");
    Objects.requireNonNull(normalizedBackupFilePath, "normalizedBackupFilePath");
    Objects.requireNonNull(normalizedBackupBookKeyFilePath, "normalizedBackupBookKeyFilePath");
    return passphraseResolver
        .resolve(sourceAccess, SqlitePassphraseIntent.EXISTING_SECRET)
        .fold(
            sourcePassphrase ->
                publishResolvedBackupPair(
                    sourceAccess.bookFilePath(),
                    normalizedBackupFilePath,
                    normalizedBackupBookKeyFilePath,
                    sourcePassphrase),
            ContractDecision::rejected);
  }

  @Override
  public PreparedBookReplacement prepareReplacement(
      Path normalizedSourceBookPath, Path normalizedTargetBookPath) {
    return PreparedReplacement.create(normalizedSourceBookPath, normalizedTargetBookPath);
  }

  @Override
  public List<Path> staleRollbackArtifacts(Path normalizedBookPath) {
    try {
      return SqliteRekeyRollbackFile.staleRollbackArtifacts(normalizedBookPath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to inspect FinGrind SQLite rollback artifacts beside " + normalizedBookPath + ".",
          exception);
    }
  }

  @Override
  public boolean isRollbackArtifactForBook(
      Path normalizedBookPath, Path normalizedRollbackArtifactPath) {
    return SqliteRekeyRollbackFile.isRollbackArtifactForBook(
        normalizedBookPath, normalizedRollbackArtifactPath);
  }

  @Override
  public void deleteRollbackArtifact(Path normalizedRollbackArtifactPath) {
    SqliteBookMaintenanceFiles.deleteRollbackArtifact(normalizedRollbackArtifactPath);
  }

  @Override
  public void recordMaintenanceEvent(ProtectedBookMaintenanceEvent maintenanceEvent) {
    SqliteProtectedBookMaintenanceJournal.append(maintenanceEvent);
  }

  private ContractDecision<BookVerification> verifyResolvedBook(
      Path normalizedBookPath, SqliteBookPassphrase bookPassphrase) {
    try (SqliteBookPassphrase ignored = bookPassphrase) {
      ContractDecision<SqliteReadSession> opened =
          SqliteBookSessions.openResolvedRead(normalizedBookPath, bookPassphrase);
      return opened.fold(
          bookSession -> inspectOpenedBook(normalizedBookPath, bookSession),
          failure ->
              ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED
                      .code()
                      .equals(failure.code())
                  ? ContractDecision.accepted(
                      new VerificationFailure(
                          normalizedBookPath,
                          ProtectedBookMaintenanceVerificationFailure
                              .PROTECTED_BOOK_VERIFICATION_FAILED))
                  : ContractDecision.rejected(failure));
    }
  }

  private ContractDecision<Path> publishResolvedBackupPair(
      Path normalizedBookPath,
      Path normalizedBackupFilePath,
      Path normalizedBackupBookKeyFilePath,
      SqliteBookPassphrase sourcePassphrase) {
    try (SqliteBookPassphrase ignoredSource = sourcePassphrase;
        SqliteBookPassphrase backupKeyPassphrase = sourcePassphrase.copy();
        SqliteNativeDatabase sourceDatabase =
            SqliteNativeConnections.openWithoutRollbackArtifactWarning(
                normalizedBookPath, sourcePassphrase, SqliteNativeOpenMode.READ_WRITE_EXISTING)) {
      sourceDatabase.executeStatement("begin immediate");
      SqliteBookKeyFileMaterializer.materialize(
          normalizedBackupBookKeyFilePath, backupKeyPassphrase);
      try {
        SqliteBookMaintenanceFiles.copyFreshBook(normalizedBookPath, normalizedBackupFilePath);
        sourceDatabase.executeStatement("rollback");
        return ContractDecision.accepted(normalizedBackupFilePath);
      } catch (RuntimeException exception) {
        SqliteStoreOperations.rollbackQuietly(sourceDatabase);
        SqliteBookKeyFileGenerator.deleteQuietly(normalizedBackupBookKeyFilePath);
        throw exception;
      }
    }
  }

  private ContractDecision<BookVerification> inspectOpenedBook(
      Path normalizedBookPath, SqliteReadSession bookSession) {
    try (SqliteReadSession ignored = bookSession) {
      BookLifecycleInspection inspection = bookSession.inspectBook();
      return ContractDecision.accepted(mapInspection(normalizedBookPath, inspection));
    }
  }

  private BookVerification mapInspection(
      Path normalizedBookPath, BookLifecycleInspection inspection) {
    Objects.requireNonNull(inspection, "inspection");
    return switch (inspection) {
      case BookLifecycleInspection.Initialized _ -> new VerifiedBook(normalizedBookPath);
      case BookLifecycleInspection.Missing _ ->
          new VerificationFailure(
              normalizedBookPath, ProtectedBookMaintenanceVerificationFailure.MISSING);
      case BookLifecycleInspection.Existing existing ->
          new VerificationFailure(normalizedBookPath, mapInspectionFailure(existing.status()));
    };
  }

  private ProtectedBookMaintenanceVerificationFailure mapInspectionFailure(
      BookLifecycleInspection.Status status) {
    return switch (Objects.requireNonNull(status, "status")) {
      case MISSING -> ProtectedBookMaintenanceVerificationFailure.MISSING;
      case BLANK_SQLITE -> ProtectedBookMaintenanceVerificationFailure.BLANK_SQLITE;
      case FOREIGN_SQLITE -> ProtectedBookMaintenanceVerificationFailure.FOREIGN_SQLITE;
      case UNSUPPORTED_FORMAT_VERSION ->
          ProtectedBookMaintenanceVerificationFailure.UNSUPPORTED_FORMAT_VERSION;
      case INCOMPLETE_FINGRIND -> ProtectedBookMaintenanceVerificationFailure.INCOMPLETE_FINGRIND;
      case INITIALIZED ->
          throw new IllegalArgumentException("INITIALIZED is not one rejection inspection status.");
    };
  }

  private static void requireRegularNonSymlinkFile(Path normalizedPath) {
    if (!Files.isRegularFile(normalizedPath, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalStateException(
          "The FinGrind protected book path must resolve to one regular non-symlink file: "
              + normalizedPath);
    }
  }

  /** Reversible filesystem replacement that keeps one rollback copy of the previous target. */
  private static final class PreparedReplacement implements PreparedBookReplacement {
    private final Path targetBookPath;
    private final Path stagedReplacementPath;
    private final @Nullable Path previousTargetBackupPath;
    private boolean finished;

    private PreparedReplacement(
        Path targetBookPath, Path stagedReplacementPath, @Nullable Path previousTargetBackupPath) {
      this.targetBookPath = targetBookPath;
      this.stagedReplacementPath = stagedReplacementPath;
      this.previousTargetBackupPath = previousTargetBackupPath;
    }

    private static PreparedReplacement create(
        Path normalizedSourceBookPath, Path normalizedTargetBookPath) {
      @Nullable Path stagedReplacementPath = null;
      @Nullable Path previousTargetBackupPath = null;
      try {
        SqliteBookFileSecurity.ensureSecureParentDirectory(normalizedTargetBookPath);
        Path targetParentDirectory =
            Objects.requireNonNull(
                normalizedTargetBookPath.getParent(), "normalizedTargetBookPath parent");
        String targetFileName =
            Objects.requireNonNull(
                    normalizedTargetBookPath.getFileName(), "normalizedTargetBookPath fileName")
                .toString();
        stagedReplacementPath =
            Files.createTempFile(targetParentDirectory, targetFileName + ".restore-", ".tmp");
        Files.copy(
            normalizedSourceBookPath,
            stagedReplacementPath,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.COPY_ATTRIBUTES);
        SqliteBookFileSecurity.hardenBookArtifacts(stagedReplacementPath);
        if (Files.exists(normalizedTargetBookPath, LinkOption.NOFOLLOW_LINKS)) {
          requireRegularNonSymlinkFile(normalizedTargetBookPath);
          previousTargetBackupPath =
              Files.createTempFile(targetParentDirectory, targetFileName + ".previous-", ".sqlite");
          moveReplacing(normalizedTargetBookPath, previousTargetBackupPath);
        }
        moveReplacing(stagedReplacementPath, normalizedTargetBookPath);
        SqliteBookFileSecurity.hardenBookArtifacts(normalizedTargetBookPath);
        return new PreparedReplacement(
            normalizedTargetBookPath, stagedReplacementPath, previousTargetBackupPath);
      } catch (IOException exception) {
        cleanupQuietly(stagedReplacementPath);
        if (previousTargetBackupPath != null
            && !Files.exists(normalizedTargetBookPath, LinkOption.NOFOLLOW_LINKS)) {
          try {
            moveReplacing(previousTargetBackupPath, normalizedTargetBookPath);
          } catch (IOException restoreException) {
            SqliteBestEffort.reportCleanupFailure(
                "restoring one previous protected book after replacement failure",
                restoreException);
          }
        }
        cleanupQuietly(previousTargetBackupPath);
        throw new IllegalStateException(
            "Failed to replace the FinGrind SQLite book at " + normalizedTargetBookPath + ".",
            exception);
      }
    }

    @Override
    public Path targetBookPath() {
      return targetBookPath;
    }

    @Override
    public void commit() {
      cleanupQuietly(previousTargetBackupPath);
      cleanupQuietly(stagedReplacementPath);
      finished = true;
    }

    @Override
    public void rollback() {
      if (finished) {
        return;
      }
      cleanupQuietly(targetBookPath);
      if (previousTargetBackupPath != null
          && Files.exists(previousTargetBackupPath, LinkOption.NOFOLLOW_LINKS)) {
        try {
          moveReplacing(previousTargetBackupPath, targetBookPath);
          SqliteBookFileSecurity.hardenBookArtifacts(targetBookPath);
        } catch (IOException exception) {
          throw new IllegalStateException(
              "Failed to restore the previous FinGrind SQLite book at " + targetBookPath + ".",
              exception);
        }
      }
      cleanupQuietly(stagedReplacementPath);
      finished = true;
    }

    @Override
    public void close() {
      if (!finished) {
        rollback();
      }
      cleanupQuietly(previousTargetBackupPath);
      cleanupQuietly(stagedReplacementPath);
    }

    private static void cleanupQuietly(@Nullable Path path) {
      if (path == null) {
        return;
      }
      SqliteBookKeyFileGenerator.deleteQuietly(path);
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
      try {
        Files.move(
            source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException exception) {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }
}
