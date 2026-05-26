package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.executor.bookkeeping.BookAuditEvent;
import dev.erst.fingrind.executor.bookkeeping.BookAuditEventKind;
import dev.erst.fingrind.executor.maintenance.MaintenanceCompletion;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditCompensationKind;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditKind;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.sqlite.secret.SqliteBookKeyFile;
import dev.erst.fingrind.sqlite.secret.SqliteBookKeyFileGenerator;
import dev.erst.fingrind.sqlite.secret.SqliteBookPassphrase;
import dev.erst.fingrind.sqlite.secret.SqlitePassphraseIntent;
import dev.erst.fingrind.sqlite.secret.SqlitePassphraseResolver;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * SQLite-backed maintenance store for protected-book verification and staged artifact workflows.
 */
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
  public LeaseAcquisition acquireExistingArtifactLease(Path normalizedArtifactPath) {
    return switch (SqliteBookMaintenanceLease.acquire(
        normalizedArtifactPath, SqliteBookMaintenanceLease.LeaseIntent.EXISTING_ARTIFACT)) {
      case SqliteBookMaintenanceLease.HeldLease heldLease -> heldLease;
      case SqliteBookMaintenanceLease.LeaseBusy leaseBusy ->
          new LeaseBusy(leaseBusy.artifactPath());
    };
  }

  @Override
  public LeaseAcquisition acquireManagedArtifactLease(Path normalizedArtifactPath) {
    return switch (SqliteBookMaintenanceLease.acquire(
        normalizedArtifactPath, SqliteBookMaintenanceLease.LeaseIntent.MANAGED_TARGET)) {
      case SqliteBookMaintenanceLease.HeldLease heldLease -> heldLease;
      case SqliteBookMaintenanceLease.LeaseBusy leaseBusy ->
          new LeaseBusy(leaseBusy.artifactPath());
    };
  }

  @Override
  public MaintenanceDecision<BookVerification> verifyInitializedBook(
      ProtectedBookAccess bookAccess) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Path normalizedBookPath = normalize(bookAccess.bookFilePath(), "bookFilePath");
    ProtectedBookAccess normalizedAccess =
        new ProtectedBookAccess(normalizedBookPath, bookAccess.passphraseSource());
    if (!Files.exists(normalizedBookPath, LinkOption.NOFOLLOW_LINKS)) {
      return MaintenanceDecision.accepted(
          new VerificationFailure(normalizedBookPath, ProtectedBookVerificationFailure.MISSING));
    }
    requireRegularNonSymlinkFile(normalizedBookPath);
    return passphraseResolver
        .resolve(
            normalizedBookPath,
            normalizedAccess.passphraseSource().toPublished(),
            SqlitePassphraseIntent.EXISTING_SECRET)
        .fold(
            bookPassphrase -> verifyResolvedBook(normalizedBookPath, bookPassphrase),
            failure -> MaintenanceDecision.failed(MaintenanceFailure.fromContractFailure(failure)));
  }

  @Override
  public MaintenanceDecision<StagedBackupPair> stageBackupPair(
      ProtectedBookAccess sourceAccess,
      Path normalizedBackupFilePath,
      Path normalizedBackupBookKeyFilePath) {
    Objects.requireNonNull(sourceAccess, "sourceAccess");
    Objects.requireNonNull(normalizedBackupFilePath, "normalizedBackupFilePath");
    Objects.requireNonNull(normalizedBackupBookKeyFilePath, "normalizedBackupBookKeyFilePath");
    return passphraseResolver
        .resolve(
            sourceAccess.bookFilePath(),
            sourceAccess.passphraseSource().toPublished(),
            SqlitePassphraseIntent.EXISTING_SECRET)
        .fold(
            sourcePassphrase ->
                stageResolvedBackupPair(
                    sourceAccess.bookFilePath(),
                    normalizedBackupFilePath,
                    normalizedBackupBookKeyFilePath,
                    sourcePassphrase),
            failure -> MaintenanceDecision.failed(MaintenanceFailure.fromContractFailure(failure)));
  }

  @Override
  public StagedBookReplacement stageReplacement(
      Path normalizedSourceBookPath, Path normalizedTargetBookPath) {
    return StagedReplacement.create(normalizedSourceBookPath, normalizedTargetBookPath);
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
  public StagedRollbackArtifactDeletion stageRollbackArtifactDeletion(
      Path normalizedRollbackArtifactPath) {
    requireRegularNonSymlinkFile(normalizedRollbackArtifactPath);
    return StagedRollbackDeletion.create(normalizedRollbackArtifactPath);
  }

  @Override
  public MaintenanceDecision<MaintenanceCompletion> appendMaintenanceAudit(
      ProtectedBookAccess bookAccess,
      Instant recordedAt,
      ProtectedBookMaintenanceAuditKind auditKind) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(recordedAt, "recordedAt");
    Objects.requireNonNull(auditKind, "auditKind");
    Path normalizedBookPath = normalize(bookAccess.bookFilePath(), "bookFilePath");
    return passphraseResolver
        .resolve(
            normalizedBookPath,
            bookAccess.passphraseSource().toPublished(),
            SqlitePassphraseIntent.EXISTING_SECRET)
        .fold(
            passphrase ->
                appendResolvedMaintenanceAudit(
                    normalizedBookPath, passphrase, recordedAt, auditKind),
            failure -> MaintenanceDecision.failed(MaintenanceFailure.fromContractFailure(failure)));
  }

  @Override
  public MaintenanceDecision<MaintenanceCompletion> appendMaintenanceAuditCompensation(
      ProtectedBookAccess bookAccess,
      Instant recordedAt,
      ProtectedBookMaintenanceAuditCompensationKind auditKind) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(recordedAt, "recordedAt");
    Objects.requireNonNull(auditKind, "auditKind");
    Path normalizedBookPath = normalize(bookAccess.bookFilePath(), "bookFilePath");
    return passphraseResolver
        .resolve(
            normalizedBookPath,
            bookAccess.passphraseSource().toPublished(),
            SqlitePassphraseIntent.EXISTING_SECRET)
        .fold(
            passphrase ->
                appendResolvedMaintenanceAuditCompensation(
                    normalizedBookPath, passphrase, recordedAt, auditKind),
            failure -> MaintenanceDecision.failed(MaintenanceFailure.fromContractFailure(failure)));
  }

  private MaintenanceDecision<BookVerification> verifyResolvedBook(
      Path normalizedBookPath, SqliteBookPassphrase bookPassphrase) {
    try (SqliteBookPassphrase ignored = bookPassphrase) {
      return SqliteBookSessions.openResolvedRead(normalizedBookPath, bookPassphrase)
          .fold(
              bookSession -> inspectOpenedBook(normalizedBookPath, bookSession),
              failure ->
                  MaintenanceDecision.accepted(
                      new VerificationFailure(
                          normalizedBookPath, protectedBookVerificationFailure(failure))));
    }
  }

  private static ProtectedBookVerificationFailure protectedBookVerificationFailure(
      dev.erst.fingrind.contract.runtime.ContractFailure failure) {
    if (!ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED
        .code()
        .equals(Objects.requireNonNull(failure, "failure").code())) {
      throw new IllegalStateException(
          "SQLite read-session verification rejected with one non-verification contract failure: "
              + failure.code());
    }
    return ProtectedBookVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED;
  }

  private MaintenanceDecision<StagedBackupPair> stageResolvedBackupPair(
      Path normalizedBookPath,
      Path normalizedBackupFilePath,
      Path normalizedBackupBookKeyFilePath,
      SqliteBookPassphrase sourcePassphrase) {
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
            new StagedBackupPairImpl(
                stagedBackupFilePath,
                normalizedBackupFilePath,
                normalizedBackupBookKeyFilePath,
                stagedBackupPassphrase.copy()));
      } catch (RuntimeException exception) {
        deleteQuietlyIfPresent(stagedBackupFilePath);
        throw exception;
      }
    }
  }

  private MaintenanceDecision<MaintenanceCompletion> appendResolvedMaintenanceAudit(
      Path normalizedBookPath,
      SqliteBookPassphrase passphrase,
      Instant recordedAt,
      ProtectedBookMaintenanceAuditKind auditKind) {
    try (SqliteBookPassphrase ignored = passphrase;
        SqliteNativeDatabase database =
            SqliteNativeConnections.openWithoutRollbackArtifactWarning(
                normalizedBookPath, passphrase, SqliteNativeOpenMode.READ_WRITE_EXISTING)) {
      database.executeStatement("begin immediate");
      try {
        SqliteAuditEventWriter.insertAuditEvent(
            database, maintenanceAuditEvent(auditKind, recordedAt));
        database.executeStatement("commit");
        return MaintenanceDecision.accepted(MaintenanceCompletion.DONE);
      } catch (RuntimeException exception) {
        SqliteStoreOperations.rollbackQuietly(database);
        throw exception;
      }
    }
  }

  private MaintenanceDecision<MaintenanceCompletion> appendResolvedMaintenanceAuditCompensation(
      Path normalizedBookPath,
      SqliteBookPassphrase passphrase,
      Instant recordedAt,
      ProtectedBookMaintenanceAuditCompensationKind auditKind) {
    try (SqliteBookPassphrase ignored = passphrase;
        SqliteNativeDatabase database =
            SqliteNativeConnections.openWithoutRollbackArtifactWarning(
                normalizedBookPath, passphrase, SqliteNativeOpenMode.READ_WRITE_EXISTING)) {
      database.executeStatement("begin immediate");
      try {
        SqliteAuditEventWriter.insertAuditEvent(
            database, maintenanceAuditCompensationEvent(auditKind, recordedAt));
        database.executeStatement("commit");
        return MaintenanceDecision.accepted(MaintenanceCompletion.DONE);
      } catch (RuntimeException exception) {
        SqliteStoreOperations.rollbackQuietly(database);
        throw exception;
      }
    }
  }

  private MaintenanceDecision<BookVerification> inspectOpenedBook(
      Path normalizedBookPath, SqliteReadSession bookSession) {
    try (SqliteReadSession ignored = bookSession) {
      BookLifecycleInspection inspection = bookSession.inspectBook();
      return MaintenanceDecision.accepted(mapInspection(normalizedBookPath, inspection));
    }
  }

  private BookVerification mapInspection(
      Path normalizedBookPath, BookLifecycleInspection inspection) {
    Objects.requireNonNull(inspection, "inspection");
    return switch (inspection) {
      case BookLifecycleInspection.Initialized _ -> new VerifiedBook(normalizedBookPath);
      case BookLifecycleInspection.Missing _ ->
          new VerificationFailure(normalizedBookPath, ProtectedBookVerificationFailure.MISSING);
      case BookLifecycleInspection.Existing existing ->
          new VerificationFailure(normalizedBookPath, mapInspectionFailure(existing.status()));
    };
  }

  private ProtectedBookVerificationFailure mapInspectionFailure(
      BookLifecycleInspection.Status status) {
    return switch (Objects.requireNonNull(status, "status")) {
      case MISSING -> ProtectedBookVerificationFailure.MISSING;
      case BLANK_SQLITE -> ProtectedBookVerificationFailure.BLANK_SQLITE;
      case FOREIGN_SQLITE -> ProtectedBookVerificationFailure.FOREIGN_SQLITE;
      case UNSUPPORTED_FORMAT_VERSION ->
          ProtectedBookVerificationFailure.UNSUPPORTED_FORMAT_VERSION;
      case INCOMPLETE_FINGRIND -> ProtectedBookVerificationFailure.INCOMPLETE_FINGRIND;
      case INITIALIZED ->
          throw new IllegalArgumentException("INITIALIZED is not one rejection inspection status.");
    };
  }

  private static BookAuditEvent maintenanceAuditEvent(
      ProtectedBookMaintenanceAuditKind auditKind, java.time.Instant recordedAt) {
    return switch (auditKind) {
      case BACKUP_CREATED ->
          new BookAuditEvent(recordedAt, BookAuditEventKind.BACKUP_CREATED, null, null, null);
      case BACKUP_RESTORED ->
          new BookAuditEvent(recordedAt, BookAuditEventKind.BACKUP_RESTORED, null, null, null);
      case REKEY_ROLLBACK_RESTORED ->
          new BookAuditEvent(
              recordedAt, BookAuditEventKind.REKEY_ROLLBACK_RESTORED, null, null, null);
      case REKEY_ROLLBACK_DELETED ->
          new BookAuditEvent(
              recordedAt, BookAuditEventKind.REKEY_ROLLBACK_DELETED, null, null, null);
    };
  }

  private static BookAuditEvent maintenanceAuditCompensationEvent(
      ProtectedBookMaintenanceAuditCompensationKind auditKind, Instant recordedAt) {
    return switch (auditKind) {
      case BACKUP_CREATED ->
          new BookAuditEvent(
              recordedAt, BookAuditEventKind.BACKUP_CREATED_COMPENSATED, null, null, null);
      case REKEY_ROLLBACK_DELETED ->
          new BookAuditEvent(
              recordedAt, BookAuditEventKind.REKEY_ROLLBACK_DELETED_COMPENSATED, null, null, null);
    };
  }

  private static void requireRegularNonSymlinkFile(Path normalizedPath) {
    if (!Files.isRegularFile(normalizedPath, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalStateException(
          "The FinGrind protected book path must resolve to one regular non-symlink file: "
              + normalizedPath);
    }
  }

  private static Path createStagedSibling(Path finalPath, String infix, String suffix) {
    try {
      Path parent = Objects.requireNonNull(finalPath.getParent(), "finalPath parent");
      String baseName =
          Objects.requireNonNull(finalPath.getFileName(), "finalPath fileName").toString();
      return Files.createTempFile(parent, baseName + infix, suffix);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to create one staged maintenance artifact beside " + finalPath + ".", exception);
    }
  }

  private static void exportBackupUsingSqlite(
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

  private static void ensureSecureParentDirectory(Path artifactPath) {
    try {
      SqliteBookFileSecurity.ensureSecureParentDirectory(artifactPath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to secure the parent directory for " + artifactPath + ".", exception);
    }
  }

  private static void hardenBookArtifacts(Path artifactPath) {
    try {
      SqliteBookFileSecurity.hardenBookArtifacts(artifactPath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to harden the FinGrind protected-book artifacts for " + artifactPath + ".",
          exception);
    }
  }

  private static String escapeSqlLiteral(String value) {
    return value.replace("'", "''");
  }

  private static void deleteQuietlyIfPresent(@Nullable Path path) {
    if (path != null) {
      SqliteBookKeyFileGenerator.deleteQuietly(path);
    }
  }

  /** Staged encrypted backup pair implementation. */
  private final class StagedBackupPairImpl implements StagedBackupPair {
    private final Path stagedBackupFilePath;
    private final Path finalBackupFilePath;
    private final Path finalBackupBookKeyFilePath;
    private @Nullable SqliteBookPassphrase backupPassphrase;
    private boolean backupFilePublished;
    private boolean backupKeyFilePublished;
    private boolean finished;

    private StagedBackupPairImpl(
        Path stagedBackupFilePath,
        Path finalBackupFilePath,
        Path finalBackupBookKeyFilePath,
        SqliteBookPassphrase backupPassphrase) {
      this.stagedBackupFilePath =
          Objects.requireNonNull(stagedBackupFilePath, "stagedBackupFilePath");
      this.finalBackupFilePath = Objects.requireNonNull(finalBackupFilePath, "finalBackupFilePath");
      this.finalBackupBookKeyFilePath =
          Objects.requireNonNull(finalBackupBookKeyFilePath, "finalBackupBookKeyFilePath");
      this.backupPassphrase = Objects.requireNonNull(backupPassphrase, "backupPassphrase");
    }

    @Override
    public MaintenanceDecision<BookVerification> verifyInitializedBackup() {
      return verifyResolvedBook(stagedBackupFilePath, currentBackupPassphrase().copy());
    }

    @Override
    public void commit() {
      if (finished) {
        return;
      }
      @Nullable Path stagedBackupBookKeyFilePath = null;
      try (SqliteBookPassphrase committedBackupPassphrase = takeBackupPassphrase()) {
        stagedBackupBookKeyFilePath =
            createStagedSibling(finalBackupBookKeyFilePath, ".backup-key-", ".tmp");
        deleteQuietlyIfPresent(stagedBackupBookKeyFilePath);
        try (SqliteBookPassphrase materializedPassphrase = committedBackupPassphrase.copy()) {
          SqliteBookKeyFile.materialize(stagedBackupBookKeyFilePath, materializedPassphrase);
        }
        moveReplacing(stagedBackupFilePath, finalBackupFilePath);
        backupFilePublished = true;
        moveReplacing(stagedBackupBookKeyFilePath, finalBackupBookKeyFilePath);
        backupKeyFilePublished = true;
        SqliteBookFileSecurity.hardenBookArtifacts(finalBackupFilePath);
        SqliteBookFileSecurity.hardenOwnerOnlyFile(finalBackupBookKeyFilePath);
        finished = true;
      } catch (IOException exception) {
        deleteQuietlyIfPresent(stagedBackupBookKeyFilePath);
        rollbackPublishedBackupArtifacts();
        finished = true;
        throw new IllegalStateException(
            "Failed to publish the staged FinGrind backup pair.", exception);
      }
    }

    @Override
    public void rollback() {
      if (finished) {
        return;
      }
      rollbackPublishedBackupArtifacts();
      closeUnusedBackupPassphrase();
      finished = true;
    }

    @Override
    public void close() {
      if (!finished) {
        rollback();
      }
    }

    private SqliteBookPassphrase currentBackupPassphrase() {
      return Objects.requireNonNull(backupPassphrase, "backupPassphrase");
    }

    private SqliteBookPassphrase takeBackupPassphrase() {
      SqliteBookPassphrase passphrase = currentBackupPassphrase();
      backupPassphrase = null;
      return passphrase;
    }

    private void closeUnusedBackupPassphrase() {
      if (backupPassphrase != null) {
        backupPassphrase.close();
        backupPassphrase = null;
      }
    }

    private void rollbackPublishedBackupArtifacts() {
      if (backupKeyFilePublished) {
        SqliteBookKeyFileGenerator.deleteQuietly(finalBackupBookKeyFilePath);
      }
      if (backupFilePublished) {
        SqliteBookKeyFileGenerator.deleteQuietly(finalBackupFilePath);
      }
      SqliteBookKeyFileGenerator.deleteQuietly(stagedBackupFilePath);
    }
  }

  /** Reversible staged replacement that delays the live swap until commit. */
  private static final class StagedReplacement implements StagedBookReplacement {
    private final Path stagedBookPath;
    private final Path targetBookPath;
    private final @Nullable Path previousTargetBackupPath;
    private boolean finished;

    private StagedReplacement(
        Path stagedBookPath, Path targetBookPath, @Nullable Path previousTargetBackupPath) {
      this.stagedBookPath = Objects.requireNonNull(stagedBookPath, "stagedBookPath");
      this.targetBookPath = Objects.requireNonNull(targetBookPath, "targetBookPath");
      this.previousTargetBackupPath = previousTargetBackupPath;
    }

    private static StagedReplacement create(
        Path normalizedSourceBookPath, Path normalizedTargetBookPath) {
      @Nullable Path stagedReplacementPath = null;
      @Nullable Path previousTargetBackupPath = null;
      try {
        SqliteBookFileSecurity.ensureSecureParentDirectory(normalizedTargetBookPath);
        SqliteBookMaintenanceFiles.cleanupAbandonedStageArtifacts(normalizedTargetBookPath);
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
        }
        return new StagedReplacement(
            stagedReplacementPath, normalizedTargetBookPath, previousTargetBackupPath);
      } catch (IOException exception) {
        deleteQuietlyIfPresent(stagedReplacementPath);
        deleteQuietlyIfPresent(previousTargetBackupPath);
        throw new IllegalStateException(
            "Failed to stage the FinGrind SQLite book replacement at "
                + normalizedTargetBookPath
                + ".",
            exception);
      }
    }

    @Override
    public Path stagedBookPath() {
      return stagedBookPath;
    }

    @Override
    public void commit() {
      if (finished) {
        return;
      }
      try {
        if (Files.exists(targetBookPath, LinkOption.NOFOLLOW_LINKS)) {
          moveReplacing(
              targetBookPath,
              Objects.requireNonNull(previousTargetBackupPath, "previousTargetBackupPath"));
        }
        moveReplacing(stagedBookPath, targetBookPath);
        deleteQuietlyIfPresent(previousTargetBackupPath);
        finished = true;
      } catch (IOException exception) {
        if (!Files.exists(targetBookPath, LinkOption.NOFOLLOW_LINKS)
            && previousTargetBackupPath != null
            && Files.exists(previousTargetBackupPath, LinkOption.NOFOLLOW_LINKS)) {
          try {
            moveReplacing(previousTargetBackupPath, targetBookPath);
          } catch (IOException restoreException) {
            SqliteBestEffort.reportCleanupFailure(
                "restoring one previous protected book after replacement commit failure",
                restoreException);
          }
        }
        throw new IllegalStateException(
            "Failed to commit the staged FinGrind SQLite replacement at " + targetBookPath + ".",
            exception);
      }
    }

    @Override
    public void rollback() {
      if (finished) {
        return;
      }
      SqliteBookKeyFileGenerator.deleteQuietly(stagedBookPath);
      deleteQuietlyIfPresent(previousTargetBackupPath);
      finished = true;
    }

    @Override
    public void close() {
      if (!finished) {
        rollback();
      }
    }
  }

  /**
   * Commit-only rollback-artifact deletion that leaves the recovery object untouched until publish.
   */
  private static final class StagedRollbackDeletion implements StagedRollbackArtifactDeletion {
    private final Path rollbackArtifactPath;
    private boolean finished;

    private StagedRollbackDeletion(Path rollbackArtifactPath) {
      this.rollbackArtifactPath =
          Objects.requireNonNull(rollbackArtifactPath, "rollbackArtifactPath");
    }

    private static StagedRollbackDeletion create(Path normalizedRollbackArtifactPath) {
      return new StagedRollbackDeletion(normalizedRollbackArtifactPath);
    }

    @Override
    public void commit() {
      if (finished) {
        return;
      }
      SqliteBookMaintenanceFiles.deleteRollbackArtifact(rollbackArtifactPath);
      finished = true;
    }

    @Override
    public void rollback() {
      if (finished) {
        return;
      }
      finished = true;
    }

    @Override
    public void close() {
      if (!finished) {
        rollback();
      }
    }
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
