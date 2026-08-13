package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublication;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.attestation.AttestationAdmissionRejectedException;
import dev.erst.fingrind.core.attestation.AttestationArtifactSnapshotReader;
import dev.erst.fingrind.core.attestation.AttestationAuthorizationException;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.core.attestation.AttestationBackupArtifact;
import dev.erst.fingrind.core.attestation.AttestationBackupArtifactVerification;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationKeyFiles;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationOperationPreimages;
import dev.erst.fingrind.core.attestation.AttestationOperationRequest;
import dev.erst.fingrind.core.attestation.AttestationRegistryMutation;
import dev.erst.fingrind.core.attestation.AttestationSigningSession;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.core.attestation.AttestationVerificationException;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.spi.AttestedProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowSourceMember;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowSourceMembers;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationAdmission;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationRecoveryRequest;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import dev.erst.fingrind.executor.spi.StagedPairPublicationCommitOutcome;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Deterministic attested-store fixture that uses real evidence, artifacts, and signing sessions.
 */
final class AttestationMaintenanceTestSupport {
  private static final UUID PRINCIPAL_ID = UUID.fromString("10213243-5465-7687-98a9-babcbddceeff");

  private AttestationMaintenanceTestSupport() {}

  static CredentialFixture createCredential(Path directory) throws IOException {
    return createCredential(directory, PRINCIPAL_ID, "founder");
  }

  static CredentialFixture createCredential(Path directory, UUID principalId, String fileStem)
      throws IOException {
    Path canonicalDirectory = directory.toRealPath();
    Path keyPath = canonicalDirectory.resolve(fileStem + ".fgatk");
    Path passphrasePath = canonicalDirectory.resolve(fileStem + ".passphrase");
    char[] passphrase = "test attestation passphrase".toCharArray();
    try {
      AttestationKeyFiles.create(keyPath, passphrase);
      Files.writeString(passphrasePath, "test attestation passphrase\n");
    } finally {
      java.util.Arrays.fill(passphrase, '\0');
    }
    return new CredentialFixture(
        new AttestationCredentialSource(
            dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8,
            principalId,
            keyPath,
            passphrasePath));
  }

  static AttestationEvidence genesis(CredentialFixture credential, Instant recordedAt) {
    return genesis(List.of(credential), recordedAt);
  }

  static AttestationEvidence genesis(List<CredentialFixture> credentials, Instant recordedAt) {
    return AttestationGenesisFactory.prepare(
            ExecutorAccountingTestSupport.bookIdentity(),
            recordedAt,
            credentials.stream()
                .map(
                    credential ->
                        new dev.erst.fingrind.contract.bookkeeping.AttestationFounderInput(
                            dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8,
                            credential.source().principalId(),
                            credential.source().encryptedKeyFilePath(),
                            credential.source().passphraseFilePath()))
                .toList())
        .evidence();
  }

  static BookAccess bookAccess(Path bookPath, CredentialFixture credential) {
    return new BookAccess(
        bookPath.toAbsolutePath().normalize(),
        new BookAccess.PassphraseSource.KeyFile(bookPath.resolveSibling("book.key")),
        List.of(credential.source()));
  }

  record CredentialFixture(AttestationCredentialSource source) {
    CredentialFixture {
      Objects.requireNonNull(source, "source");
    }

    AttestationSigningSession openSession() {
      return AttestationSigningSessionFactory.open(List.of(source));
    }
  }

  /** Shared mutable controls and evidence state for the maintenance-store fixture. */
  private static class MaintenanceFixtureControl {
    final MaintenanceStore.BookHandles bookHandles;
    final MaintenanceStore.EvidenceState evidenceState;
    final MaintenanceStore.AdmissionState admissionState;
    final MaintenanceStore.StagingState stagingState;
    final MaintenanceStore.FailureState failureState;
    private final FixtureOverrides overrides;

    private MaintenanceFixtureControl(Path bookPath, List<AttestationEvidence> evidence) {
      Path normalizedBookPath = MaintenanceStore.normalizePath(bookPath, "bookPath");
      bookHandles = new MaintenanceStore.BookHandles(normalizedBookPath);
      evidenceState = new MaintenanceStore.EvidenceState(bookHandles, evidence);
      admissionState =
          new MaintenanceStore.AdmissionState(normalizedBookPath, bookHandles.liveBook());
      failureState = new MaintenanceStore.FailureState();
      stagingState = new MaintenanceStore.StagingState(this, bookHandles);
      overrides = new FixtureOverrides(admissionState, stagingState, failureState);
    }

