package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerResult;
import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.CommitEntryResult;
import dev.erst.fingrind.contract.ContractDiscovery;
import dev.erst.fingrind.contract.ContractErrorException;
import dev.erst.fingrind.contract.ContractErrors;
import dev.erst.fingrind.contract.DeclareAccountCommand;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.GetPostingResult;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.LedgerPlanResult;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.ListPostingsResult;
import dev.erst.fingrind.contract.MachineContract;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryResult;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.contract.PreflightEntryResult;
import dev.erst.fingrind.contract.RekeyBookResult;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.report.pdf.PdfReportService;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import dev.erst.fingrind.sqlite.SqliteFailureClassifier;
import dev.erst.fingrind.sqlite.SqliteRuntime;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

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
  private final CliBookWorkflow bookWorkflow;
  private final CliPdfReportExporter pdfReportExporter;

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
    this.bookWorkflow = Objects.requireNonNull(bookWorkflow, "bookWorkflow");
    this.pdfReportExporter =
        new CliPdfReportExporter(
            new PdfReportService(metadata.applicationName(), metadata.version(), this.clock));
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
    OutputMode failureOutputMode = inferredFailureOutputMode(args);
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
            runGenerateBookKeyFileCommand(
                generateBookKeyFile.bookKeyFilePath(), generateBookKeyFile.outputMode());
        case CliCommand.OpenBook openBook ->
            runOpenBookCommand(openBook.bookAccess(), openBook.outputMode());
        case CliCommand.RekeyBook rekeyBook ->
            runRekeyBookCommand(
                rekeyBook.bookAccess(),
                rekeyBook.replacementPassphraseSource(),
                rekeyBook.outputMode());
        case CliCommand.DeclareAccount declareAccount ->
            runDeclareAccountCommand(
                declareAccount.bookAccess(),
                declareAccount.requestFile(),
                declareAccount.outputMode());
        case CliCommand.InspectBook inspectBook ->
            runInspectBookCommand(inspectBook.bookAccess(), inspectBook.outputMode());
        case CliCommand.ListAccounts listAccounts ->
            runListAccountsCommand(
                listAccounts.bookAccess(), listAccounts.query(), listAccounts.outputMode());
        case CliCommand.GetPosting getPosting ->
            runGetPostingCommand(
                getPosting.bookAccess(), getPosting.postingId(), getPosting.outputMode());
        case CliCommand.ListPostings listPostings ->
            runListPostingsCommand(
                listPostings.bookAccess(), listPostings.query(), listPostings.outputMode());
        case CliCommand.AccountBalance accountBalance ->
            runAccountBalanceCommand(
                accountBalance.bookAccess(), accountBalance.query(), accountBalance.output());
        case CliCommand.TrialBalance trialBalance ->
            runTrialBalanceCommand(
                trialBalance.bookAccess(), trialBalance.query(), trialBalance.output());
        case CliCommand.AccountLedger accountLedger ->
            runAccountLedgerCommand(
                accountLedger.bookAccess(), accountLedger.query(), accountLedger.output());
        case CliCommand.PeriodSummary periodSummary ->
            runPeriodSummaryCommand(
                periodSummary.bookAccess(), periodSummary.query(), periodSummary.output());
        case CliCommand.ExecutePlan executePlan ->
            runExecutePlanCommand(executePlan.bookAccess(), executePlan.requestFile());
        case CliCommand.PreflightEntry preflightEntry ->
            runPreflightEntryCommand(
                preflightEntry.bookAccess(),
                preflightEntry.requestFile(),
                preflightEntry.outputMode());
        case CliCommand.PostEntry postEntry ->
            runPostEntryCommand(
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

  private int runGenerateBookKeyFileCommand(Path bookKeyFilePath, OutputMode outputMode) {
    responseWriter.writeGenerateBookKeyFileResult(
        SqliteBookKeyFileGenerator.generate(bookKeyFilePath), outputMode);
    return 0;
  }

  private int runOpenBookCommand(BookAccess bookAccess, OutputMode outputMode) {
    OpenBookResult result = bookWorkflow.openBook(bookAccess);
    responseWriter.writeOpenBookResult(bookAccess.bookFilePath(), result, outputMode);
    return exitCodeFor(result);
  }

  private int runRekeyBookCommand(
      BookAccess bookAccess,
      BookAccess.PassphraseSource replacementPassphraseSource,
      OutputMode outputMode) {
    RekeyBookResult result = bookWorkflow.rekeyBook(bookAccess, replacementPassphraseSource);
    responseWriter.writeRekeyBookResult(result, outputMode);
    return exitCodeFor(result);
  }

  private int runDeclareAccountCommand(
      BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    DeclareAccountCommand command = requestReader.readDeclareAccountCommand(requestFile);
    DeclareAccountResult result = bookWorkflow.declareAccount(bookAccess, command);
    responseWriter.writeDeclareAccountResult(result, outputMode);
    return exitCodeFor(result);
  }

  private int runInspectBookCommand(BookAccess bookAccess, OutputMode outputMode) {
    BookInspection inspection = bookWorkflow.inspectBook(bookAccess);
    responseWriter.writeBookInspection(bookAccess.bookFilePath(), inspection, outputMode);
    return 0;
  }

  private int runListAccountsCommand(
      BookAccess bookAccess, ListAccountsQuery query, OutputMode outputMode) {
    ListAccountsResult result = bookWorkflow.listAccounts(bookAccess, query);
    responseWriter.writeListAccountsResult(result, outputMode);
    return exitCodeFor(result);
  }

  private int runGetPostingCommand(
      BookAccess bookAccess, dev.erst.fingrind.core.PostingId postingId, OutputMode outputMode) {
    GetPostingResult result = bookWorkflow.getPosting(bookAccess, postingId);
    responseWriter.writeGetPostingResult(result, outputMode);
    return exitCodeFor(result);
  }

  private int runListPostingsCommand(
      BookAccess bookAccess, ListPostingsQuery query, OutputMode outputMode) {
    ListPostingsResult result = bookWorkflow.listPostings(bookAccess, query);
    responseWriter.writeListPostingsResult(result, outputMode);
    return exitCodeFor(result);
  }

  private int runAccountBalanceCommand(
      BookAccess bookAccess, AccountBalanceQuery query, CliCommand.ReportOutput output) {
    AccountBalanceResult result = bookWorkflow.accountBalance(bookAccess, query);
    exportAccountBalanceIfRequested(bookAccess.bookFilePath(), result, output.pdfOutPath());
    responseWriter.writeAccountBalanceResult(result, output.outputMode());
    return exitCodeFor(result);
  }

  private int runTrialBalanceCommand(
      BookAccess bookAccess, TrialBalanceQuery query, CliCommand.ReportOutput output) {
    TrialBalanceResult result = bookWorkflow.trialBalance(bookAccess, query);
    exportTrialBalanceIfRequested(bookAccess.bookFilePath(), result, output.pdfOutPath());
    responseWriter.writeTrialBalanceResult(result, output.outputMode());
    return exitCodeFor(result);
  }

  private int runAccountLedgerCommand(
      BookAccess bookAccess, AccountLedgerQuery query, CliCommand.ReportOutput output) {
    AccountLedgerResult result = bookWorkflow.accountLedger(bookAccess, query);
    exportAccountLedgerIfRequested(bookAccess.bookFilePath(), result, output.pdfOutPath());
    responseWriter.writeAccountLedgerResult(result, output.outputMode());
    return exitCodeFor(result);
  }

  private int runPeriodSummaryCommand(
      BookAccess bookAccess, PeriodSummaryQuery query, CliCommand.ReportOutput output) {
    PeriodSummaryResult result = bookWorkflow.periodSummary(bookAccess, query);
    exportPeriodSummaryIfRequested(bookAccess.bookFilePath(), result, output.pdfOutPath());
    responseWriter.writePeriodSummaryResult(result, output.outputMode());
    return exitCodeFor(result);
  }

  private static OutputMode inferredFailureOutputMode(String[] args) {
    if (args.length == 0) {
      return OutputMode.HUMAN;
    }
    OutputMode inferred =
        ProtocolCatalog.findByToken(args[0])
            .map(
                operation ->
                    switch (operation.id()) {
                      case HELP, CAPABILITIES, VERSION -> OutputMode.HUMAN;
                      default -> OutputMode.JSON;
                    })
            .orElse(OutputMode.JSON);
    int index = 1;
    while (index + 1 < args.length) {
      if (!"--output".equals(args[index])) {
        index++;
        continue;
      }
      try {
        inferred = OutputMode.fromWireValue(args[index + 1]);
      } catch (IllegalArgumentException ignored) {
        // Keep the last valid or default inferred mode when the raw argument is malformed.
      }
      index += 2;
    }
    return inferred == OutputMode.HUMAN ? OutputMode.HUMAN : OutputMode.JSON;
  }

  private void exportAccountBalanceIfRequested(
      Path bookFilePath, AccountBalanceResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return;
    }
    result.fold(
        reported -> {
          pdfReportExporter.exportAccountBalance(outputPath, bookFilePath, reported.snapshot());
          return Boolean.TRUE;
        },
        rejected -> Boolean.FALSE);
  }

  private void exportTrialBalanceIfRequested(
      Path bookFilePath, TrialBalanceResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return;
    }
    result.fold(
        reported -> {
          pdfReportExporter.exportTrialBalance(outputPath, bookFilePath, reported.report());
          return Boolean.TRUE;
        },
        rejected -> Boolean.FALSE);
  }

  private void exportAccountLedgerIfRequested(
      Path bookFilePath, AccountLedgerResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return;
    }
    result.fold(
        reported -> {
          pdfReportExporter.exportAccountLedger(outputPath, bookFilePath, reported.report());
          return Boolean.TRUE;
        },
        rejected -> Boolean.FALSE);
  }

  private void exportPeriodSummaryIfRequested(
      Path bookFilePath, PeriodSummaryResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return;
    }
    result.fold(
        reported -> {
          pdfReportExporter.exportPeriodSummary(outputPath, bookFilePath, reported.report());
          return Boolean.TRUE;
        },
        rejected -> Boolean.FALSE);
  }

  private int runExecutePlanCommand(BookAccess bookAccess, Path requestFile) {
    LedgerPlan plan = requestReader.readLedgerPlan(requestFile);
    LedgerPlanResult result = bookWorkflow.executePlan(bookAccess, plan);
    responseWriter.writeLedgerPlanResult(result);
    return exitCodeFor(result);
  }

  private int runPreflightEntryCommand(
      BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    PostEntryCommand command = requestReader.readPostEntryCommand(requestFile);
    PreflightEntryResult result = bookWorkflow.preflight(bookAccess, command);
    responseWriter.writePostEntryResult(result, outputMode);
    return exitCodeFor(result);
  }

  private int runPostEntryCommand(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    PostEntryCommand command = requestReader.readPostEntryCommand(requestFile);
    CommitEntryResult result = bookWorkflow.commit(bookAccess, command);
    responseWriter.writePostEntryResult(result, outputMode);
    return exitCodeFor(result);
  }

  private static int exitCodeFor(PreflightEntryResult result) {
    return switch (result) {
      case PostEntryResult.PreflightAccepted _ -> 0;
      case PostEntryResult.PreflightRejected _ -> 2;
    };
  }

  private static int exitCodeFor(CommitEntryResult result) {
    return switch (result) {
      case PostEntryResult.Committed _ -> 0;
      case PostEntryResult.CommitRejected _ -> 2;
    };
  }

  private static int exitCodeFor(OpenBookResult result) {
    return switch (result) {
      case OpenBookResult.Opened _ -> 0;
      case OpenBookResult.Rejected _ -> 2;
    };
  }

  private static int exitCodeFor(DeclareAccountResult result) {
    return switch (result) {
      case DeclareAccountResult.Declared _ -> 0;
      case DeclareAccountResult.Rejected _ -> 2;
    };
  }

  private static int exitCodeFor(ListAccountsResult result) {
    return switch (result) {
      case ListAccountsResult.Listed _ -> 0;
      case ListAccountsResult.Rejected _ -> 2;
    };
  }

  private static int exitCodeFor(GetPostingResult result) {
    return switch (result) {
      case GetPostingResult.Found _ -> 0;
      case GetPostingResult.Rejected _ -> 2;
    };
  }

  private static int exitCodeFor(ListPostingsResult result) {
    return switch (result) {
      case ListPostingsResult.Listed _ -> 0;
      case ListPostingsResult.Rejected _ -> 2;
    };
  }

  private static int exitCodeFor(AccountBalanceResult result) {
    return switch (result) {
      case AccountBalanceResult.Reported _ -> 0;
      case AccountBalanceResult.Rejected _ -> 2;
    };
  }

  private static int exitCodeFor(TrialBalanceResult result) {
    return switch (result) {
      case TrialBalanceResult.Reported _ -> 0;
      case TrialBalanceResult.Rejected _ -> 2;
    };
  }

  private static int exitCodeFor(AccountLedgerResult result) {
    return switch (result) {
      case AccountLedgerResult.Reported _ -> 0;
      case AccountLedgerResult.Rejected _ -> 2;
    };
  }

  private static int exitCodeFor(PeriodSummaryResult result) {
    return switch (result) {
      case PeriodSummaryResult.Reported _ -> 0;
      case PeriodSummaryResult.Rejected _ -> 2;
    };
  }

  private static int exitCodeFor(RekeyBookResult result) {
    return switch (result) {
      case RekeyBookResult.Rekeyed _ -> 0;
      case RekeyBookResult.Rejected _ -> 2;
    };
  }

  private static int exitCodeFor(LedgerPlanResult result) {
    return switch (result) {
      case LedgerPlanResult.Succeeded _ -> 0;
      case LedgerPlanResult.Rejected _ -> 2;
      case LedgerPlanResult.AssertionFailed _ -> 3;
    };
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
        "26+",
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

  private static CliFailure cliFailure(Exception exception) {
    return switch (Objects.requireNonNull(exception, "exception")) {
      case CliArgumentsException cliArgumentsException -> cliArgumentsException.failure();
      case CliRequestException cliRequestException -> cliRequestException.failure();
      default -> throw new IllegalArgumentException("Unsupported CLI failure type.");
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
