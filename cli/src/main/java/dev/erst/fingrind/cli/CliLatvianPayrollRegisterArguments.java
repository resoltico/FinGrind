package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterQuery;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Parses the book-wide Latvian payroll register report surface. */
final class CliLatvianPayrollRegisterArguments {
  private static final CliBookArgumentParser.CommandArgumentSpec ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(ProtocolOptions.Presentation.OUTPUT, ProtocolOptions.Presentation.PDF_OUT),
          List.of());

  private CliLatvianPayrollRegisterArguments() {}

  static CliCommand parseLatvianPayrollRegisterCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, ARGUMENTS);
    @Nullable OutputMode outputMode = null;
    @Nullable Path pdfOutPath = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.Presentation.OUTPUT.equals(argument)) {
        outputMode = CliReportOptionArguments.requireReportOutputMode(outputMode, argumentIterator);
        continue;
      }
      pdfOutPath = CliOptionModes.requirePdfOutPath(pdfOutPath, argumentIterator);
    }
    return new LatvianPayrollRegister(
        parsedArguments.bookAccess(),
        new LatvianPayrollRegisterQuery(),
        CliOptionModes.resolvedReportOutput(outputMode, pdfOutPath));
  }
}
