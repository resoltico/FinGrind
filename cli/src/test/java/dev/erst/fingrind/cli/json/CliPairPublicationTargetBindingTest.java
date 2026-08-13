package dev.erst.fingrind.cli.json;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Verifies final-only pair-publication facts bind exactly to their reported lifecycle targets. */
class CliPairPublicationTargetBindingTest {
  @Test
  void requireExactTargets_acceptsNoPublicationAndRejectsEitherMismatchedTarget() {
    assertDoesNotThrow(
        () -> CliPairPublicationTargetBinding.requireExactTargets("book", "secret", null));

    CliBookPairPublicationJsonModels.PairPublicationPayload publication =
        new CliBookPairPublicationJsonModels.PairPublicationPayload(
            new CliBookPairPublicationJsonModels.PairPublicationMemberPayload("book"),
            new CliBookPairPublicationJsonModels.PairPublicationMemberPayload("secret"),
            new CliEnvelopeJsonModels.PublicationTransaction(
                "0123456789abcdef0123456789abcdef", "complete", "all-committed", "complete"));

    assertEquals(
        "Pair publication must bind the payload's book target.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    CliPairPublicationTargetBinding.requireExactTargets(
                        "other-book", "secret", publication))
            .getMessage());
    assertEquals(
        "Pair publication must bind the payload's generated-secret target.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    CliPairPublicationTargetBinding.requireExactTargets(
                        "book", "other-secret", publication))
            .getMessage());
  }
}
