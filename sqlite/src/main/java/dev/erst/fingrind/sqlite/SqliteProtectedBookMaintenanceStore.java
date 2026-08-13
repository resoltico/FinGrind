package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.attestation.AttestationAppendOutcome;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationOperationPreimages;
import dev.erst.fingrind.core.attestation.AttestationRegistryMutation;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.AttestedProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.PreparedPairPublication;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowScopeAcquisition;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowScopeBusy;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowSourceMembers;
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
    this(passphraseResolver, SqliteBookMaintenanceLease::acquireWorkflowScope);
  }

  SqliteProtectedBookMaintenanceStore(
      SqlitePassphraseResolver passphraseResolver,
      SqliteProtectedBookMaintenanceArtifactStore.WorkflowScopeAcquirer workflowScopeAcquirer) {
    super(workflowScopeAcquirer);
    this.passphraseResolver = Objects.requireNonNull(passphraseResolver, "passphraseResolver");
    this.verificationSupport = new SqliteProtectedBookVerificationSupport();
    this.backupArtifactVerifier = new SqliteBackupArtifactVerifier(verificationSupport);
    // Legacy retained-stage records are evidence-only during the journal migration. The sole
    // production path is the authenticated transaction journal, so a custom legacy verifier
    // cannot accidentally reinstate sidecar recovery authority.
    this.pairPublicationPreparation = SqliteProtectedBookPairPublicationPreparation.journaled(this);
  }

  @Override
  public WorkflowScopeAcquisition acquireWorkflowScope(
      WorkflowSourceMembers normalizedSourceMembers,
      Path normalizedBookTargetPath,
      ProtectedBookMaintenanceArtifactRole bookTargetArtifactRole,
      Path normalizedSecretTargetPath,
      ProtectedBookMaintenanceArtifactRole secretTargetArtifactRole) {
    return switch (acquireWorkflowLeaseScope(
        normalizedSourceMembers,
        normalizedBookTargetPath,
        bookTargetArtifactRole,
        normalizedSecretTargetPath,
        secretTargetArtifactRole)) {
      case SqliteWorkflowScopeHeld held ->
          new SqliteProtectedBookWorkflowScope(
              held.scope(),
              pairPublicationPreparation,
              normalizedBookTargetPath,
              normalizedSecretTargetPath,
              bookTargetArtifactRole,
              secretTargetArtifactRole);
      case SqliteWorkflowScopeBusy busy ->
          new WorkflowScopeBusy(busy.artifactPath(), busy.artifactRole());
    };
  }

  @Override
  public MaintenanceDecision<BookVerification> verifyInitializedBook(
      ProtectedBookAccess bookAccess, ProtectedBookMaintenanceArtifactRole artifactRole) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Path normalizedBookPath =
        normalizeOptionalInspectionArtifact(
            bookAccess.bookFilePath(), "bookFilePath", artifactRole);
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
    return SqliteProtectedBookBackupStaging.stageResolvedPair(
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
    return SqliteProtectedBookRestoreStaging.stageResolvedPair(
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
  public AttestationAppendOutcome appendAttestedOperation(
      VerifiedBook verifiedBook,
      AttestationOperationKind operationKind,
      Instant recordedAt,
      AttestationOperationPreimages preimages,
      AttestationOperationAuthorizer authorizer,
      @Nullable AttestationBackupAcknowledgement backupAcknowledgement) {
    SqliteVerifiedBook sqliteVerifiedBook = requireVerifiedBook(verifiedBook);
    return SqliteAttestedOperationAppender.append(
        sqliteVerifiedBook,
        operationKind,
        recordedAt,
        preimages,
        authorizer,
        backupAcknowledgement);
  }

  @Override
  public AttestationVerification appendAttestedRegistryMutation(
      VerifiedBook verifiedBook,
      AttestationRegistryMutation mutation,
      Instant recordedAt,
      AttestationOperationAuthorizer authorizer) {
    return SqliteAttestedOperationAppender.appendRegistryMutation(
        requireVerifiedBook(verifiedBook), mutation, recordedAt, authorizer);
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
