package dev.erst.fingrind.cli.json;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Verifies retained pair-publication facts bind exactly to their reported lifecycle targets. */
class CliPairPublicationRetentionTargetBindingTest {
  @Test
  void requireExactTargets_acceptsNoRetentionAndRejectsEitherMismatchedTarget() {
    assertDoesNotThrow(
        () -> CliPairPublicationRetentionTargetBinding.requireExactTargets("book", "secret", null));

    CliBookPairPublicationJsonModels.PairPublicationRetentionPayload retention =
        new CliBookPairPublicationJsonModels.PairPublicationRetentionPayload(
            new CliBookPairPublicationJsonModels.PairPublicationMemberPublicationPayload(
                "book", "book-stage"),
            new CliBookPairPublicationJsonModels.PairPublicationMemberPublicationPayload(
                "secret", "secret-stage"));

    assertEquals(
        "Pair publication retention must bind the payload's book target.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    CliPairPublicationRetentionTargetBinding.requireExactTargets(
                        "other-book", "secret", retention))
            .getMessage());
    assertEquals(
        "Pair publication retention must bind the payload's generated-secret target.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    CliPairPublicationRetentionTargetBinding.requireExactTargets(
                        "book", "other-secret", retention))
            .getMessage());
  }
}
