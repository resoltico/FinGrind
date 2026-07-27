package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.RECORDED_AT;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.acknowledgement;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.decode;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies lifecycle-profile provenance and exact lifecycle-fact membership. */
class AttestationLifecycleEffectProfileCoverageTest {
  @Test
  void lifecycleProfile_rejectsValidlyEncodedFactsFromTheWrongLifecycleFamily() {
    AttestationBackupAcknowledgement acknowledgement = acknowledgement();
    AttestationOperationPreimages backup =
        AttestationLifecycleMutationProjection.backupBook("backup-book", acknowledgement);
    AttestationOperationPreimages restore =
        AttestationLifecycleMutationProjection.restoreBook("restore-book", acknowledgement);
    AttestationPreimage backupRequest = decode(backup.request());
    AttestationPreimage backupEffect = decode(backup.effect());
    AttestationPreimage restoreRequest = decode(restore.request());
    AttestationPreimage restoreEffect = decode(restore.effect());

    assertDoesNotThrow(
        () ->
            AttestationLifecycleEffectProfile.requireValid(
                AttestationOperationKind.BACKUP_CREATED, backupRequest, backupEffect));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationLifecycleEffectProfile.requireValid(
                AttestationOperationKind.BACKUP_CREATED, restoreRequest, backupEffect));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationLifecycleEffectProfile.requireValid(
                AttestationOperationKind.BACKUP_CREATED, backupRequest, restoreEffect));
  }

  @Test
  void backupSourceMustMatchTheVerifiedHeadAtItsDeclaredOrder() {
    AttestationBackupAcknowledgement acknowledgement = acknowledgement();
    AttestationOperationPreimages backup =
        AttestationLifecycleMutationProjection.backupBook("backup-book", acknowledgement);
    AttestationPreimage backupRequest = decode(backup.request());
    AttestationPreimage backupEffect = decode(backup.effect());
    AttestationBookOperation sourceOperation =
        operation(
            acknowledgement.sourceOrder(),
            AttestationOperationKind.BACKUP_CREATED,
            backupRequest,
            backupEffect);

    assertDoesNotThrow(
        () ->
            AttestationLifecycleEffectProfile.requireVerifiedBackupSource(
                backupRequest,
                List.of(
                    new AttestationBookVerification.VerifiedOperation(
                        acknowledgement.sourceOrder(),
                        AttestationHash.of(acknowledgement.sourceOperationHead()),
                        sourceOperation))));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationLifecycleEffectProfile.requireVerifiedBackupSource(
                backupRequest,
                List.of(
                    new AttestationBookVerification.VerifiedOperation(
                        acknowledgement.sourceOrder(),
                        AttestationHash.sha256(new byte[] {9}),
                        sourceOperation))));
  }

  private static AttestationBookOperation operation(
      BigInteger operationOrder,
      AttestationOperationKind operationKind,
      AttestationPreimage request,
      AttestationPreimage effect) {
    AttestationOperationPayload payload =
        new AttestationOperationPayload(
            AttestationAuthorizationTestSupport.BOOK_ID,
            operationOrder,
            operationKind.wireToken(),
            AttestationHash.sha256(new byte[] {7}),
            RECORDED_AT,
            AttestationHash.sha256(request.encoded()),
            AttestationHash.sha256(effect.encoded()));
    return AttestationBookOperation.decode(
        AttestationEnvelope.of(payload, List.of()).encoded(), request.encoded(), effect.encoded());
  }
}
