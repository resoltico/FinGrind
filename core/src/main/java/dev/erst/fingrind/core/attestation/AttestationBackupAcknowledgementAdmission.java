package dev.erst.fingrind.core.attestation;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Decides whether one backup-created acknowledgement may enter an existing immutable chain. */
public enum AttestationBackupAcknowledgementAdmission {
  /** No existing acknowledgement uses the requested backup identity. */
  APPEND,
  /** The exact immutable tuple is already recorded, so resume is a successful no-op. */
  IDENTICAL_REPLAY,
  /** The backup identity is already bound to a different immutable tuple. */
  CONFLICT;

  /**
   * Resolves an acknowledgement before a new operation is signed.
   *
   * <p>The method intentionally classifies only previously verified immutable evidence. A malformed
   * evidence chain remains a structural verification failure at the storage boundary.
   */
  public static AttestationBackupAcknowledgementAdmission evaluate(
      List<AttestationEvidence> existing, AttestationBackupAcknowledgement requested) {
    List<AttestationEvidence> checkedExisting =
        List.copyOf(Objects.requireNonNull(existing, "existing"));
    AttestationBackupAcknowledgement checkedRequested =
        Objects.requireNonNull(requested, "requested");
    boolean identicalReplay = false;
    for (AttestationEvidence evidence : checkedExisting) {
      AttestationBackupAcknowledgement existingAcknowledgement = from(evidence);
      if (existingAcknowledgement != null
          && existingAcknowledgement.backupId().equals(checkedRequested.backupId())) {
        if (!existingAcknowledgement.sameTuple(checkedRequested)) {
          return CONFLICT;
        }
        identicalReplay = true;
      }
    }
    return identicalReplay ? IDENTICAL_REPLAY : APPEND;
  }

  static @Nullable AttestationBackupAcknowledgement from(AttestationEvidence evidence) {
    AttestationEvidence checkedEvidence = Objects.requireNonNull(evidence, "evidence");
    AttestationDecodedEnvelope<AttestationOperationPayload> envelope =
        AttestationDecodedEnvelope.operation(checkedEvidence.operationEnvelope());
    if (AttestationOperationKind.forWireToken(envelope.payload().operationKind())
        != AttestationOperationKind.BACKUP_CREATED) {
      return null;
    }
    AttestationPreimage effect =
        AttestationPreimage.decode(
            checkedEvidence.effectPreimage(), AttestationAuthorizationFailure.PREIMAGE_INVALID);
    List<AttestationPreimage.Fact> records =
        effect.records().stream().filter(record -> record.recordTypeTag() == 0x0006).toList();
    if (records.size() != 1) {
      throw new AttestationAuthorizationException(AttestationAuthorizationFailure.PREIMAGE_INVALID);
    }
    AttestationPreimage.Fact acknowledgement = records.getFirst();
    return new AttestationBackupAcknowledgement(
        AttestationPreimageValueReader.uuid(
            acknowledgement, 1, AttestationAuthorizationFailure.PREIMAGE_INVALID),
        AttestationPreimageValueReader.hash(
                acknowledgement, 2, AttestationAuthorizationFailure.PREIMAGE_INVALID)
            .bytes(),
        AttestationPreimageValueReader.unsigned64(
            acknowledgement, 3, AttestationAuthorizationFailure.PREIMAGE_INVALID),
        AttestationPreimageValueReader.hash(
                acknowledgement, 4, AttestationAuthorizationFailure.PREIMAGE_INVALID)
            .bytes());
  }
}
