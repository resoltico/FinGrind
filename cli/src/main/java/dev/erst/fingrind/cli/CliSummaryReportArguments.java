package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
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
              ProtocolOptions.EFFECTIVE_DATE_AS_OF,
              ProtocolOptions.POSTING_COVERAGE,
              ProtocolOptions.OUTPUT,
              ProtocolOptions.PDF_OUT),
          List.of());
  private static final CliBookArgumentParser.CommandArgumentSpec PERIOD_SUMMARY_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.EFFECTIVE_DATE_FROM,
              ProtocolOptions.EFFECTIVE_DATE_TO,
              ProtocolOptions.POSTING_COVERAGE,
              ProtocolOptions.OUTPUT,
              ProtocolOptions.PDF_OUT),
          List.of());
  private static final CliBookArgumentParser.CommandArgumentSpec FINANCIAL_POSITION_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.EFFECTIVE_DATE_AS_OF,
              ProtocolOptions.OUTPUT,
              ProtocolOptions.PDF_OUT),
          List.of());
  private static final CliBookArgumentParser.CommandArgumentSpec INCOME_STATEMENT_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.EFFECTIVE_DATE_FROM,
              ProtocolOptions.EFFECTIVE_DATE_TO,
              ProtocolOptions.OUTPUT,
              ProtocolOptions.PDF_OUT),
          List.of());
  private static final CliBookArgumentParser.CommandArgumentSpec CHANGES_IN_EQUITY_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.EFFECTIVE_DATE_FROM,
              ProtocolOptions.EFFECTIVE_DATE_TO,
              ProtocolOptions.OUTPUT,
              ProtocolOptions.PDF_OUT),
          List.of());

  private CliSummaryReportArguments() {}

  static CliCommand parseTrialBalanceCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, TRIAL_BALANCE_ARGUMENTS);
    @Nullable LocalDate effectiveDateTo = null;
    @Nullable PostingCoverage postingCoverage = null;
    @Nullable OutputMode outputMode = null;
    @Nullable Path pdfOutPath = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.EFFECTIVE_DATE_AS_OF.equals(argument)) {
        effectiveDateTo =
            CliReportArguments.requireDateOption(
                effectiveDateTo, argumentIterator, ProtocolOptions.EFFECTIVE_DATE_AS_OF);
        continue;
      }
      if (ProtocolOptions.POSTING_COVERAGE.equals(argument)) {
        postingCoverage = CliOptionModes.requirePostingCoverage(postingCoverage, argumentIterator);
        continue;
      }
      if (ProtocolOptions.OUTPUT.equals(argument)) {
        outputMode = CliReportArguments.requireReportOutputMode(outputMode, argumentIterator);
        continue;
      }
      pdfOutPath = CliOptionModes.requirePdfOutPath(pdfOutPath, argumentIterator);
    }
    LocalDate resolvedEffectiveDateTo = effectiveDateTo;
    PostingCoverage resolvedPostingCoverage =
        postingCoverage == null ? PostingCoverage.ALL_POSTING_KINDS : postingCoverage;
    return new TrialBalance(
        parsedArguments.bookAccess(),
        CliArgumentValueParser.requireValidArgument(
            ProtocolOptions.EFFECTIVE_DATE_AS_OF,
            () ->
                new TrialBalanceQuery(
                    Optional.ofNullable(resolvedEffectiveDateTo), resolvedPostingCoverage)),
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
      if (ProtocolOptions.EFFECTIVE_DATE_FROM.equals(argument)) {
        effectiveDateFrom =
            CliReportArguments.requireDateOption(
                effectiveDateFrom, argumentIterator, ProtocolOptions.EFFECTIVE_DATE_FROM);
        continue;
      }
      if (ProtocolOptions.EFFECTIVE_DATE_TO.equals(argument)) {
        effectiveDateTo =
            CliReportArguments.requireDateOption(
                effectiveDateTo, argumentIterator, ProtocolOptions.EFFECTIVE_DATE_TO);
        continue;
      }
      if (ProtocolOptions.POSTING_COVERAGE.equals(argument)) {
        postingCoverage = CliOptionModes.requirePostingCoverage(postingCoverage, argumentIterator);
        continue;
      }
      if (ProtocolOptions.OUTPUT.equals(argument)) {
        outputMode = CliReportArguments.requireReportOutputMode(outputMode, argumentIterator);
        continue;
      }
      pdfOutPath = CliOptionModes.requirePdfOutPath(pdfOutPath, argumentIterator);
    }
    if (effectiveDateFrom == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.EFFECTIVE_DATE_FROM,
          "A " + ProtocolOptions.EFFECTIVE_DATE_FROM + " argument is required.");
    }
    if (effectiveDateTo == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.EFFECTIVE_DATE_TO,
          "A " + ProtocolOptions.EFFECTIVE_DATE_TO + " argument is required.");
    }
    LocalDate requiredEffectiveDateFrom = effectiveDateFrom;
    LocalDate requiredEffectiveDateTo = effectiveDateTo;
    PostingCoverage resolvedPostingCoverage =
        postingCoverage == null ? PostingCoverage.ALL_POSTING_KINDS : postingCoverage;
    CliArgumentValueParser.requireOrderedDateRange(
        requiredEffectiveDateFrom,
        requiredEffectiveDateTo,
        ProtocolOptions.EFFECTIVE_DATE_FROM,
        ProtocolOptions.EFFECTIVE_DATE_TO);
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
    @Nullable OutputMode outputMode = null;
    @Nullable Path pdfOutPath = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.EFFECTIVE_DATE_AS_OF.equals(argument)) {
        effectiveDateTo =
            CliReportArguments.requireDateOption(
                effectiveDateTo, argumentIterator, ProtocolOptions.EFFECTIVE_DATE_AS_OF);
        continue;
      }
      if (ProtocolOptions.OUTPUT.equals(argument)) {
        outputMode = CliReportArguments.requireReportOutputMode(outputMode, argumentIterator);
        continue;
      }
      pdfOutPath = CliOptionModes.requirePdfOutPath(pdfOutPath, argumentIterator);
    }
    LocalDate resolvedEffectiveDateTo = effectiveDateTo;
    return new FinancialPosition(
        parsedArguments.bookAccess(),
        new FinancialPositionQuery(Optional.ofNullable(resolvedEffectiveDateTo)),
        CliOptionModes.resolvedReportOutput(outputMode, pdfOutPath));
  }

  static CliCommand parseIncomeStatementCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, INCOME_STATEMENT_ARGUMENTS);
    return boundedPeriodReport(
        parsedArguments,
        (from, to, output) ->
            new IncomeStatement(
                parsedArguments.bookAccess(), new IncomeStatementQuery(from, to), output));
  }

  static CliCommand parseChangesInEquityCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, CHANGES_IN_EQUITY_ARGUMENTS);
    return boundedPeriodReport(
        parsedArguments,
        (from, to, output) ->
            new ChangesInEquity(
                parsedArguments.bookAccess(), new ChangesInEquityQuery(from, to), output));
  }

  private static CliCommand boundedPeriodReport(
      CliBookArgumentParser.ParsedBookArguments parsedArguments,
      BoundedPeriodReportFactory commandFactory) {
    @Nullable LocalDate effectiveDateFrom = null;
    @Nullable LocalDate effectiveDateTo = null;
    @Nullable OutputMode outputMode = null;
    @Nullable Path pdfOutPath = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.EFFECTIVE_DATE_FROM.equals(argument)) {
        effectiveDateFrom =
            CliReportArguments.requireDateOption(
                effectiveDateFrom, argumentIterator, ProtocolOptions.EFFECTIVE_DATE_FROM);
        continue;
      }
      if (ProtocolOptions.EFFECTIVE_DATE_TO.equals(argument)) {
        effectiveDateTo =
            CliReportArguments.requireDateOption(
                effectiveDateTo, argumentIterator, ProtocolOptions.EFFECTIVE_DATE_TO);
        continue;
      }
      if (ProtocolOptions.OUTPUT.equals(argument)) {
        outputMode = CliReportArguments.requireReportOutputMode(outputMode, argumentIterator);
        continue;
      }
      pdfOutPath = CliOptionModes.requirePdfOutPath(pdfOutPath, argumentIterator);
    }
    if (effectiveDateFrom == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.EFFECTIVE_DATE_FROM,
          "A " + ProtocolOptions.EFFECTIVE_DATE_FROM + " argument is required.");
    }
    if (effectiveDateTo == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.EFFECTIVE_DATE_TO,
          "A " + ProtocolOptions.EFFECTIVE_DATE_TO + " argument is required.");
    }
    LocalDate requiredEffectiveDateFrom = effectiveDateFrom;
    LocalDate requiredEffectiveDateTo = effectiveDateTo;
    CliArgumentValueParser.requireOrderedDateRange(
        requiredEffectiveDateFrom,
        requiredEffectiveDateTo,
        ProtocolOptions.EFFECTIVE_DATE_FROM,
        ProtocolOptions.EFFECTIVE_DATE_TO);
    CliCommand.ReportOutput resolvedOutput =
        CliOptionModes.resolvedReportOutput(outputMode, pdfOutPath);
    return commandFactory.create(
        requiredEffectiveDateFrom, requiredEffectiveDateTo, resolvedOutput);
  }

  /** Creates one bounded-period report command from validated effective-date arguments. */
  @FunctionalInterface
  private interface BoundedPeriodReportFactory {
    /**
     * Creates a summary report command for one inclusive effective-date window.
     *
     * @param effectiveDateFrom lower effective-date bound
     * @param effectiveDateTo upper effective-date bound
     * @param output selected operator output destination
     * @return report command ready for execution
     */
    CliCommand create(
        LocalDate effectiveDateFrom, LocalDate effectiveDateTo, CliCommand.ReportOutput output);
  }
}
