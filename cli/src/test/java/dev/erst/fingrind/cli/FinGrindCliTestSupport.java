package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;

/** Thin compatibility base that now composes smaller CLI fixture and workflow support classes. */
class FinGrindCliTestSupport extends CliWorkflowDoubleSupport {
  protected static String[] jsonArguments(String... arguments) {
    if (arguments.length == 0) {
      return arguments;
    }
    for (String argument : arguments) {
      if ("--output".equals(argument)) {
        return arguments;
      }
    }
    if (ProtocolCatalog.findByToken(arguments[0])
        .filter(operation -> !operation.outputModes().isEmpty())
        .isEmpty()) {
      return arguments;
    }
    String[] jsonArguments = Arrays.copyOf(arguments, arguments.length + 2);
    jsonArguments[arguments.length] = "--output";
    jsonArguments[arguments.length + 1] = "json";
    return jsonArguments;
  }

  protected static FinGrindCli cli(InputStream inputStream, PrintStream outputStream, Clock clock) {
    return FinGrindCli.standard(inputStream, outputStream, diagnosticsStream(), clock);
  }

  protected static FinGrindCli cli(
      InputStream inputStream,
      PrintStream outputStream,
      Clock clock,
      CliBookWorkflow bookWorkflow) {
    return new FinGrindCli(
        inputStream,
        outputStream,
        diagnosticsStream(),
        clock,
        bookWorkflow,
        bookWorkflow,
        bookWorkflow);
  }

  protected static FinGrindCli cli(
      InputStream inputStream,
      PrintStream outputStream,
      Clock clock,
      CliBookPassphraseResolver.Terminal terminal) {
    return FinGrindCli.withTerminal(
        inputStream, outputStream, diagnosticsStream(), clock, terminal);
  }

  protected static FinGrindCli cli(
      InputStream inputStream,
      PrintStream outputStream,
      PrintStream diagnosticsStream,
      Clock clock,
      CliBookWorkflow bookWorkflow) {
    return new FinGrindCli(
        inputStream,
        outputStream,
        diagnosticsStream,
        clock,
        bookWorkflow,
        bookWorkflow,
        bookWorkflow);
  }

  private static PrintStream diagnosticsStream() {
    return new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8);
  }
}
