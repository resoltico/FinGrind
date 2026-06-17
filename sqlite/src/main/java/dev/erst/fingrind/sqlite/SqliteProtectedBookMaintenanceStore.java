package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.MaintenanceCompletion;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditCompensationKind;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditKind;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import dev.erst.fingrind.executor.spi.StagedBookReplacement;
import dev.erst.fingrind.executor.spi.StagedRollbackArtifactDeletion;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * SQLite-backed maintenance store for protected-book verification and staged artifact workflows.
 */
public final class SqliteProtectedBookMaintenanceStore implements ProtectedBookMaintenanceStore {
  private final SqlitePassphraseResolver passphraseResolver;
  private final SqliteProtectedBookVerificationSupport verificationSupport;
  private final SqliteProtectedBookMaintenanceAuditSupport auditSupport;

  /** Creates the SQLite maintenance store with one passphrase-resolution seam. */
  public SqliteProtectedBookMaintenanceStore(SqlitePassphraseResolver passphraseResolver) {
    this.passphraseResolver = Objects.requireNonNull(passphraseResolver, "passphraseResolver");
    this.verificationSupport = new SqliteProtectedBookVerificationSupport();
    this.auditSupport = new SqliteProtectedBookMaintenanceAuditSupport();
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
  public LeaseAcquisition acquireExistingArtifactLease(
      Path normalizedArtifactPath, ProtectedBookMaintenanceArtifactRole artifactRole) {
    try {
      return switch (SqliteBookMaintenanceLease.acquire(
          normalizedArtifactPath, SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT)) {
        case SqliteHeldLease heldLease -> heldLease;
        case SqliteLeaseBusy leaseBusy -> new LeaseBusy(leaseBusy.artifactPath());
      };
    } catch (SqliteCallerPathContractException exception) {
      throw maintenanceRejection(artifactRole, exception);
    }
  }

  @Override
  public LeaseAcquisition acquireManagedArtifactLease(
      Path normalizedArtifactPath, ProtectedBookMaintenanceArtifactRole artifactRole) {
    try {
      return switch (SqliteBookMaintenanceLease.acquire(
          normalizedArtifactPath, SqliteMaintenanceLeaseIntent.MANAGED_TARGET)) {
        case SqliteHeldLease heldLease -> heldLease;
        case SqliteLeaseBusy leaseBusy -> new LeaseBusy(leaseBusy.artifactPath());
      };
    } catch (SqliteCallerPathContractException exception) {
      throw maintenanceRejection(artifactRole, exception);
    }
  }

  @Override
  public MaintenanceDecision<BookVerification> verifyInitializedBook(
      ProtectedBookAccess bookAccess, ProtectedBookMaintenanceArtifactRole artifactRole) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Path normalizedBookPath = normalize(bookAccess.bookFilePath(), "bookFilePath");
    ProtectedBookAccess normalizedAccess =
        new ProtectedBookAccess(normalizedBookPath, bookAccess.passphraseSource());
    if (!Files.exists(normalizedBookPath, LinkOption.NOFOLLOW_LINKS)) {
      return MaintenanceDecision.accepted(
          new VerificationFailure(normalizedBookPath, ProtectedBookVerificationFailure.MISSING));
    }
    return passphraseResolver
        .resolve(
            normalizedBookPath,
            normalizedAccess.passphraseSource().toPublished(),
            SqlitePassphraseIntent.EXISTING_SECRET)
        .fold(
            bookPassphrase ->
                verifyInitializedResolvedBook(normalizedBookPath, bookPassphrase, artifactRole),
            failure -> MaintenanceDecision.failed(MaintenanceFailure.fromContractFailure(failure)));
  }

  @Override
  public MaintenanceDecision<StagedBackupPair> stageBackupPair(
      VerifiedBook sourceBook,
      Path normalizedBackupFilePath,
      Path normalizedBackupBookKeyFilePath) {
    SqliteVerifiedBook verifiedSourceBook = requireVerifiedBook(sourceBook);
    Objects.requireNonNull(normalizedBackupFilePath, "normalizedBackupFilePath");
    Objects.requireNonNull(normalizedBackupBookKeyFilePath, "normalizedBackupBookKeyFilePath");
    try {
      return SqliteProtectedBookStagingSupport.stageResolvedBackupPair(
          verifiedSourceBook.artifactPath(),
          normalizedBackupFilePath,
          normalizedBackupBookKeyFilePath,
          verifiedSourceBook.passphraseCopy(),
          verificationSupport);
    } catch (SqliteCallerPathContractException exception) {
      ProtectedBookMaintenanceArtifactRole artifactRole =
          exception.requestedPath().equals(normalizedBackupBookKeyFilePath)
              ? ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET
              : ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET;
      throw maintenanceRejection(artifactRole, exception);
    }
  }

