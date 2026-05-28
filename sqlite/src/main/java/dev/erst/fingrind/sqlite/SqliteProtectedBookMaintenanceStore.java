package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.MaintenanceCompletion;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditCompensationKind;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditKind;
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
  public LeaseAcquisition acquireExistingArtifactLease(Path normalizedArtifactPath) {
    return switch (SqliteBookMaintenanceLease.acquire(
        normalizedArtifactPath, SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT)) {
      case SqliteHeldLease heldLease -> heldLease;
      case SqliteLeaseBusy leaseBusy -> new LeaseBusy(leaseBusy.artifactPath());
    };
  }

  @Override
  public LeaseAcquisition acquireManagedArtifactLease(Path normalizedArtifactPath) {
    return switch (SqliteBookMaintenanceLease.acquire(
        normalizedArtifactPath, SqliteMaintenanceLeaseIntent.MANAGED_TARGET)) {
      case SqliteHeldLease heldLease -> heldLease;
      case SqliteLeaseBusy leaseBusy -> new LeaseBusy(leaseBusy.artifactPath());
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
    SqliteProtectedBookStagingSupport.requireRegularNonSymlinkFile(normalizedBookPath);
    return passphraseResolver
        .resolve(
            normalizedBookPath,
            normalizedAccess.passphraseSource().toPublished(),
            SqlitePassphraseIntent.EXISTING_SECRET)
        .fold(
            bookPassphrase ->
                verificationSupport.verifyResolvedBook(normalizedBookPath, bookPassphrase),
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
                SqliteProtectedBookStagingSupport.stageResolvedBackupPair(
                    sourceAccess.bookFilePath(),
                    normalizedBackupFilePath,
                    normalizedBackupBookKeyFilePath,
                    sourcePassphrase,
                    verificationSupport),
            failure -> MaintenanceDecision.failed(MaintenanceFailure.fromContractFailure(failure)));
  }

  @Override
  public StagedBookReplacement stageReplacement(
      Path normalizedSourceBookPath, Path normalizedTargetBookPath) {
    return SqliteStagedBookReplacement.create(normalizedSourceBookPath, normalizedTargetBookPath);
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
    SqliteProtectedBookStagingSupport.requireRegularNonSymlinkFile(normalizedRollbackArtifactPath);
    return SqliteStagedRollbackDeletion.create(normalizedRollbackArtifactPath);
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
                auditSupport.appendResolvedMaintenanceAudit(
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
                auditSupport.appendResolvedMaintenanceAuditCompensation(
                    normalizedBookPath, passphrase, recordedAt, auditKind),
            failure -> MaintenanceDecision.failed(MaintenanceFailure.fromContractFailure(failure)));
  }
}
