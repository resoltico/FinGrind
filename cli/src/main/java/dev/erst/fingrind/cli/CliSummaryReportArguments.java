package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
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
              ProtocolOptions.EFFECTIVE_DATE_TO, ProtocolOptions.OUTPUT, ProtocolOptions.PDF_OUT),
          List.of());
  private static final CliBookArgumentParser.CommandArgumentSpec PERIOD_SUMMARY_ARGUMENTS =
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
    @Nullable OutputMode outputMode = null;
    @Nullable Path pdfOutPath = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
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
      pdfOutPath = CliArgumentValueParser.requirePdfOutPath(pdfOutPath, argumentIterator);
    }
    LocalDate resolvedEffectiveDateTo = effectiveDateTo;
    return new TrialBalance(
        parsedArguments.bookAccess(),
        CliArgumentValueParser.requireValidArgument(
            ProtocolOptions.EFFECTIVE_DATE_TO,
            () -> new TrialBalanceQuery(Optional.ofNullable(resolvedEffectiveDateTo))),
        CliArgumentValueParser.resolvedReportOutput(outputMode, pdfOutPath));
  }

  static CliCommand parsePeriodSummaryCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, PERIOD_SUMMARY_ARGUMENTS);
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
      pdfOutPath = CliArgumentValueParser.requirePdfOutPath(pdfOutPath, argumentIterator);
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
    return new PeriodSummary(
        parsedArguments.bookAccess(),
        CliArgumentValueParser.requireValidArgument(
            ProtocolOptions.EFFECTIVE_DATE_FROM,
            () -> new PeriodSummaryQuery(requiredEffectiveDateFrom, requiredEffectiveDateTo)),
        CliArgumentValueParser.resolvedReportOutput(outputMode, pdfOutPath));
  }
}
