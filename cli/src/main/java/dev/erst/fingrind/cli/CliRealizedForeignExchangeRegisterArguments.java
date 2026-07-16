package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterQuery;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Parses the realized foreign-exchange register report surface. */
final class CliRealizedForeignExchangeRegisterArguments {
  private static final CliBookArgumentParser.CommandArgumentSpec ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(ProtocolOptions.Presentation.OUTPUT, ProtocolOptions.Presentation.PDF_OUT),
          List.of());

  private CliRealizedForeignExchangeRegisterArguments() {}

  static CliCommand parseRealizedForeignExchangeRegisterCommand(List<String> arguments) {
    var parsed = CliBookArgumentParser.parseBookAndCommandArguments(arguments, ARGUMENTS);
    @Nullable OutputMode outputMode = null;
    @Nullable Path pdfOut = null;
    ListIterator<String> iterator = parsed.commandArguments().listIterator();
    while (iterator.hasNext()) {
      String option = iterator.next();
      if (ProtocolOptions.Presentation.OUTPUT.equals(option)) {
        outputMode = CliReportOptionArguments.requireReportOutputMode(outputMode, iterator);
      } else {
        pdfOut = CliOptionModes.requirePdfOutPath(pdfOut, iterator);
      }
    }
    return new RealizedForeignExchangeRegister(
        parsed.bookAccess(),
        new RealizedForeignExchangeRegisterQuery(),
        CliOptionModes.resolvedReportOutput(outputMode, pdfOut));
  }
}
