package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.contract.runtime.ContractErrors;
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

  static CliArgumentsException invalidEnvironmentSelection(
      String environmentVariable, String message, Throwable cause) {
    return new CliArgumentsException(
        ContractErrors.Descriptor.INVALID_REQUEST.code(),
        environmentVariable,
        message,
        "Unset "
            + environmentVariable
            + " or set it to "
            + String.join(", ", "json", "text")
            + ", then rerun the command.",
        cause);
  }

  static CliArgumentsException unsupportedArgument(String argument, List<String> supportedOptions) {
    return invalid(argument, unsupportedArgumentMessage(argument, supportedOptions));
  }

  static CliArgumentsException unknownCommand(String commandName) {
    return new CliArgumentsException(
        ContractErrors.Descriptor.UNKNOWN_COMMAND.code(),
        commandName,
        "Unsupported command: " + commandName,
        CliInvocationText.helpExamplesHint());
  }

  private static String unsupportedArgumentMessage(String argument, List<String> supportedOptions) {
    String message = "Unsupported argument: " + argument;
    @Nullable String nearestSupportedOption = nearestSupportedOption(argument, supportedOptions);
    return nearestSupportedOption == null
        ? message
        : message + ". Did you mean " + nearestSupportedOption + "?";
  }

  private static @Nullable String nearestSupportedOption(
      String argument, List<String> supportedOptions) {
    if (!argument.startsWith("-")) {
      return null;
    }
    return supportedOptions.stream()
        .filter(option -> option.startsWith("-"))
        .map(option -> new OptionDistance(option, optionDistance(argument, option)))
        .filter(
            candidate ->
                candidate.distance() <= 3
                    || candidate.option().startsWith(argument)
                    || argument.startsWith(candidate.option()))
        .min(
            Comparator.comparingInt(OptionDistance::distance)
                .thenComparingInt(candidate -> candidate.option().length()))
        .map(OptionDistance::option)
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

  private record OptionDistance(String option, int distance) {}
}
