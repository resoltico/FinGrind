package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgementAdmission;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationOperationPreimages;
import dev.erst.fingrind.core.attestation.AttestationOperationRequest;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.core.attestation.AttestationVerificationException;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
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
      throw new SqliteAttestationStaleHeadException(
          checkedObservedHead, currentHead.bytes(), currentHead.order());
    }

    List<AttestationEvidence> completeEvidence = new ArrayList<>(persistedEvidence);
    completeEvidence.add(checkedCandidate);
    AttestationVerification candidateVerification = verifyCandidate(completeEvidence);
    BigInteger expectedOrder = currentHead.order().add(BigInteger.ONE);
    if (!candidateVerification.headOrder().equals(expectedOrder)) {
      throw new IllegalArgumentException("attestation-preimage-invalid");
    }
    insert(activeDatabase, candidateVerification, checkedCandidate);
    return candidateVerification;
  }

  /**
   * Signs and appends one operation while the caller owns the immediate write transaction.
   *
   * <p>The current head is loaded only after the transaction has begun; the authorizer therefore
   * cannot reserve a sequence before a credential prompt. The candidate is then passed through the
   * same full persisted-chain and compare-and-swap validation as externally supplied evidence.
   */
  static AttestationVerification appendAuthorized(
      SqliteNativeDatabase activeDatabase,
      String operationKind,
      Instant recordedAt,
      AttestationOperationPreimages preimages,
      AttestationOperationAuthorizer authorizer) {
    return appendAuthorized(activeDatabase, operationKind, recordedAt, preimages, authorizer, null);
  }

  /** Signs and appends one operation with optional exact-tuple backup acknowledgement admission. */
  static AttestationVerification appendAuthorized(
      SqliteNativeDatabase activeDatabase,
      String operationKind,
      Instant recordedAt,
      AttestationOperationPreimages preimages,
      AttestationOperationAuthorizer authorizer,
      @Nullable AttestationBackupAcknowledgement backupAcknowledgement) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    String checkedOperationKind = Objects.requireNonNull(operationKind, "operationKind");
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
    if (checkedAuthorizer instanceof AttestationPlanOperationAuthorizer planAuthorizer) {
      planAuthorizer.collectChildMutation(checkedOperationKind, checkedPreimages);
      return persistedVerification;
    }
    if (backupAcknowledgement != null) {
      switch (AttestationBackupAcknowledgementAdmission.evaluate(
          persistedEvidence, backupAcknowledgement)) {
        case APPEND -> {
          // Continue into the shared signing and CAS boundary below.
        }
        case IDENTICAL_REPLAY -> {
          return persistedVerification;
        }
        case CONFLICT ->
            throw new SqliteAttestationBackupAcknowledgementConflictException(
                backupAcknowledgement.backupId());
      }
    }
    AttestationEvidence candidateEvidence =
        checkedAuthorizer.authorize(
            new AttestationOperationRequest(
                persistedVerification.bookId(),
                persistedVerification.headOrder().add(BigInteger.ONE),
                checkedOperationKind,
                persistedVerification.operationHead(),
                checkedRecordedAt,
                checkedPreimages.request(),
                checkedPreimages.effect()));
    return append(activeDatabase, persistedVerification.operationHead(), candidateEvidence);
  }

  /**
   * Appends one aggregate execute-plan operation after all child domain mutations have succeeded.
   */
  static AttestationVerification appendPlanAuthorized(
      SqliteNativeDatabase activeDatabase,
      String planId,
      Instant recordedAt,
      AttestationPlanOperationAuthorizer authorizer) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    String checkedPlanId = Objects.requireNonNull(planId, "planId");
    Instant checkedRecordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
    AttestationPlanOperationAuthorizer checkedAuthorizer =
        Objects.requireNonNull(authorizer, "authorizer");
    if (!checkedAuthorizer.hasChildMutations()) {
      throw new IllegalArgumentException("execute-plan did not produce a mutating child step.");
    }
    List<AttestationEvidence> persistedEvidence = loadAll(activeDatabase);
    AttestationVerification persistedVerification =
        verifyPersisted(persistedEvidence)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Protected-book mutation requires a persisted attestation genesis."));
    AttestationOperationPreimages planPreimages = checkedAuthorizer.planPreimages(checkedPlanId);
    AttestationEvidence candidateEvidence =
        checkedAuthorizer.authorizePlan(
            new AttestationOperationRequest(
                persistedVerification.bookId(),
                persistedVerification.headOrder().add(BigInteger.ONE),
                "execute-plan",
                persistedVerification.operationHead(),
                checkedRecordedAt,
                planPreimages.request(),
                planPreimages.effect()));
    return append(activeDatabase, persistedVerification.operationHead(), candidateEvidence);
  }

  static List<AttestationEvidence> loadAll(SqliteNativeDatabase activeDatabase) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqliteAttestationEvidenceSql.LOAD_ALL)) {
      List<AttestationEvidence> evidence = new ArrayList<>();
      while (statement.step() == SqliteNativeResultCode.code("ROW")) {
        String persistedOrder = SqlitePostingMapper.requiredText(statement, 0);
        String expectedOrder = orderHex(BigInteger.valueOf(evidence.size()));
        if (!persistedOrder.equals(expectedOrder)) {
          throw new IllegalStateException(
              "Persisted attestation operation order is not a canonical contiguous sequence.");
        }
        evidence.add(
            new AttestationEvidence(
                decode(SqlitePostingMapper.requiredText(statement, 1)),
                decode(SqlitePostingMapper.requiredText(statement, 2)),
                decode(SqlitePostingMapper.requiredText(statement, 3))));
      }
      return List.copyOf(evidence);
    }
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
    try {
      return AttestationVerifier.verifyBook(evidence);
    } catch (AttestationVerificationException exception) {
      throw new IllegalArgumentException(exception.code(), exception);
    }
  }

  private static void insert(
      SqliteNativeDatabase activeDatabase,
      AttestationVerification verification,
      AttestationEvidence evidence) {
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqliteAttestationEvidenceSql.INSERT)) {
      statement.bindText(1, orderHex(verification.headOrder()));
      statement.bindText(2, encode(evidence.operationEnvelope()));
      statement.bindText(3, encode(evidence.requestPreimage()));
      statement.bindText(4, encode(evidence.effectPreimage()));
      statement.bindText(5, hex(verification.operationHead()));
      statement.step();
    }
  }

  private static byte[] requireHead(byte[] value, String name) {
    byte[] checked = Arrays.copyOf(Objects.requireNonNull(value, name), value.length);
    if (checked.length != 32) {
      throw new IllegalArgumentException(name + " must contain exactly 32 bytes.");
    }
    return checked;
  }

  private static byte[] decode(String encoded) {
    try {
      return Base64.getDecoder().decode(encoded);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException(
          "Persisted attestation evidence is not valid base64.", exception);
    }
  }

  private static String encode(byte[] value) {
    return Base64.getEncoder().encodeToString(value);
  }

  private static String orderHex(BigInteger order) {
    BigInteger checkedOrder = Objects.requireNonNull(order, "order");
    if (checkedOrder.signum() < 0 || checkedOrder.bitLength() > Long.SIZE) {
      throw new IllegalArgumentException("operation order must fit an unsigned 64-bit value.");
    }
    return "%016x".formatted(checkedOrder);
  }

  private static String hex(byte[] bytes) {
    return java.util.HexFormat.of().formatHex(requireHead(bytes, "operationHead"));
  }

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
}
