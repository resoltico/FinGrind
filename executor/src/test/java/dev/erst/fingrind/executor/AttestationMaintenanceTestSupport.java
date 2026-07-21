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
    private final StubBook liveBook;
    private final StubBook snapshotBook;
    private final StubBook restoredBook;
    private final Map<StubBook, List<AttestationEvidence>> evidenceByBook =
        new ConcurrentHashMap<>();
    private final byte[] snapshot = new byte[] {4, 8, 15, 16, 23, 42};
    private BackupArtifactPairState backupPairState = BackupArtifactPairState.ABSENT;
    private List<Path> liveBlockingArtifacts = List.of();
    private List<Path> backupBlockingArtifacts = List.of();
    private LeaseAcquisition managedLease;
    private LeaseAcquisition existingLease;
    private MaintenanceDecision<BookVerification> liveVerification;
    private MaintenanceDecision<StagedBackupPair> stagedBackup;
    private MaintenanceDecision<StagedRestoredBookPair> stagedRestore;
    private MaintenanceDecision<BookVerification> stagedBackupVerification;
    private MaintenanceDecision<BookVerification> stagedRestoreVerification;
    private @Nullable RuntimeException prepareFailure;
    private @Nullable RuntimeException appendFailure;
    private byte @Nullable [] sealedArtifact;

    Store(Path bookPath, List<AttestationEvidence> evidence) {
      Path normalizedBookPath = normalize(bookPath, "bookPath");
      liveBook = new StubBook(normalizedBookPath);
      snapshotBook = new StubBook(normalizedBookPath.resolveSibling("snapshot.sqlite"));
      restoredBook = new StubBook(normalizedBookPath.resolveSibling("restored.sqlite"));
      List<AttestationEvidence> checkedEvidence = List.copyOf(evidence);
      evidenceByBook.put(liveBook, checkedEvidence);
      evidenceByBook.put(snapshotBook, checkedEvidence);
      evidenceByBook.put(restoredBook, checkedEvidence);
      liveVerification = MaintenanceDecision.accepted(liveBook);
      stagedBackup = MaintenanceDecision.accepted(new StubStagedBackup(this));
      stagedRestore = MaintenanceDecision.accepted(new StubStagedRestore(this));
      stagedBackupVerification = MaintenanceDecision.accepted(snapshotBook);
      stagedRestoreVerification = MaintenanceDecision.accepted(restoredBook);
      managedLease = new StubLease(normalizedBookPath);
      existingLease = new StubLease(normalizedBookPath);
    }

    void setLiveVerification(MaintenanceDecision<BookVerification> verification) {
      liveVerification = Objects.requireNonNull(verification, "verification");
    }

    void setBackupPairState(BackupArtifactPairState state) {
      backupPairState = Objects.requireNonNull(state, "state");
    }

    void setLiveBlockingArtifacts(List<Path> blockingArtifacts) {
      liveBlockingArtifacts = List.copyOf(blockingArtifacts);
    }

    void setBackupBlockingArtifacts(List<Path> blockingArtifacts) {
      backupBlockingArtifacts = List.copyOf(blockingArtifacts);
    }

    void setManagedLease(LeaseAcquisition lease) {
      managedLease = Objects.requireNonNull(lease, "lease");
    }

    void setExistingLease(LeaseAcquisition lease) {
      existingLease = Objects.requireNonNull(lease, "lease");
    }

    void setStagedBackup(MaintenanceDecision<StagedBackupPair> staged) {
      stagedBackup = Objects.requireNonNull(staged, "staged");
    }

    void setStagedRestore(MaintenanceDecision<StagedRestoredBookPair> staged) {
      stagedRestore = Objects.requireNonNull(staged, "staged");
    }

    void setStagedBackupVerification(MaintenanceDecision<BookVerification> verification) {
      stagedBackupVerification = Objects.requireNonNull(verification, "verification");
    }

    void setStagedRestoreVerification(MaintenanceDecision<BookVerification> verification) {
      stagedRestoreVerification = Objects.requireNonNull(verification, "verification");
    }

    void setPrepareFailure(RuntimeException failure) {
      prepareFailure = Objects.requireNonNull(failure, "failure");
    }

    void setAppendFailure(RuntimeException failure) {
      appendFailure = Objects.requireNonNull(failure, "failure");
    }

    List<AttestationEvidence> liveEvidence() {
      return evidenceFor(liveBook);
    }

    List<AttestationEvidence> restoredEvidence() {
      return evidenceFor(restoredBook);
    }

    void setLiveEvidence(List<AttestationEvidence> evidence) {
      evidenceByBook.put(liveBook, List.copyOf(evidence));
    }

    void setSnapshotEvidence(List<AttestationEvidence> evidence) {
      evidenceByBook.put(snapshotBook, List.copyOf(evidence));
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
      if (prepareFailure != null) {
        throw prepareFailure;
      }
      return new StubPublication(
          normalizedBookTargetPath, normalizedSecretTargetPath, bookTargetPolicy);
    }

    @Override
    public List<Path> blockingArtifactsForBook(Path normalizedBookPath) {
      return liveBlockingArtifacts;
    }

    @Override
    public List<Path> blockingArtifactsForBackupSource(Path normalizedBackupFilePath) {
      return backupBlockingArtifacts;
    }

    @Override
    public BackupArtifactPairState backupArtifactPairState(
        Path normalizedBackupArtifactPath, Path normalizedBackupKeyFilePath) {
      return backupPairState;
    }

    @Override
    public LeaseAcquisition acquireExistingArtifactLease(
        Path normalizedArtifactPath, ProtectedBookMaintenanceArtifactRole artifactRole) {
      return existingLease;
    }

    @Override
    public LeaseAcquisition acquireManagedArtifactLease(
        Path normalizedArtifactPath, ProtectedBookMaintenanceArtifactRole artifactRole) {
      return managedLease;
    }

    @Override
    public MaintenanceDecision<BookVerification> verifyInitializedBook(
        ProtectedBookAccess bookAccess, ProtectedBookMaintenanceArtifactRole artifactRole) {
      return liveVerification;
    }

    @Override
    public MaintenanceDecision<StagedBackupPair> stageBackupPair(
        VerifiedBook sourceBook, PreparedPairPublication preparedPairPublication) {
      return stagedBackup;
    }

    @Override
    public MaintenanceDecision<StagedRestoredBookPair> stageRestoredBookPair(
        VerifiedBook sourceBook, PreparedPairPublication preparedPairPublication) {
      return stagedRestore;
    }

    @Override
    public List<AttestationEvidence> loadAttestationEvidence(VerifiedBook verifiedBook) {
      return evidenceFor((StubBook) verifiedBook);
    }

    @Override
    public AttestationVerification appendAttestedOperation(
        VerifiedBook verifiedBook,
        String operationKind,
        Instant recordedAt,
        AttestationOperationPreimages preimages,
        AttestationOperationAuthorizer authorizer,
        @Nullable AttestationBackupAcknowledgement backupAcknowledgement) {
      if (appendFailure != null) {
        throw appendFailure;
      }
      StubBook book = (StubBook) verifiedBook;
      AttestationVerification head = AttestationVerifier.verifyBook(evidenceFor(book));
      AttestationEvidence appended =
          authorizer.authorize(
              new AttestationOperationRequest(
                  head.bookId(),
                  head.headOrder().add(BigInteger.ONE),
                  operationKind,
                  head.operationHead(),
                  recordedAt,
                  preimages.request(),
                  preimages.effect()));
      List<AttestationEvidence> appendedEvidence = new ArrayList<>(evidenceFor(book));
      appendedEvidence.add(appended);
      evidenceByBook.put(book, List.copyOf(appendedEvidence));
      return AttestationVerifier.verifyBook(appendedEvidence);
    }

    @Override
    public VerifiedBackupArtifact verifyBackupArtifact(
        Path normalizedBackupArtifactPath, Path normalizedBackupKeyFilePath) {
      byte[] artifact = Objects.requireNonNull(sealedArtifact, "sealedArtifact");
      AttestationArtifactSnapshotReader reader = ignored -> evidenceFor(snapshotBook);
      return new StubVerifiedBackupArtifact(
          AttestationBackupArtifact.verify(artifact, reader), snapshotBook);
    }

    private List<AttestationEvidence> evidenceFor(StubBook book) {
      return Objects.requireNonNull(evidenceByBook.get(book), "known verified book");
    }

    private void sealArtifact(byte[] artifact) {
      sealedArtifact = artifact.clone();
    }

    private void publishBackup() {
      backupPairState = BackupArtifactPairState.COMPLETE;
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
      return store.stagedBackupVerification;
    }

    @Override
    public byte[] snapshot() {
      return store.snapshot.clone();
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
      return store.stagedRestoreVerification;
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
