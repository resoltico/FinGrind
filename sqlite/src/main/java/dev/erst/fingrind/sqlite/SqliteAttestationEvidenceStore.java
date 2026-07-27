package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.attestation.AttestationAdmissionRejectedException;
import dev.erst.fingrind.core.attestation.AttestationAppendOutcome;
import dev.erst.fingrind.core.attestation.AttestationAuthorizationException;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgementAdmission;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationOperationPreimages;
import dev.erst.fingrind.core.attestation.AttestationOperationRequest;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationRegistryMutation;
import dev.erst.fingrind.core.attestation.AttestationStaleHeadException;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.core.attestation.AttestationVerificationException;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Transaction-local persistence boundary for immutable operation evidence and head
 * compare-and-swap.
 *
 * <p>Callers must already own the book's immediate SQLite write transaction. This class first
 * verifies the persisted chain, compares the observed signing head, verifies the candidate chain,
 * and only then appends the raw evidence. SQLite triggers prevent later replacement or deletion.
 */
final class SqliteAttestationEvidenceStore {
  private static final byte[] GENESIS_PREVIOUS_HEAD = new byte[32];
  private static final AttestationOperationKind EXECUTE_PLAN =
      AttestationOperationKind.EXECUTE_PLAN;

  private SqliteAttestationEvidenceStore() {}

  static AttestationVerification append(
      SqliteNativeDatabase activeDatabase,
      byte[] observedHead,
      AttestationEvidence candidateEvidence) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    byte[] checkedObservedHead = requireHead(observedHead, "observedHead");
    AttestationEvidence checkedCandidate =
        Objects.requireNonNull(candidateEvidence, "candidateEvidence");
    List<AttestationEvidence> persistedEvidence = loadAll(activeDatabase);
    Optional<AttestationVerification> persistedVerification = verifyPersisted(persistedEvidence);
    Head currentHead =
        persistedVerification
            .map(verification -> new Head(verification.operationHead(), verification.headOrder()))
            .orElseGet(() -> new Head(GENESIS_PREVIOUS_HEAD, BigInteger.valueOf(-1L)));
    if (!Arrays.equals(checkedObservedHead, currentHead.bytes())) {
      throw new AttestationStaleHeadException(
          checkedObservedHead, currentHead.bytes(), currentHead.order());
    }