    void setLiveVerification(
        MaintenanceDecision<ProtectedBookMaintenanceStore.BookVerification> verification) {
      admissionState.liveVerification = Objects.requireNonNull(verification, "verification");
    }

    void rejectLiveVerification(ProtectedBookMaintenanceRejection rejection) {
      failureState.liveVerification =
          new ProtectedBookMaintenanceRejectionException(
              Objects.requireNonNull(rejection, "rejection"));
    }

    void rejectVerificationFor(
        ProtectedBookAccess bookAccess, ProtectedBookMaintenanceRejection rejection) {
      failureState.verificationByBookAccess.put(
          Objects.requireNonNull(bookAccess, "bookAccess"),
          new ProtectedBookMaintenanceRejectionException(
              Objects.requireNonNull(rejection, "rejection")));
    }

    void setInjectedPairAdmission(ProtectedBookPairPublicationAdmission admission) {
      admissionState.injectedPairAdmission = Objects.requireNonNull(admission, "admission");
    }

    void setLiveBlockingArtifacts(List<Path> blockingArtifacts) {
      admissionState.liveBlockingArtifacts = List.copyOf(blockingArtifacts);
    }

    void setManagedLease(ProtectedBookMaintenanceStore.LeaseAcquisition lease) {
      admissionState.managedLease = Objects.requireNonNull(lease, "lease");
    }

    void setExistingLease(ProtectedBookMaintenanceStore.LeaseAcquisition lease) {
      admissionState.existingLease = Objects.requireNonNull(lease, "lease");
    }

    void setStagedBackup(MaintenanceDecision<StagedBackupPair> staged) {
      stagingState.backup = Objects.requireNonNull(staged, "staged");
    }

    void setStagedRestore(MaintenanceDecision<StagedRestoredBookPair> staged) {
      stagingState.restore = Objects.requireNonNull(staged, "staged");
    }

    void setStagedBackupVerification(
        MaintenanceDecision<ProtectedBookMaintenanceStore.BookVerification> verification) {
      stagingState.backupVerification = Objects.requireNonNull(verification, "verification");
    }

    void setPairAdmissionFailure(RuntimeException failure) {
      failureState.pairAdmission = Objects.requireNonNull(failure, "failure");
    }

    void canonicalize(Path requestedPath, Path canonicalPath) {
      admissionState.canonicalPaths.put(
          MaintenanceStore.normalizePath(requestedPath, "requestedPath"),
          MaintenanceStore.normalizePath(canonicalPath, "canonicalPath"));
    }

    void rejectNormalization(
        Path requestedPath,
        ProtectedBookMaintenanceArtifactRole observedArtifactRole,
        ProtectedBookMaintenanceRejection.ArtifactPathInvalid rejection) {
      admissionState.normalizationFailures.put(
          new MaintenanceStore.NormalizationFailureKey(
              MaintenanceStore.normalizePath(requestedPath, "requestedPath"),
              Objects.requireNonNull(observedArtifactRole, "observedArtifactRole")),
          new ProtectedBookMaintenanceRejectionException(
              Objects.requireNonNull(rejection, "rejection")));
    }

    void rejectExistingSourceNormalization(
        Path requestedPath,
        ProtectedBookMaintenanceArtifactRole observedArtifactRole,
        ProtectedBookMaintenanceRejection.ArtifactPathInvalid rejection) {
      admissionState.existingSourceNormalizationFailures.put(
          new MaintenanceStore.NormalizationFailureKey(
              MaintenanceStore.normalizePath(requestedPath, "requestedPath"),
              Objects.requireNonNull(observedArtifactRole, "observedArtifactRole")),
          new ProtectedBookMaintenanceRejectionException(
              Objects.requireNonNull(rejection, "rejection")));
    }

    Path verifiedBookPath() {
      return Objects.requireNonNull(admissionState.verifiedBookPath, "verifiedBookPath");
    }

    ProtectedBookAccess verifiedBookAccess() {
      return Objects.requireNonNull(admissionState.verifiedBookAccess, "verifiedBookAccess");
    }

    List<MaintenanceStore.NormalizationRequest> normalizationRequests() {
      return List.copyOf(admissionState.normalizationRequests);
    }

