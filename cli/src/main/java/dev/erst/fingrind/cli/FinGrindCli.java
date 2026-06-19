package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.PublicCliBundleTarget;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.report.pdf.PdfReportService;
import dev.erst.fingrind.sqlite.SqliteRuntime;
import java.io.InputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** Command dispatcher for the FinGrind agent-first CLI surface. */
final class FinGrindCli {
  static final String RUNTIME_DISTRIBUTION_PROPERTY = "fingrind.runtime.distribution";
  static final String RUNTIME_BUNDLE_TARGET_PROPERTY = "fingrind.runtime.bundle-target";
  static final String DIRECT_JAVA_RUNTIME_DISTRIBUTION =
      ProtocolCatalog.distribution().directJavaRuntimeDistribution().wireValue();
  static final String SOURCE_CHECKOUT_RUNTIME_DISTRIBUTION =
      ProtocolCatalog.distribution().sourceCheckoutRuntimeDistribution().wireValue();
  static final String CONTAINER_RUNTIME_DISTRIBUTION =
      ProtocolCatalog.distribution().containerRuntimeDistribution().wireValue();
  static final String BUNDLE_RUNTIME_DISTRIBUTION =
      ProtocolCatalog.distribution().bundleRuntimeDistribution().wireValue();

  private final CliFailureResponseWriter failureWriter;
  private final CliDiagnosticsWriter diagnosticsWriter;
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
        new SqliteCliLifecycleWorkflow(
            clock,
            new CliBookPassphraseResolver(inputStream, CliBookPassphraseResolver.systemTerminal())),
        new SqliteCliMutationWorkflow(
            clock,
            new CliBookPassphraseResolver(inputStream, CliBookPassphraseResolver.systemTerminal())),
        new SqliteCliReadWorkflow(
            new CliBookPassphraseResolver(
                inputStream, CliBookPassphraseResolver.systemTerminal())));
  }

  static CliRunner standardRunner(CliRuntimeEnvironment runtimeEnvironment) {
    Objects.requireNonNull(runtimeEnvironment, "runtimeEnvironment");
    return standard(
            runtimeEnvironment.inputStream(),
            runtimeEnvironment.outputStream(),
            runtimeEnvironment.errorStream(),
            runtimeEnvironment.clock())
        ::run;
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
        new SqliteCliLifecycleWorkflow(
            clock,
            new CliBookPassphraseResolver(
                inputStream, Objects.requireNonNull(terminal, "terminal"))),
        new SqliteCliMutationWorkflow(
            clock,
            new CliBookPassphraseResolver(
                inputStream, Objects.requireNonNull(terminal, "terminal"))),
        new SqliteCliReadWorkflow(
            new CliBookPassphraseResolver(
                inputStream, Objects.requireNonNull(terminal, "terminal"))));
  }

  FinGrindCli(
      InputStream inputStream,
      PrintStream outputStream,
      PrintStream diagnosticsStream,
      Clock clock,
      CliBookLifecycleWorkflow lifecycleWorkflow,
      CliBookMutationWorkflow mutationWorkflow,
      CliBookReadWorkflow readWorkflow) {
    CliRequestReader requestReader = new CliRequestReader(inputStream);
    CliOutputChannel outputChannel = new CliOutputChannel(outputStream, diagnosticsStream);
    this.failureWriter = new CliFailureResponseWriter(outputChannel);
    this.diagnosticsWriter = new CliDiagnosticsWriter(diagnosticsStream);
    CliMetadata metadata = new CliMetadata();
    Clock resolvedClock = Objects.requireNonNull(clock, "clock");
    CliBookLifecycleWorkflow resolvedLifecycleWorkflow =
        Objects.requireNonNull(lifecycleWorkflow, "lifecycleWorkflow");
    CliBookMutationWorkflow resolvedMutationWorkflow =
        Objects.requireNonNull(mutationWorkflow, "mutationWorkflow");
    CliBookReadWorkflow resolvedReadWorkflow = Objects.requireNonNull(readWorkflow, "readWorkflow");
    CliDiscoveryResponseWriter discoveryResponseWriter =
        new CliDiscoveryResponseWriter(outputChannel);
    CliMutationResponseWriter mutationResponseWriter = new CliMutationResponseWriter(outputChannel);
    CliBookReadResponseWriter bookReadResponseWriter = new CliBookReadResponseWriter(outputChannel);
    CliReportResponseWriter reportResponseWriter = new CliReportResponseWriter(outputChannel);
    CliPlanResponseWriter planResponseWriter = new CliPlanResponseWriter(outputChannel);
    CliPdfReportExporter pdfExporter =
        new CliPdfReportExporter(
            new PdfReportService(metadata.applicationName(), metadata.version(), resolvedClock));
    CliAdministrativeCommandExecutor administrativeCommandExecutor =
        new CliAdministrativeCommandExecutor(
            requestReader,
            mutationResponseWriter,
            failureWriter,
            resolvedLifecycleWorkflow,
            resolvedMutationWorkflow);
    CliDiscoveryCommandExecutor discoveryCommandExecutor =
        new CliDiscoveryCommandExecutor(discoveryResponseWriter, metadata);
    CliMutationCommandExecutor mutationCommandExecutor =
        new CliMutationCommandExecutor(
            requestReader,
            mutationResponseWriter,
            planResponseWriter,
            failureWriter,
            resolvedMutationWorkflow);
    CliQueryCommandExecutor queryCommandExecutor =
        new CliQueryCommandExecutor(bookReadResponseWriter, failureWriter, resolvedReadWorkflow);
    CliReportCommandExecutor reportCommandExecutor =
        new CliReportCommandExecutor(
            reportResponseWriter,
            failureWriter,
            diagnosticsWriter,
            resolvedReadWorkflow,
            pdfExporter);
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
    try {
      CliCommand command = CliArguments.parse(args);
      return command.execute(executionContext);
    } catch (CliArgumentsException | CliRequestException exception) {
      CliFailure failure = CliFailureMapper.cliFailure(exception);
      failureWriter.writeFailure(failure);
      return CliExecutionPolicy.failureExitCode(failure);
    } catch (ContractFailureException exception) {
      CliFailure failure = CliFailureMapper.contractFailure(exception.failure());
      failureWriter.writeDeterministicFailure(failure);
      return CliExecutionPolicy.failureExitCode(failure);
    } catch (RuntimeException exception) {
      String errorId = nextInternalErrorId();
      CliFailure failure = CliFailureMapper.runtimeFailure(exception, errorId);
      if (failure != null) {
        failureWriter.writeFailure(failure);
        return CliExecutionPolicy.failureExitCode(failure);
      }
      CliFailure internalFailure = CliFailureMapper.internalError(errorId);
      failureWriter.writeFailure(internalFailure);
      return CliExecutionPolicy.failureExitCode(internalFailure);
    }
  }

  static EnvironmentDescriptor environmentDescriptor(
      SqliteRuntime.Probe runtimeProbe, String runtimeDistribution) {
    return CliRuntimeContractDescriptors.environmentDescriptor(
        runtimeProbe, runtimeDistribution, runtimeBundleTarget());
  }

  static String runtimeDistribution() {
    return System.getProperty(RUNTIME_DISTRIBUTION_PROPERTY, DIRECT_JAVA_RUNTIME_DISTRIBUTION);
  }

  static @org.jspecify.annotations.Nullable PublicCliBundleTarget runtimeBundleTarget() {
    String rawBundleTarget = System.getProperty(RUNTIME_BUNDLE_TARGET_PROPERTY);
    if (rawBundleTarget == null || rawBundleTarget.isBlank()) {
      return null;
    }
    return PublicCliBundleTarget.fromWireValue(rawBundleTarget);
  }

  private static String nextInternalErrorId() {
    return "fg-internal-" + UUID.randomUUID();
  }
}
