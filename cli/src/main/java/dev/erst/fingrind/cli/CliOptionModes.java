package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerPageCursor;
import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.DiscoveryFocus;
import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.tax.TaxRegistrationPageCursor;
import dev.erst.fingrind.core.PostingCoverage;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** Parses choice-like CLI options and resolves output defaults. */
final class CliOptionModes {
  private CliOptionModes() {}

  static PostingCoverage requirePostingCoverage(
      @Nullable PostingCoverage currentPostingCoverage, ListIterator<String> argumentIterator) {
    if (currentPostingCoverage != null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.ReportQuery.POSTING_COVERAGE,
          "Duplicate argument: " + ProtocolOptions.ReportQuery.POSTING_COVERAGE);
    }
    String rawValue =
        CliOptionValues.requireValue(
            argumentIterator, ProtocolOptions.ReportQuery.POSTING_COVERAGE);
    try {
      return PostingCoverage.fromWireValue(rawValue);
    } catch (IllegalArgumentException exception) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.ReportQuery.POSTING_COVERAGE,
          "Unsupported posting coverage for "
              + ProtocolOptions.ReportQuery.POSTING_COVERAGE
              + ": "
              + rawValue
              + ". Accepted values: "
              + String.join(", ", PostingCoverage.wireValues())
              + ".",
          exception);
    }
  }

  static OutputMode requireOutputMode(
      @Nullable OutputMode currentOutputMode,
      String rawOutputMode,
      List<OutputMode> supportedModes) {
    if (currentOutputMode != null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.Presentation.OUTPUT,
          "Duplicate argument: " + ProtocolOptions.Presentation.OUTPUT);
    }
    OutputMode outputMode;
    try {
      outputMode = OutputMode.fromWireValue(rawOutputMode);
    } catch (IllegalArgumentException exception) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.Presentation.OUTPUT,
          unsupportedOutputModeMessage(rawOutputMode, supportedModes),
          exception);
    }
    if (!supportedModes.contains(outputMode)) {
      throw CliArgumentValueParser.unsupportedOutputSelection(
          ProtocolOptions.Presentation.OUTPUT,
          unsupportedOutputModeMessage(rawOutputMode, supportedModes));
    }
    return outputMode;
  }

  static PlanResultDetail requirePlanResultDetail(
      @Nullable PlanResultDetail currentResultDetail, ListIterator<String> argumentIterator) {
    if (currentResultDetail != null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.Discovery.RESULT_DETAIL,
          "Duplicate argument: " + ProtocolOptions.Discovery.RESULT_DETAIL);
    }
    String rawValue =
        CliOptionValues.requireValue(argumentIterator, ProtocolOptions.Discovery.RESULT_DETAIL);
    try {
      return PlanResultDetail.fromWireValue(rawValue);
    } catch (IllegalArgumentException exception) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.Discovery.RESULT_DETAIL,
          "Unsupported result detail for "
              + ProtocolOptions.Discovery.RESULT_DETAIL
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
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.Discovery.DETAIL,
          "Duplicate argument: " + ProtocolOptions.Discovery.DETAIL);
    }
    String rawValue =
        CliOptionValues.requireValue(argumentIterator, ProtocolOptions.Discovery.DETAIL);
    try {
      return DiscoveryDetail.fromWireValue(rawValue);
    } catch (IllegalArgumentException exception) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.Discovery.DETAIL,
          "Unsupported discovery detail for "
              + ProtocolOptions.Discovery.DETAIL
              + ": "
              + rawValue
              + ". Accepted values: "
              + String.join(
                  ", ", dev.erst.fingrind.core.WireValue.wireValues(DiscoveryDetail.class))
              + ".",
          exception);
    }
  }

  static DiscoveryFocus requireDiscoveryFocus(
      @Nullable DiscoveryFocus currentFocus, ListIterator<String> argumentIterator) {
    if (currentFocus != null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.Discovery.FOCUS,
          "Duplicate argument: " + ProtocolOptions.Discovery.FOCUS);
    }
    String rawValue =
        CliOptionValues.requireValue(argumentIterator, ProtocolOptions.Discovery.FOCUS);
    try {
      return DiscoveryFocus.fromWireValue(rawValue);
    } catch (IllegalArgumentException exception) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.Discovery.FOCUS,
          "Unsupported discovery focus for "
              + ProtocolOptions.Discovery.FOCUS
              + ": "
              + rawValue
              + ". Accepted values: "
              + String.join(", ", dev.erst.fingrind.core.WireValue.wireValues(DiscoveryFocus.class))
              + ".",
          exception);
    }
  }

  static OperationCategory requireOperationCategory(
      @Nullable OperationCategory currentCategory, ListIterator<String> argumentIterator) {
    if (currentCategory != null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.Discovery.CATEGORY,
          "Duplicate argument: " + ProtocolOptions.Discovery.CATEGORY);
    }
    String rawValue =
        CliOptionValues.requireValue(argumentIterator, ProtocolOptions.Discovery.CATEGORY);
    try {
      return OperationCategory.fromWireValue(rawValue);
    } catch (IllegalArgumentException exception) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.Discovery.CATEGORY,
          "Unsupported operation category for "
              + ProtocolOptions.Discovery.CATEGORY
              + ": "
              + rawValue
              + ". Accepted values: "
              + String.join(
                  ", ", dev.erst.fingrind.core.WireValue.wireValues(OperationCategory.class))
              + ".",
          exception);
    }
  }

  static OutputMode resolvedOutputMode(@Nullable OutputMode outputMode) {
    return CliOutputModeDefaults.resolved(
        outputMode, CliOutputModeDefaults.OutputSurface.SELECTABLE);
  }

  static OutputMode resolvedDiscoveryOutputMode(@Nullable OutputMode outputMode) {
    return CliOutputModeDefaults.resolved(
        outputMode, CliOutputModeDefaults.OutputSurface.DISCOVERY);
  }

  static CliReportOutput resolvedReportOutput(
      @Nullable OutputMode outputMode, @Nullable Path pdfOutPath) {
    OutputMode resolvedOutputMode = resolvedOutputMode(outputMode);
    if (pdfOutPath != null && resolvedOutputMode == OutputMode.CSV) {
      throw CliArgumentValueParser.unsupportedOutputSelection(
          ProtocolOptions.Presentation.OUTPUT,
          "Unsupported output mode for "
              + ProtocolOptions.Presentation.OUTPUT
              + ": csv. When "
              + ProtocolOptions.Presentation.PDF_OUT
              + " is selected, accepted stdout modes are json or text.");
    }
    return new CliReportOutput(resolvedOutputMode, pdfOutPath);
  }

  static Path requirePdfOutPath(
      @Nullable Path currentPdfOutPath, ListIterator<String> argumentIterator) {
    if (currentPdfOutPath != null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.Presentation.PDF_OUT,
          "Duplicate argument: " + ProtocolOptions.Presentation.PDF_OUT);
    }
    return CliOptionValues.requirePathOptionValue(
        argumentIterator, ProtocolOptions.Presentation.PDF_OUT);
  }

  static List<OutputMode> supportedOutputModes(OutputMode... outputModes) {
    return List.of(outputModes);
  }

  static PostingPageCursor postingPageCursor(String wireValue) {
    try {
      return PostingPageCursor.fromWireValue(wireValue);
    } catch (IllegalArgumentException exception) {
      throw new CliArgumentsException(
          ContractErrors.Descriptor.INVALID_PAGE_CURSOR.code(),
          ProtocolOptions.ReportQuery.CURSOR,
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
          ProtocolOptions.ReportQuery.CURSOR,
          Objects.requireNonNullElse(exception.getMessage(), "Unsupported account page cursor."),
          CliOperationText.listAccountsCursorRepairHint(),
          exception);
    }
  }

  static AccountLedgerPageCursor accountLedgerPageCursor(String wireValue) {
    try {
      return AccountLedgerPageCursor.fromWireValue(wireValue);
    } catch (IllegalArgumentException exception) {
      throw new CliArgumentsException(
          ContractErrors.Descriptor.INVALID_PAGE_CURSOR.code(),
          ProtocolOptions.ReportQuery.CURSOR,
          Objects.requireNonNullElse(
              exception.getMessage(), "Unsupported account ledger page cursor."),
          "Pass the nextCursor returned by "
              + OperationId.ACCOUNT_LEDGER.wireName()
              + " unchanged, or omit "
              + ProtocolOptions.ReportQuery.CURSOR
              + " to start at the first ledger row.",
          exception);
    }
  }

  static TaxRegistrationPageCursor taxRegistrationPageCursor(String wireValue) {
    try {
      return TaxRegistrationPageCursor.fromWireValue(wireValue);
    } catch (IllegalArgumentException exception) {
      throw new CliArgumentsException(
          ContractErrors.Descriptor.INVALID_PAGE_CURSOR.code(),
          ProtocolOptions.ReportQuery.CURSOR,
          Objects.requireNonNullElse(
              exception.getMessage(), "Unsupported tax registration page cursor."),
          CliOperationText.listTaxRegistrationsCursorRepairHint(),
          exception);
    }
  }

  private static String unsupportedOutputModeMessage(
      String rawOutputMode, List<OutputMode> supportedModes) {
    return "Unsupported output mode for "
        + ProtocolOptions.Presentation.OUTPUT
        + ": "
        + rawOutputMode
        + ". Accepted values: "
        + supportedModes.stream().map(OutputMode::wireValue).collect(Collectors.joining(", "))
        + ".";
  }
}
