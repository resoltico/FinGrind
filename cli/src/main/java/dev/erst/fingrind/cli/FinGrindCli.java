package dev.erst.fingrind.cli;

import dev.erst.fingrind.application.BookAccess;
import dev.erst.fingrind.application.BookAdministrationService;
import dev.erst.fingrind.application.BookSession;
import dev.erst.fingrind.application.DeclareAccountCommand;
import dev.erst.fingrind.application.DeclareAccountResult;
import dev.erst.fingrind.application.ListAccountsResult;
import dev.erst.fingrind.application.MachineContract;
import dev.erst.fingrind.application.OpenBookResult;
import dev.erst.fingrind.application.PostEntryCommand;
import dev.erst.fingrind.application.PostEntryResult;
import dev.erst.fingrind.application.PostingApplicationService;
import dev.erst.fingrind.application.UuidV7PostingIdGenerator;
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
  private static final String HELP_HINT = "Run 'fingrind help' to inspect the supported commands.";

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
        case CliCommand.OpenBook command -> runOpenBookCommand(command.bookAccess());
        case CliCommand.DeclareAccount command ->
            runDeclareAccountCommand(command.bookAccess(), command.requestFile());
        case CliCommand.ListAccounts command -> runListAccountsCommand(command.bookAccess());
        case CliCommand.PreflightEntry command ->
            runEntryCommand(command.bookAccess(), command.requestFile(), bookWorkflow::preflight);
        case CliCommand.PostEntry command ->
            runEntryCommand(command.bookAccess(), command.requestFile(), bookWorkflow::commit);
      };
    } catch (CliArgumentsException exception) {
      responseWriter.writeFailure(exception.failure());
      return 2;
    } catch (CliRequestException exception) {
      responseWriter.writeFailure(exception.failure());
      return 2;
    } catch (IllegalArgumentException exception) {
      responseWriter.writeFailure(
          new CliFailure("invalid-request", message(exception), HELP_HINT, null));
      return 2;
    } catch (IllegalStateException exception) {
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
    responseWriter.writeRequestTemplate(MachineContract.requestTemplate());
    return 0;
  }

  private int runOpenBookCommand(BookAccess bookAccess) {
    OpenBookResult result = bookWorkflow.openBook(bookAccess);
    responseWriter.writeOpenBookResult(bookAccess.bookFilePath(), result);
    return exitCodeFor(result);
  }

  private int runDeclareAccountCommand(BookAccess bookAccess, Path requestFile) {
    DeclareAccountCommand command = requestReader.readDeclareAccountCommand(requestFile);
    DeclareAccountResult result = bookWorkflow.declareAccount(bookAccess, command);
    responseWriter.writeDeclareAccountResult(result);
    return exitCodeFor(result);
  }

  private int runListAccountsCommand(BookAccess bookAccess) {
    ListAccountsResult result = bookWorkflow.listAccounts(bookAccess);
    responseWriter.writeListAccountsResult(result);
    return exitCodeFor(result);
  }

  private int runEntryCommand(
      BookAccess bookAccess, Path requestFile, EntryOperation entryOperation) {
    PostEntryCommand command = requestReader.readPostEntryCommand(requestFile);
    PostEntryResult result = entryOperation.execute(bookAccess, command);
    responseWriter.writePostEntryResult(result);
    return exitCodeFor(result);
  }

  private static int exitCodeFor(PostEntryResult result) {
    return switch (result) {
      case PostEntryResult.PreflightAccepted _ -> 0;
      case PostEntryResult.Committed _ -> 0;
      case PostEntryResult.Rejected _ -> 2;
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

  private MachineContract.ApplicationIdentity applicationIdentity() {
    return new MachineContract.ApplicationIdentity(
        metadata.applicationName(), metadata.version(), metadata.description());
  }

  private MachineContract.EnvironmentDescriptor environmentDescriptor() {
    return environmentDescriptor(SqliteRuntime.probe());
  }

  static MachineContract.EnvironmentDescriptor environmentDescriptor(
      SqliteRuntime.Probe runtimeProbe) {
    return new MachineContract.EnvironmentDescriptor(
        "26+",
        SqliteRuntime.STORAGE_DRIVER,
        SqliteRuntime.STORAGE_ENGINE,
        SqliteRuntime.BOOK_PROTECTION_MODE,
        SqliteRuntime.DEFAULT_BOOK_CIPHER,
        runtimeProbe.librarySource(),
        runtimeProbe.requiredMinimumSqliteVersion(),
        runtimeProbe.requiredSqlite3mcVersion(),
        runtimeProbe.status().wireValue(),
        runtimeProbe.loadedSqliteVersion(),
        runtimeProbe.loadedSqlite3mcVersion(),
        runtimeProbe.issue());
  }

  private CliFailure runtimeFailure(IllegalStateException exception) {
    String message = message(exception);
    String hint =
        message.contains("SQLite")
            ? "Inspect the selected book file path, chosen book passphrase source, initialization state, filesystem permissions, and the SQLite runtime message, then rerun after fixing the underlying storage problem."
            : "Inspect the message and rerun after fixing the underlying runtime problem.";
    return new CliFailure("runtime-failure", message, hint, null);
  }

  private static String message(Exception exception) {
    return Objects.requireNonNullElse(exception.getMessage(), "CLI command failed.");
  }

  /** Execution seam for routing CLI commands through the selected book adapter. */
  interface BookWorkflow {
    /** Opens the selected book and installs the canonical FinGrind schema when possible. */
    OpenBookResult openBook(BookAccess bookAccess);

    /** Declares or reactivates one account inside the selected book. */
    DeclareAccountResult declareAccount(BookAccess bookAccess, DeclareAccountCommand command);

    /** Lists the declared accounts currently stored in the selected book. */
    ListAccountsResult listAccounts(BookAccess bookAccess);

    /** Validates a posting request without mutating the selected book. */
    PostEntryResult preflight(BookAccess bookAccess, PostEntryCommand command);

    /** Commits a posting request into the selected book. */
    PostEntryResult commit(BookAccess bookAccess, PostEntryCommand command);
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
      try (BookSession bookSession = openBookSession(bookAccess)) {
        return new BookAdministrationService(bookSession, clock).openBook();
      }
    }

    @Override
    public DeclareAccountResult declareAccount(
        BookAccess bookAccess, DeclareAccountCommand command) {
      try (BookSession bookSession = openBookSession(bookAccess)) {
        return new BookAdministrationService(bookSession, clock).declareAccount(command);
      }
    }

    @Override
    public ListAccountsResult listAccounts(BookAccess bookAccess) {
      try (BookSession bookSession = openBookSession(bookAccess)) {
        return new BookAdministrationService(bookSession, clock).listAccounts();
      }
    }

    @Override
    public PostEntryResult preflight(BookAccess bookAccess, PostEntryCommand command) {
      try (BookSession bookSession = openBookSession(bookAccess)) {
        return postingApplicationService(bookSession, clock).preflight(command);
      }
    }

    @Override
    public PostEntryResult commit(BookAccess bookAccess, PostEntryCommand command) {
      try (BookSession bookSession = openBookSession(bookAccess)) {
        return postingApplicationService(bookSession, clock).commit(command);
      }
    }

    private BookSession openBookSession(BookAccess bookAccess) {
      return new SqlitePostingFactStore(
          bookAccess.bookFilePath(), passphraseResolver.resolve(bookAccess));
    }

    private static PostingApplicationService postingApplicationService(
        BookSession bookSession, Clock clock) {
      return new PostingApplicationService(bookSession, new UuidV7PostingIdGenerator(), clock);
    }
  }

  /** Executes one write-boundary command against the selected book file. */
  @FunctionalInterface
  private interface EntryOperation {
    /** Executes the selected write-boundary command. */
    PostEntryResult execute(BookAccess bookAccess, PostEntryCommand command);
  }
}
