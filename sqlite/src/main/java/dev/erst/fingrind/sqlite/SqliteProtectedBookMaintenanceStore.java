package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.MaintenanceCompletion;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditCompensationKind;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditKind;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.PreparedPairPublication;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * SQLite-backed maintenance store for protected-book verification and staged artifact workflows.
 */
public final class SqliteProtectedBookMaintenanceStore
    extends SqliteProtectedBookMaintenanceArtifactStore {
  private final SqlitePassphraseResolver passphraseResolver;
  private final SqliteProtectedBookVerificationSupport verificationSupport;
  private final SqliteProtectedBookMaintenanceAuditSupport auditSupport;
  private final SqliteProtectedBookPairPublicationPreparation pairPublicationPreparation;

  /** Creates the SQLite maintenance store with one passphrase-resolution seam. */
  public SqliteProtectedBookMaintenanceStore(SqlitePassphraseResolver passphraseResolver) {
    this(passphraseResolver, null);
  }

  SqliteProtectedBookMaintenanceStore(
      SqlitePassphraseResolver passphraseResolver,
      SqliteProtectedBookPairPublicationPreparation.@Nullable InterruptedPairCompanionBookVerifier
          interruptedPairCompanionBookVerifier) {
    this.passphraseResolver = Objects.requireNonNull(passphraseResolver, "passphraseResolver");
    this.verificationSupport = new SqliteProtectedBookVerificationSupport();
    this.auditSupport = new SqliteProtectedBookMaintenanceAuditSupport();
    this.pairPublicationPreparation =
        new SqliteProtectedBookPairPublicationPreparation(
            this,
            interruptedPairCompanionBookVerifier == null
                ? this::opensInitializedBook
                : interruptedPairCompanionBookVerifier);
  }

  @Override
  public PreparedPairPublication preparePairPublication(
      Path normalizedSecretTargetPath,
      Path normalizedBookTargetPath,
      RestoredBookTargetPolicy bookTargetPolicy,
      ProtectedBookMaintenanceArtifactRole bookArtifactRole,
      ProtectedBookMaintenanceArtifactRole secretArtifactRole) {
    return pairPublicationPreparation.prepare(
        normalizedSecretTargetPath,
        normalizedBookTargetPath,
        bookTargetPolicy,
        bookArtifactRole,
        secretArtifactRole);
  }

  void recoverInterruptedPairPublication(
      Path normalizedSecretTargetPath, Path normalizedBookTargetPath) {
    pairPublicationPreparation.recoverInterruptedPublication(
        normalizedSecretTargetPath, normalizedBookTargetPath);
  }

  private boolean opensInitializedBook(
      Path normalizedBookTargetPath, Path normalizedSecretTargetPath) {
    return hasRegularBookPair(normalizedBookTargetPath, normalizedSecretTargetPath)
        && SqliteBookKeyFile.loadDecision(normalizedSecretTargetPath)
            .fold(
                passphrase -> {
                  BookVerification verification =
                      verificationSupport.verifyResolvedBook(normalizedBookTargetPath, passphrase);
                  if (verification instanceof VerifiedBook verifiedBook) {
                    try (verifiedBook) {
                      return true;
                    }
                  }
                  return false;
                },
                rejected -> false);
  }

  static boolean hasRegularBookPair(
      Path normalizedBookTargetPath, Path normalizedSecretTargetPath) {
    return Files.isRegularFile(normalizedBookTargetPath, LinkOption.NOFOLLOW_LINKS)
        && Files.isRegularFile(normalizedSecretTargetPath, LinkOption.NOFOLLOW_LINKS);
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
      VerifiedBook sourceBook, PreparedPairPublication preparedPairPublication) {
    SqliteVerifiedBook verifiedSourceBook = requireVerifiedBook(sourceBook);
    SqlitePreparedPairPublication preparedPublication =
        requirePreparedPairPublication(preparedPairPublication);
    return SqliteProtectedBookStagingSupport.stageResolvedBackupPair(
        verifiedSourceBook.artifactPath(),
        preparedPublication,
        verifiedSourceBook.passphraseCopy(),
        verificationSupport);
  }

  @Override
  public MaintenanceDecision<StagedRestoredBookPair> stageRestoredBookPair(
      VerifiedBook sourceBook, PreparedPairPublication preparedPairPublication) {
    SqliteVerifiedBook verifiedSourceBook = requireVerifiedBook(sourceBook);
    SqlitePreparedPairPublication preparedPublication =
        requirePreparedPairPublication(preparedPairPublication);
    return SqliteProtectedBookStagingSupport.stageResolvedRestoredBookPair(
        verifiedSourceBook.artifactPath(),
        preparedPublication,
        verifiedSourceBook.passphraseCopy(),
        verificationSupport);
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
      SqliteProtectedBookStagingFiles.requireRegularNonSymlinkFile(normalizedBookPath);
    } catch (SqliteCallerPathContractException exception) {
      bookPassphrase.close();
      throw maintenanceRejection(artifactRole, exception);
    }
    return MaintenanceDecision.accepted(
        verificationSupport.verifyResolvedBook(normalizedBookPath, bookPassphrase));
  }

  private static SqlitePreparedPairPublication requirePreparedPairPublication(
      PreparedPairPublication preparedPairPublication) {
    if (preparedPairPublication
        instanceof SqlitePreparedPairPublication sqlitePreparedPublication) {
      return sqlitePreparedPublication;
    }
    throw new IllegalArgumentException(
        "The FinGrind SQLite maintenance store requires its own prepared pair-publication handle.");
  }
}
