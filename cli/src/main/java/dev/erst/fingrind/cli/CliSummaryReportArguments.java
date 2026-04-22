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
  private CliSummaryReportArguments() {}

  static CliCommand parseTrialBalanceCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments);
    @Nullable LocalDate effectiveDateTo = null;
    @Nullable OutputMode outputMode = null;
    @Nullable Path pdfOutPath = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      switch (argument) {
        case ProtocolOptions.EFFECTIVE_DATE_TO ->
            effectiveDateTo =
                CliReportArguments.requireDateOption(
                    effectiveDateTo, argumentIterator, ProtocolOptions.EFFECTIVE_DATE_TO);
        case ProtocolOptions.OUTPUT ->
            outputMode = CliReportArguments.requireReportOutputMode(outputMode, argumentIterator);
        case ProtocolOptions.PDF_OUT ->
            pdfOutPath = CliArgumentValueParser.requirePdfOutPath(pdfOutPath, argumentIterator);
        default ->
            throw CliArgumentValueParser.invalid(argument, "Unsupported argument: " + argument);
      }
    }
    LocalDate resolvedEffectiveDateTo = effectiveDateTo;
    return new CliCommand.TrialBalance(
        parsedArguments.bookAccess(),
        CliArgumentValueParser.requireValidArgument(
            ProtocolOptions.EFFECTIVE_DATE_TO,
            () -> new TrialBalanceQuery(Optional.ofNullable(resolvedEffectiveDateTo))),
        CliArgumentValueParser.resolvedReportOutput(outputMode, pdfOutPath));
  }

  static CliCommand parsePeriodSummaryCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments);
    @Nullable LocalDate effectiveDateFrom = null;
    @Nullable LocalDate effectiveDateTo = null;
    @Nullable OutputMode outputMode = null;
    @Nullable Path pdfOutPath = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      switch (argument) {
        case ProtocolOptions.EFFECTIVE_DATE_FROM ->
            effectiveDateFrom =
                CliReportArguments.requireDateOption(
                    effectiveDateFrom, argumentIterator, ProtocolOptions.EFFECTIVE_DATE_FROM);
        case ProtocolOptions.EFFECTIVE_DATE_TO ->
            effectiveDateTo =
                CliReportArguments.requireDateOption(
                    effectiveDateTo, argumentIterator, ProtocolOptions.EFFECTIVE_DATE_TO);
        case ProtocolOptions.OUTPUT ->
            outputMode = CliReportArguments.requireReportOutputMode(outputMode, argumentIterator);
        case ProtocolOptions.PDF_OUT ->
            pdfOutPath = CliArgumentValueParser.requirePdfOutPath(pdfOutPath, argumentIterator);
        default ->
            throw CliArgumentValueParser.invalid(argument, "Unsupported argument: " + argument);
      }
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
    return new CliCommand.PeriodSummary(
        parsedArguments.bookAccess(),
        CliArgumentValueParser.requireValidArgument(
            ProtocolOptions.EFFECTIVE_DATE_FROM,
            () -> new PeriodSummaryQuery(requiredEffectiveDateFrom, requiredEffectiveDateTo)),
        CliArgumentValueParser.resolvedReportOutput(outputMode, pdfOutPath));
  }
}
