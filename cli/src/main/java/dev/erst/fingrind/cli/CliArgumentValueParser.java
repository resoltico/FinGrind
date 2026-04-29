package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountPageCursor;
import dev.erst.fingrind.contract.ContractErrors;
import dev.erst.fingrind.contract.PostingPageCursor;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** Shared low-level helpers for deterministic CLI argument parsing. */
final class CliArgumentValueParser {
  private CliArgumentValueParser() {}

  static int parseIntegerOption(String rawValue, String optionName) {
    try {
      return Integer.parseInt(rawValue);
    } catch (NumberFormatException exception) {
      throw invalid(optionName, "Option must be an integer: " + optionName, exception);
    }
  }

  static LocalDate parseLocalDateOption(String rawValue, String optionName) {
    try {
      return LocalDate.parse(rawValue);
    } catch (java.time.DateTimeException exception) {
      throw invalid(optionName, "Option must be an ISO-8601 local date: " + optionName, exception);
    }
  }

  static String requireValue(ListIterator<String> argumentIterator, String optionName) {
    if (!argumentIterator.hasNext()) {
      throw invalid(optionName, "Missing value for " + optionName + ".");
    }
    return argumentIterator.next();
  }

  static Path requirePathOptionValue(ListIterator<String> argumentIterator, String optionName) {
    return parsePathOption(requireValue(argumentIterator, optionName), optionName);
  }

  static Path parsePathOption(String rawValue, String optionName) {
    try {
      return Path.of(rawValue);
    } catch (InvalidPathException exception) {
      throw invalid(
          optionName,
          "Option must be a valid filesystem path for " + optionName + ": " + rawValue,
          exception);
    }
  }

  static PostingPageCursor postingPageCursor(String wireValue) {
    try {
      return PostingPageCursor.fromWireValue(wireValue);
    } catch (IllegalArgumentException exception) {
      throw new CliArgumentsException(
          ContractErrors.Descriptor.INVALID_PAGE_CURSOR.code(),
          ProtocolOptions.CURSOR,
          Objects.requireNonNullElse(exception.getMessage(), "Unsupported posting page cursor."),
          CliOperationText.listPostingsCursorRepairHint(),
          exception);
    }
  }

  static AccountPageCursor accountPageCursor(String wireValue) {
    try {
      return AccountPageCursor.fromWireValue(wireValue);
    } catch (IllegalArgumentException exception) {
      throw new CliArgumentsException(
          ContractErrors.Descriptor.INVALID_PAGE_CURSOR.code(),
          ProtocolOptions.CURSOR,
          Objects.requireNonNullElse(exception.getMessage(), "Unsupported account page cursor."),
          CliOperationText.listAccountsCursorRepairHint(),
          exception);
    }
  }

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

  static OutputMode requireOutputMode(
      @Nullable OutputMode currentOutputMode,
      String rawOutputMode,
      List<OutputMode> supportedModes) {
    if (currentOutputMode != null) {
      throw invalid(ProtocolOptions.OUTPUT, "Duplicate argument: " + ProtocolOptions.OUTPUT);
    }
    OutputMode outputMode;
    try {
      outputMode = OutputMode.fromWireValue(rawOutputMode);
    } catch (IllegalArgumentException exception) {
      throw invalid(
          ProtocolOptions.OUTPUT,
          unsupportedOutputModeMessage(rawOutputMode, supportedModes),
          exception);
    }
    if (!supportedModes.contains(outputMode)) {
      throw invalid(
          ProtocolOptions.OUTPUT, unsupportedOutputModeMessage(rawOutputMode, supportedModes));
    }
    return outputMode;
  }

  private static String unsupportedOutputModeMessage(
      String rawOutputMode, List<OutputMode> supportedModes) {
    return "Unsupported output mode for "
        + ProtocolOptions.OUTPUT
        + ": "
        + rawOutputMode
        + ". Accepted values: "
        + supportedModes.stream().map(OutputMode::wireValue).collect(Collectors.joining(", "))
        + ".";
  }

  static OutputMode resolvedOutputMode(@Nullable OutputMode outputMode) {
    return outputMode == null ? OutputMode.JSON : outputMode;
  }

  static OutputMode resolvedDiscoveryOutputMode(@Nullable OutputMode outputMode) {
    return outputMode == null ? OutputMode.HUMAN : outputMode;
  }

  static CliCommand.ReportOutput resolvedReportOutput(
      @Nullable OutputMode outputMode, @Nullable Path pdfOutPath) {
    return new CliCommand.ReportOutput(resolvedOutputMode(outputMode), pdfOutPath);
  }

  static Path requirePdfOutPath(
      @Nullable Path currentPdfOutPath, ListIterator<String> argumentIterator) {
    if (currentPdfOutPath != null) {
      throw invalid(ProtocolOptions.PDF_OUT, "Duplicate argument: " + ProtocolOptions.PDF_OUT);
    }
    return requirePathOptionValue(argumentIterator, ProtocolOptions.PDF_OUT);
  }

  static List<OutputMode> supportedOutputModes(OutputMode... outputModes) {
    return List.of(outputModes);
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
