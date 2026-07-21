package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Executes every complete-book Slice 4 negative from its committed byte mutation. */
class AttestationStaticNegativeBookCorpusTest {
  @Test
  void executesEveryCompleteBookNegativeFromItsFixedBaseAndEdit() {
    for (String id : AttestationStaticCorpusVectors.negativeBookIds()) {
      AttestationStaticCorpus.Fixture fixture =
          AttestationStaticCorpusVectors.negativeBookFixture(id);
      assertEquals(AttestationStaticCorpus.VerificationScope.BOOK, fixture.scope(), id);
      AttestationStaticCorpusVectors.requirePolicyFold(
          AttestationStaticCorpusVectors.negativePolicy(id));
      assertFailure(
          fixture.expectedFirstFailure(),
          () ->
              AttestationBookVerifier.verify(
                  AttestationCorpusResources.source(id, fixture.source()).decode()));
    }
  }
}
