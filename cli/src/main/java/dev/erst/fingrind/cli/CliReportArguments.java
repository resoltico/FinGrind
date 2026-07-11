package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.core.ComparativeSelection;
import dev.erst.fingrind.core.EffectiveDateRange;
import java.time.LocalDate;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Routes report command parsing to narrower account-scoped and summary-specific parsers. */
final class CliReportArguments {
  private CliReportArguments() {}

  static CliCommand parseAccountBalanceCommand(List<String> arguments) {
    return CliAccountReportArguments.parseAccountBalanceCommand(arguments);
  }

  static CliCommand parseTrialBalanceCommand(List<String> arguments) {
    return CliSummaryReportArguments.parseTrialBalanceCommand(arguments);
  }

  static CliCommand parseAccountLedgerCommand(List<String> arguments) {
    return CliAccountReportArguments.parseAccountLedgerCommand(arguments);
  }

  static CliCommand parsePeriodSummaryCommand(List<String> arguments) {
    return CliSummaryReportArguments.parsePeriodSummaryCommand(arguments);
  }

  static CliCommand parseFinancialPositionCommand(List<String> arguments) {
    return CliSummaryReportArguments.parseFinancialPositionCommand(arguments);
  }

  static CliCommand parseIncomeStatementCommand(List<String> arguments) {
    return CliSummaryReportArguments.parseIncomeStatementCommand(arguments);
  }

  static CliCommand parseCashFlowStatementCommand(List<String> arguments) {
    return CliSummaryReportArguments.parseCashFlowStatementCommand(arguments);
  }

  static CliCommand parseChangesInEquityCommand(List<String> arguments) {
    return CliSummaryReportArguments.parseChangesInEquityCommand(arguments);
  }

  static CliCommand parseInventoryValuationCommand(List<String> arguments) {
    return CliInventoryValuationArguments.parseInventoryValuationCommand(arguments);
  }

  static @Nullable LocalDate requireDateOption(
      @Nullable LocalDate currentValue, ListIterator<String> argumentIterator, String optionName) {
    if (currentValue != null) {
      throw CliArgumentValueParser.invalid(optionName, "Duplicate argument: " + optionName);
    }
    return CliOptionValues.parseLocalDateOption(
        CliOptionValues.requireValue(argumentIterator, optionName), optionName);
  }

  static @Nullable OutputMode requireReportOutputMode(
      @Nullable OutputMode currentOutputMode, ListIterator<String> argumentIterator) {
    String rawOutputMode = CliOptionValues.requireValue(argumentIterator, ProtocolOptions.OUTPUT);
    if ("pdf".equals(rawOutputMode)) {
      throw CliArgumentValueParser.unsupportedOutputSelection(
          ProtocolOptions.OUTPUT,
          "Unsupported output mode for "
              + ProtocolOptions.OUTPUT
              + ": "
              + rawOutputMode
              + ". PDF export is file-only; use "
              + ProtocolOptions.PDF_OUT
              + " <path> together with one stdout mode from json or text.");
    }
    return CliOptionModes.requireOutputMode(
        currentOutputMode,
        rawOutputMode,
        CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV));
  }

  static ComparativeSelection requireComparativeSelection(
      @Nullable ComparativeSelection currentSelection,
      ListIterator<String> argumentIterator,
      ComparativeArgumentShape argumentShape) {
    if (currentSelection != null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.COMPARATIVE, "Duplicate argument: " + ProtocolOptions.COMPARATIVE);
    }
    String rawValue = CliOptionValues.requireValue(argumentIterator, ProtocolOptions.COMPARATIVE);
    return switch (rawValue) {
      case "none" -> ComparativeSelection.none();
      case "prior-period" -> ComparativeSelection.priorPeriod();
      default -> ComparativeSelection.range(parseComparativeRange(rawValue, argumentShape));
    };
  }

  private static EffectiveDateRange parseComparativeRange(
      String rawValue, ComparativeArgumentShape argumentShape) {
    int separatorIndex = rawValue.indexOf("..");
    if (separatorIndex < 0) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.COMPARATIVE, comparativeSyntaxMessage(argumentShape, rawValue));
    }
    String rawFrom = rawValue.substring(0, separatorIndex);
    String rawTo = rawValue.substring(separatorIndex + 2);
    return switch (argumentShape) {
      case AS_OF -> parseAsOfComparativeRange(rawValue, rawFrom, rawTo);
      case PERIOD -> parsePeriodComparativeRange(rawValue, rawFrom, rawTo);
    };
  }

  private static EffectiveDateRange parseAsOfComparativeRange(
      String rawValue, String rawFrom, String rawTo) {
    if (!rawFrom.isEmpty() || rawTo.isEmpty()) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.COMPARATIVE,
          "As-of "
              + ProtocolOptions.COMPARATIVE
              + " must use ..YYYY-MM-DD, none, or prior-period. Received: "
              + rawValue);
    }
    return EffectiveDateRange.to(
        CliOptionValues.parseLocalDateOption(rawTo, ProtocolOptions.COMPARATIVE));
  }

  private static EffectiveDateRange parsePeriodComparativeRange(
      String rawValue, String rawFrom, String rawTo) {
    if (rawFrom.isEmpty() || rawTo.isEmpty()) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.COMPARATIVE,
          "Period "
              + ProtocolOptions.COMPARATIVE
              + " must use YYYY-MM-DD..YYYY-MM-DD, none, or prior-period. Received: "
              + rawValue);
    }
    LocalDate effectiveDateFrom =
        CliOptionValues.parseLocalDateOption(rawFrom, ProtocolOptions.COMPARATIVE);
    LocalDate effectiveDateTo =
        CliOptionValues.parseLocalDateOption(rawTo, ProtocolOptions.COMPARATIVE);
    CliArgumentValueParser.requireOrderedDateRange(
        effectiveDateFrom,
        effectiveDateTo,
        ProtocolOptions.COMPARATIVE,
        ProtocolOptions.COMPARATIVE);
    return EffectiveDateRange.bounded(effectiveDateFrom, effectiveDateTo);
  }

  private static String comparativeSyntaxMessage(
      ComparativeArgumentShape argumentShape, String rawValue) {
    return switch (argumentShape) {
      case AS_OF ->
          "As-of "
              + ProtocolOptions.COMPARATIVE
              + " must use ..YYYY-MM-DD, none, or prior-period. Received: "
              + rawValue;
      case PERIOD ->
          "Period "
              + ProtocolOptions.COMPARATIVE
              + " must use YYYY-MM-DD..YYYY-MM-DD, none, or prior-period. Received: "
              + rawValue;
    };
  }

  /** Distinguishes open-ended as-of ranges from fully bounded period ranges. */
  enum ComparativeArgumentShape {
    AS_OF,
    PERIOD
  }
}
