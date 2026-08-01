package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.credential;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.RECORDED_AT;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.acknowledgement;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.decode;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.mutation;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.replaceField;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.tags;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies immutable backup, restore, rekey, and credential-registry mutation evidence. */
class AttestationLifecycleMutationProjectionTest {
  @Test
  void lifecycleProjection_commitsBackupRestoreAndRekeyWithoutSecretMaterial() {
    AttestationBackupAcknowledgement acknowledgement = acknowledgement();
    AttestationOperationPreimages backup =
        AttestationLifecycleMutationProjection.backupBook("backup-book", acknowledgement);
    AttestationOperationPreimages restore =
        AttestationLifecycleMutationProjection.restoreBook("restore-book", acknowledgement);
    AttestationOperationPreimages rekey =
        AttestationLifecycleMutationProjection.rekeyBook(
            "rekey-book", BigInteger.TWO, RECORDED_AT, Optional.of("scheduled"));
    AttestationPreimage backupRequest = decode(backup.request());
    AttestationPreimage backupEffect = decode(backup.effect());
    AttestationPreimage restoreRequest = decode(restore.request());
    AttestationPreimage restoreEffect = decode(restore.effect());
    AttestationPreimage rekeyRequest = decode(rekey.request());
    AttestationPreimage rekeyEffect = decode(rekey.effect());

    assertEquals(List.of(0x0100, 0x0150), tags(backupRequest));
    assertEquals(List.of(0x0006), tags(backupEffect));
    assertEquals(List.of(0x0100, 0x0160), tags(restoreRequest));
    assertEquals(List.of(0x00A0), tags(restoreEffect));
    assertEquals(List.of(0x0100, 0x0170), tags(rekeyRequest));
    assertEquals(AttestationEffectMutation.ACKNOWLEDGE.wireValue(), mutation(backupEffect, 0x0006));
    assertEquals(AttestationEffectMutation.DERIVE.wireValue(), mutation(restoreEffect, 0x00A0));
    assertEquals(AttestationEffectMutation.DERIVE.wireValue(), mutation(rekeyEffect, 0x0007));
    assertDoesNotThrow(
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.BACKUP_CREATED, backupRequest, backupEffect));
    assertDoesNotThrow(
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.RESTORE_BOOK, restoreRequest, restoreEffect));
    assertDoesNotThrow(
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.REKEY_BOOK, rekeyRequest, rekeyEffect));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.BACKUP_CREATED,
                backupRequest,
                replaceField(
                    backupEffect,
                    0x0006,
                    0,
                    AttestationPreimageProjectionFields.present(
                        AttestationNumericFieldValue.mutation(
                            AttestationEffectMutation.CREATE.wireValue())))));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.BACKUP_CREATED,
                backupRequest,
                replaceField(
                    backupEffect,
                    0x0006,
                    1,
                    AttestationPreimageProjectionFields.uuid(UUID.randomUUID()))));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.RESTORE_BOOK,
                restoreRequest,
                replaceField(
                    restoreEffect,
                    0x00A0,
                    4,
                    AttestationPreimageProjectionFields.present(
                        AttestationNumericFieldValue.booleanValue(false)))));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.REKEY_BOOK,
                rekeyRequest,
                replaceField(
                    rekeyEffect,
                    0x0007,
                    1,
                    AttestationPreimageProjectionFields.present(
                        AttestationNumericFieldValue.unsigned64(BigInteger.valueOf(3))))));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.REKEY_BOOK,
                rekeyRequest,
                replaceField(
                    rekeyEffect,
                    0x0007,
                    0,
                    AttestationPreimageProjectionFields.present(
                        AttestationNumericFieldValue.mutation(
                            AttestationEffectMutation.CREATE.wireValue())))));
    assertEquals(
        List.of(0x0100, 0x0170),
        tags(
            decode(
                AttestationLifecycleMutationProjection.rekeyBook(
                        "rekey-book", BigInteger.valueOf(3), RECORDED_AT, Optional.empty())
                    .request())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationLifecycleMutationProjection.rekeyBook(
                "rekey-book", BigInteger.ONE, RECORDED_AT, Optional.empty()));
  }

  @Test
  void credentialProjection_rejectsAnUnpairedRolloverRetirement() {
    TestCredential credential = credential();
    AttestationPublicCredential publicCredential =
        new AttestationPublicCredential(credential.pair().getPublic().getEncoded());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationRegistryMutationProjection.binding(
                "rollover-key",
                credential.principalId(),
                publicCredential,
                "rollover",
                "operator",
                Optional.of(AttestationHash.sha256(new byte[] {7})),
                Optional.empty()));
  }

  @Test
  void lifecycleProfile_exposesTheValidatedDerivedValuesAndRejectsWrongOperationFamilies() {
    AttestationBackupAcknowledgement acknowledgement = acknowledgement();
    AttestationOperationPreimages backup =
        AttestationLifecycleMutationProjection.backupBook("backup-book", acknowledgement);
    AttestationOperationPreimages restore =
        AttestationLifecycleMutationProjection.restoreBook("restore-book", acknowledgement);
    AttestationOperationPreimages rekey =
        AttestationLifecycleMutationProjection.rekeyBook(
            "rekey-book", BigInteger.TWO, RECORDED_AT, Optional.empty());
    AttestationPreimage backupRequest = decode(backup.request());
    AttestationPreimage restoreRequest = decode(restore.request());
    AttestationPreimage restoreEffect = decode(restore.effect());
    AttestationPreimage rekeyEffect = decode(rekey.effect());
    AttestationOperationPayload restorePayload =
        payload(
            acknowledgement.sourceOrder().add(BigInteger.ONE),
            AttestationOperationKind.RESTORE_BOOK,
            AttestationHash.of(acknowledgement.sourceOperationHead()),
            RECORDED_AT,
            restoreRequest,
            restoreEffect);
    AttestationOperationPayload rekeyPayload =
        payload(
            BigInteger.ONE,
            AttestationOperationKind.REKEY_BOOK,
            AttestationHash.sha256(new byte[] {9}),
            RECORDED_AT,
            decode(rekey.request()),
            rekeyEffect);

    assertEquals(
        acknowledgement.backupId(), AttestationLifecycleEffectProfile.backupId(backupRequest));
    assertEquals(BigInteger.TWO, AttestationLifecycleEffectProfile.rekeyEpoch(rekeyEffect));
    assertDoesNotThrow(
        () ->
            AttestationLifecycleEffectProfile.requireRestorePredecessor(
                restorePayload, restoreRequest));
    assertDoesNotThrow(
        () ->
            AttestationLifecycleEffectProfile.requireVerifiedBackupSource(
                backupRequest,
                List.of(
                    new AttestationBookVerification.VerifiedOperation(
                        acknowledgement.sourceOrder(),
                        AttestationHash.of(acknowledgement.sourceOperationHead()),
                        operation(
                            acknowledgement.sourceOrder(),
                            AttestationOperationKind.BACKUP_CREATED,
                            backupRequest,
                            decode(backup.effect()))))));
    assertDoesNotThrow(
        () -> AttestationLifecycleEffectProfile.requireRekeyRecordedAt(rekeyPayload, rekeyEffect));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationLifecycleEffectProfile.requireRestorePredecessor(
                payload(
                    restorePayload.operationOrder().add(BigInteger.ONE),
                    AttestationOperationKind.RESTORE_BOOK,
                    restorePayload.previousHead(),
                    RECORDED_AT,
                    restoreRequest,
                    restoreEffect),
                restoreRequest));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationLifecycleEffectProfile.requireRekeyRecordedAt(
                payload(
                    rekeyPayload.operationOrder(),
                    AttestationOperationKind.REKEY_BOOK,
                    rekeyPayload.previousHead(),
                    RECORDED_AT.plusSeconds(1),
                    decode(rekey.request()),
                    rekeyEffect),
                rekeyEffect));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationLifecycleEffectProfile.requireVerifiedBackupSource(
                backupRequest, List.of()));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationLifecycleEffectProfile.requireValid(
                AttestationOperationKind.POST_ENTRY, backupRequest, decode(backup.effect())));
  }

  private static AttestationOperationPayload payload(
      BigInteger operationOrder,
      AttestationOperationKind operationKind,
      AttestationHash previousHead,
      Instant recordedAt,
      AttestationPreimage request,
      AttestationPreimage effect) {
    return new AttestationOperationPayload(
        AttestationAuthorizationTestSupport.BOOK_ID,
        operationOrder,
        operationKind.wireToken(),
        previousHead,
        recordedAt,
        AttestationHash.sha256(request.encoded()),
        AttestationHash.sha256(effect.encoded()));
  }

  private static AttestationBookOperation operation(
      BigInteger operationOrder,
      AttestationOperationKind operationKind,
      AttestationPreimage request,
      AttestationPreimage effect) {
    AttestationOperationPayload payload =
        payload(
            operationOrder,
            operationKind,
            AttestationHash.sha256(new byte[] {7}),
            RECORDED_AT,
            request,
            effect);
    return AttestationBookOperation.decode(
        AttestationEnvelope.of(payload, List.of()).encoded(), request.encoded(), effect.encoded());
  }
}
