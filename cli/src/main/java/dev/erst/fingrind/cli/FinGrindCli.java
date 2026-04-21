package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.ContractDiscovery;
import dev.erst.fingrind.contract.ContractErrorException;
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
  private final CliCommandExecutor commandExecutor;
  private final CliDiscoveryCommandExecutor discoveryCommandExecutor;

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
    this.commandExecutor =
        new CliCommandExecutor(requestReader, responseWriter, resolvedBookWorkflow, pdfExporter);
    this.discoveryCommandExecutor =
        new CliDiscoveryCommandExecutor(responseWriter, metadata, this.clock);
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
    OutputMode failureOutputMode = CliExecutionSupport.inferredFailureOutputMode(args);
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
            commandExecutor.runGenerateBookKeyFileCommand(
                generateBookKeyFile.bookKeyFilePath(), generateBookKeyFile.outputMode());
        case CliCommand.OpenBook openBook ->
            commandExecutor.runOpenBookCommand(openBook.bookAccess(), openBook.outputMode());
        case CliCommand.RekeyBook rekeyBook ->
            commandExecutor.runRekeyBookCommand(
                rekeyBook.bookAccess(),
                rekeyBook.replacementPassphraseSource(),
                rekeyBook.outputMode());
        case CliCommand.DeclareAccount declareAccount ->
            commandExecutor.runDeclareAccountCommand(
                declareAccount.bookAccess(),
                declareAccount.requestFile(),
                declareAccount.outputMode());
        case CliCommand.InspectBook inspectBook ->
            commandExecutor.runInspectBookCommand(
                inspectBook.bookAccess(), inspectBook.outputMode());
        case CliCommand.ListAccounts listAccounts ->
            commandExecutor.runListAccountsCommand(
                listAccounts.bookAccess(), listAccounts.query(), listAccounts.outputMode());
        case CliCommand.GetPosting getPosting ->
            commandExecutor.runGetPostingCommand(
                getPosting.bookAccess(), getPosting.postingId(), getPosting.outputMode());
        case CliCommand.ListPostings listPostings ->
            commandExecutor.runListPostingsCommand(
                listPostings.bookAccess(), listPostings.query(), listPostings.outputMode());
        case CliCommand.AccountBalance accountBalance ->
            commandExecutor.runAccountBalanceCommand(
                accountBalance.bookAccess(), accountBalance.query(), accountBalance.output());
        case CliCommand.TrialBalance trialBalance ->
            commandExecutor.runTrialBalanceCommand(
                trialBalance.bookAccess(), trialBalance.query(), trialBalance.output());
        case CliCommand.AccountLedger accountLedger ->
            commandExecutor.runAccountLedgerCommand(
                accountLedger.bookAccess(), accountLedger.query(), accountLedger.output());
        case CliCommand.PeriodSummary periodSummary ->
            commandExecutor.runPeriodSummaryCommand(
                periodSummary.bookAccess(), periodSummary.query(), periodSummary.output());
        case CliCommand.ExecutePlan executePlan ->
            commandExecutor.runExecutePlanCommand(
                executePlan.bookAccess(), executePlan.requestFile());
        case CliCommand.PreflightEntry preflightEntry ->
            commandExecutor.runPreflightEntryCommand(
                preflightEntry.bookAccess(),
                preflightEntry.requestFile(),
                preflightEntry.outputMode());
        case CliCommand.PostEntry postEntry ->
            commandExecutor.runPostEntryCommand(
                postEntry.bookAccess(), postEntry.requestFile(), postEntry.outputMode());
      };
    } catch (CliArgumentsException | CliRequestException exception) {
      responseWriter.writeFailure(CliFailureMapper.cliFailure(exception), failureOutputMode);
      return 2;
    } catch (ContractErrorException exception) {
      responseWriter.writeFailure(
          CliFailureMapper.contractErrorFailure(exception), failureOutputMode);
      return 2;
    } catch (RuntimeException exception) {
      responseWriter.writeFailure(CliFailureMapper.runtimeFailure(exception), failureOutputMode);
      return 1;
    }
  }

  static ContractDiscovery.EnvironmentDescriptor environmentDescriptor(
      SqliteRuntime.Probe runtimeProbe, String runtimeDistribution) {
    return CliRuntimeContractSupport.environmentDescriptor(runtimeProbe, runtimeDistribution);
  }

  static String runtimeDistribution() {
    return System.getProperty(RUNTIME_DISTRIBUTION_PROPERTY, DIRECT_JAVA_RUNTIME_DISTRIBUTION);
  }
}