  @Override
  public MaintenanceDecision<BookVerification> verifyInitializedReplica(
      Path normalizedReplicaBookPath, VerifiedBook sourceBook) {
    Objects.requireNonNull(normalizedReplicaBookPath, "normalizedReplicaBookPath");
    SqliteVerifiedBook verifiedSourceBook = requireVerifiedBook(sourceBook);
    return verifyInitializedResolvedBook(
        normalizedReplicaBookPath,
        verifiedSourceBook.passphraseCopy(),
        ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET);
  }

  @Override
  public StagedBookReplacement stageReplacement(
      Path normalizedSourceBookPath, Path normalizedTargetBookPath) {
    try {
      return SqliteStagedBookReplacement.create(normalizedSourceBookPath, normalizedTargetBookPath);
    } catch (SqliteCallerPathContractException exception) {
      throw maintenanceRejection(ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET, exception);
    }
  }

  @Override
  public List<Path> staleRollbackArtifacts(Path normalizedBookPath) {
    try {
      return SqliteRekeyRollbackFile.staleRollbackArtifacts(normalizedBookPath);
    } catch (java.io.IOException exception) {
      throw new IllegalStateException(
          "Failed to inspect FinGrind SQLite rollback artifacts beside "
              + dev.erst.fingrind.contract.runtime.PublicPathHint.fromPath(normalizedBookPath)
                  .value()
              + ".",
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
    try {
      SqliteProtectedBookStagingSupport.requireRegularNonSymlinkFile(
          normalizedRollbackArtifactPath);
      return SqliteStagedRollbackDeletion.create(normalizedRollbackArtifactPath);
    } catch (SqliteCallerPathContractException exception) {
      throw maintenanceRejection(ProtectedBookMaintenanceArtifactRole.ROLLBACK_ARTIFACT, exception);
    }
  }

  @Override
  public MaintenanceDecision<MaintenanceCompletion> appendMaintenanceAudit(
      VerifiedBook verifiedBook, Instant recordedAt, ProtectedBookMaintenanceAuditKind auditKind) {
    Objects.requireNonNull(recordedAt, "recordedAt");
    Objects.requireNonNull(auditKind, "auditKind");
    SqliteVerifiedBook sqliteVerifiedBook = requireVerifiedBook(verifiedBook);
    return auditSupport.appendResolvedMaintenanceAudit(
        sqliteVerifiedBook.artifactPath(),
        sqliteVerifiedBook.passphraseCopy(),
        recordedAt,
        auditKind);
  }

  @Override
  public MaintenanceDecision<MaintenanceCompletion> appendMaintenanceAuditCompensation(
      VerifiedBook verifiedBook,
      Instant recordedAt,
      ProtectedBookMaintenanceAuditCompensationKind auditKind) {
    Objects.requireNonNull(recordedAt, "recordedAt");
    Objects.requireNonNull(auditKind, "auditKind");
    SqliteVerifiedBook sqliteVerifiedBook = requireVerifiedBook(verifiedBook);
    return auditSupport.appendResolvedMaintenanceAuditCompensation(
        sqliteVerifiedBook.artifactPath(),
        sqliteVerifiedBook.passphraseCopy(),
        recordedAt,
        auditKind);
  }

  private MaintenanceDecision<BookVerification> verifyInitializedResolvedBook(
      Path normalizedBookPath,
      SqliteBookPassphrase bookPassphrase,
      ProtectedBookMaintenanceArtifactRole artifactRole) {
    if (!Files.exists(normalizedBookPath, LinkOption.NOFOLLOW_LINKS)) {
      bookPassphrase.close();
      return MaintenanceDecision.accepted(
          new VerificationFailure(normalizedBookPath, ProtectedBookVerificationFailure.MISSING));
    }
    try {
      SqliteProtectedBookStagingSupport.requireRegularNonSymlinkFile(normalizedBookPath);
    } catch (SqliteCallerPathContractException exception) {
      bookPassphrase.close();
      throw maintenanceRejection(artifactRole, exception);
    }
    return verificationSupport.verifyResolvedBook(normalizedBookPath, bookPassphrase);
  }

  private static SqliteVerifiedBook requireVerifiedBook(VerifiedBook verifiedBook) {
    Objects.requireNonNull(verifiedBook, "verifiedBook");
    if (verifiedBook instanceof SqliteVerifiedBook sqliteVerifiedBook) {
      return sqliteVerifiedBook;
    }
    throw new IllegalArgumentException(
        "The SQLite maintenance store requires one verified SQLite book handle.");
  }

  private static ProtectedBookMaintenanceRejectionException maintenanceRejection(
      ProtectedBookMaintenanceArtifactRole artifactRole,
      SqliteCallerPathContractException exception) {
    return new ProtectedBookMaintenanceRejectionException(
        SqliteCallerPathFailureMapper.maintenanceRejection(artifactRole, exception));
  }
}
