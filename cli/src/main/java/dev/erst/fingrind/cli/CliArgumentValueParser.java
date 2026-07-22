package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.attestation.AttestationCustodianNotSupportedException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** Shared low-level helpers for deterministic CLI argument parsing. */
final class CliArgumentValueParser {
  private CliArgumentValueParser() {}

  static <T> T requireValidArgument(String argument, Supplier<T> supplier) {
    Objects.requireNonNull(argument, "argument");
    Objects.requireNonNull(supplier, "supplier");
    try {
      return supplier.get();
    } catch (IllegalArgumentException exception) {
      throw invalid(
          argument,
          Objects.requireNonNullElse(exception.getMessage(), "Invalid argument value."),
          exception);
    }
  }

  static int requirePageLimit(int limit, String optionName) {
    if (limit < ProtocolInteractionLimits.PAGE_LIMIT_MIN
        || limit > ProtocolInteractionLimits.PAGE_LIMIT_MAX) {
      throw invalid(
          optionName,
          optionName
              + " must be between "
              + ProtocolInteractionLimits.PAGE_LIMIT_MIN
              + " and "
              + ProtocolInteractionLimits.PAGE_LIMIT_MAX
              + ".");
    }
    return limit;
  }

  static void requireOrderedDateRange(
      LocalDate effectiveDateFrom,
      LocalDate effectiveDateTo,
      String effectiveDateFromOption,
      String effectiveDateToOption) {
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    Objects.requireNonNull(effectiveDateFromOption, "effectiveDateFromOption");
    Objects.requireNonNull(effectiveDateToOption, "effectiveDateToOption");
    if (effectiveDateFrom.isAfter(effectiveDateTo)) {
      throw invalid(
          effectiveDateFromOption,
          effectiveDateFromOption + " must be on or before " + effectiveDateToOption + ".");
    }
  }

  static CliArgumentsException invalid(String argument, String message) {
    return new CliArgumentsException(
        ContractErrors.Descriptor.INVALID_REQUEST.code(),
        argument,
        message,
        CliInvocationText.helpSyntaxHint());
  }

  static CliArgumentsException invalid(String argument, String message, Throwable cause) {
    return new CliArgumentsException(
        ContractErrors.Descriptor.INVALID_REQUEST.code(),
        argument,
        message,
        CliInvocationText.helpSyntaxHint(),
        cause);
  }

  static CliArgumentsException unsupportedOutputSelection(String argument, String message) {
    return new CliArgumentsException(
        ContractErrors.Descriptor.UNSUPPORTED_OUTPUT_SELECTION.code(),
        argument,
        message,
        CliInvocationText.helpSyntaxHint());
  }

  static CliArgumentsException unsupportedAttestationCustodian(
      AttestationCustodianNotSupportedException exception) {
    AttestationCustodianNotSupportedException checkedException =
        Objects.requireNonNull(exception, "exception");
    return new CliArgumentsException(
        ContractErrors.Descriptor.CUSTODIAN_NOT_SUPPORTED.code(),
        "--attestation-custodian",
        "FinGrind does not implement the selected attestation custodian: "
            + checkedException.custodian()
            + ".",
        "Select file-pkcs8 for a local encrypted PKCS#8 credential.",
        checkedException);
  }

  static CliArgumentsException unsupportedArgument(String argument, List<String> supportedOptions) {
    return invalid(argument, unsupportedArgumentMessage(argument, supportedOptions));
  }

  static CliArgumentsException unknownCommand(String commandName, List<String> supportedCommands) {
    return new CliArgumentsException(
        ContractErrors.Descriptor.UNKNOWN_COMMAND.code(),
        commandName,
        unsupportedCommandMessage(commandName, supportedCommands),
        CliInvocationText.helpExamplesHint());
  }

  private static String unsupportedArgumentMessage(String argument, List<String> supportedOptions) {
    return unsupportedTokenMessage("Unsupported argument: ", argument, supportedOptions);
  }

  private static String unsupportedCommandMessage(
      String commandName, List<String> supportedCommands) {
    return unsupportedTokenMessage("Unsupported command: ", commandName, supportedCommands);
  }

  private static String unsupportedTokenMessage(
      String prefix, String token, List<String> supportedTokens) {
    String message = prefix + token;
    @Nullable String nearestSupportedToken = nearestSupportedToken(token, supportedTokens);
    return nearestSupportedToken == null
        ? message
        : message + ". Did you mean " + nearestSupportedToken + "?";
  }

  private static @Nullable String nearestSupportedToken(
      String token, List<String> supportedTokens) {
    String normalizedToken = normalizedToken(token);
    if (normalizedToken.isEmpty()) {
      return null;
    }
    return supportedTokens.stream()
        .map(
            candidate ->
                new TokenDistance(
                    candidate, normalizedToken, tokenDistance(normalizedToken, candidate)))
        .filter(
            candidate ->
                candidate.distance() <= candidate.maximumSuggestedDistance()
                    || candidate.prefixMatch()
                    || candidate.containsMatch())
        .min(
            Comparator.comparing(TokenDistance::prefixMatch)
                .reversed()
                .thenComparing(TokenDistance::containsMatch, Comparator.reverseOrder())
                .thenComparingInt(TokenDistance::distance)
                .thenComparingInt(candidate -> candidate.candidate().length()))
        .map(TokenDistance::candidate)
        .orElse(null);
  }

  private static int optionDistance(String left, String right) {
    int[][] distances = new int[left.length() + 1][right.length() + 1];
    for (int leftIndex = 0; leftIndex <= left.length(); leftIndex++) {
      distances[leftIndex][0] = leftIndex;
    }
    for (int rightIndex = 0; rightIndex <= right.length(); rightIndex++) {
      distances[0][rightIndex] = rightIndex;
    }
    for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
      for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
        int substitutionCost = left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1;
        distances[leftIndex][rightIndex] =
            Math.min(
                Math.min(
                    distances[leftIndex - 1][rightIndex] + 1,
                    distances[leftIndex][rightIndex - 1] + 1),
                distances[leftIndex - 1][rightIndex - 1] + substitutionCost);
      }
    }
    return distances[left.length()][right.length()];
  }

  private static String normalizedToken(String token) {
    String stripped = token.strip().toLowerCase(java.util.Locale.ROOT);
    int index = 0;
    while (index < stripped.length() && stripped.charAt(index) == '-') {
      index++;
    }
    return stripped.substring(index);
  }

  private static int tokenDistance(String normalizedToken, String candidate) {
    return optionDistance(normalizedToken, normalizedToken(candidate));
  }

  private record TokenDistance(String candidate, String input, int distance) {
    private int maximumSuggestedDistance() {
      return Math.max(
          2, Math.min(6, Math.max(input.length(), normalizedToken(candidate).length()) / 3));
    }

    private boolean prefixMatch() {
      String normalizedCandidate = normalizedToken(candidate);
      return normalizedCandidate.startsWith(input) || input.startsWith(normalizedCandidate);
    }

    private boolean containsMatch() {
      String normalizedCandidate = normalizedToken(candidate);
      return normalizedCandidate.contains(input) || input.contains(normalizedCandidate);
    }
  }
}
