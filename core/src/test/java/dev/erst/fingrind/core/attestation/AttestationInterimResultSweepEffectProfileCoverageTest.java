package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.JULY_2026;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.closePosting;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.decode;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.replaceFirstField;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies every request-to-effect correspondence required for an interim-result sweep. */
class AttestationInterimResultSweepEffectProfileCoverageTest {
  @Test
  void interimSweepProfile_rejectsEveryMismatchedOrForbiddenCloseDeclarationField() {
    AttestationOperationPreimages projected =
        AttestationPeriodCloseMutationProjection.projectInterimResultSweep(
            "interim-result-sweep",
            JULY_2026,
            "3200",
            1,
            List.of(),
            List.of(closePosting("interim-result-sweep")));
    AttestationPreimage request = decode(projected.request());
    AttestationPreimage effect = decode(projected.effect());

    assertDoesNotThrow(
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.INTERIM_RESULT_SWEEP, request, effect));

    assertProfileFailure(
        request,
        replaceFirstField(
            effect,
            0x0040,
            2,
            AttestationPreimageProjectionFields.date(JULY_2026.effectiveDateFrom().plusDays(1))));
    assertProfileFailure(
        request,
        replaceFirstField(
            effect,
            0x0040,
            3,
            AttestationPreimageProjectionFields.date(JULY_2026.effectiveDateTo().minusDays(1))));
    assertProfileFailure(
        request,
        replaceFirstField(effect, 0x0040, 4, AttestationPreimageProjectionFields.text("3201")));
    assertProfileFailure(
        replaceFirstField(request, 0x0140, 5, AttestationPreimageProjectionFields.text("3100")),
        effect);
    assertProfileFailure(
        replaceFirstField(request, 0x0140, 6, AttestationPreimageProjectionFields.text("3300")),
        effect);
  }

  private static void assertProfileFailure(
      AttestationPreimage request, AttestationPreimage effect) {
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.INTERIM_RESULT_SWEEP, request, effect));
  }
}
