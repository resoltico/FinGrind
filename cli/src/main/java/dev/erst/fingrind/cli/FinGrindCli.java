package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.EnvironmentDescriptor;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.report.pdf.PdfReportService;
import dev.erst.fingrind.sqlite.SqliteRuntime;
import java.io.InputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.util.Objects;

/** Command dispatcher for the FinGrind agent-first CLI surface. */
final class FinGrindCli {
  static final String RUNTIME_DISTRIBUTION_PROPERTY = "fingrind.runtime.distribution";
  static final String DIRECT_JAVA_RUNTIME_DISTRIBUTION = "direct-java-invocation";
  static final String SOURCE_CHECKOUT_RUNTIME_DISTRIBUTION = "source-checkout-gradle";
  static final String CONTAINER_RUNTIME_DISTRIBUTION = "container-image";
  static final String BUNDLE_RUNTIME_DISTRIBUTION = "self-contained-bundle";

  private final CliRequestReader requestReader;
  private final CliResponseWriter responseWriter;
  private final CliMetadata metadata;
  private final Clock clock;
  private final CliAdministrativeCommandExecutor administrativeCommandExecutor;
  private final CliDiscoveryCommandExecutor discoveryCommandExecutor;
  private final CliMutationCommandExecutor mutationCommandExecutor;
  private final CliQueryCommandExecutor queryCommandExecutor;
  private final CliReportCommandExecutor reportCommandExecutor;

  FinGrindCli(InputStream inputStream, PrintStream outputStream, Clock clock) {
    this(
        inputStream,
        outputStream,
        clock,
        new SqliteCliBookWorkflow(
            clock,
            new CliBookPassphraseResolver(
                inputStream, CliBookPassphraseResolver.systemTerminal())));
  }

  FinGrindCli(
      InputStream inputStream,
      PrintStream outputStream,
      Clock clock,
      CliBookWorkflow bookWorkflow) {
    this.requestReader = new CliRequestReader(inputStream);
    this.responseWriter = new CliResponseWriter(outputStream);
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
        new CliReportCommandExecutor(responseWriter, resolvedBookWorkflow, pdfExporter);
  }

  FinGrindCli(
      InputStream inputStream,
      PrintStream outputStream,
      Clock clock,
      CliBookPassphraseResolver.Terminal terminal) {
    this(
        inputStream,
        outputStream,
        clock,
        new SqliteCliBookWorkflow(
            clock,
            new CliBookPassphraseResolver(
                inputStream, Objects.requireNonNull(terminal, "terminal"))));
  }

  /** Runs one CLI command and writes a deterministic JSON envelope. */
  int run(String[] args) {
    OutputMode failureOutputMode = CliExecutionPolicy.inferredFailureOutputMode(args);
    try {
      CliCommand command = CliArguments.parse(args);
      failureOutputMode = command.failureOutputMode();
      return switch (command) {
        case CliCommand.Help help -> discoveryCommandExecutor.writeHelp(help.outputMode());
        case CliCommand.Capabilities capabilities ->
            discoveryCommandExecutor.writeCapabilities(capabilities.outputMode());
        case CliCommand.Version version ->
            discoveryCommandExecutor.writeVersion(version.outputMode());
        case CliCommand.PrintRequestTemplate _ -> discoveryCommandExecutor.writeRequestTemplate();
        case CliCommand.PrintPlanTemplate _ -> discoveryCommandExecutor.writePlanTemplate();
        case CliCommand.GenerateBookKeyFile generateBookKeyFile ->
            administrativeCommandExecutor.runGenerateBookKeyFileCommand(
                generateBookKeyFile.bookKeyFilePath(), generateBookKeyFile.outputMode());
        case CliCommand.OpenBook openBook ->
            administrativeCommandExecutor.runOpenBookCommand(
                openBook.bookAccess(), openBook.outputMode());
        case CliCommand.RekeyBook rekeyBook ->
            administrativeCommandExecutor.runRekeyBookCommand(
                rekeyBook.bookAccess(),
                rekeyBook.replacementPassphraseSource(),
                rekeyBook.outputMode());
        case CliCommand.DeclareAccount declareAccount ->
            administrativeCommandExecutor.runDeclareAccountCommand(
                declareAccount.bookAccess(),
                declareAccount.requestFile(),
                declareAccount.outputMode());
        case CliCommand.InspectBook inspectBook ->
            queryCommandExecutor.runInspectBookCommand(
                inspectBook.bookAccess(), inspectBook.outputMode());
        case CliCommand.ListAccounts listAccounts ->
            queryCommandExecutor.runListAccountsCommand(
                listAccounts.bookAccess(), listAccounts.query(), listAccounts.outputMode());
        case CliCommand.GetPosting getPosting ->
            queryCommandExecutor.runGetPostingCommand(
                getPosting.bookAccess(), getPosting.postingId(), getPosting.outputMode());
        case CliCommand.ListPostings listPostings ->
            queryCommandExecutor.runListPostingsCommand(
                listPostings.bookAccess(), listPostings.query(), listPostings.outputMode());
        case CliCommand.AccountBalance accountBalance ->
            reportCommandExecutor.runAccountBalanceCommand(
                accountBalance.bookAccess(), accountBalance.query(), accountBalance.output());
        case CliCommand.TrialBalance trialBalance ->
            reportCommandExecutor.runTrialBalanceCommand(
                trialBalance.bookAccess(), trialBalance.query(), trialBalance.output());
        case CliCommand.AccountLedger accountLedger ->
            reportCommandExecutor.runAccountLedgerCommand(
                accountLedger.bookAccess(), accountLedger.query(), accountLedger.output());
        case CliCommand.PeriodSummary periodSummary ->
            reportCommandExecutor.runPeriodSummaryCommand(
                periodSummary.bookAccess(), periodSummary.query(), periodSummary.output());
        case CliCommand.ExecutePlan executePlan ->
            mutationCommandExecutor.runExecutePlanCommand(
                executePlan.bookAccess(), executePlan.requestFile());
        case CliCommand.PreflightEntry preflightEntry ->
            mutationCommandExecutor.runPreflightEntryCommand(
                preflightEntry.bookAccess(),
                preflightEntry.requestFile(),
                preflightEntry.outputMode());
        case CliCommand.PostEntry postEntry ->
            mutationCommandExecutor.runPostEntryCommand(
                postEntry.bookAccess(), postEntry.requestFile(), postEntry.outputMode());
      };
    } catch (CliArgumentsException | CliRequestException exception) {
      responseWriter.writeFailure(CliFailureMapper.cliFailure(exception), failureOutputMode);
      return CliExecutionPolicy.invalidInvocationExitCode();
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
