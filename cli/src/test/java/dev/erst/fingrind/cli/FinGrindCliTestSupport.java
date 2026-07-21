package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolBookAccessOptions;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/** Thin compatibility base that now composes smaller CLI fixture and workflow support classes. */
class FinGrindCliTestSupport extends CliWorkflowDoubleSupport {
  private static final String TEST_ATTESTATION_PRINCIPAL_ID =
      "10213243-5465-7687-98a9-babcbddceeff";

  protected static String[] jsonArguments(String... arguments) {
    if (arguments.length == 0) {
      return arguments;
    }
    List<String> normalizedArguments = new ArrayList<>(List.of(attestedArguments(arguments)));
    if (normalizedArguments.contains("--output")
        || ProtocolCatalog.findByToken(normalizedArguments.getFirst())
            .filter(operation -> !operation.outputModes().isEmpty())
            .isEmpty()) {
      return normalizedArguments.toArray(String[]::new);
    }
    normalizedArguments.addAll(List.of("--output", "json"));
    return normalizedArguments.toArray(String[]::new);
  }

  /**
   * Supplies the founder credential for fixture commands that mutate an already opened test book.
   *
   * <p>The production CLI intentionally has no ambient credential. Tests that exercise a real
   * mutation must make the same credential choice as a caller, even when they assert the default
   * text output instead of JSON.
   */
  protected static String[] attestedArguments(String... arguments) {
    if (arguments.length == 0) {
      return arguments;
    }
    List<String> normalizedArguments = new ArrayList<>(List.of(arguments));
    appendFixtureAttestationCredentials(normalizedArguments);
    return normalizedArguments.toArray(String[]::new);
  }

  private static void appendFixtureAttestationCredentials(List<String> arguments) {
    if ("open-book".equals(arguments.getFirst())
        || arguments.contains(ProtocolOptions.Attestation.PRINCIPAL_ID)) {
      return;
    }
    int bookFileIndex = arguments.indexOf(ProtocolBookAccessOptions.BOOK_FILE);
    if (bookFileIndex < 0 || bookFileIndex + 1 >= arguments.size()) {
      return;
    }
    Path bookFile = Path.of(arguments.get(bookFileIndex + 1));
    Path absoluteBookFile = bookFile.toAbsolutePath();
    String name = absoluteBookFile.getFileName().toString();
    arguments.addAll(
        List.of(
            ProtocolOptions.Attestation.PRINCIPAL_ID,
            TEST_ATTESTATION_PRINCIPAL_ID,
            ProtocolOptions.Attestation.KEY_FILE,
            absoluteBookFile.resolveSibling(name + ".founder.fgatk").toString(),
            ProtocolOptions.Attestation.PASSPHRASE_FILE,
            absoluteBookFile.resolveSibling(name + ".founder-passphrase").toString()));
  }

  protected static FinGrindCli cli(InputStream inputStream, PrintStream outputStream, Clock clock) {
    return FinGrindCli.standard(inputStream, outputStream, outputStream, clock);
  }

  protected static FinGrindCli cli(
      InputStream inputStream,
      PrintStream outputStream,
      PrintStream diagnosticsStream,
      Clock clock) {
    return FinGrindCli.standard(inputStream, outputStream, diagnosticsStream, clock);
  }

  protected static FinGrindCli cli(
      InputStream inputStream,
      PrintStream outputStream,
      Clock clock,
      CliBookWorkflow bookWorkflow) {
    return new FinGrindCli(
        inputStream, outputStream, outputStream, clock, bookWorkflow, bookWorkflow, bookWorkflow);
  }

  protected static FinGrindCli cli(
      InputStream inputStream,
      PrintStream outputStream,
      Clock clock,
      CliBookPassphraseResolver.Terminal terminal) {
    return FinGrindCli.withTerminal(inputStream, outputStream, outputStream, clock, terminal);
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
}
