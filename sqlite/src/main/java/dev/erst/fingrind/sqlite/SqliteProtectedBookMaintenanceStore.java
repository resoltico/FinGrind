package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.core.attestation.AttestationAppendOutcome;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationLifecycleRecoveryEvidenceVerifier;
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
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationBinding;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
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
    this(
        passphraseResolver,
        null,
        SqliteProtectedBookPublicationSupport.productionPairDirectoryForcer(),
        SqliteSecureRegularFileAccess::forceFile);
  }

  SqliteProtectedBookMaintenanceStore(
      SqlitePassphraseResolver passphraseResolver,
      SqliteProtectedBookPairPublicationPreparation.@Nullable RecoveredPairVerifier
          recoveredPairVerifier,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer,
      SqliteProtectedBookPairPublicationRecord.RecoveryRecordFileForcer recoveryRecordFileForcer) {
    this(
        passphraseResolver,
        recoveredPairVerifier,
        directoryForcer,
        recoveryRecordFileForcer,
        SqliteBookMaintenanceLease::acquireWorkflowScope);
  }

  SqliteProtectedBookMaintenanceStore(
      SqlitePassphraseResolver passphraseResolver,
      SqliteProtectedBookPairPublicationPreparation.@Nullable RecoveredPairVerifier
          recoveredPairVerifier,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer,
      SqliteProtectedBookPairPublicationRecord.RecoveryRecordFileForcer recoveryRecordFileForcer,
      SqliteProtectedBookMaintenanceArtifactStore.WorkflowScopeAcquirer workflowScopeAcquirer) {
    super(workflowScopeAcquirer);
    this.passphraseResolver = Objects.requireNonNull(passphraseResolver, "passphraseResolver");
    this.verificationSupport = new SqliteProtectedBookVerificationSupport();
    this.backupArtifactVerifier = new SqliteBackupArtifactVerifier(verificationSupport);
    this.pairPublicationPreparation =
        new SqliteProtectedBookPairPublicationPreparation(
            this,
            recoveredPairVerifier == null ? this::verifiesRecoveredPair : recoveredPairVerifier,
            directoryForcer,
            recoveryRecordFileForcer);
  }

  boolean verifiesRecoveredPair(
      Path normalizedBookTargetPath,
      Path normalizedSecretTargetPath,
      ProtectedBookPairPublicationBinding binding) {
    return hasRegularBookPair(normalizedBookTargetPath, normalizedSecretTargetPath)
        && SqliteBookKeyFile.loadDecision(normalizedSecretTargetPath)
            .fold(
                passphrase -> {
                  BookVerification verification =
                      verificationSupport.verifyResolvedBook(normalizedBookTargetPath, passphrase);
                  if (verification instanceof VerifiedBook verifiedBook) {
                    try (verifiedBook) {
                      return switch (binding) {
                        case ProtectedBookPairPublicationBinding.Backup backup ->
                            recoveredBackupMatches(
                                normalizedBookTargetPath, normalizedSecretTargetPath, backup);
                        case ProtectedBookPairPublicationBinding.Restore restore ->
                            recoveredRestoreMatches(verifiedBook, restore);
                        case ProtectedBookPairPublicationBinding.Rekey rekey ->
                            recoveredRekeyMatches(verifiedBook, rekey);
                      };
                    }
                  }
                  return false;
                },
                rejected -> false);
  }

  private boolean recoveredBackupMatches(
      Path normalizedBackupArtifactPath,
      Path normalizedBackupKeyFilePath,
      ProtectedBookPairPublicationBinding.Backup binding) {
    try {
      try (AttestedProtectedBookMaintenanceStore.VerifiedBackupArtifact artifact =
          backupArtifactVerifier.verify(
              normalizedBackupArtifactPath, normalizedBackupKeyFilePath)) {
        var verification = artifact.verification();
        var acknowledgement = binding.acknowledgement();
        return verification.backupId().equals(acknowledgement.backupId())
            && Arrays.equals(verification.artifactDigest(), acknowledgement.backupArtifactDigest())
            && verification.sourceOrder().equals(acknowledgement.sourceOrder())
            && Arrays.equals(
                verification.sourceOperationHead(), acknowledgement.sourceOperationHead());
      }
    } catch (RuntimeException invalidArtifact) {
      return false;
    }
  }

  private boolean recoveredRestoreMatches(
      VerifiedBook verifiedBook, ProtectedBookPairPublicationBinding.Restore binding) {
    AttestationCommit expectedCommit = binding.attestationCommit();
    return AttestationLifecycleRecoveryEvidenceVerifier.matchesRestoreHead(
        loadAttestationEvidence(verifiedBook),
        binding.acknowledgement(),
        expectedCommit.operationOrder(),
        HexFormat.of().parseHex(expectedCommit.operationHeadHex()));
  }

  private boolean recoveredRekeyMatches(
      VerifiedBook verifiedBook, ProtectedBookPairPublicationBinding.Rekey binding) {
    AttestationCommit sourceCommit = binding.sourceCommit();
    AttestationCommit expectedCommit = binding.attestationCommit();
    return AttestationLifecycleRecoveryEvidenceVerifier.matchesRekeyHead(
        loadAttestationEvidence(verifiedBook),
        sourceCommit.operationOrder(),
        HexFormat.of().parseHex(sourceCommit.operationHeadHex()),
        expectedCommit.operationOrder(),
        HexFormat.of().parseHex(expectedCommit.operationHeadHex()));
  }

  static boolean hasRegularBookPair(
      Path normalizedBookTargetPath, Path normalizedSecretTargetPath) {
    return Files.isRegularFile(normalizedBookTargetPath, LinkOption.NOFOLLOW_LINKS)
        && Files.isRegularFile(normalizedSecretTargetPath, LinkOption.NOFOLLOW_LINKS);
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
