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
          List.of(ProtocolOptions.AS_OF, ProtocolOptions.OUTPUT, ProtocolOptions.PDF_OUT),
          List.of(ProtocolOptions.MOVEMENTS));

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
      if (ProtocolOptions.AS_OF.equals(argument)) {
        effectiveDateAsOf =
            CliReportArguments.requireDateOption(
                effectiveDateAsOf, argumentIterator, ProtocolOptions.AS_OF);
        continue;
      }
      if (ProtocolOptions.MOVEMENTS.equals(argument)) {
        if (includeMovements) {
          throw CliArgumentValueParser.invalid(
              ProtocolOptions.MOVEMENTS, "Duplicate argument: " + ProtocolOptions.MOVEMENTS);
        }
        includeMovements = true;
        continue;
      }
      if (ProtocolOptions.OUTPUT.equals(argument)) {
        outputMode = CliReportArguments.requireReportOutputMode(outputMode, argumentIterator);
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
