package dev.erst.fingrind.cli.json;

import org.jspecify.annotations.Nullable;

/** Validates that final-only pair-publication facts bind their reported final targets. */
final class CliPairPublicationTargetBinding {
  private CliPairPublicationTargetBinding() {}

  static void requireExactTargets(
      String bookTarget,
      String generatedSecretTarget,
      CliBookPairPublicationJsonModels.@Nullable PairPublicationPayload publication) {
    if (publication == null) {
      return;
    }
    if (!bookTarget.equals(publication.bookPublication().path())) {
      throw new IllegalArgumentException("Pair publication must bind the payload's book target.");
    }
    if (!generatedSecretTarget.equals(publication.generatedSecretPublication().path())) {
      throw new IllegalArgumentException(
          "Pair publication must bind the payload's generated-secret target.");
    }
  }
}
