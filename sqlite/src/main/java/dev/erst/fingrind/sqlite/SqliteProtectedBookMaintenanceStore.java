package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationOperationPreimages;
import dev.erst.fingrind.core.attestation.AttestationStaleHeadException;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.AttestedProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.PreparedPairPublication;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * SQLite-backed maintenance store for protected-book verification and staged artifact workflows.
 */
public final class SqliteProtectedBookMaintenanceStore
    extends SqliteProtectedBookMaintenanceArtifactStore
    implements AttestedProtectedBookMaintenanceStore {
  private final SqlitePassphraseResolver passphraseResolver;
  private final SqliteProtectedBookVerificationSupport verificationSupport;
  private final SqliteBackupArtifactVerifier backupArtifactVerifier;
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
    this.backupArtifactVerifier = new SqliteBackupArtifactVerifier(verificationSupport);
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
  public BackupArtifactPairState backupArtifactPairState(
      Path normalizedBackupArtifactPath, Path normalizedBackupKeyFilePath) {
    Path artifactPath =
        Objects.requireNonNull(normalizedBackupArtifactPath, "normalizedBackupArtifactPath");
    Path keyPath =
        Objects.requireNonNull(normalizedBackupKeyFilePath, "normalizedBackupKeyFilePath");
    boolean artifactExists = Files.exists(artifactPath, LinkOption.NOFOLLOW_LINKS);
    boolean keyExists = Files.exists(keyPath, LinkOption.NOFOLLOW_LINKS);
    if (artifactExists && keyExists) {
      return BackupArtifactPairState.COMPLETE;
    }
    if (artifactExists) {
      return BackupArtifactPairState.ARTIFACT_ONLY;
    }
    if (keyExists) {
      return BackupArtifactPairState.KEY_ONLY;
    }
    return BackupArtifactPairState.ABSENT;
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
  public List<AttestationEvidence> loadAttestationEvidence(VerifiedBook verifiedBook) {
    SqliteVerifiedBook sqliteVerifiedBook = requireVerifiedBook(verifiedBook);
    try (SqliteBookPassphrase passphrase = sqliteVerifiedBook.passphraseCopy();
        SqliteNativeDatabase database =
            SqliteNativeConnections.open(
                sqliteVerifiedBook.artifactPath(), passphrase, SqliteNativeOpenMode.READ_ONLY)) {
      return SqliteAttestationEvidenceStore.loadAll(database);
    }
  }

  @Override
  public AttestationVerification appendAttestedOperation(
      VerifiedBook verifiedBook,
      AttestationOperationKind operationKind,
      Instant recordedAt,
      AttestationOperationPreimages preimages,
      AttestationOperationAuthorizer authorizer,
      @Nullable AttestationBackupAcknowledgement backupAcknowledgement) {
    SqliteVerifiedBook sqliteVerifiedBook = requireVerifiedBook(verifiedBook);
    boolean retryStaleHead = retriesStaleHead(operationKind, backupAcknowledgement);
    return retryStaleHead(
        retryStaleHead,
        () ->
            appendAttestedOperationAttempt(
                sqliteVerifiedBook,
                operationKind,
                recordedAt,
                preimages,
                authorizer,
                backupAcknowledgement));
  }

  static <T> T retryStaleHead(boolean retryStaleHead, StaleHeadRetryAttempt<T> attempt) {
    StaleHeadRetryAttempt<T> checkedAttempt = Objects.requireNonNull(attempt, "attempt");
    while (true) {
      try {
        return checkedAttempt.run();
      } catch (AttestationStaleHeadException exception) {
        if (!retryStaleHead) {
          throw exception;
        }
      }
    }
  }

  static boolean retriesStaleHead(
      AttestationOperationKind operationKind,
      @Nullable AttestationBackupAcknowledgement backupAcknowledgement) {
    return operationKind == AttestationOperationKind.BACKUP_CREATED
        && backupAcknowledgement != null;
  }

  /** Supplies one attempt to append an operation whose authenticated head may become stale. */
  @FunctionalInterface
  interface StaleHeadRetryAttempt<T> {
    /** Performs one append attempt. */
    T run();
  }

  private static AttestationVerification appendAttestedOperationAttempt(
      SqliteVerifiedBook sqliteVerifiedBook,
      AttestationOperationKind operationKind,
      Instant recordedAt,
      AttestationOperationPreimages preimages,
      AttestationOperationAuthorizer authorizer,
      @Nullable AttestationBackupAcknowledgement backupAcknowledgement) {
    try (SqliteBookPassphrase passphrase = sqliteVerifiedBook.passphraseCopy();
        SqliteNativeDatabase database =
            SqliteNativeConnections.open(
                sqliteVerifiedBook.artifactPath(),
                passphrase,
                SqliteNativeOpenMode.READ_WRITE_EXISTING)) {
      SqliteAttestationEvidenceStore.ObservedHead observedHead =
          SqliteAttestationEvidenceStore.observeRequired(database);
      database.executeStatement("begin immediate");
      try {
        AttestationVerification verification =
            SqliteAttestationEvidenceStore.appendAuthorized(
                database,
                observedHead,
                operationKind,
                recordedAt,
                preimages,
                authorizer,
                backupAcknowledgement);
        database.executeStatement("commit");
        return verification;
      } catch (RuntimeException exception) {
        SqliteStoreOperations.rollbackQuietly(database);
        throw exception;
      }
    }
  }

  @Override
  public VerifiedBackupArtifact verifyBackupArtifact(
      Path normalizedBackupArtifactPath, Path normalizedBackupKeyFilePath) {
    return backupArtifactVerifier.verify(normalizedBackupArtifactPath, normalizedBackupKeyFilePath);
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
