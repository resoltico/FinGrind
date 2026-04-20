package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.ContractDiscovery;
import dev.erst.fingrind.contract.ContractErrorException;
import dev.erst.fingrind.contract.ContractErrors;
import dev.erst.fingrind.contract.MachineContract;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.report.pdf.PdfReportService;
import dev.erst.fingrind.sqlite.SqliteFailureClassifier;
import dev.erst.fingrind.sqlite.SqliteRuntime;
import java.io.InputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
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
        case CliCommand.Help help -> writeHelp(help.outputMode());
        case CliCommand.Capabilities capabilities -> writeCapabilities(capabilities.outputMode());
        case CliCommand.Version version -> writeVersion(version.outputMode());
        case CliCommand.PrintRequestTemplate _ -> writeRequestTemplate();
        case CliCommand.PrintPlanTemplate _ -> writePlanTemplate();
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
      responseWriter.writeFailure(cliFailure(exception), failureOutputMode);
      return 2;
    } catch (ContractErrorException exception) {
      responseWriter.writeFailure(contractErrorFailure(exception), failureOutputMode);
      return 2;
    } catch (RuntimeException exception) {
      responseWriter.writeFailure(runtimeFailure(exception), failureOutputMode);
      return 1;
    }
  }

  private int writeHelp(OutputMode outputMode) {
    responseWriter.writeHelp(
        MachineContract.help(applicationIdentity(), environmentDescriptor()), outputMode);
    return 0;
  }

  private int writeCapabilities(OutputMode outputMode) {
    responseWriter.writeCapabilities(
        MachineContract.capabilities(
            applicationIdentity(), environmentDescriptor(), Instant.now(clock)),
        outputMode);
    return 0;
  }

  private int writeVersion(OutputMode outputMode) {
    responseWriter.writeVersion(MachineContract.version(applicationIdentity()), outputMode);
    return 0;
  }

  private int writeRequestTemplate() {
    responseWriter.writeRequestTemplate(MachineContract.requestTemplate(clock));
    return 0;
  }

  private int writePlanTemplate() {
    responseWriter.writePlanTemplate(MachineContract.planTemplate(clock));
    return 0;
  }

  private ContractDiscovery.ApplicationIdentity applicationIdentity() {
    return new ContractDiscovery.ApplicationIdentity(
        metadata.applicationName(), metadata.version(), metadata.description());
  }

  private ContractDiscovery.EnvironmentDescriptor environmentDescriptor() {
    return environmentDescriptor(SqliteRuntime.probe(), runtimeDistribution());
  }

  static ContractDiscovery.EnvironmentDescriptor environmentDescriptor(
      SqliteRuntime.Probe runtimeProbe, String runtimeDistribution) {
    return new ContractDiscovery.EnvironmentDescriptor(
        runtimeDistribution,
        "self-contained-bundle",
        ProtocolCatalog.supportedPublicCliBundleTargets(),
        ProtocolCatalog.unsupportedPublicCliOperatingSystems(),
        ProtocolCatalog.sourceCheckoutJava(),
        SqliteRuntime.STORAGE_DRIVER,
        SqliteRuntime.STORAGE_ENGINE,
        SqliteRuntime.BOOK_PROTECTION_MODE,
        SqliteRuntime.DEFAULT_BOOK_CIPHER,
        runtimeProbe.libraryMode(),
        SqliteRuntime.LIBRARY_ENVIRONMENT_VARIABLE,
        SqliteRuntime.BUNDLE_HOME_SYSTEM_PROPERTY,
        SqliteRuntime.REQUIRED_SQLITE_COMPILE_OPTIONS,
        runtimeProbe.status() == SqliteRuntime.Status.READY,
        runtimeProbe.requiredMinimumSqliteVersion(),
        runtimeProbe.requiredSqlite3mcVersion(),
        runtimeProbe.status().wireValue(),
        runtimeProbe.loadedSqliteVersion(),
        runtimeProbe.loadedSqlite3mcVersion(),
        runtimeProbe.issue());
  }

  private static String runtimeDistribution() {
    return System.getProperty(RUNTIME_DISTRIBUTION_PROPERTY, DIRECT_JAVA_RUNTIME_DISTRIBUTION);
  }

  private static CliFailure cliFailure(CliCommandException exception) {
    return switch (Objects.requireNonNull(exception, "exception")) {
      case CliArgumentsException cliArgumentsException -> cliArgumentsException.failure();
      case CliRequestException cliRequestException -> cliRequestException.failure();
    };
  }

  private static CliFailure contractErrorFailure(ContractErrorException exception) {
    return new CliFailure(
        exception.code(),
        Objects.requireNonNullElse(exception.getMessage(), exception.descriptor().description()),
        exception.hint(),
        exception.argument());
  }

  private CliFailure runtimeFailure(RuntimeException exception) {
    if (exception instanceof CliPdfExportException pdfExportException) {
      return new CliFailure(
          ContractErrors.Descriptor.RUNTIME_FAILURE.code(),
          message(pdfExportException),
          "Inspect the selected --pdf-out destination, its parent directory permissions, and the available filesystem space, then rerun the command.",
          "--pdf-out");
    }
    String message = message(exception);
    String hint =
        switch (SqliteFailureClassifier.classify(exception)) {
          case MANAGED_RUNTIME ->
              "Run the published FinGrind bundle launcher (bin/fingrind on macOS/Linux or bin\\fingrind.ps1 on Windows), or for a local source checkout build the managed SQLite runtime with ./gradlew prepareManagedSqlite and set FINGRIND_SQLITE_LIBRARY before rerunning.";
          case STORAGE ->
              "Inspect the selected book file path, chosen book passphrase source, initialization state, filesystem permissions, and the SQLite runtime message, then rerun after fixing the underlying storage problem.";
          case OTHER ->
              "Inspect the message and rerun after fixing the underlying runtime problem.";
        };
    return new CliFailure(ContractErrors.Descriptor.RUNTIME_FAILURE.code(), message, hint, null);
  }

  private static String message(Exception exception) {
    return Objects.requireNonNullElse(exception.getMessage(), "CLI command failed.");
  }
}
