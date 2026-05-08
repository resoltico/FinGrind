package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.ContractFailureException;
import dev.erst.fingrind.contract.EnvironmentDescriptor;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.report.pdf.PdfReportService;
import dev.erst.fingrind.sqlite.SqliteRuntime;
import java.io.InputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.util.Objects;

/** Command dispatcher for the FinGrind agent-first CLI surface. */
final class FinGrindCli {
  static final String RUNTIME_DISTRIBUTION_PROPERTY = "fingrind.runtime.distribution";
  static final String DIRECT_JAVA_RUNTIME_DISTRIBUTION =
      ProtocolCatalog.directJavaRuntimeDistribution().wireValue();
  static final String SOURCE_CHECKOUT_RUNTIME_DISTRIBUTION =
      ProtocolCatalog.sourceCheckoutRuntimeDistribution().wireValue();
  static final String CONTAINER_RUNTIME_DISTRIBUTION =
      ProtocolCatalog.containerRuntimeDistribution().wireValue();
  static final String BUNDLE_RUNTIME_DISTRIBUTION =
      ProtocolCatalog.bundleRuntimeDistribution().wireValue();

  private final CliRequestReader requestReader;
  private final CliResponseWriter responseWriter;
  private final CliDiagnosticsWriter diagnosticsWriter;
  private final CliMetadata metadata;
  private final Clock clock;
  private final CliAdministrativeCommandExecutor administrativeCommandExecutor;
  private final CliDiscoveryCommandExecutor discoveryCommandExecutor;
  private final CliMutationCommandExecutor mutationCommandExecutor;
  private final CliQueryCommandExecutor queryCommandExecutor;
  private final CliReportCommandExecutor reportCommandExecutor;
  private final CliExecutionContext executionContext;

  static FinGrindCli standard(
      InputStream inputStream,
      PrintStream outputStream,
      PrintStream diagnosticsStream,
      Clock clock) {
    return new FinGrindCli(
        inputStream,
        outputStream,
        diagnosticsStream,
        clock,
        new SqliteCliBookWorkflow(
            clock,
            new CliBookPassphraseResolver(
                inputStream, CliBookPassphraseResolver.systemTerminal())));
  }

  static FinGrindCli withTerminal(
      InputStream inputStream,
      PrintStream outputStream,
      PrintStream diagnosticsStream,
      Clock clock,
      CliBookPassphraseResolver.Terminal terminal) {
    return new FinGrindCli(
        inputStream,
        outputStream,
        diagnosticsStream,
        clock,
        new SqliteCliBookWorkflow(
            clock,
            new CliBookPassphraseResolver(
                inputStream, Objects.requireNonNull(terminal, "terminal"))));
  }

  FinGrindCli(
      InputStream inputStream,
      PrintStream outputStream,
      PrintStream diagnosticsStream,
      Clock clock,
      CliBookWorkflow bookWorkflow) {
    this.requestReader = new CliRequestReader(inputStream);
    this.responseWriter = new CliResponseWriter(outputStream);
    this.diagnosticsWriter = new CliDiagnosticsWriter(diagnosticsStream);
    this.metadata = new CliMetadata();
    this.clock = Objects.requireNonNull(clock, "clock");
    CliBookWorkflow resolvedBookWorkflow = Objects.requireNonNull(bookWorkflow, "bookWorkflow");
    CliPdfReportExporter pdfExporter =
        new CliPdfReportExporter(
            new PdfReportService(metadata.applicationName(), metadata.version(), this.clock));
    this.administrativeCommandExecutor =
        new CliAdministrativeCommandExecutor(requestReader, responseWriter, resolvedBookWorkflow);
    this.discoveryCommandExecutor =
        new CliDiscoveryCommandExecutor(responseWriter, metadata, this.clock);
    this.mutationCommandExecutor =
        new CliMutationCommandExecutor(requestReader, responseWriter, resolvedBookWorkflow);
    this.queryCommandExecutor = new CliQueryCommandExecutor(responseWriter, resolvedBookWorkflow);
    this.reportCommandExecutor =
        new CliReportCommandExecutor(
            responseWriter, diagnosticsWriter, resolvedBookWorkflow, pdfExporter);
    this.executionContext =
        new CliExecutionContext(
            administrativeCommandExecutor,
            discoveryCommandExecutor,
            mutationCommandExecutor,
            queryCommandExecutor,
            reportCommandExecutor);
  }

  /** Runs one CLI command and writes a deterministic JSON envelope. */
  int run(String[] args) {
    OutputMode failureOutputMode = CliExecutionPolicy.inferredFailureOutputMode(args);
    try {
      CliCommand command = CliArguments.parse(args);
      failureOutputMode = command.failureOutputMode();
      return command.execute(executionContext);
    } catch (CliArgumentsException | CliRequestException exception) {
      responseWriter.writeFailure(CliFailureMapper.cliFailure(exception), failureOutputMode);
      return CliExecutionPolicy.invalidInvocationExitCode();
    } catch (ContractFailureException exception) {
      responseWriter.writeDeterministicFailure(
          CliFailureMapper.contractFailure(exception.failure()), failureOutputMode);
      return CliExecutionPolicy.deterministicFailureExitCode();
    } catch (RuntimeException exception) {
      responseWriter.writeFailure(CliFailureMapper.runtimeFailure(exception), failureOutputMode);
      return CliExecutionPolicy.runtimeFailureExitCode();
    }
  }

  static EnvironmentDescriptor environmentDescriptor(
      SqliteRuntime.Probe runtimeProbe, String runtimeDistribution) {
    return CliRuntimeContractDescriptors.environmentDescriptor(runtimeProbe, runtimeDistribution);
  }

  static String runtimeDistribution() {
    return System.getProperty(RUNTIME_DISTRIBUTION_PROPERTY, DIRECT_JAVA_RUNTIME_DISTRIBUTION);
  }
}
