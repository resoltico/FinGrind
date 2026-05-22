package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.AccountingPolicyProfile;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BusinessActivityTag;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.InteractionLimits;
import dev.erst.fingrind.core.PostingCoverage;
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

  static CurrencyUnit parseCurrencyUnitOption(String rawValue, String optionName) {
    try {
      return CurrencyUnit.of(rawValue);
    } catch (IllegalArgumentException exception) {
      throw invalid(
          optionName,
          Objects.requireNonNullElse(
              exception.getMessage(),
              "Option must be one supported ISO 4217 currency code: " + optionName),
          exception);
    }
  }

  static BookEntityName parseBookEntityNameOption(String rawValue, String optionName) {
    try {
      return new BookEntityName(rawValue);
    } catch (IllegalArgumentException exception) {
      throw invalid(
          optionName,
          Objects.requireNonNullElse(exception.getMessage(), "Invalid book entity name."),
          exception);
    }
  }

  static FiscalYearStart parseFiscalYearStartOption(String rawValue, String optionName) {
    try {
      return FiscalYearStart.parse(rawValue);
    } catch (IllegalArgumentException exception) {
      throw invalid(
          optionName,
          Objects.requireNonNullElse(
              exception.getMessage(), "Option must use MM-DD for " + optionName + "."),
          exception);
    }
  }

  static AccountingPolicyProfile parseAccountingPolicyProfileOption(
      String rawValue, String optionName) {
    try {
      return AccountingPolicyProfile.fromWireValue(rawValue);
    } catch (IllegalArgumentException exception) {
      throw invalid(
          optionName,
          Objects.requireNonNullElse(exception.getMessage(), "Unsupported policy profile."),
          exception);
    }
  }

  static BusinessActivityTag parseBusinessActivityTagOption(String rawValue, String optionName) {
    try {
      return new BusinessActivityTag(rawValue);
    } catch (IllegalArgumentException exception) {
      throw invalid(
          optionName,
          Objects.requireNonNullElse(exception.getMessage(), "Unsupported business activity tag."),
          exception);
    }
  }

  static PostingCoverage requirePostingCoverage(
      @Nullable PostingCoverage currentPostingCoverage, ListIterator<String> argumentIterator) {
    if (currentPostingCoverage != null) {
      throw invalid(
          ProtocolOptions.POSTING_COVERAGE,
          "Duplicate argument: " + ProtocolOptions.POSTING_COVERAGE);
    }
    String rawValue = requireValue(argumentIterator, ProtocolOptions.POSTING_COVERAGE);
    try {
      return PostingCoverage.fromWireValue(rawValue);
    } catch (IllegalArgumentException exception) {
      throw invalid(
          ProtocolOptions.POSTING_COVERAGE,
          "Unsupported posting coverage for "
              + ProtocolOptions.POSTING_COVERAGE
              + ": "
              + rawValue
              + ". Accepted values: "
              + String.join(", ", PostingCoverage.wireValues())
              + ".",
          exception);
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

  static PlanResultDetail requirePlanResultDetail(
      @Nullable PlanResultDetail currentResultDetail, ListIterator<String> argumentIterator) {
    if (currentResultDetail != null) {
      throw invalid(
          ProtocolOptions.RESULT_DETAIL, "Duplicate argument: " + ProtocolOptions.RESULT_DETAIL);
    }
    String rawValue = requireValue(argumentIterator, ProtocolOptions.RESULT_DETAIL);
    try {
      return PlanResultDetail.fromWireValue(rawValue);
    } catch (IllegalArgumentException exception) {
      throw invalid(
          ProtocolOptions.RESULT_DETAIL,
          "Unsupported result detail for "
              + ProtocolOptions.RESULT_DETAIL
              + ": "
              + rawValue
              + ". Accepted values: "
              + String.join(
                  ", ", dev.erst.fingrind.core.WireValue.wireValues(PlanResultDetail.class))
              + ".",
          exception);
    }
  }

  static DiscoveryDetail requireDiscoveryDetail(
      @Nullable DiscoveryDetail currentDetail, ListIterator<String> argumentIterator) {
    if (currentDetail != null) {
      throw invalid(ProtocolOptions.DETAIL, "Duplicate argument: " + ProtocolOptions.DETAIL);
    }
    String rawValue = requireValue(argumentIterator, ProtocolOptions.DETAIL);
    try {
      return DiscoveryDetail.fromWireValue(rawValue);
    } catch (IllegalArgumentException exception) {
      throw invalid(
          ProtocolOptions.DETAIL,
          "Unsupported discovery detail for "
              + ProtocolOptions.DETAIL
              + ": "
              + rawValue
              + ". Accepted values: "
              + String.join(
                  ", ", dev.erst.fingrind.core.WireValue.wireValues(DiscoveryDetail.class))
              + ".",
          exception);
    }
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
    return CliOutputModeDefaults.resolved(outputMode);
  }

  static OutputMode resolvedDiscoveryOutputMode(@Nullable OutputMode outputMode) {
    return CliOutputModeDefaults.resolvedDiscovery(outputMode);
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
