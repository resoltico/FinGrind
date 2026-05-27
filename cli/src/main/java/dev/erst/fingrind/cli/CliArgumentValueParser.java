package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.InteractionLimits;
import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Supplier;

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
    if (limit < InteractionLimits.PAGE_LIMIT_MIN || limit > InteractionLimits.PAGE_LIMIT_MAX) {
      throw invalid(
          optionName,
          optionName
              + " must be between "
              + InteractionLimits.PAGE_LIMIT_MIN
              + " and "
              + InteractionLimits.PAGE_LIMIT_MAX
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

  static CliArgumentsException unknownCommand(String commandName) {
    return new CliArgumentsException(
        ContractErrors.Descriptor.UNKNOWN_COMMAND.code(),
        commandName,
        "Unsupported command: " + commandName,
        CliInvocationText.helpExamplesHint());
  }
}