    List<AttestationEvidence> completeEvidence = new ArrayList<>(persistedEvidence);
    completeEvidence.add(checkedCandidate);
    AttestationVerification candidateVerification = verifyCandidate(completeEvidence);
    SqliteAttestationEvidencePersistence.insert(
        activeDatabase, candidateVerification, checkedCandidate);
    return candidateVerification;
  }

  /**
   * Admits a signature made against one authenticated head observed before the write transaction.
   */
  static AttestationAppendOutcome.Appended appendAuthorized(
      SqliteNativeDatabase activeDatabase,
      ObservedHead observedHead,
      AttestationOperationKind operationKind,
      Instant recordedAt,
      AttestationOperationPreimages preimages,
      AttestationOperationAuthorizer authorizer) {
    return appendAuthorized(
            activeDatabase, observedHead, operationKind, recordedAt, preimages, authorizer, null)
        .requireAppended();
  }

  /**
   * Admits a signature made against one authenticated head observed before the write transaction.
   */
  static AttestationAppendOutcome appendAuthorized(
      SqliteNativeDatabase activeDatabase,
      ObservedHead observedHead,
      AttestationOperationKind operationKind,
      Instant recordedAt,
      AttestationOperationPreimages preimages,
      AttestationOperationAuthorizer authorizer,
      @Nullable AttestationBackupAcknowledgement backupAcknowledgement) {
    return appendAuthorized(
        activeDatabase,
        observedHead,
        operationKind,
        recordedAt,
        preimages,
        authorizer,
        backupAcknowledgement,
        null);
  }

  /**
   * Admits one registry mutation after its target has been checked against this transaction's
   * authenticated authority head and before a custodian is asked to sign it.
   */
  static AttestationVerification appendAuthorizedRegistryMutation(
      SqliteNativeDatabase activeDatabase,
      ObservedHead observedHead,
      AttestationRegistryMutation mutation,
      Instant recordedAt,
      AttestationOperationAuthorizer authorizer) {
    AttestationRegistryMutation checkedMutation = Objects.requireNonNull(mutation, "mutation");
    return appendAuthorized(
            activeDatabase,
            observedHead,
            checkedMutation.operationKind(),
            recordedAt,
            checkedMutation.preimages(),
            authorizer,
            null,
            checkedMutation)
        .requireVerifiedAppend();
  }

  private static AttestationAppendOutcome appendAuthorized(
      SqliteNativeDatabase activeDatabase,
      ObservedHead observedHead,
      AttestationOperationKind operationKind,
      Instant recordedAt,
      AttestationOperationPreimages preimages,
      AttestationOperationAuthorizer authorizer,
      @Nullable AttestationBackupAcknowledgement backupAcknowledgement,
      @Nullable AttestationRegistryMutation registryMutation) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    ObservedHead checkedObservedHead = Objects.requireNonNull(observedHead, "observedHead");
    AttestationOperationKind checkedOperationKind =
        Objects.requireNonNull(operationKind, "operationKind");
    Instant checkedRecordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
    AttestationOperationPreimages checkedPreimages = Objects.requireNonNull(preimages, "preimages");
    AttestationOperationAuthorizer checkedAuthorizer =
        AttestationOperationAuthorizer.require(authorizer);
    List<AttestationEvidence> persistedEvidence = loadAll(activeDatabase);
    AttestationVerification persistedVerification =
        verifyPersisted(persistedEvidence)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Protected-book mutation requires a persisted attestation genesis."));
    if (!Arrays.equals(
        checkedObservedHead.operationHead(), persistedVerification.operationHead())) {
      throw new AttestationStaleHeadException(
          checkedObservedHead.operationHead(),
          persistedVerification.operationHead(),
          persistedVerification.headOrder());
    }
    if (registryMutation != null) {
      try {
        AttestationVerifier.requireRegistryMutationAdmissible(persistedEvidence, registryMutation);
      } catch (AttestationAuthorizationException exception) {
        throw AttestationAdmissionRejectedException.from(exception);
      }
    }
    if (backupAcknowledgement != null) {
      AttestationBackupAcknowledgementAdmission admission =
          AttestationBackupAcknowledgementAdmission.evaluate(
              persistedEvidence, backupAcknowledgement);
      if (admission == AttestationBackupAcknowledgementAdmission.IDENTICAL_REPLAY) {
        return AttestationAppendOutcome.AlreadyPresent.INSTANCE;
      }
      if (admission == AttestationBackupAcknowledgementAdmission.CONFLICT) {
        throw new SqliteAttestationBackupAcknowledgementConflictException(
            backupAcknowledgement.backupId());
      }
    }
    AttestationEvidence candidateEvidence =
        checkedAuthorizer.authorize(
            new AttestationOperationRequest(
                persistedVerification.bookId(),
                persistedVerification.headOrder().add(BigInteger.ONE),
                checkedOperationKind.wireToken(),
                persistedVerification.operationHead(),
                checkedRecordedAt,
                checkedPreimages.request(),
                checkedPreimages.effect()));
    return new AttestationAppendOutcome.Appended(
        append(activeDatabase, checkedObservedHead.operationHead(), candidateEvidence));
  }

  static ObservedHead observeRequired(SqliteNativeDatabase activeDatabase) {
    List<AttestationEvidence> persistedEvidence = loadAll(activeDatabase);
    AttestationVerification verification =
        verifyPersisted(persistedEvidence)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Protected-book mutation requires a persisted attestation genesis."));
    return new ObservedHead(verification.operationHead(), verification.headOrder());
  }

  /**
   * Verifies that one head observed before write admission remains the authenticated current head.
   */
  static AttestationVerification requireCurrentObservedHead(
      SqliteNativeDatabase activeDatabase, ObservedHead observedHead) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    ObservedHead checkedObservedHead = Objects.requireNonNull(observedHead, "observedHead");
    AttestationVerification persistedVerification =
        verifyPersisted(loadAll(activeDatabase))
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Protected-book mutation requires a persisted attestation genesis."));
    if (!Arrays.equals(
        checkedObservedHead.operationHead(), persistedVerification.operationHead())) {
      throw new AttestationStaleHeadException(
          checkedObservedHead.operationHead(),
          persistedVerification.operationHead(),
          persistedVerification.headOrder());
    }
    return persistedVerification;
  }

  /**
   * Appends one aggregate execute-plan operation after all child domain mutations have succeeded.
   */
  static AttestationVerification appendPlanAuthorized(
      SqliteNativeDatabase activeDatabase,
      ObservedHead observedHead,
      Instant recordedAt,
      AttestationOperationPreimages planPreimages,
      AttestationPlanOperationAuthorizer authorizer) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    ObservedHead checkedObservedHead = Objects.requireNonNull(observedHead, "observedHead");
    Instant checkedRecordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
    AttestationOperationPreimages checkedPlanPreimages =
        Objects.requireNonNull(planPreimages, "planPreimages");
    AttestationPlanOperationAuthorizer checkedAuthorizer =
        Objects.requireNonNull(authorizer, "authorizer");
    AttestationVerification persistedVerification =
        requireCurrentObservedHead(activeDatabase, checkedObservedHead);
    AttestationEvidence candidateEvidence =
        checkedAuthorizer.authorizePlan(
            new AttestationOperationRequest(
                persistedVerification.bookId(),
                persistedVerification.headOrder().add(BigInteger.ONE),
                EXECUTE_PLAN.wireToken(),
                persistedVerification.operationHead(),
                checkedRecordedAt,
                checkedPlanPreimages.request(),
                checkedPlanPreimages.effect()));
    return append(activeDatabase, checkedObservedHead.operationHead(), candidateEvidence);
  }

  static List<AttestationEvidence> loadAll(SqliteNativeDatabase activeDatabase) {
    return SqliteAttestationEvidencePersistence.loadAll(activeDatabase);
  }

  private static Optional<AttestationVerification> verifyPersisted(
      List<AttestationEvidence> evidence) {
    if (evidence.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(AttestationVerifier.verifyBook(evidence));
    } catch (AttestationVerificationException exception) {
      throw new IllegalStateException(
          "Persisted attestation evidence violates its canonical chain: " + exception.code(),
          exception);
    }
  }

  private static AttestationVerification verifyCandidate(List<AttestationEvidence> evidence) {
    return SqliteAttestationCandidateVerifier.verify(evidence);
  }

  private static byte[] requireHead(byte[] value, String name) {
    byte[] checked = Arrays.copyOf(Objects.requireNonNull(value, name), value.length);
    if (checked.length != 32) {
      throw new IllegalArgumentException(name + " must contain exactly 32 bytes.");
    }
    return checked;
  }

  /** Immutable verified operation-chain head read from the persisted evidence table. */
  private static final class Head {
    private final byte[] bytes;
    private final BigInteger order;

    private Head(byte[] bytes, BigInteger order) {
      this.bytes = requireHead(bytes, "bytes");
      this.order = Objects.requireNonNull(order, "order");
    }

    private byte[] bytes() {
      return bytes.clone();
    }

    private BigInteger order() {
      return order;
    }
  }

  /** Immutable authenticated head observed before one caller attempts write admission. */
  static final class ObservedHead {
    private final byte[] operationHead;
    private final BigInteger operationOrder;

    private ObservedHead(byte[] operationHead, BigInteger operationOrder) {
      this.operationHead = requireHead(operationHead, "operationHead");
      this.operationOrder = Objects.requireNonNull(operationOrder, "operationOrder");
    }

    byte[] operationHead() {
      return operationHead.clone();
    }

    BigInteger operationOrder() {
      return operationOrder;
    }
  }
}
