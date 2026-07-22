package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.attestation.AttestationArtifactSnapshotReader;
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
import dev.erst.fingrind.core.attestation.AttestationSigningSession;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.spi.AttestedProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
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
    Path keyPath = directory.resolve("founder.fgatk");
    Path passphrasePath = directory.resolve("founder.passphrase");
    char[] passphrase = "test attestation passphrase".toCharArray();
    try {
      AttestationKeyFiles.create(keyPath, passphrase);
      Files.writeString(passphrasePath, "test attestation passphrase\n");
    } finally {
      java.util.Arrays.fill(passphrase, '\0');
    }
    return new CredentialFixture(
        new AttestationCredentialSource(PRINCIPAL_ID, keyPath, passphrasePath));
  }

  static AttestationEvidence genesis(CredentialFixture credential, Instant recordedAt) {
    return AttestationGenesisFactory.create(
        ExecutorAccountingTestSupport.bookIdentity(),
        recordedAt,
        List.of(
            new dev.erst.fingrind.contract.bookkeeping.AttestationFounderInput(
                PRINCIPAL_ID,
                credential.source().encryptedKeyFilePath(),
                credential.source().passphraseFilePath())));
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

  /** Mutable test double for the attested maintenance SPI. */
  static final class Store implements AttestedProtectedBookMaintenanceStore {
    private final BookHandles bookHandles;
    private final EvidenceState evidenceState;
    private final BackupArtifactState backupArtifactState = new BackupArtifactState();
    private final AdmissionState admissionState;
    private final StagingState stagingState;
    private final FailureState failureState = new FailureState();
    private final FixtureOverrides overrides;

    Store(Path bookPath, List<AttestationEvidence> evidence) {
      Path normalizedBookPath = normalize(bookPath, "bookPath");
      bookHandles = new BookHandles(normalizedBookPath);
      evidenceState = new EvidenceState(bookHandles, evidence);
      admissionState = new AdmissionState(normalizedBookPath, bookHandles.liveBook());
      stagingState = new StagingState(this, bookHandles);
      overrides =
          new FixtureOverrides(backupArtifactState, admissionState, stagingState, failureState);
    }

    void setLiveVerification(MaintenanceDecision<BookVerification> verification) {
      admissionState.liveVerification = Objects.requireNonNull(verification, "verification");
    }

    void setBackupPairState(BackupArtifactPairState state) {
      backupArtifactState.pairState = Objects.requireNonNull(state, "state");
    }

    void setLiveBlockingArtifacts(List<Path> blockingArtifacts) {
      admissionState.liveBlockingArtifacts = List.copyOf(blockingArtifacts);
    }

    void setManagedLease(LeaseAcquisition lease) {
      admissionState.managedLease = Objects.requireNonNull(lease, "lease");
    }

    void setExistingLease(LeaseAcquisition lease) {
      admissionState.existingLease = Objects.requireNonNull(lease, "lease");
    }

    void setStagedBackup(MaintenanceDecision<StagedBackupPair> staged) {
      stagingState.backup = Objects.requireNonNull(staged, "staged");
    }

    void setStagedRestore(MaintenanceDecision<StagedRestoredBookPair> staged) {
      stagingState.restore = Objects.requireNonNull(staged, "staged");
    }

    void setStagedBackupVerification(MaintenanceDecision<BookVerification> verification) {
      stagingState.backupVerification = Objects.requireNonNull(verification, "verification");
    }

    void setPrepareFailure(RuntimeException failure) {
      failureState.prepare = Objects.requireNonNull(failure, "failure");
    }

    void setAppendFailure(RuntimeException failure) {
      failureState.append = Objects.requireNonNull(failure, "failure");
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

    @Override
    public Path normalize(Path path, String argumentName) {
      Objects.requireNonNull(argumentName, "argumentName");
      return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    @Override
    public PreparedPairPublication preparePairPublication(
        Path normalizedSecretTargetPath,
        Path normalizedBookTargetPath,
        RestoredBookTargetPolicy bookTargetPolicy,
        ProtectedBookMaintenanceArtifactRole bookArtifactRole,
        ProtectedBookMaintenanceArtifactRole secretArtifactRole) {
      if (failureState.prepare != null) {
        throw failureState.prepare;
      }
      return new StubPublication(
          normalizedBookTargetPath, normalizedSecretTargetPath, bookTargetPolicy);
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
    public BackupArtifactPairState backupArtifactPairState(
        Path normalizedBackupArtifactPath, Path normalizedBackupKeyFilePath) {
      return backupArtifactState.pairState;
    }

    @Override
    public void recoverInterruptedBackupPublication(
        Path normalizedBackupArtifactPath, Path normalizedBackupKeyFilePath) {
      if (failureState.backupRecovery != null) {
        throw failureState.backupRecovery;
      }
      if (backupArtifactState.recoveredPairState != null) {
        backupArtifactState.pairState = backupArtifactState.recoveredPairState;
      }
    }

    @Override
    public LeaseAcquisition acquireExistingArtifactLease(
        Path normalizedArtifactPath, ProtectedBookMaintenanceArtifactRole artifactRole) {
      if (failureState.existingLease != null) {
        throw failureState.existingLease;
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

    @Override
    public List<AttestationEvidence> loadAttestationEvidence(VerifiedBook verifiedBook) {
      return evidenceFor((StubBook) verifiedBook);
    }

    @Override
    public AttestationVerification appendAttestedOperation(
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
      evidenceState.byBook.put(book, List.copyOf(appendedEvidence));
      return AttestationVerifier.verifyBook(appendedEvidence);
    }

    @Override
    public VerifiedBackupArtifact verifyBackupArtifact(
        Path normalizedBackupArtifactPath, Path normalizedBackupKeyFilePath) {
      if (failureState.backupArtifactVerification != null) {
        throw failureState.backupArtifactVerification;
      }
      byte[] artifact = Objects.requireNonNull(backupArtifactState.sealed, "sealedArtifact");
      AttestationArtifactSnapshotReader reader = ignored -> evidenceFor(bookHandles.snapshotBook());
      return new StubVerifiedBackupArtifact(
          AttestationBackupArtifact.verify(artifact, reader), bookHandles.snapshotBook());
    }

    private List<AttestationEvidence> evidenceFor(StubBook book) {
      return Objects.requireNonNull(evidenceState.byBook.get(book), "known verified book");
    }

    private void sealArtifact(byte[] artifact) {
      backupArtifactState.sealed = artifact.clone();
    }

    private void publishBackup() {
      backupArtifactState.pairState = BackupArtifactPairState.COMPLETE;
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

    /** Represents the externally observable backup pair and its sealed artifact bytes. */
    private static final class BackupArtifactState {
      private BackupArtifactPairState pairState = BackupArtifactPairState.ABSENT;
      private @Nullable BackupArtifactPairState recoveredPairState;
      private byte @Nullable [] sealed;
    }

    /** Holds mutable admission outcomes before a maintenance workflow opens a verified book. */
    private static final class AdmissionState {
      private List<Path> liveBlockingArtifacts = List.of();
      private List<Path> backupBlockingArtifacts = List.of();
      private LeaseAcquisition managedLease;
      private LeaseAcquisition existingLease;
      private MaintenanceDecision<BookVerification> liveVerification;

      private AdmissionState(Path normalizedBookPath, StubBook liveBook) {
        managedLease = new StubLease(normalizedBookPath);
        existingLease = new StubLease(normalizedBookPath);
        liveVerification = MaintenanceDecision.accepted(liveBook);
      }
    }

    /** Holds staging and staged-verification outcomes for each lifecycle artifact kind. */
    private static final class StagingState {
      private MaintenanceDecision<StagedBackupPair> backup;
      private MaintenanceDecision<StagedRestoredBookPair> restore;
      private MaintenanceDecision<BookVerification> backupVerification;
      private MaintenanceDecision<BookVerification> restoreVerification;

      private StagingState(Store store, BookHandles bookHandles) {
        backup = MaintenanceDecision.accepted(new StubStagedBackup(store));
        restore = MaintenanceDecision.accepted(new StubStagedRestore(store));
        backupVerification = MaintenanceDecision.accepted(bookHandles.snapshotBook());
        restoreVerification = MaintenanceDecision.accepted(bookHandles.restoredBook());
      }
    }

    /** Holds injected runtime faults at the explicit persistence and publication boundaries. */
    private static final class FailureState {
      private @Nullable RuntimeException prepare;
      private @Nullable RuntimeException stagedBackup;
      private @Nullable RuntimeException append;
      private @Nullable RuntimeException existingLease;
      private @Nullable RuntimeException backupArtifactVerification;
      private @Nullable RuntimeException backupRecovery;
    }
  }

  /** Groups the uncommon one-off fixture overrides used by lifecycle failure tests. */
  static final class FixtureOverrides {
    private final Store.BackupArtifactState backupArtifactState;
    private final Store.AdmissionState admissionState;
    private final Store.StagingState stagingState;
    private final Store.FailureState failureState;

    private FixtureOverrides(
        Store.BackupArtifactState backupArtifactState,
        Store.AdmissionState admissionState,
        Store.StagingState stagingState,
        Store.FailureState failureState) {
      this.backupArtifactState = backupArtifactState;
      this.admissionState = admissionState;
      this.stagingState = stagingState;
      this.failureState = failureState;
    }

    void recoveredBackupPairState(ProtectedBookMaintenanceStore.BackupArtifactPairState state) {
      backupArtifactState.recoveredPairState = Objects.requireNonNull(state, "state");
    }

    void backupBlockingArtifacts(List<Path> blockingArtifacts) {
      admissionState.backupBlockingArtifacts = List.copyOf(blockingArtifacts);
    }

    void stagedBackupFailure(RuntimeException failure) {
      failureState.stagedBackup = Objects.requireNonNull(failure, "failure");
    }

    void backupRecoveryFailure(RuntimeException failure) {
      failureState.backupRecovery = Objects.requireNonNull(failure, "failure");
    }

    void backupArtifactVerificationFailure(RuntimeException failure) {
      failureState.backupArtifactVerification = Objects.requireNonNull(failure, "failure");
    }

    void existingLeaseFailure(RuntimeException failure) {
      failureState.existingLease = Objects.requireNonNull(failure, "failure");
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

  private record StubPublication(
      Path bookTargetPath, Path secretTargetPath, RestoredBookTargetPolicy bookTargetPolicy)
      implements ProtectedBookMaintenanceStore.PreparedPairPublication {
    @Override
    public void close() {}
  }

  /** Represents the temporary backup pair before publication. */
  private static final class StubStagedBackup implements StagedBackupPair {
    private final Store store;

    private StubStagedBackup(Store store) {
      this.store = store;
    }

    @Override
    public MaintenanceDecision<ProtectedBookMaintenanceStore.BookVerification>
        verifyInitializedBackup() {
      return store.stagingState.backupVerification;
    }

    @Override
    public byte[] snapshot() {
      return store.evidenceState.snapshot.clone();
    }

    @Override
    public void sealArtifact(byte[] artifact) {
      store.sealArtifact(artifact);
    }

    @Override
    public void commit() {
      store.publishBackup();
    }

    @Override
    public void rollback() {}

    @Override
    public void close() {}
  }

  /** Represents the temporary restore pair before publication. */
  private static final class StubStagedRestore implements StagedRestoredBookPair {
    private final Store store;

    private StubStagedRestore(Store store) {
      this.store = store;
    }

    @Override
    public MaintenanceDecision<ProtectedBookMaintenanceStore.BookVerification>
        verifyInitializedRestoredBook() {
      return store.stagingState.restoreVerification;
    }

    @Override
    public void commit() {}

    @Override
    public void rollback() {}

    @Override
    public void close() {}
  }

  private record StubVerifiedBackupArtifact(
      AttestationBackupArtifactVerification verification,
      ProtectedBookMaintenanceStore.VerifiedBook snapshotBook)
      implements AttestedProtectedBookMaintenanceStore.VerifiedBackupArtifact {
    @Override
    public void close() {}
  }
}
