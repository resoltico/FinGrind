package dev.erst.fingrind.cli.json;

import org.jspecify.annotations.Nullable;

/** Validates that retained pair-publication facts bind their exact reported final targets. */
final class CliPairPublicationRetentionTargetBinding {
  private CliPairPublicationRetentionTargetBinding() {}

  static void requireExactTargets(
      String bookTarget,
      String generatedSecretTarget,
      CliBookPairPublicationJsonModels.@Nullable PairPublicationRetentionPayload retention) {
    if (retention == null) {
      return;
    }
    if (!bookTarget.equals(retention.bookPublication().path())) {
      throw new IllegalArgumentException(
          "Pair publication retention must bind the payload's book target.");
    }
    if (!generatedSecretTarget.equals(retention.generatedSecretPublication().path())) {
      throw new IllegalArgumentException(
          "Pair publication retention must bind the payload's generated-secret target.");
    }
  }
}