    List<AttestationEvidence> liveEvidence() {
      return evidenceFor(bookHandles.liveBook());
    }

    List<AttestationEvidence> restoredEvidence() {
      return evidenceFor(bookHandles.restoredBook());
    }

    void setLiveEvidence(List<AttestationEvidence> attestationEvidence) {
      evidenceState.byBook.put(bookHandles.liveBook(), List.copyOf(attestationEvidence));
    }

    void setSnapshotEvidence(List<AttestationEvidence> attestationEvidence) {
      evidenceState.byBook.put(bookHandles.snapshotBook(), List.copyOf(attestationEvidence));
    }

    FixtureOverrides overrides() {
      return overrides;
    }

    private List<AttestationEvidence> evidenceFor(StubBook book) {
      return Objects.requireNonNull(evidenceState.byBook.get(book), "known verified book");
    }

    private void sealArtifact(byte[] artifact) {
      admissionState.sealedBackupArtifact = artifact.clone();
    }

    private void publishBackup() {
      PublicationTargets publicationTargets =
          Objects.requireNonNull(
              admissionState.lastPreparedPublicationTargets, "last prepared publication targets");
      admissionState.completedBackupPublicationTargets = publicationTargets;
    }
  }

  /** Mutable test double for maintenance workflows that do not need attestation persistence. */
  static class MaintenanceStore extends MaintenanceFixtureControl
      implements ProtectedBookMaintenanceStore {

    MaintenanceStore(Path bookPath, List<AttestationEvidence> evidence) {
      super(bookPath, evidence);
    }

    @Override
    public Path normalizeOptionalInspectionArtifact(
        Path path, String argumentName, ProtectedBookMaintenanceArtifactRole artifactRole) {
      return normalize(path, argumentName, artifactRole, NormalizationBoundary.OPTIONAL_ARTIFACT);
    }

    @Override
    public Path normalizeFinalTarget(
        Path path, String argumentName, ProtectedBookMaintenanceArtifactRole artifactRole) {
      return normalize(path, argumentName, artifactRole, NormalizationBoundary.FINAL_TARGET);
    }

    @Override
    public Path normalizeExistingSource(
        Path path, String argumentName, ProtectedBookMaintenanceArtifactRole artifactRole) {
      return normalize(path, argumentName, artifactRole, NormalizationBoundary.EXISTING_SOURCE);
    }

    private Path normalize(
        Path path,
        String argumentName,
        ProtectedBookMaintenanceArtifactRole artifactRole,
        NormalizationBoundary normalizationBoundary) {
      Path normalizedPath = normalizePath(path, argumentName);
      admissionState.normalizationRequests.add(
          new NormalizationRequest(
              normalizedPath, argumentName, artifactRole, normalizationBoundary));
      NormalizationFailureKey failureKey =
          new NormalizationFailureKey(normalizedPath, artifactRole);
      RuntimeException rejection =
          normalizationBoundary == NormalizationBoundary.EXISTING_SOURCE
              ? admissionState.existingSourceNormalizationFailures.get(failureKey)
              : null;
      if (rejection == null) {
        rejection = admissionState.normalizationFailures.get(failureKey);
      }
      if (rejection != null) {
        throw rejection;
      }
      return admissionState.canonicalPaths.getOrDefault(normalizedPath, normalizedPath);
    }

    private static Path normalizePath(Path path, String argumentName) {
      Objects.requireNonNull(argumentName, "argumentName");
      return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    private static ProtectedBookMaintenanceArtifactRole roleForWorkflowMember(
        Path artifactPath,
        WorkflowSourceMembers sourceMembers,
        Path bookTargetPath,
        ProtectedBookMaintenanceArtifactRole bookTargetArtifactRole,
        ProtectedBookMaintenanceArtifactRole secretTargetArtifactRole) {
      Path checkedArtifactPath = Objects.requireNonNull(artifactPath, "artifactPath");
      for (WorkflowSourceMember sourceMember :
          Objects.requireNonNull(sourceMembers, "sourceMembers").members()) {
        if (checkedArtifactPath.equals(sourceMember.artifactPath())) {
          return sourceMember.artifactRole();
        }
      }
      if (checkedArtifactPath.equals(Objects.requireNonNull(bookTargetPath, "bookTargetPath"))) {
        return Objects.requireNonNull(bookTargetArtifactRole, "bookTargetArtifactRole");
      }
      return Objects.requireNonNull(secretTargetArtifactRole, "secretTargetArtifactRole");
    }

    @Override
    public WorkflowScopeAcquisition acquireWorkflowScope(
        WorkflowSourceMembers normalizedSourceMembers,
        Path normalizedBookTargetPath,
        ProtectedBookMaintenanceArtifactRole bookTargetArtifactRole,
        Path normalizedSecretTargetPath,
        ProtectedBookMaintenanceArtifactRole secretTargetArtifactRole) {
      if (failureState.workflowScopeAcquisition != null) {
        throw failureState.workflowScopeAcquisition;
      }
      if (admissionState.existingLease instanceof LeaseBusy busy) {
        return new WorkflowScopeBusy(
            busy.artifactPath(),
            roleForWorkflowMember(
                busy.artifactPath(),
                normalizedSourceMembers,
                normalizedBookTargetPath,
                bookTargetArtifactRole,
                secretTargetArtifactRole));
      }
      if (admissionState.managedLease instanceof LeaseBusy busy) {
        return new WorkflowScopeBusy(
            busy.artifactPath(),
            roleForWorkflowMember(
                busy.artifactPath(),
                normalizedSourceMembers,
                normalizedBookTargetPath,
                bookTargetArtifactRole,
                secretTargetArtifactRole));
      }
      return new StubWorkflowScope(
          normalizedSourceMembers.primaryMember().artifactPath(),
          normalizedBookTargetPath,
          normalizedSecretTargetPath,
          admissionState,
          failureState);
    }

    @Override
    public List<Path> blockingArtifactsForBook(Path normalizedBookPath) {
      return admissionState.liveBlockingArtifacts;
    }

    @Override
    public List<Path> blockingArtifactsForBackupSource(Path normalizedBackupFilePath) {
      return admissionState.backupBlockingArtifacts;
    }

    @Override
    public LeaseAcquisition acquireExistingArtifactLease(
        Path normalizedArtifactPath, ProtectedBookMaintenanceArtifactRole artifactRole) {
      if (failureState.workflowScopeAcquisition != null) {
        throw failureState.workflowScopeAcquisition;
      }
      return admissionState.existingLease;
    }

    @Override
    public LeaseAcquisition acquireManagedArtifactLease(
        Path normalizedArtifactPath, ProtectedBookMaintenanceArtifactRole artifactRole) {
      return admissionState.managedLease;
    }

    @Override
    public MaintenanceDecision<BookVerification> verifyInitializedBook(
        ProtectedBookAccess bookAccess, ProtectedBookMaintenanceArtifactRole artifactRole) {
      if (failureState.liveVerification != null) {
        throw failureState.liveVerification;
      }
      RuntimeException verificationFailure = failureState.verificationByBookAccess.get(bookAccess);
      if (verificationFailure != null) {
        throw verificationFailure;
      }
      admissionState.verifiedBookAccess = bookAccess;
      admissionState.verifiedBookPath = bookAccess.bookFilePath();
      return admissionState.liveVerification;
    }

    @Override
    public MaintenanceDecision<StagedBackupPair> stageBackupPair(
        VerifiedBook sourceBook, PreparedPairPublication preparedPairPublication) {
      if (failureState.stagedBackup != null) {
        throw failureState.stagedBackup;
      }
      return stagingState.backup;
    }

    @Override
    public MaintenanceDecision<StagedRestoredBookPair> stageRestoredBookPair(
        VerifiedBook sourceBook, PreparedPairPublication preparedPairPublication) {
      return stagingState.restore;
    }

    protected List<AttestationEvidence> evidenceFor(StubBook book) {
      return Objects.requireNonNull(evidenceState.byBook.get(book), "known verified book");
    }

    /** Groups the three verified book handles that share one fixture filesystem root. */
    private record BookHandles(StubBook liveBook, StubBook snapshotBook, StubBook restoredBook) {
      private BookHandles(Path normalizedBookPath) {
        this(
            new StubBook(normalizedBookPath),
            new StubBook(normalizedBookPath.resolveSibling("snapshot.sqlite")),
            new StubBook(normalizedBookPath.resolveSibling("restored.sqlite")));
      }
    }

    /** Owns test evidence independently for each verified-book handle. */
    private static final class EvidenceState {
      private final Map<StubBook, List<AttestationEvidence>> byBook = new ConcurrentHashMap<>();
      private final byte[] snapshot = new byte[] {4, 8, 15, 16, 23, 42};

      private EvidenceState(BookHandles bookHandles, List<AttestationEvidence> evidence) {
        List<AttestationEvidence> checkedEvidence = List.copyOf(evidence);
        byBook.put(bookHandles.liveBook(), checkedEvidence);
        byBook.put(bookHandles.snapshotBook(), checkedEvidence);
        byBook.put(bookHandles.restoredBook(), checkedEvidence);
      }
    }

    /** Holds mutable admission outcomes before a maintenance workflow opens a verified book. */
    private static final class AdmissionState {
      private List<Path> liveBlockingArtifacts = List.of();
      private List<Path> backupBlockingArtifacts = List.of();
      private LeaseAcquisition managedLease;
      private LeaseAcquisition existingLease;
      private MaintenanceDecision<BookVerification> liveVerification;
      private @Nullable ProtectedBookPairPublicationAdmission injectedPairAdmission;
      private @Nullable PublicationTargets lastPreparedPublicationTargets;
      private @Nullable PublicationTargets completedBackupPublicationTargets;
      private byte @Nullable [] sealedBackupArtifact;
      private final Map<Path, Path> canonicalPaths = new ConcurrentHashMap<>();
      private final Map<NormalizationFailureKey, RuntimeException> normalizationFailures =
          new ConcurrentHashMap<>();
      private final Map<NormalizationFailureKey, RuntimeException>
          existingSourceNormalizationFailures = new ConcurrentHashMap<>();
      private final List<NormalizationRequest> normalizationRequests = new ArrayList<>();
      private @Nullable Path verifiedBookPath;
      private @Nullable ProtectedBookAccess verifiedBookAccess;

      private AdmissionState(Path normalizedBookPath, StubBook liveBook) {
        managedLease = new StubLease(normalizedBookPath);
        existingLease = new StubLease(normalizedBookPath);
        liveVerification = MaintenanceDecision.accepted(liveBook);
      }
    }

    /** Closed fixture categories for testing maintenance artifact normalization boundaries. */
    enum NormalizationBoundary {
      OPTIONAL_ARTIFACT,
      FINAL_TARGET,
      EXISTING_SOURCE
    }

    record NormalizationRequest(
        Path requestedPath,
        String argumentName,
        ProtectedBookMaintenanceArtifactRole artifactRole,
        NormalizationBoundary normalizationBoundary) {
      NormalizationRequest {
        Objects.requireNonNull(requestedPath, "requestedPath");
        Objects.requireNonNull(argumentName, "argumentName");
        Objects.requireNonNull(artifactRole, "artifactRole");
        Objects.requireNonNull(normalizationBoundary, "normalizationBoundary");
      }
    }

    private record NormalizationFailureKey(
        Path requestedPath, ProtectedBookMaintenanceArtifactRole artifactRole) {
      private NormalizationFailureKey {
        Objects.requireNonNull(requestedPath, "requestedPath");
        Objects.requireNonNull(artifactRole, "artifactRole");
      }
    }

    /** Holds staging and staged-verification outcomes for each lifecycle artifact kind. */
    private static final class StagingState {
      private MaintenanceDecision<StagedBackupPair> backup;
      private MaintenanceDecision<StagedRestoredBookPair> restore;
      private MaintenanceDecision<BookVerification> backupVerification;
      private MaintenanceDecision<BookVerification> restoreVerification;

      private StagingState(MaintenanceFixtureControl control, BookHandles bookHandles) {
        backup = MaintenanceDecision.accepted(new StubStagedBackup(control));
        restore = MaintenanceDecision.accepted(new StubStagedRestore(control));
        backupVerification = MaintenanceDecision.accepted(bookHandles.snapshotBook());
        restoreVerification = MaintenanceDecision.accepted(bookHandles.restoredBook());
      }
    }

    /** Holds injected runtime faults at the explicit persistence and publication boundaries. */
    private static final class FailureState {
      private @Nullable RuntimeException pairAdmission;
      private @Nullable RuntimeException liveVerification;
      private final Map<ProtectedBookAccess, RuntimeException> verificationByBookAccess =
          new ConcurrentHashMap<>();
      private @Nullable RuntimeException stagedBackup;
      private @Nullable RuntimeException append;
      private @Nullable RuntimeException workflowScopeAcquisition;
      private @Nullable RuntimeException backupArtifactVerification;
    }
  }

  /** Attested extension of the maintenance fixture with an in-memory evidence chain per book. */
  static final class Store extends MaintenanceStore
      implements AttestedProtectedBookMaintenanceStore {
    private boolean nextAppendIsAlreadyPresent;

    Store(Path bookPath, List<AttestationEvidence> evidence) {
      super(bookPath, evidence);
    }

    /**
     * Makes the next append persist as an exact concurrent operation while reporting that this
     * caller appended nothing.
     */
    void simulateConcurrentExactAppend() {
      if (nextAppendIsAlreadyPresent) {
        throw new IllegalStateException("A concurrent append simulation is already armed.");
      }
      nextAppendIsAlreadyPresent = true;
    }

    @Override
    public List<AttestationEvidence> loadAttestationEvidence(VerifiedBook verifiedBook) {
      return evidenceFor((StubBook) verifiedBook);
    }

    @Override
    public dev.erst.fingrind.core.attestation.AttestationAppendOutcome appendAttestedOperation(
        VerifiedBook verifiedBook,
        AttestationOperationKind operationKind,
        Instant recordedAt,
        AttestationOperationPreimages preimages,
        AttestationOperationAuthorizer authorizer,
        @Nullable AttestationBackupAcknowledgement backupAcknowledgement) {
      if (failureState.append != null) {
        throw failureState.append;
      }
      StubBook book = (StubBook) verifiedBook;
      AttestationVerification head = AttestationVerifier.verifyBook(evidenceFor(book));
      AttestationEvidence appended =
          authorizer.authorize(
              new AttestationOperationRequest(
                  head.bookId(),
                  head.headOrder().add(BigInteger.ONE),
                  operationKind.wireToken(),
                  head.operationHead(),
                  recordedAt,
                  preimages.request(),
                  preimages.effect()));
      List<AttestationEvidence> appendedEvidence = new ArrayList<>(evidenceFor(book));
      appendedEvidence.add(appended);
      AttestationVerification verification;
      try {
        verification = AttestationVerifier.verifyBook(appendedEvidence);
      } catch (AttestationVerificationException exception) {
        throw AttestationAdmissionRejectedException.from(
            (AttestationAuthorizationException)
                Objects.requireNonNull(
                    exception.getCause(), "candidate verification must preserve its cause"),
            exception);
      }
      evidenceState.byBook.put(book, List.copyOf(appendedEvidence));
      if (nextAppendIsAlreadyPresent) {
        nextAppendIsAlreadyPresent = false;
        return dev.erst.fingrind.core.attestation.AttestationAppendOutcome.AlreadyPresent.INSTANCE;
      }
      return new dev.erst.fingrind.core.attestation.AttestationAppendOutcome.Appended(verification);
    }

    @Override
    public AttestationVerification appendAttestedRegistryMutation(
        VerifiedBook verifiedBook,
        AttestationRegistryMutation mutation,
        Instant recordedAt,
        AttestationOperationAuthorizer authorizer) {
      StubBook book = (StubBook) verifiedBook;
      AttestationRegistryMutation checkedMutation = Objects.requireNonNull(mutation, "mutation");
      try {
        AttestationVerifier.requireRegistryMutationAdmissible(evidenceFor(book), checkedMutation);
      } catch (AttestationAuthorizationException exception) {
        throw AttestationAdmissionRejectedException.from(exception);
      }
      return appendAttestedOperation(
              verifiedBook,
              checkedMutation.operationKind(),
              recordedAt,
              checkedMutation.preimages(),
              authorizer,
              null)
          .requireVerifiedAppend();
    }

    @Override
    public VerifiedBackupArtifact verifyBackupArtifact(
        Path normalizedBackupArtifactPath, Path normalizedBackupKeyFilePath) {
      if (failureState.backupArtifactVerification != null) {
        throw failureState.backupArtifactVerification;
      }
      byte[] artifact =
          Objects.requireNonNull(admissionState.sealedBackupArtifact, "sealedArtifact");
      AttestationArtifactSnapshotReader reader = ignored -> evidenceFor(bookHandles.snapshotBook());
      return new StubVerifiedBackupArtifact(
          AttestationBackupArtifact.verify(artifact, reader), bookHandles.snapshotBook());
    }
  }

  /** Groups the uncommon one-off fixture overrides used by lifecycle failure tests. */
  static final class FixtureOverrides {
    private final MaintenanceStore.AdmissionState admissionState;
    private final MaintenanceStore.StagingState stagingState;
    private final MaintenanceStore.FailureState failureState;

    private FixtureOverrides(
        MaintenanceStore.AdmissionState admissionState,
        MaintenanceStore.StagingState stagingState,
        MaintenanceStore.FailureState failureState) {
      this.admissionState = admissionState;
      this.stagingState = stagingState;
      this.failureState = failureState;
    }

    void backupBlockingArtifacts(List<Path> blockingArtifacts) {
      admissionState.backupBlockingArtifacts = List.copyOf(blockingArtifacts);
    }

    void stagedBackupFailure(RuntimeException failure) {
      failureState.stagedBackup = Objects.requireNonNull(failure, "failure");
    }

    void appendFailure(RuntimeException failure) {
      failureState.append = Objects.requireNonNull(failure, "failure");
    }

    void backupArtifactVerificationFailure(RuntimeException failure) {
      failureState.backupArtifactVerification = Objects.requireNonNull(failure, "failure");
    }

    void workflowScopeAcquisitionFailure(RuntimeException failure) {
      failureState.workflowScopeAcquisition = Objects.requireNonNull(failure, "failure");
    }

    void stagedRestoreVerification(
        MaintenanceDecision<ProtectedBookMaintenanceStore.BookVerification> verification) {
      stagingState.restoreVerification = Objects.requireNonNull(verification, "verification");
    }
  }

  private record StubBook(Path artifactPath) implements ProtectedBookMaintenanceStore.VerifiedBook {
    private StubBook {
      Objects.requireNonNull(artifactPath, "artifactPath");
    }

    @Override
    public void close() {}
  }

  private record StubLease(Path artifactPath) implements ProtectedBookMaintenanceStore.HeldLease {
    private StubLease {
      Objects.requireNonNull(artifactPath, "artifactPath");
    }

    @Override
    public void close() {}
  }

  /** Immutable target paths retained after a prepared publication closes. */
  private record PublicationTargets(Path bookTargetPath, Path secretTargetPath) {
    private PublicationTargets {
      Objects.requireNonNull(bookTargetPath, "bookTargetPath");
      Objects.requireNonNull(secretTargetPath, "secretTargetPath");
    }
  }

  private record StubPublication(
      PublicationTargets targets, RestoredBookTargetPolicy bookTargetPolicy)
      implements ProtectedBookMaintenanceStore.PreparedPairPublication {
    private StubPublication(
        Path bookTargetPath, Path secretTargetPath, RestoredBookTargetPolicy bookTargetPolicy) {
      this(new PublicationTargets(bookTargetPath, secretTargetPath), bookTargetPolicy);
    }

    @Override
    public Path bookTargetPath() {
      return targets.bookTargetPath();
    }

    @Override
    public Path secretTargetPath() {
      return targets.secretTargetPath();
    }

    @Override
    public void close() {}
  }

  /** Keeps the fixture's source authority alive while its exact target publication is admitted. */
  private static final class StubWorkflowScope
      implements ProtectedBookMaintenanceStore.HeldWorkflowScope {
    private final Path sourceArtifactPath;
    private final Path bookTargetPath;
    private final Path secretTargetPath;
    private final MaintenanceStore.AdmissionState admissionState;
    private final MaintenanceStore.FailureState failureState;
    private boolean admitted;

    private StubWorkflowScope(
        Path sourceArtifactPath,
        Path bookTargetPath,
        Path secretTargetPath,
        MaintenanceStore.AdmissionState admissionState,
        MaintenanceStore.FailureState failureState) {
      this.sourceArtifactPath = Objects.requireNonNull(sourceArtifactPath, "sourceArtifactPath");
      this.bookTargetPath = Objects.requireNonNull(bookTargetPath, "bookTargetPath");
      this.secretTargetPath = Objects.requireNonNull(secretTargetPath, "secretTargetPath");
      this.admissionState = Objects.requireNonNull(admissionState, "admissionState");
      this.failureState = Objects.requireNonNull(failureState, "failureState");
    }

    @Override
    public Path artifactPath() {
      return sourceArtifactPath;
    }

    @Override
    public ProtectedBookPairPublicationAdmission admitPairPublication(
        RestoredBookTargetPolicy bookTargetPolicy,
        ProtectedBookPairPublicationRecoveryRequest request) {
      if (admitted) {
        throw new IllegalStateException(
            "Fixture workflow scope admits one target pair exactly once.");
      }
      admitted = true;
      Objects.requireNonNull(request, "request");
      if (failureState.pairAdmission != null) {
        throw failureState.pairAdmission;
      }
      if (admissionState.injectedPairAdmission != null) {
        return admissionState.injectedPairAdmission;
      }
      if (isCompletedBackupPair(bookTargetPath, secretTargetPath)) {
        return new ProtectedBookPairPublicationAdmission.ExistingCompleteBackup(
            bookTargetPath, secretTargetPath);
      }
      StubPublication publication =
          new StubPublication(bookTargetPath, secretTargetPath, bookTargetPolicy);
      admissionState.lastPreparedPublicationTargets = publication.targets();
      return new ProtectedBookPairPublicationAdmission.Prepared(publication);
    }

    private boolean isCompletedBackupPair(Path bookTargetPath, Path secretTargetPath) {
      @Nullable PublicationTargets completedBackupTargets =
          admissionState.completedBackupPublicationTargets;
      return completedBackupTargets != null
          && completedBackupTargets.bookTargetPath().equals(bookTargetPath)
          && completedBackupTargets.secretTargetPath().equals(secretTargetPath);
    }

    @Override
    public void close() {}
  }

  /** Represents the temporary backup pair before publication. */
  private static final class StubStagedBackup implements StagedBackupPair {
    private final MaintenanceFixtureControl control;

    private StubStagedBackup(MaintenanceFixtureControl control) {
      this.control = control;
    }

    @Override
    public MaintenanceDecision<ProtectedBookMaintenanceStore.BookVerification>
        verifyInitializedBackup() {
      return control.stagingState.backupVerification;
    }

    @Override
    public byte[] snapshot() {
      return control.evidenceState.snapshot.clone();
    }

    @Override
    public void sealArtifact(byte[] artifact) {
      control.sealArtifact(artifact);
    }

    @Override
    public StagedPairPublicationCommitOutcome commit() {
      control.publishBackup();
      PublicationTargets publicationTargets =
          Objects.requireNonNull(
              control.admissionState.lastPreparedPublicationTargets,
              "last prepared publication targets");
      return new StagedPairPublicationCommitOutcome.Published(
          pairPublication(
              publicationTargets.bookTargetPath(), publicationTargets.secretTargetPath()));
    }

    @Override
    public void retainUnpublishedArtifacts() {}

    @Override
    public void close() {}
  }

  /** Represents the temporary restore pair before publication. */
  private static final class StubStagedRestore implements StagedRestoredBookPair {
    private final MaintenanceFixtureControl control;

    private StubStagedRestore(MaintenanceFixtureControl control) {
      this.control = control;
    }

    @Override
    public MaintenanceDecision<ProtectedBookMaintenanceStore.BookVerification>
        verifyInitializedRestoredBook() {
      return control.stagingState.restoreVerification;
    }

    @Override
    public StagedPairPublicationCommitOutcome commit() {
      PublicationTargets publicationTargets =
          Objects.requireNonNull(
              control.admissionState.lastPreparedPublicationTargets,
              "last prepared publication targets");
      return new StagedPairPublicationCommitOutcome.Published(
          pairPublication(
              publicationTargets.bookTargetPath(), publicationTargets.secretTargetPath()));
    }

    @Override
    public void retainUnpublishedArtifacts() {}

    @Override
    public void close() {}
  }

  private static ProtectedBookPairPublication pairPublication(
      Path bookFinalArtifactPath, Path generatedSecretFinalArtifactPath) {
    return new ProtectedBookPairPublication(
        PublicationTransactionTestFixtures.completedArtifact(bookFinalArtifactPath),
        PublicationTransactionTestFixtures.completedArtifact(generatedSecretFinalArtifactPath));
  }

  private record StubVerifiedBackupArtifact(
      AttestationBackupArtifactVerification verification,
      ProtectedBookMaintenanceStore.VerifiedBook snapshotBook)
      implements AttestedProtectedBookMaintenanceStore.VerifiedBackupArtifact {
    @Override
    public void close() {}
  }
}
