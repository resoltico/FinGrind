package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.*;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.executor.*;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import dev.erst.fingrind.sqlite.SqliteFailureClassifier;
import dev.erst.fingrind.sqlite.SqlitePostingFactStore;
import dev.erst.fingrind.sqlite.SqliteRuntime;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
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
  private final BookWorkflow bookWorkflow;

  FinGrindCli(InputStream inputStream, PrintStream outputStream, Clock clock) {
    this(
        inputStream,
        outputStream,
        clock,
        new SqliteBookWorkflow(
            clock,
            new CliBookPassphraseResolver(
                inputStream, CliBookPassphraseResolver.systemTerminal())));
  }

  FinGrindCli(
      InputStream inputStream, PrintStream outputStream, Clock clock, BookWorkflow bookWorkflow) {
    this.requestReader = new CliRequestReader(inputStream);
    this.responseWriter = new CliResponseWriter(outputStream);
    this.metadata = new CliMetadata();
    this.clock = Objects.requireNonNull(clock, "clock");
    this.bookWorkflow = Objects.requireNonNull(bookWorkflow, "bookWorkflow");
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
        new SqliteBookWorkflow(
            clock,
            new CliBookPassphraseResolver(
                inputStream, Objects.requireNonNull(terminal, "terminal"))));
  }

  /** Runs one CLI command and writes a deterministic JSON envelope. */
  int run(String[] args) {
    try {
      return switch (CliArguments.parse(args)) {
        case CliCommand.Help _ -> writeHelp();
        case CliCommand.Capabilities _ -> writeCapabilities();
        case CliCommand.Version _ -> writeVersion();
        case CliCommand.PrintRequestTemplate _ -> writeRequestTemplate();
        case CliCommand.PrintPlanTemplate _ -> writePlanTemplate();
        case CliCommand.GenerateBookKeyFile command ->
            runGenerateBookKeyFileCommand(command.bookKeyFilePath());
        case CliCommand.OpenBook command -> runOpenBookCommand(command.bookAccess());
        case CliCommand.RekeyBook command ->
            runRekeyBookCommand(command.bookAccess(), command.replacementPassphraseSource());
        case CliCommand.DeclareAccount command ->
            runDeclareAccountCommand(command.bookAccess(), command.requestFile());
        case CliCommand.InspectBook command -> runInspectBookCommand(command.bookAccess());
        case CliCommand.ListAccounts command ->
            runListAccountsCommand(command.bookAccess(), command.query());
        case CliCommand.GetPosting command ->
            runGetPostingCommand(command.bookAccess(), command.postingId());
        case CliCommand.ListPostings command ->
            runListPostingsCommand(command.bookAccess(), command.query());
        case CliCommand.AccountBalance command ->
            runAccountBalanceCommand(command.bookAccess(), command.query());
        case CliCommand.ExecutePlan command ->
            runExecutePlanCommand(command.bookAccess(), command.requestFile());
        case CliCommand.PreflightEntry command ->
            runPreflightEntryCommand(command.bookAccess(), command.requestFile());
        case CliCommand.PostEntry command ->
            runPostEntryCommand(command.bookAccess(), command.requestFile());
      };
    } catch (CliArgumentsException | CliRequestException exception) {
      responseWriter.writeFailure(cliFailure(exception));
      return 2;
    } catch (RuntimeException exception) {
      responseWriter.writeFailure(runtimeFailure(exception));
      return 1;
    }
  }

  private int writeHelp() {
    responseWriter.writeHelp(MachineContract.help(applicationIdentity(), environmentDescriptor()));
    return 0;
  }

  private int writeCapabilities() {
    responseWriter.writeCapabilities(
        MachineContract.capabilities(
            applicationIdentity(), environmentDescriptor(), Instant.now(clock)));
    return 0;
  }

  private int writeVersion() {
    responseWriter.writeVersion(MachineContract.version(applicationIdentity()));
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

  private int runGenerateBookKeyFileCommand(Path bookKeyFilePath) {
    responseWriter.writeGenerateBookKeyFileResult(
        SqliteBookKeyFileGenerator.generate(bookKeyFilePath));
    return 0;
  }

  private int runOpenBookCommand(BookAccess bookAccess) {
    OpenBookResult result = bookWorkflow.openBook(bookAccess);
    responseWriter.writeOpenBookResult(bookAccess.bookFilePath(), result);
    return exitCodeFor(result);
  }

  private int runRekeyBookCommand(
      BookAccess bookAccess, BookAccess.PassphraseSource replacementPassphraseSource) {
    RekeyBookResult result = bookWorkflow.rekeyBook(bookAccess, replacementPassphraseSource);
    responseWriter.writeRekeyBookResult(result);
    return exitCodeFor(result);
  }

  private int runDeclareAccountCommand(BookAccess bookAccess, Path requestFile) {
    DeclareAccountCommand command = requestReader.readDeclareAccountCommand(requestFile);
    DeclareAccountResult result = bookWorkflow.declareAccount(bookAccess, command);
    responseWriter.writeDeclareAccountResult(result);
    return exitCodeFor(result);
  }

  private int runInspectBookCommand(BookAccess bookAccess) {
    BookInspection inspection = bookWorkflow.inspectBook(bookAccess);
    responseWriter.writeBookInspection(bookAccess.bookFilePath(), inspection);
    return 0;
  }

  private int runListAccountsCommand(BookAccess bookAccess, ListAccountsQuery query) {
    ListAccountsResult result = bookWorkflow.listAccounts(bookAccess, query);
    responseWriter.writeListAccountsResult(result);
    return exitCodeFor(result);
  }

  private int runGetPostingCommand(
      BookAccess bookAccess, dev.erst.fingrind.core.PostingId postingId) {
    GetPostingResult result = bookWorkflow.getPosting(bookAccess, postingId);
    responseWriter.writeGetPostingResult(result);
    return exitCodeFor(result);
  }

  private int runListPostingsCommand(BookAccess bookAccess, ListPostingsQuery query) {
    ListPostingsResult result = bookWorkflow.listPostings(bookAccess, query);
    responseWriter.writeListPostingsResult(result);
    return exitCodeFor(result);
  }

  private int runAccountBalanceCommand(BookAccess bookAccess, AccountBalanceQuery query) {
    AccountBalanceResult result = bookWorkflow.accountBalance(bookAccess, query);
    responseWriter.writeAccountBalanceResult(result);
    return exitCodeFor(result);
  }

  private int runExecutePlanCommand(BookAccess bookAccess, Path requestFile) {
    LedgerPlan plan = requestReader.readLedgerPlan(requestFile);
    LedgerPlanResult result = bookWorkflow.executePlan(bookAccess, plan);
    responseWriter.writeLedgerPlanResult(result);
    return exitCodeFor(result);
  }

  private int runPreflightEntryCommand(BookAccess bookAccess, Path requestFile) {
    PostEntryCommand command = requestReader.readPostEntryCommand(requestFile);
    PreflightEntryResult result = bookWorkflow.preflight(bookAccess, command);
    responseWriter.writePostEntryResult(result);
    return exitCodeFor(result);
  }

  private int runPostEntryCommand(BookAccess bookAccess, Path requestFile) {
    PostEntryCommand command = requestReader.readPostEntryCommand(requestFile);
    CommitEntryResult result = bookWorkflow.commit(bookAccess, command);
    responseWriter.writePostEntryResult(result);
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

  private CliFailure runtimeFailure(RuntimeException exception) {
    String message = message(exception);
    String hint =
        switch (SqliteFailureClassifier.classify(exception)) {
          case MANAGED_RUNTIME ->
              "Run the published FinGrind bundle launcher (bin/fingrind on macOS/Linux or bin\\fingrind.cmd on Windows), or for a local source checkout build the managed SQLite runtime with ./gradlew prepareManagedSqlite and set FINGRIND_SQLITE_LIBRARY before rerunning.";
          case STORAGE ->
              "Inspect the selected book file path, chosen book passphrase source, initialization state, filesystem permissions, and the SQLite runtime message, then rerun after fixing the underlying storage problem.";
          case OTHER ->
              "Inspect the message and rerun after fixing the underlying runtime problem.";
        };
    return new CliFailure("runtime-failure", message, hint, null);
  }

  private static String message(Exception exception) {
    return Objects.requireNonNullElse(exception.getMessage(), "CLI command failed.");
  }

  /** Execution seam for routing CLI commands through the selected book adapter. */
  interface BookWorkflow {
    /** Opens the selected book and installs the canonical FinGrind schema when possible. */
    OpenBookResult openBook(BookAccess bookAccess);

    /** Rotates the passphrase that protects one existing book file. */
    RekeyBookResult rekeyBook(
        BookAccess bookAccess, BookAccess.PassphraseSource replacementPassphraseSource);

    /** Declares or reactivates one account inside the selected book. */
    DeclareAccountResult declareAccount(BookAccess bookAccess, DeclareAccountCommand command);

    /** Inspects one selected book for lifecycle and compatibility state. */
    BookInspection inspectBook(BookAccess bookAccess);

    /** Lists the declared accounts currently stored in the selected book. */
    ListAccountsResult listAccounts(BookAccess bookAccess, ListAccountsQuery query);

    /** Returns one committed posting by durable posting identity. */
    GetPostingResult getPosting(BookAccess bookAccess, dev.erst.fingrind.core.PostingId postingId);

    /** Lists one filtered page of committed postings. */
    ListPostingsResult listPostings(BookAccess bookAccess, ListPostingsQuery query);

    /** Computes per-currency balances for one declared account. */
    AccountBalanceResult accountBalance(BookAccess bookAccess, AccountBalanceQuery query);

    /** Executes one ordered AI-agent ledger plan atomically. */
    LedgerPlanResult executePlan(BookAccess bookAccess, LedgerPlan plan);

    /** Validates a posting request without mutating the selected book. */
    PreflightEntryResult preflight(BookAccess bookAccess, PostEntryCommand command);

    /** Commits a posting request into the selected book. */
    CommitEntryResult commit(BookAccess bookAccess, PostEntryCommand command);
  }

  /** Default workflow that opens one SQLite-backed book file per command. */
  private static final class SqliteBookWorkflow implements BookWorkflow {
    private final Clock clock;
    private final CliBookPassphraseResolver passphraseResolver;

    private SqliteBookWorkflow(Clock clock, CliBookPassphraseResolver passphraseResolver) {
      this.clock = Objects.requireNonNull(clock, "clock");
      this.passphraseResolver = Objects.requireNonNull(passphraseResolver, "passphraseResolver");
    }

    @Override
    public OpenBookResult openBook(BookAccess bookAccess) {
      try (SqlitePostingFactStore bookSession =
          openBookSession(
              bookAccess,
              SqlitePostingFactStore.AccessMode.READ_WRITE_CREATE,
              CliBookPassphraseResolver.PromptStyle.CONFIRMED_NEW_SECRET)) {
        return new BookAdministrationService(bookSession.administrationSession(), clock).openBook();
      }
    }

    @Override
    public RekeyBookResult rekeyBook(
        BookAccess bookAccess, BookAccess.PassphraseSource replacementPassphraseSource) {
      try (SqlitePostingFactStore bookSession =
              openBookSession(
                  bookAccess,
                  SqlitePostingFactStore.AccessMode.READ_WRITE_EXISTING,
                  CliBookPassphraseResolver.PromptStyle.SINGLE);
          var replacementPassphrase =
              passphraseResolver.resolve(
                  bookAccess.bookFilePath(),
                  replacementPassphraseSource,
                  CliBookPassphraseResolver.PromptStyle.CONFIRMED_NEW_SECRET)) {
        return bookSession.rekeyBook(replacementPassphrase);
      }
    }

    @Override
    public DeclareAccountResult declareAccount(
        BookAccess bookAccess, DeclareAccountCommand command) {
      try (SqlitePostingFactStore bookSession =
          openBookSession(
              bookAccess,
              SqlitePostingFactStore.AccessMode.READ_WRITE_EXISTING,
              CliBookPassphraseResolver.PromptStyle.SINGLE)) {
        return new BookAdministrationService(bookSession.administrationSession(), clock)
            .declareAccount(command);
      }
    }

    @Override
    public BookInspection inspectBook(BookAccess bookAccess) {
      try (SqlitePostingFactStore bookSession =
          openBookSession(
              bookAccess,
              SqlitePostingFactStore.AccessMode.READ_ONLY,
              CliBookPassphraseResolver.PromptStyle.SINGLE)) {
        return new BookQueryService(bookSession.querySession()).inspectBook();
      }
    }

    @Override
    public ListAccountsResult listAccounts(BookAccess bookAccess, ListAccountsQuery query) {
      try (SqlitePostingFactStore bookSession =
          openBookSession(
              bookAccess,
              SqlitePostingFactStore.AccessMode.READ_ONLY,
              CliBookPassphraseResolver.PromptStyle.SINGLE)) {
        return new BookQueryService(bookSession.querySession()).listAccounts(query);
      }
    }

    @Override
    public GetPostingResult getPosting(
        BookAccess bookAccess, dev.erst.fingrind.core.PostingId postingId) {
      try (SqlitePostingFactStore bookSession =
          openBookSession(
              bookAccess,
              SqlitePostingFactStore.AccessMode.READ_ONLY,
              CliBookPassphraseResolver.PromptStyle.SINGLE)) {
        return new BookQueryService(bookSession.querySession()).getPosting(postingId);
      }
    }

    @Override
    public ListPostingsResult listPostings(BookAccess bookAccess, ListPostingsQuery query) {
      try (SqlitePostingFactStore bookSession =
          openBookSession(
              bookAccess,
              SqlitePostingFactStore.AccessMode.READ_ONLY,
              CliBookPassphraseResolver.PromptStyle.SINGLE)) {
        return new BookQueryService(bookSession.querySession()).listPostings(query);
      }
    }

    @Override
    public AccountBalanceResult accountBalance(BookAccess bookAccess, AccountBalanceQuery query) {
      try (SqlitePostingFactStore bookSession =
          openBookSession(
              bookAccess,
              SqlitePostingFactStore.AccessMode.READ_ONLY,
              CliBookPassphraseResolver.PromptStyle.SINGLE)) {
        return new BookQueryService(bookSession.querySession()).accountBalance(query);
      }
    }

    @Override
    public LedgerPlanResult executePlan(BookAccess bookAccess, LedgerPlan plan) {
      boolean initializesBook = plan.beginsWithOpenBook();
      try (SqlitePostingFactStore bookSession =
          openBookSession(
              bookAccess,
              SqlitePostingFactStore.AccessMode.PLAN_EXECUTION,
              initializesBook
                  ? CliBookPassphraseResolver.PromptStyle.CONFIRMED_NEW_SECRET
                  : CliBookPassphraseResolver.PromptStyle.SINGLE)) {
        return new LedgerPlanService(bookSession, new UuidV7PostingIdGenerator(), clock)
            .execute(plan);
      }
    }

    @Override
    public PreflightEntryResult preflight(BookAccess bookAccess, PostEntryCommand command) {
      try (SqlitePostingFactStore bookSession =
          openBookSession(
              bookAccess,
              SqlitePostingFactStore.AccessMode.READ_ONLY,
              CliBookPassphraseResolver.PromptStyle.SINGLE)) {
        return postingApplicationService(bookSession.postingSession(), clock).preflight(command);
      }
    }

    @Override
    public CommitEntryResult commit(BookAccess bookAccess, PostEntryCommand command) {
      try (SqlitePostingFactStore bookSession =
          openBookSession(
              bookAccess,
              SqlitePostingFactStore.AccessMode.READ_WRITE_EXISTING,
              CliBookPassphraseResolver.PromptStyle.SINGLE)) {
        return postingApplicationService(bookSession.postingSession(), clock).commit(command);
      }
    }

    private SqlitePostingFactStore openBookSession(
        BookAccess bookAccess,
        SqlitePostingFactStore.AccessMode accessMode,
        CliBookPassphraseResolver.PromptStyle promptStyle) {
      return new SqlitePostingFactStore(
          bookAccess.bookFilePath(),
          passphraseResolver.resolve(bookAccess, promptStyle),
          accessMode);
    }

    private static PostingApplicationService postingApplicationService(
        PostingBookSession bookSession, Clock clock) {
      return new PostingApplicationService(bookSession, new UuidV7PostingIdGenerator(), clock);
    }
  }
}
