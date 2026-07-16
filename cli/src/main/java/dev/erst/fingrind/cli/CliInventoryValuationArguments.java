package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.InventoryValuationQuery;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Parses the exact inventory-valuation report surface. */
final class CliInventoryValuationArguments {
  private static final CliBookArgumentParser.CommandArgumentSpec ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.DateRange.AS_OF,
              ProtocolOptions.Presentation.OUTPUT,
              ProtocolOptions.Presentation.PDF_OUT),
          List.of(ProtocolOptions.DateRange.MOVEMENTS));

  private CliInventoryValuationArguments() {}

  static CliCommand parseInventoryValuationCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, ARGUMENTS);
    @Nullable LocalDate effectiveDateAsOf = null;
    boolean includeMovements = false;
    @Nullable OutputMode outputMode = null;
    @Nullable Path pdfOutPath = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.DateRange.AS_OF.equals(argument)) {
        effectiveDateAsOf =
            CliReportOptionArguments.requireDateOption(
                effectiveDateAsOf, argumentIterator, ProtocolOptions.DateRange.AS_OF);
        continue;
      }
      if (ProtocolOptions.DateRange.MOVEMENTS.equals(argument)) {
        if (includeMovements) {
          throw CliArgumentValueParser.invalid(
              ProtocolOptions.DateRange.MOVEMENTS,
              "Duplicate argument: " + ProtocolOptions.DateRange.MOVEMENTS);
        }
        includeMovements = true;
        continue;
      }
      if (ProtocolOptions.Presentation.OUTPUT.equals(argument)) {
        outputMode = CliReportOptionArguments.requireReportOutputMode(outputMode, argumentIterator);
        continue;
      }
      pdfOutPath = CliOptionModes.requirePdfOutPath(pdfOutPath, argumentIterator);
    }
    return new InventoryValuation(
        parsedArguments.bookAccess(),
        new InventoryValuationQuery(Optional.ofNullable(effectiveDateAsOf), includeMovements),
        CliOptionModes.resolvedReportOutput(outputMode, pdfOutPath));
  }
}
