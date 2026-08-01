package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

/** Validates the exact request-to-effect relation for protected-book lifecycle operations. */
final class AttestationLifecycleEffectProfile {
  private static final int BACKUP_REQUEST = 0x0150;
  private static final int RESTORE_REQUEST = 0x0160;
  private static final int REKEY_REQUEST = 0x0170;
  private static final int BACKUP_ACKNOWLEDGEMENT = 0x0006;
  private static final int KEY_EPOCH = 0x0007;
  private static final int RESTORE_PROVENANCE = 0x00A0;

  private AttestationLifecycleEffectProfile() {}

  /** Requires the lifecycle effect to be the complete permitted derivation of its request. */
  static void requireValid(
      AttestationOperationKind operationKind,
      AttestationPreimage requestPreimage,
      AttestationPreimage effectPreimage) {
    switch (operationKind) {
      case BACKUP_CREATED -> requireBackupAcknowledgement(requestPreimage, effectPreimage);
      case RESTORE_BOOK -> requireRestoreProvenance(requestPreimage, effectPreimage);
      case REKEY_BOOK -> requireRekeyEffect(requestPreimage, effectPreimage);
      default -> throw failure();
    }
  }

  /** Requires a restore request to continue exactly from its declared verified source head. */
  static void requireRestorePredecessor(
      AttestationOperationPayload payload, AttestationPreimage requestPreimage) {
    AttestationPreimage.Fact restore = request(requestPreimage, RESTORE_REQUEST);
    BigInteger sourceOrder =
        AttestationPreimageValueReader.unsigned64(
            restore, 2, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
    AttestationHash sourceHead =
        AttestationPreimageValueReader.hash(
            restore, 3, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
    if (!sourceOrder.add(BigInteger.ONE).equals(payload.operationOrder())
        || !sourceHead.equals(payload.previousHead())) {
      throw failure();
    }
  }

  /** Requires a backup acknowledgement to name an already verified immutable source position. */
  static void requireVerifiedBackupSource(
      AttestationPreimage requestPreimage,
      List<AttestationBookVerification.VerifiedOperation> verifiedOperations) {
    AttestationPreimage.Fact backup = request(requestPreimage, BACKUP_REQUEST);
    BigInteger sourceOrder =
        AttestationPreimageValueReader.unsigned64(
            backup, 2, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
    AttestationHash sourceHead =
        AttestationPreimageValueReader.hash(
            backup, 3, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
    AttestationBookVerification.VerifiedOperation source =
        verifiedOperations.stream()
            .filter(operation -> operation.operationOrder().equals(sourceOrder))
            .findFirst()
            .orElseThrow(AttestationLifecycleEffectProfile::failure);
    if (!source.head().equals(sourceHead)) {
      throw failure();
    }
  }

  /** Returns the already profile-validated rekey epoch carried by one effect preimage. */
  static BigInteger rekeyEpoch(AttestationPreimage effectPreimage) {
    return AttestationPreimageValueReader.unsigned64(
        effect(effectPreimage, KEY_EPOCH),
        1,
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
  }

  /** Requires the derived rekey instant to equal the operation's canonical recorded instant. */
  static void requireRekeyRecordedAt(
      AttestationOperationPayload payload, AttestationPreimage effectPreimage) {
    AttestationPreimage.Fact effect = effect(effectPreimage, KEY_EPOCH);
    byte[] expected = AttestationPreimageProjectionFields.instant(payload.recordedAt()).encoded();
    if (!Arrays.equals(AttestationPreimageFields.requireField(effect, 2).encoded(), expected)) {
      throw failure();
    }
  }

  /** Returns the already profile-validated backup identity carried by one request preimage. */
  static java.util.UUID backupId(AttestationPreimage requestPreimage) {
    return AttestationPreimageValueReader.uuid(
        request(requestPreimage, BACKUP_REQUEST),
        0,
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
  }

  private static void requireBackupAcknowledgement(
      AttestationPreimage requestPreimage, AttestationPreimage effectPreimage) {
    AttestationPreimage.Fact request = request(requestPreimage, BACKUP_REQUEST);
    AttestationPreimage.Fact effect = effect(effectPreimage, BACKUP_ACKNOWLEDGEMENT);
    requireMutation(effect, AttestationEffectMutation.ACKNOWLEDGE);
    requireSameFields(request, 0, effect, 1, 4);
  }

  private static void requireRestoreProvenance(
      AttestationPreimage requestPreimage, AttestationPreimage effectPreimage) {
    AttestationPreimage.Fact request = request(requestPreimage, RESTORE_REQUEST);
    AttestationPreimage.Fact effect = effect(effectPreimage, RESTORE_PROVENANCE);
    requireMutation(effect, AttestationEffectMutation.DERIVE);
    requireSameFields(request, 0, effect, 1, 3);
    if (!AttestationPreimageValueReader.booleanValue(
        effect, 4, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID)) {
      throw failure();
    }
  }

  private static void requireRekeyEffect(
      AttestationPreimage requestPreimage, AttestationPreimage effectPreimage) {
    AttestationPreimage.Fact request = request(requestPreimage, REKEY_REQUEST);
    AttestationPreimage.Fact effect = effect(effectPreimage, KEY_EPOCH);
    requireMutation(effect, AttestationEffectMutation.DERIVE);
    requireSameFields(request, 0, effect, 1, 1);
  }

  private static void requireMutation(
      AttestationPreimage.Fact effect, AttestationEffectMutation expectedMutation) {
    if (AttestationPreimageValueReader.mutation(
            effect, 0, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID)
        != expectedMutation.wireValue()) {
      throw failure();
    }
  }

  private static void requireSameFields(
      AttestationPreimage.Fact request,
      int requestStart,
      AttestationPreimage.Fact effect,
      int effectStart,
      int fieldCount) {
    for (int offset = 0; offset < fieldCount; offset++) {
      AttestationPreimageFields.requireSameField(
          request,
          requestStart + offset,
          effect,
          effectStart + offset,
          AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
    }
  }

  private static AttestationPreimage.Fact request(AttestationPreimage preimage, int tag) {
    requireExactRecords(preimage, 0x0100, tag);
    return AttestationPreimageFields.records(preimage, tag).getFirst();
  }

  private static AttestationPreimage.Fact effect(AttestationPreimage preimage, int tag) {
    requireExactRecords(preimage, tag);
    return AttestationPreimageFields.records(preimage, tag).getFirst();
  }

  private static void requireExactRecords(AttestationPreimage preimage, int... tags) {
    if (preimage.records().size() != tags.length) {
      throw failure();
    }
    for (int tag : tags) {
      if (AttestationPreimageFields.records(preimage, tag).size() != 1) {
        throw failure();
      }
    }
  }

  private static AttestationAuthorizationException failure() {
    return AttestationOperationProfile.failure();
  }
}
