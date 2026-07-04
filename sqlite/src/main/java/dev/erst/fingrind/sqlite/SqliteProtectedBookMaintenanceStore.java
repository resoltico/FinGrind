package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.MaintenanceCompletion;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditCompensationKind;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditKind;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * SQLite-backed maintenance store for protected-book verification and staged artifact workflows.
 */
public final class SqliteProtectedBookMaintenanceStore
    extends SqliteProtectedBookMaintenanceArtifactStore {
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
  public MaintenanceDecision<StagedRestoredBookPair> stageRestoredBookPair(
      VerifiedBook sourceBook, Path normalizedBookFilePath, Path normalizedBookKeyFilePath) {
    SqliteVerifiedBook verifiedSourceBook = requireVerifiedBook(sourceBook);
    Objects.requireNonNull(normalizedBookFilePath, "normalizedBookFilePath");
    Objects.requireNonNull(normalizedBookKeyFilePath, "normalizedBookKeyFilePath");
    try {
      return SqliteProtectedBookStagingSupport.stageResolvedRestoredBookPair(
          verifiedSourceBook.artifactPath(),
          normalizedBookFilePath,
          normalizedBookKeyFilePath,
          verifiedSourceBook.passphraseCopy(),
          verificationSupport);
    } catch (SqliteCallerPathContractException exception) {
      throw maintenanceRejection(ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET, exception);
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
}
