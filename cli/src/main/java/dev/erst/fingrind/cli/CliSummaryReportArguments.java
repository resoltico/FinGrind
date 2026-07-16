package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.core.ComparativeSelection;
import dev.erst.fingrind.core.PostingCoverage;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Parses book-wide summary report commands. */
final class CliSummaryReportArguments {
  private static final CliBookArgumentParser.CommandArgumentSpec TRIAL_BALANCE_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.DateRange.EFFECTIVE_DATE_AS_OF,
              ProtocolOptions.ReportQuery.COMPARATIVE,
              ProtocolOptions.ReportQuery.POSTING_COVERAGE,
              ProtocolOptions.Presentation.OUTPUT,
              ProtocolOptions.Presentation.PDF_OUT),
          List.of());
  private static final CliBookArgumentParser.CommandArgumentSpec PERIOD_SUMMARY_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.DateRange.PERIOD_START,
              ProtocolOptions.DateRange.PERIOD_END,
              ProtocolOptions.ReportQuery.POSTING_COVERAGE,
              ProtocolOptions.Presentation.OUTPUT,
              ProtocolOptions.Presentation.PDF_OUT),
          List.of());
  private static final CliBookArgumentParser.CommandArgumentSpec FINANCIAL_POSITION_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.DateRange.EFFECTIVE_DATE_AS_OF,
              ProtocolOptions.ReportQuery.COMPARATIVE,
              ProtocolOptions.Presentation.OUTPUT,
              ProtocolOptions.Presentation.PDF_OUT),
          List.of());
  private static final CliBookArgumentParser.CommandArgumentSpec INCOME_STATEMENT_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.DateRange.PERIOD_START,
              ProtocolOptions.DateRange.PERIOD_END,
              ProtocolOptions.ReportQuery.COMPARATIVE,
              ProtocolOptions.Presentation.OUTPUT,
              ProtocolOptions.Presentation.PDF_OUT),
          List.of());
  private static final CliBookArgumentParser.CommandArgumentSpec CHANGES_IN_EQUITY_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.DateRange.PERIOD_START,
              ProtocolOptions.DateRange.PERIOD_END,
              ProtocolOptions.ReportQuery.COMPARATIVE,
              ProtocolOptions.Presentation.OUTPUT,
              ProtocolOptions.Presentation.PDF_OUT),
          List.of());
  private static final CliBookArgumentParser.CommandArgumentSpec CASH_FLOW_STATEMENT_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.DateRange.PERIOD_START,
              ProtocolOptions.DateRange.PERIOD_END,
              ProtocolOptions.ReportQuery.COMPARATIVE,
              ProtocolOptions.Presentation.OUTPUT,
              ProtocolOptions.Presentation.PDF_OUT),
          List.of());

  private CliSummaryReportArguments() {}

  static CliCommand parseTrialBalanceCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, TRIAL_BALANCE_ARGUMENTS);
    @Nullable LocalDate effectiveDateTo = null;
    @Nullable PostingCoverage postingCoverage = null;
    @Nullable ComparativeSelection comparativeSelection = null;
    @Nullable OutputMode outputMode = null;
    @Nullable Path pdfOutPath = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.DateRange.EFFECTIVE_DATE_AS_OF.equals(argument)) {
        effectiveDateTo =
            CliReportOptionArguments.requireDateOption(
                effectiveDateTo, argumentIterator, ProtocolOptions.DateRange.EFFECTIVE_DATE_AS_OF);
        continue;
      }
      if (ProtocolOptions.ReportQuery.POSTING_COVERAGE.equals(argument)) {
        postingCoverage = CliOptionModes.requirePostingCoverage(postingCoverage, argumentIterator);
        continue;
      }
      if (ProtocolOptions.ReportQuery.COMPARATIVE.equals(argument)) {
        comparativeSelection =
            CliReportOptionArguments.requireComparativeSelection(
                comparativeSelection,
                argumentIterator,
                CliReportOptionArguments.ComparativeArgumentShape.AS_OF);
        continue;
      }
      if (ProtocolOptions.Presentation.OUTPUT.equals(argument)) {
        outputMode = CliReportOptionArguments.requireReportOutputMode(outputMode, argumentIterator);
        continue;
      }
      pdfOutPath = CliOptionModes.requirePdfOutPath(pdfOutPath, argumentIterator);
    }
    LocalDate resolvedEffectiveDateTo = effectiveDateTo;
    PostingCoverage resolvedPostingCoverage =
        postingCoverage == null ? PostingCoverage.ALL_POSTING_KINDS : postingCoverage;
    ComparativeSelection resolvedComparativeSelection =
        comparativeSelection == null ? ComparativeSelection.none() : comparativeSelection;
    return new TrialBalance(
        parsedArguments.bookAccess(),
        CliArgumentValueParser.requireValidArgument(
            ProtocolOptions.DateRange.EFFECTIVE_DATE_AS_OF,
            () ->
                new TrialBalanceQuery(
                    Optional.ofNullable(resolvedEffectiveDateTo),
                    resolvedPostingCoverage,
                    resolvedComparativeSelection)),
        CliOptionModes.resolvedReportOutput(outputMode, pdfOutPath));
  }

  static CliCommand parsePeriodSummaryCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, PERIOD_SUMMARY_ARGUMENTS);
    @Nullable LocalDate effectiveDateFrom = null;
    @Nullable LocalDate effectiveDateTo = null;
    @Nullable PostingCoverage postingCoverage = null;
    @Nullable OutputMode outputMode = null;
    @Nullable Path pdfOutPath = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.DateRange.PERIOD_START.equals(argument)) {
        effectiveDateFrom =
            CliReportOptionArguments.requireDateOption(
                effectiveDateFrom, argumentIterator, ProtocolOptions.DateRange.PERIOD_START);
        continue;
      }
      if (ProtocolOptions.DateRange.PERIOD_END.equals(argument)) {
        effectiveDateTo =
            CliReportOptionArguments.requireDateOption(
                effectiveDateTo, argumentIterator, ProtocolOptions.DateRange.PERIOD_END);
        continue;
      }
      if (ProtocolOptions.ReportQuery.POSTING_COVERAGE.equals(argument)) {
        postingCoverage = CliOptionModes.requirePostingCoverage(postingCoverage, argumentIterator);
        continue;
      }
      if (ProtocolOptions.Presentation.OUTPUT.equals(argument)) {
        outputMode = CliReportOptionArguments.requireReportOutputMode(outputMode, argumentIterator);
        continue;
      }
      pdfOutPath = CliOptionModes.requirePdfOutPath(pdfOutPath, argumentIterator);
    }
    if (effectiveDateFrom == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.DateRange.PERIOD_START,
          "A " + ProtocolOptions.DateRange.PERIOD_START + " argument is required.");
    }
    if (effectiveDateTo == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.DateRange.PERIOD_END,
          "A " + ProtocolOptions.DateRange.PERIOD_END + " argument is required.");
    }
    LocalDate requiredEffectiveDateFrom = effectiveDateFrom;
    LocalDate requiredEffectiveDateTo = effectiveDateTo;
    PostingCoverage resolvedPostingCoverage =
        postingCoverage == null ? PostingCoverage.ALL_POSTING_KINDS : postingCoverage;
    CliArgumentValueParser.requireOrderedDateRange(
        requiredEffectiveDateFrom,
        requiredEffectiveDateTo,
        ProtocolOptions.DateRange.PERIOD_START,
        ProtocolOptions.DateRange.PERIOD_END);
    return new PeriodSummary(
        parsedArguments.bookAccess(),
        new PeriodSummaryQuery(
            requiredEffectiveDateFrom, requiredEffectiveDateTo, resolvedPostingCoverage),
        CliOptionModes.resolvedReportOutput(outputMode, pdfOutPath));
  }

  static CliCommand parseFinancialPositionCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, FINANCIAL_POSITION_ARGUMENTS);
    @Nullable LocalDate effectiveDateTo = null;
    @Nullable ComparativeSelection comparativeSelection = null;
    @Nullable OutputMode outputMode = null;
    @Nullable Path pdfOutPath = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.DateRange.EFFECTIVE_DATE_AS_OF.equals(argument)) {
        effectiveDateTo =
            CliReportOptionArguments.requireDateOption(
                effectiveDateTo, argumentIterator, ProtocolOptions.DateRange.EFFECTIVE_DATE_AS_OF);
        continue;
      }
      if (ProtocolOptions.ReportQuery.COMPARATIVE.equals(argument)) {
        comparativeSelection =
            CliReportOptionArguments.requireComparativeSelection(
                comparativeSelection,
                argumentIterator,
                CliReportOptionArguments.ComparativeArgumentShape.AS_OF);
        continue;
      }
      if (ProtocolOptions.Presentation.OUTPUT.equals(argument)) {
        outputMode = CliReportOptionArguments.requireReportOutputMode(outputMode, argumentIterator);
        continue;
      }
      pdfOutPath = CliOptionModes.requirePdfOutPath(pdfOutPath, argumentIterator);
    }
    LocalDate resolvedEffectiveDateTo = effectiveDateTo;
    ComparativeSelection resolvedComparativeSelection =
        comparativeSelection == null ? ComparativeSelection.none() : comparativeSelection;
    return new FinancialPosition(
        parsedArguments.bookAccess(),
        new FinancialPositionQuery(
            Optional.ofNullable(resolvedEffectiveDateTo), resolvedComparativeSelection),
        CliOptionModes.resolvedReportOutput(outputMode, pdfOutPath));
  }

  static CliCommand parseIncomeStatementCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, INCOME_STATEMENT_ARGUMENTS);
    return boundedPeriodReport(
        parsedArguments,
        (from, to, comparativeSelection, output) ->
            new IncomeStatement(
                parsedArguments.bookAccess(),
                new IncomeStatementQuery(from, to, comparativeSelection),
                output));
  }

  static CliCommand parseChangesInEquityCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, CHANGES_IN_EQUITY_ARGUMENTS);
    return boundedPeriodReport(
        parsedArguments,
        (from, to, comparativeSelection, output) ->
            new ChangesInEquity(
                parsedArguments.bookAccess(),
                new ChangesInEquityQuery(from, to, comparativeSelection),
                output));
  }

  static CliCommand parseCashFlowStatementCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(
            arguments, CASH_FLOW_STATEMENT_ARGUMENTS);
    return boundedPeriodReport(
        parsedArguments,
        (from, to, comparativeSelection, output) ->
            new CashFlowStatement(
                parsedArguments.bookAccess(),
                new CashFlowStatementQuery(from, to, comparativeSelection),
                output));
  }

  private static CliCommand boundedPeriodReport(
      CliBookArgumentParser.ParsedBookArguments parsedArguments,
      BoundedPeriodReportFactory commandFactory) {
    @Nullable LocalDate effectiveDateFrom = null;
    @Nullable LocalDate effectiveDateTo = null;
    @Nullable ComparativeSelection comparativeSelection = null;
    @Nullable OutputMode outputMode = null;
    @Nullable Path pdfOutPath = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.DateRange.PERIOD_START.equals(argument)) {
        effectiveDateFrom =
            CliReportOptionArguments.requireDateOption(
                effectiveDateFrom, argumentIterator, ProtocolOptions.DateRange.PERIOD_START);
        continue;
      }
      if (ProtocolOptions.DateRange.PERIOD_END.equals(argument)) {
        effectiveDateTo =
            CliReportOptionArguments.requireDateOption(
                effectiveDateTo, argumentIterator, ProtocolOptions.DateRange.PERIOD_END);
        continue;
      }
      if (ProtocolOptions.ReportQuery.COMPARATIVE.equals(argument)) {
        comparativeSelection =
            CliReportOptionArguments.requireComparativeSelection(
                comparativeSelection,
                argumentIterator,
                CliReportOptionArguments.ComparativeArgumentShape.PERIOD);
        continue;
      }
      if (ProtocolOptions.Presentation.OUTPUT.equals(argument)) {
        outputMode = CliReportOptionArguments.requireReportOutputMode(outputMode, argumentIterator);
        continue;
      }
      pdfOutPath = CliOptionModes.requirePdfOutPath(pdfOutPath, argumentIterator);
    }
    if (effectiveDateFrom == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.DateRange.PERIOD_START,
          "A " + ProtocolOptions.DateRange.PERIOD_START + " argument is required.");
    }
    if (effectiveDateTo == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.DateRange.PERIOD_END,
          "A " + ProtocolOptions.DateRange.PERIOD_END + " argument is required.");
    }
    LocalDate requiredEffectiveDateFrom = effectiveDateFrom;
    LocalDate requiredEffectiveDateTo = effectiveDateTo;
    CliArgumentValueParser.requireOrderedDateRange(
        requiredEffectiveDateFrom,
        requiredEffectiveDateTo,
        ProtocolOptions.DateRange.PERIOD_START,
        ProtocolOptions.DateRange.PERIOD_END);
    CliReportOutput resolvedOutput = CliOptionModes.resolvedReportOutput(outputMode, pdfOutPath);
    ComparativeSelection resolvedComparativeSelection =
        comparativeSelection == null ? ComparativeSelection.none() : comparativeSelection;
    return commandFactory.create(
        requiredEffectiveDateFrom,
        requiredEffectiveDateTo,
        resolvedComparativeSelection,
        resolvedOutput);
  }

  /** Creates one bounded-period report command from validated effective-date arguments. */
  @FunctionalInterface
  private interface BoundedPeriodReportFactory {
    /**
     * Creates a summary report command for one inclusive effective-date window.
     *
     * @param effectiveDateFrom lower effective-date bound
     * @param effectiveDateTo upper effective-date bound
     * @param comparativeSelection comparative selection for the report surface
     * @param output selected operator output destination
     * @return report command ready for execution
     */
    CliCommand create(
        LocalDate effectiveDateFrom,
        LocalDate effectiveDateTo,
        ComparativeSelection comparativeSelection,
        CliReportOutput output);
  }
}
