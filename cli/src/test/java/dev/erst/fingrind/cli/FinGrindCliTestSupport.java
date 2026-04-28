package dev.erst.fingrind.cli;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.jspecify.annotations.NullUnmarked;

/** Thin compatibility base that now composes smaller CLI fixture and workflow support classes. */
@NullUnmarked
class FinGrindCliTestSupport extends CliWorkflowDoubleSupport {
  protected static FinGrindCli cli(InputStream inputStream, PrintStream outputStream, Clock clock) {
    return FinGrindCli.standard(inputStream, outputStream, diagnosticsStream(), clock);
  }

  protected static FinGrindCli cli(
      InputStream inputStream,
      PrintStream outputStream,
      Clock clock,
      CliBookWorkflow bookWorkflow) {
    return new FinGrindCli(inputStream, outputStream, diagnosticsStream(), clock, bookWorkflow);
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
    return new FinGrindCli(inputStream, outputStream, diagnosticsStream, clock, bookWorkflow);
  }

  private static PrintStream diagnosticsStream() {
    return new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8);
  }
}
