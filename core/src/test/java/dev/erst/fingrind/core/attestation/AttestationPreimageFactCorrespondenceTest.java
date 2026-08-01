package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests order-independent request/effect fact correspondence at its shared structural seam. */
class AttestationPreimageFactCorrespondenceTest {
  private static final AttestationAuthorizationFailure FAILURE =
      AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID;

  @Test
  void mapsEachRequestFactToOneEffectFactRegardlessOfTheirCanonicalOrder() {
    AttestationPreimage.Fact post = policy("post", 1);
    AttestationPreimage.Fact administration = policy("administration", 2);

    assertDoesNotThrow(
        () ->
            AttestationPreimageFactCorrespondence.requireMappedFacts(
                preimage(post, administration),
                0x0103,
                0,
                preimage(administration, post),
                0x0103,
                0));
    assertFailure(
        FAILURE,
        () ->
            AttestationPreimageFactCorrespondence.requireMappedFacts(
                preimage(post), 0x0103, 0, preimage(administration), 0x0103, 0));
    assertFailure(
        FAILURE,
        () ->
            AttestationPreimageFactCorrespondence.requireMappedFacts(
                preimage(post), 0x0103, 0, preimage(post, administration), 0x0103, 0));
  }

  @Test
  void rejectsMismatchedOrOutOfRangeFieldCorrespondence() {
    AttestationPreimage.Fact post = policy("post", 1);
    AttestationPreimage.Fact changedQuorum = policy("post", 2);

    assertDoesNotThrow(
        () -> AttestationPreimageFactCorrespondence.requireFieldsMatch(post, 0, post, 0, 2));
    assertDoesNotThrow(
        () -> AttestationPreimageFactCorrespondence.requireSameField(post, 0, changedQuorum, 0));
    assertFailure(
        FAILURE,
        () ->
            AttestationPreimageFactCorrespondence.requireFieldsMatch(post, 0, changedQuorum, 0, 2));
    assertFailure(
        FAILURE,
        () -> AttestationPreimageFactCorrespondence.requireFieldsMatch(post, 0, post, 0, -1));
    assertFailure(
        FAILURE,
        () -> AttestationPreimageFactCorrespondence.requireFieldsMatch(post, 3, post, 0, 0));
    assertFailure(
        FAILURE,
        () -> AttestationPreimageFactCorrespondence.requireSameField(post, 1, changedQuorum, 1));
  }

  @Test
  void rejectsAnUnequalFactRemainderAndAnInsufficientRightFieldRange() {
    AttestationPreimage.Fact post = policy("post", 1);

    assertFailure(
        FAILURE,
        () ->
            AttestationPreimageFactCorrespondence.requireMappedFacts(
                preimage(post), 0x0103, 0, preimage(post), 0x0103, 1));
    assertFailure(
        FAILURE,
        () -> AttestationPreimageFactCorrespondence.requireFieldsMatch(post, 0, post, 1, 2));
  }

  private static AttestationPreimage preimage(AttestationPreimage.Fact... facts) {
    return AttestationPreimage.of(List.of(facts));
  }

  private static AttestationPreimage.Fact policy(String capability, int quorum) {
    return new AttestationPreimage.Fact(
        0x0103,
        List.of(
            AttestationField.present(AttestationTextFieldValue.token(capability)),
            AttestationField.present(AttestationNumericFieldValue.unsigned16(quorum))));
  }
}
