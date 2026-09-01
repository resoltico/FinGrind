package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.core.ComparativeSelection;
import dev.erst.fingrind.core.EffectiveDateRange;
import java.time.LocalDate;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Parses report options shared by the report-specific command parsers. */
final class CliReportOptionArguments {
  private CliReportOptionArguments() {}

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
    String rawOutputMode =
        CliOptionValues.requireValue(argumentIterator, ProtocolOptions.Presentation.OUTPUT);
    if ("pdf".equals(rawOutputMode)) {
      throw CliArgumentValueParser.unsupportedOutputSelection(
          ProtocolOptions.Presentation.OUTPUT,
          "Unsupported output mode for "
              + ProtocolOptions.Presentation.OUTPUT
              + ": "
              + rawOutputMode
              + ". PDF export is file-only; use "
              + ProtocolOptions.Presentation.PDF_OUT
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
          ProtocolOptions.ReportQuery.COMPARATIVE,
          "Duplicate argument: " + ProtocolOptions.ReportQuery.COMPARATIVE);
    }
    String rawValue =
        CliOptionValues.requireValue(argumentIterator, ProtocolOptions.ReportQuery.COMPARATIVE);
    return switch (rawValue) {
      case "none" -> ComparativeSelection.none();
      case "same-period-prior-year" -> ComparativeSelection.priorPeriod();
      default -> ComparativeSelection.range(parseComparativeRange(rawValue, argumentShape));
    };
  }

  private static EffectiveDateRange parseComparativeRange(
      String rawValue, ComparativeArgumentShape argumentShape) {
    int separatorIndex = rawValue.indexOf("..");
    if (separatorIndex < 0) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.ReportQuery.COMPARATIVE,
          comparativeSyntaxMessage(argumentShape, rawValue));
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
          ProtocolOptions.ReportQuery.COMPARATIVE,
          "As-of "
              + ProtocolOptions.ReportQuery.COMPARATIVE
              + " must use ..YYYY-MM-DD, none, or same-period-prior-year. Received: "
              + rawValue);
    }
    return EffectiveDateRange.to(
        CliOptionValues.parseLocalDateOption(rawTo, ProtocolOptions.ReportQuery.COMPARATIVE));
  }

  private static EffectiveDateRange parsePeriodComparativeRange(
      String rawValue, String rawFrom, String rawTo) {
    if (rawFrom.isEmpty() || rawTo.isEmpty()) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.ReportQuery.COMPARATIVE,
          "Period "
              + ProtocolOptions.ReportQuery.COMPARATIVE
              + " must use YYYY-MM-DD..YYYY-MM-DD, none, or same-period-prior-year. Received: "
              + rawValue);
    }
    LocalDate effectiveDateFrom =
        CliOptionValues.parseLocalDateOption(rawFrom, ProtocolOptions.ReportQuery.COMPARATIVE);
    LocalDate effectiveDateTo =
        CliOptionValues.parseLocalDateOption(rawTo, ProtocolOptions.ReportQuery.COMPARATIVE);
    CliArgumentValueParser.requireOrderedDateRange(
        effectiveDateFrom,
        effectiveDateTo,
        ProtocolOptions.ReportQuery.COMPARATIVE,
        ProtocolOptions.ReportQuery.COMPARATIVE);
    return EffectiveDateRange.bounded(effectiveDateFrom, effectiveDateTo);
  }

  private static String comparativeSyntaxMessage(
      ComparativeArgumentShape argumentShape, String rawValue) {
    return switch (argumentShape) {
      case AS_OF ->
          "As-of "
              + ProtocolOptions.ReportQuery.COMPARATIVE
              + " must use ..YYYY-MM-DD, none, or same-period-prior-year. Received: "
              + rawValue;
      case PERIOD ->
          "Period "
              + ProtocolOptions.ReportQuery.COMPARATIVE
              + " must use YYYY-MM-DD..YYYY-MM-DD, none, or same-period-prior-year. Received: "
              + rawValue;
    };
  }

  /** Distinguishes open-ended as-of ranges from fully bounded period ranges. */
  enum ComparativeArgumentShape {
    AS_OF,
    PERIOD
  }
}
