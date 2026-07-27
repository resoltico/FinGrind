package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.decode;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.planPostingEffect;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.planPostingRequest;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers plan-qualified fact ordering and every malformed wrapper boundary. */
class AttestationPlanQualifiedFactCoverageTest {
  @Test
  void effectFacts_restoreChildrenInAscendingSourceStepOrder() {
    AttestationOperationPreimages directPosting =
        AttestationPostingMutationProjection.project(planPostingRequest(), planPostingEffect());
    AttestationPreimage childEffect = decode(directPosting.effect());
    AttestationPreimage.Fact posting = matchingFact(childEffect, 0x0020);
    AttestationPreimage.Fact journalLine = matchingFact(childEffect, 0x0025);
    AttestationPreimage aggregateEffect =
        AttestationPreimage.of(
            List.of(
                AttestationPlanQualifiedFact.effectFact(9, posting),
                AttestationPlanQualifiedFact.effectFact(2, journalLine)));

    List<AttestationPreimage.Fact> restored =
        AttestationPlanQualifiedFact.effectFacts(aggregateEffect);

    assertEquals(
        List.of(0x0025, 0x0020),
        restored.stream().map(AttestationPreimage.Fact::recordTypeTag).toList());
    assertArrayEquals(journalLine.encoded(), restored.getFirst().encoded());
    assertArrayEquals(posting.encoded(), restored.getLast().encoded());
  }

  @Test
  void qualifiedFacts_rejectNegativeSourceStepsAndMismatchedSourceStepBundles() {
    AttestationPreimage.Fact childRequest = postingRequestFact();

    assertThrows(
        IllegalArgumentException.class,
        () -> AttestationPlanQualifiedFact.requestFact(-1, childRequest));

    AttestationPreimage request =
        AttestationPreimage.of(List.of(AttestationPlanQualifiedFact.requestFact(0, childRequest)));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationPlanQualifiedFact.requireValid(request, AttestationPreimage.of(List.of())));
  }

  @Test
  void qualifiedFacts_rejectEveryMalformedWrapperEncodingAndCategory() {
    AttestationPreimage.Fact childRequest = postingRequestFact();
    AttestationPreimage.Fact childEffect = postingEffectFact();
    AttestationPreimage.Fact validRequestWrapper =
        AttestationPlanQualifiedFact.requestFact(0, childRequest);

    assertInvalidRequestWrapper(
        replaceField(
            validRequestWrapper,
            0,
            AttestationField.present(
                AttestationNumericFieldValue.unsigned32(BigInteger.ONE.shiftLeft(31)))));
    assertInvalidRequestWrapper(
        replaceField(
            validRequestWrapper,
            1,
            AttestationField.present(
                AttestationNumericFieldValue.unsigned16(childRequest.recordTypeTag() + 1))));
    assertInvalidRequestWrapper(
        replaceField(
            validRequestWrapper,
            2,
            AttestationField.present(AttestationBinaryFieldValue.embedded(new byte[] {0}))));
    assertInvalidRequestWrapper(AttestationPlanQualifiedFact.requestFact(0, childEffect));
    assertInvalidRequestWrapper(
        AttestationPlanQualifiedFact.requestFact(
            0, AttestationPlanQualifiedFact.requestFact(0, childRequest)));
    assertInvalidEffectWrapper(AttestationPlanQualifiedFact.effectFact(0, childRequest));
    assertInvalidEffectWrapper(
        AttestationPlanQualifiedFact.effectFact(
            0, AttestationPlanQualifiedFact.effectFact(0, childEffect)));
  }

  private static void assertInvalidRequestWrapper(AttestationPreimage.Fact wrapper) {
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationPlanQualifiedFact.requireValid(
                AttestationPreimage.of(List.of(wrapper)), AttestationPreimage.of(List.of())));
  }

  private static void assertInvalidEffectWrapper(AttestationPreimage.Fact wrapper) {
    AttestationPreimage request =
        AttestationPreimage.of(
            List.of(AttestationPlanQualifiedFact.requestFact(0, postingRequestFact())));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationPlanQualifiedFact.requireValid(
                request, AttestationPreimage.of(List.of(wrapper))));
  }

  private static AttestationPreimage.Fact postingRequestFact() {
    AttestationOperationPreimages directPosting =
        AttestationPostingMutationProjection.project(planPostingRequest(), planPostingEffect());
    return matchingFact(decode(directPosting.request()), 0x0120);
  }

  private static AttestationPreimage.Fact postingEffectFact() {
    AttestationOperationPreimages directPosting =
        AttestationPostingMutationProjection.project(planPostingRequest(), planPostingEffect());
    return matchingFact(decode(directPosting.effect()), 0x0020);
  }

  private static AttestationPreimage.Fact matchingFact(AttestationPreimage preimage, int tag) {
    return AttestationPreimageFields.records(preimage, tag).getFirst();
  }

  private static AttestationPreimage.Fact replaceField(
      AttestationPreimage.Fact fact, int fieldIndex, AttestationField replacement) {
    List<AttestationField> fields = new ArrayList<>(fact.fields());
    fields.set(fieldIndex, replacement);
    return new AttestationPreimage.Fact(fact.recordTypeTag(), fields);
  }
}
