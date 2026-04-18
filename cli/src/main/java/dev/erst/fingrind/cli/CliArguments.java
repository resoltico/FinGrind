package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.PostingPageCursor;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolLimits;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Parses raw command-line arguments into a typed FinGrind CLI command. */
final class CliArguments {
  private CliArguments() {}

  /** Parses the raw CLI arguments into the corresponding command model. */
  static CliCommand parse(String[] args) {
    Objects.requireNonNull(args, "args must not be null");
    List<String> arguments = List.of(args);
    if (arguments.isEmpty()) {
      return new CliCommand.Help();
    }
    ProtocolOperation operation =
        ProtocolCatalog.findByToken(arguments.getFirst())
            .orElseThrow(() -> unknownCommand(arguments.getFirst()));
    return switch (operation.id()) {
      case HELP -> parseSingleToken(arguments, new CliCommand.Help());
      case VERSION -> parseSingleToken(arguments, new CliCommand.Version());
      case CAPABILITIES -> parseSingleToken(arguments, new CliCommand.Capabilities());
      case PRINT_REQUEST_TEMPLATE ->
          parseSingleToken(arguments, new CliCommand.PrintRequestTemplate());
      case PRINT_PLAN_TEMPLATE -> parseSingleToken(arguments, new CliCommand.PrintPlanTemplate());
      case GENERATE_BOOK_KEY_FILE -> parseGenerateBookKeyFileCommand(arguments);
      case OPEN_BOOK -> parseBookOnlyCommand(arguments, CliCommand.OpenBook::new);
      case REKEY_BOOK -> parseRekeyBookCommand(arguments);
      case DECLARE_ACCOUNT -> parseRequestBoundCommand(arguments, CliCommand.DeclareAccount::new);
      case INSPECT_BOOK -> parseBookOnlyCommand(arguments, CliCommand.InspectBook::new);
      case LIST_ACCOUNTS -> parseListAccountsCommand(arguments);
      case GET_POSTING -> parseGetPostingCommand(arguments);
      case LIST_POSTINGS -> parseListPostingsCommand(arguments);
      case ACCOUNT_BALANCE -> parseAccountBalanceCommand(arguments);
      case EXECUTE_PLAN -> parseRequestBoundCommand(arguments, CliCommand.ExecutePlan::new);
      case PREFLIGHT_ENTRY -> parseRequestBoundCommand(arguments, CliCommand.PreflightEntry::new);
      case POST_ENTRY -> parseRequestBoundCommand(arguments, CliCommand.PostEntry::new);
    };
  }

  private static CliCommand parseSingleToken(List<String> arguments, CliCommand command) {
    if (arguments.size() != 1) {
      throw invalid(arguments.get(1), "This command does not accept additional arguments.");
    }
    return command;
  }

  private static CliCommand parseBookOnlyCommand(
      List<String> arguments, BookOnlyCommandFactory commandFactory) {
    ParsedBookArguments parsedArguments = parseBookOnlyArguments(arguments);
    return commandFactory.create(parsedArguments.bookAccess());
  }

  private static CliCommand parseGenerateBookKeyFileCommand(List<String> arguments) {
    Path bookKeyFilePath = null;
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (!ProtocolOptions.BOOK_KEY_FILE.equals(argument)) {
        throw invalid(argument, "Unsupported argument: " + argument);
      }
      if (bookKeyFilePath != null) {
        throw invalid(
            ProtocolOptions.BOOK_KEY_FILE, "Duplicate argument: " + ProtocolOptions.BOOK_KEY_FILE);
      }
      bookKeyFilePath = Path.of(requireValue(argumentIterator, ProtocolOptions.BOOK_KEY_FILE));
    }
    if (bookKeyFilePath == null) {
      throw invalid(
          ProtocolOptions.BOOK_KEY_FILE,
          "A " + ProtocolOptions.BOOK_KEY_FILE + " argument is required.");
    }
    return new CliCommand.GenerateBookKeyFile(bookKeyFilePath);
  }

  private static CliCommand parseRequestBoundCommand(
      List<String> arguments, RequestBoundCommandFactory commandFactory) {
    ParsedBookArguments parsedArguments = parseRequestBoundArguments(arguments);
    return commandFactory.create(
        parsedArguments.bookAccess(), parsedArguments.optionalRequestFile().orElseThrow());
  }

  private static CliCommand parseGetPostingCommand(List<String> arguments) {
    ParsedBookArguments parsedArguments = parseBookAndCommandArguments(arguments);
    String postingIdValue = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (!ProtocolOptions.POSTING_ID.equals(argument)) {
        throw invalid(argument, "Unsupported argument: " + argument);
      }
      if (postingIdValue != null) {
        throw invalid(
            ProtocolOptions.POSTING_ID, "Duplicate argument: " + ProtocolOptions.POSTING_ID);
      }
      postingIdValue = requireValue(argumentIterator, ProtocolOptions.POSTING_ID);
    }
    if (postingIdValue == null) {
      throw invalid(
          ProtocolOptions.POSTING_ID, "A " + ProtocolOptions.POSTING_ID + " argument is required.");
    }
    return new CliCommand.GetPosting(parsedArguments.bookAccess(), new PostingId(postingIdValue));
  }

  private static CliCommand parseListAccountsCommand(List<String> arguments) {
    ParsedBookArguments parsedArguments = parseBookAndCommandArguments(arguments);
    Integer limit = null;
    Integer offset = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      switch (argument) {
        case ProtocolOptions.LIMIT -> {
          if (limit != null) {
            throw invalid(ProtocolOptions.LIMIT, "Duplicate argument: " + ProtocolOptions.LIMIT);
          }
          limit =
              parseIntegerOption(
                  requireValue(argumentIterator, ProtocolOptions.LIMIT), ProtocolOptions.LIMIT);
        }
        case ProtocolOptions.OFFSET -> {
          if (offset != null) {
            throw invalid(ProtocolOptions.OFFSET, "Duplicate argument: " + ProtocolOptions.OFFSET);
          }
          offset =
              parseIntegerOption(
                  requireValue(argumentIterator, ProtocolOptions.OFFSET), ProtocolOptions.OFFSET);
        }
        default -> throw invalid(argument, "Unsupported argument: " + argument);
      }
    }
    return new CliCommand.ListAccounts(
        parsedArguments.bookAccess(),
        new ListAccountsQuery(
            limit == null ? ProtocolLimits.DEFAULT_PAGE_LIMIT : limit,
            offset == null ? ProtocolLimits.DEFAULT_PAGE_OFFSET : offset));
  }

  private static CliCommand parseListPostingsCommand(List<String> arguments) {
    ParsedBookArguments parsedArguments = parseBookAndCommandArguments(arguments);
    @Nullable String accountCodeValue = null;
    @Nullable LocalDate effectiveDateFrom = null;
    @Nullable LocalDate effectiveDateTo = null;
    Integer limit = null;
    @Nullable String cursor = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      switch (argument) {
        case ProtocolOptions.ACCOUNT_CODE -> {
          if (accountCodeValue != null) {
            throw invalid(
                ProtocolOptions.ACCOUNT_CODE,
                "Duplicate argument: " + ProtocolOptions.ACCOUNT_CODE);
          }
          accountCodeValue = requireValue(argumentIterator, ProtocolOptions.ACCOUNT_CODE);
        }
        case ProtocolOptions.EFFECTIVE_DATE_FROM -> {
          if (effectiveDateFrom != null) {
            throw invalid(
                ProtocolOptions.EFFECTIVE_DATE_FROM,
                "Duplicate argument: " + ProtocolOptions.EFFECTIVE_DATE_FROM);
          }
          effectiveDateFrom =
              parseLocalDateOption(
                  requireValue(argumentIterator, ProtocolOptions.EFFECTIVE_DATE_FROM),
                  ProtocolOptions.EFFECTIVE_DATE_FROM);
        }
        case ProtocolOptions.EFFECTIVE_DATE_TO -> {
          if (effectiveDateTo != null) {
            throw invalid(
                ProtocolOptions.EFFECTIVE_DATE_TO,
                "Duplicate argument: " + ProtocolOptions.EFFECTIVE_DATE_TO);
          }
          effectiveDateTo =
              parseLocalDateOption(
                  requireValue(argumentIterator, ProtocolOptions.EFFECTIVE_DATE_TO),
                  ProtocolOptions.EFFECTIVE_DATE_TO);
        }
        case ProtocolOptions.LIMIT -> {
          if (limit != null) {
            throw invalid(ProtocolOptions.LIMIT, "Duplicate argument: " + ProtocolOptions.LIMIT);
          }
          limit =
              parseIntegerOption(
                  requireValue(argumentIterator, ProtocolOptions.LIMIT), ProtocolOptions.LIMIT);
        }
        case ProtocolOptions.CURSOR -> {
          if (cursor != null) {
            throw invalid(ProtocolOptions.CURSOR, "Duplicate argument: " + ProtocolOptions.CURSOR);
          }
          cursor = requireValue(argumentIterator, ProtocolOptions.CURSOR);
        }
        default -> throw invalid(argument, "Unsupported argument: " + argument);
      }
    }
    return new CliCommand.ListPostings(
        parsedArguments.bookAccess(),
        new ListPostingsQuery(
            Optional.ofNullable(accountCodeValue).map(AccountCode::new),
            Optional.ofNullable(effectiveDateFrom),
            Optional.ofNullable(effectiveDateTo),
            limit == null ? ProtocolLimits.DEFAULT_PAGE_LIMIT : limit,
            Optional.ofNullable(cursor).map(PostingPageCursor::fromWireValue)));
  }

  private static CliCommand parseAccountBalanceCommand(List<String> arguments) {
    ParsedBookArguments parsedArguments = parseBookAndCommandArguments(arguments);
    String accountCodeValue = null;
    @Nullable LocalDate effectiveDateFrom = null;
    @Nullable LocalDate effectiveDateTo = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      switch (argument) {
        case ProtocolOptions.ACCOUNT_CODE -> {
          if (accountCodeValue != null) {
            throw invalid(
                ProtocolOptions.ACCOUNT_CODE,
                "Duplicate argument: " + ProtocolOptions.ACCOUNT_CODE);
          }
          accountCodeValue = requireValue(argumentIterator, ProtocolOptions.ACCOUNT_CODE);
        }
        case ProtocolOptions.EFFECTIVE_DATE_FROM -> {
          if (effectiveDateFrom != null) {
            throw invalid(
                ProtocolOptions.EFFECTIVE_DATE_FROM,
                "Duplicate argument: " + ProtocolOptions.EFFECTIVE_DATE_FROM);
          }
          effectiveDateFrom =
              parseLocalDateOption(
                  requireValue(argumentIterator, ProtocolOptions.EFFECTIVE_DATE_FROM),
                  ProtocolOptions.EFFECTIVE_DATE_FROM);
        }
        case ProtocolOptions.EFFECTIVE_DATE_TO -> {
          if (effectiveDateTo != null) {
            throw invalid(
                ProtocolOptions.EFFECTIVE_DATE_TO,
                "Duplicate argument: " + ProtocolOptions.EFFECTIVE_DATE_TO);
          }
          effectiveDateTo =
              parseLocalDateOption(
                  requireValue(argumentIterator, ProtocolOptions.EFFECTIVE_DATE_TO),
                  ProtocolOptions.EFFECTIVE_DATE_TO);
        }
        default -> throw invalid(argument, "Unsupported argument: " + argument);
      }
    }
    if (accountCodeValue == null) {
      throw invalid(
          ProtocolOptions.ACCOUNT_CODE,
          "A " + ProtocolOptions.ACCOUNT_CODE + " argument is required.");
    }
    return new CliCommand.AccountBalance(
        parsedArguments.bookAccess(),
        new AccountBalanceQuery(
            new AccountCode(accountCodeValue),
            Optional.ofNullable(effectiveDateFrom),
            Optional.ofNullable(effectiveDateTo)));
  }

  private static CliCommand parseRekeyBookCommand(List<String> arguments) {
    Path bookFilePath = null;
    Path currentBookKeyFilePath = null;
    Path replacementBookKeyFilePath = null;
    PassphraseSourceKind currentPassphraseSourceKind = null;
    PassphraseSourceKind replacementPassphraseSourceKind = null;
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      switch (argument) {
        case ProtocolOptions.BOOK_FILE -> {
          if (bookFilePath != null) {
            throw invalid(
                ProtocolOptions.BOOK_FILE, "Duplicate argument: " + ProtocolOptions.BOOK_FILE);
          }
          bookFilePath = Path.of(requireValue(argumentIterator, ProtocolOptions.BOOK_FILE));
        }
        case ProtocolOptions.BOOK_KEY_FILE -> {
          currentPassphraseSourceKind =
              requireSinglePassphraseSource(
                  currentPassphraseSourceKind, PassphraseSourceKind.KEY_FILE);
          currentBookKeyFilePath =
              Path.of(requireValue(argumentIterator, ProtocolOptions.BOOK_KEY_FILE));
        }
        case ProtocolOptions.BOOK_PASSPHRASE_STDIN -> {
          currentPassphraseSourceKind =
              requireSinglePassphraseSource(
                  currentPassphraseSourceKind, PassphraseSourceKind.STANDARD_INPUT);
        }
        case ProtocolOptions.BOOK_PASSPHRASE_PROMPT -> {
          currentPassphraseSourceKind =
              requireSinglePassphraseSource(
                  currentPassphraseSourceKind, PassphraseSourceKind.INTERACTIVE_PROMPT);
        }
        case ProtocolOptions.NEW_BOOK_KEY_FILE -> {
          replacementPassphraseSourceKind =
              requireSingleReplacementPassphraseSource(
                  replacementPassphraseSourceKind, PassphraseSourceKind.KEY_FILE);
          replacementBookKeyFilePath =
              Path.of(requireValue(argumentIterator, ProtocolOptions.NEW_BOOK_KEY_FILE));
        }
        case ProtocolOptions.NEW_BOOK_PASSPHRASE_STDIN -> {
          replacementPassphraseSourceKind =
              requireSingleReplacementPassphraseSource(
                  replacementPassphraseSourceKind, PassphraseSourceKind.STANDARD_INPUT);
        }
        case ProtocolOptions.NEW_BOOK_PASSPHRASE_PROMPT -> {
          replacementPassphraseSourceKind =
              requireSingleReplacementPassphraseSource(
                  replacementPassphraseSourceKind, PassphraseSourceKind.INTERACTIVE_PROMPT);
        }
        default -> throw invalid(argument, "Unsupported argument: " + argument);
      }
    }
    if (bookFilePath == null) {
      throw invalid(
          ProtocolOptions.BOOK_FILE, "A " + ProtocolOptions.BOOK_FILE + " argument is required.");
    }
    if (currentPassphraseSourceKind == null) {
      throw invalid(
          ProtocolOptions.BOOK_KEY_FILE,
          "Exactly one current book passphrase source is required: "
              + ProtocolOptions.BOOK_KEY_FILE
              + " <path>, "
              + ProtocolOptions.BOOK_PASSPHRASE_STDIN
              + ", or "
              + ProtocolOptions.BOOK_PASSPHRASE_PROMPT
              + ".");
    }
    if (replacementPassphraseSourceKind == null) {
      throw invalid(
          ProtocolOptions.NEW_BOOK_KEY_FILE,
          "Exactly one replacement book passphrase source is required: "
              + ProtocolOptions.NEW_BOOK_KEY_FILE
              + " <path>, "
              + ProtocolOptions.NEW_BOOK_PASSPHRASE_STDIN
              + ", or "
              + ProtocolOptions.NEW_BOOK_PASSPHRASE_PROMPT
              + ".");
    }
    BookAccess.PassphraseSource currentPassphraseSource =
        passphraseSource(currentPassphraseSourceKind, currentBookKeyFilePath);
    BookAccess.PassphraseSource replacementPassphraseSource =
        passphraseSource(replacementPassphraseSourceKind, replacementBookKeyFilePath);
    validateDistinctRekeyPaths(bookFilePath, currentPassphraseSource, replacementPassphraseSource);
    validateRekeyStandardInputUsage(currentPassphraseSource, replacementPassphraseSource);
    return new CliCommand.RekeyBook(
        new BookAccess(bookFilePath, currentPassphraseSource), replacementPassphraseSource);
  }

  private static ParsedBookArguments parseBookOnlyArguments(List<String> arguments) {
    return parseBookArguments(arguments, BookArgumentMode.BOOK_ONLY);
  }

  private static ParsedBookArguments parseRequestBoundArguments(List<String> arguments) {
    return parseBookArguments(arguments, BookArgumentMode.REQUEST_BOUND);
  }

  private static ParsedBookArguments parseBookAndCommandArguments(List<String> arguments) {
    return parseBookArguments(arguments, BookArgumentMode.BOOK_WITH_COMMAND_ARGUMENTS);
  }

  private static ParsedBookArguments parseBookArguments(
      List<String> arguments, BookArgumentMode mode) {
    Path bookFilePath = null;
    Path bookKeyFilePath = null;
    PassphraseSourceKind passphraseSourceKind = null;
    Path requestFile = null;
    List<String> commandArguments = new java.util.ArrayList<>();
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      switch (argument) {
        case ProtocolOptions.BOOK_FILE -> {
          if (bookFilePath != null) {
            throw invalid(
                ProtocolOptions.BOOK_FILE, "Duplicate argument: " + ProtocolOptions.BOOK_FILE);
          }
          bookFilePath = Path.of(requireValue(argumentIterator, ProtocolOptions.BOOK_FILE));
        }
        case ProtocolOptions.BOOK_KEY_FILE -> {
          passphraseSourceKind =
              requireSinglePassphraseSource(passphraseSourceKind, PassphraseSourceKind.KEY_FILE);
          bookKeyFilePath = Path.of(requireValue(argumentIterator, ProtocolOptions.BOOK_KEY_FILE));
        }
        case ProtocolOptions.BOOK_PASSPHRASE_STDIN -> {
          passphraseSourceKind =
              requireSinglePassphraseSource(
                  passphraseSourceKind, PassphraseSourceKind.STANDARD_INPUT);
        }
        case ProtocolOptions.BOOK_PASSPHRASE_PROMPT -> {
          passphraseSourceKind =
              requireSinglePassphraseSource(
                  passphraseSourceKind, PassphraseSourceKind.INTERACTIVE_PROMPT);
        }
        case ProtocolOptions.REQUEST_FILE -> {
          if (!mode.acceptsRequestFile()) {
            throw invalid(argument, "Unsupported argument: " + argument);
          }
          if (requestFile != null) {
            throw invalid(
                ProtocolOptions.REQUEST_FILE,
                "Duplicate argument: " + ProtocolOptions.REQUEST_FILE);
          }
          requestFile = Path.of(requireValue(argumentIterator, ProtocolOptions.REQUEST_FILE));
        }
        default -> {
          if (!mode.collectsCommandArguments()) {
            throw invalid(argument, "Unsupported argument: " + argument);
          }
          commandArguments.add(argument);
        }
      }
    }
    if (bookFilePath == null) {
      throw invalid(
          ProtocolOptions.BOOK_FILE, "A " + ProtocolOptions.BOOK_FILE + " argument is required.");
    }
    if (passphraseSourceKind == null) {
      throw invalid(
          ProtocolOptions.BOOK_KEY_FILE,
          "Exactly one book passphrase source is required: "
              + ProtocolOptions.BOOK_KEY_FILE
              + " <path>, "
              + ProtocolOptions.BOOK_PASSPHRASE_STDIN
              + ", or "
              + ProtocolOptions.BOOK_PASSPHRASE_PROMPT
              + ".");
    }
    if (mode.acceptsRequestFile() && requestFile == null) {
      throw invalid(
          ProtocolOptions.REQUEST_FILE,
          "A " + ProtocolOptions.REQUEST_FILE + " argument is required.");
    }
    BookAccess.PassphraseSource passphraseSource =
        passphraseSource(passphraseSourceKind, bookKeyFilePath);
    validateDistinctPaths(bookFilePath, passphraseSource, requestFile);
    validateStandardInputUsage(passphraseSource, requestFile);
    return new ParsedBookArguments(
        new BookAccess(bookFilePath, passphraseSource), requestFile, commandArguments);
  }

  private static PassphraseSourceKind requireSinglePassphraseSource(
      @Nullable PassphraseSourceKind currentSource, PassphraseSourceKind candidateSource) {
    Objects.requireNonNull(candidateSource, "candidateSource");
    if (currentSource != null) {
      throw invalid(
          candidateSource.optionName(),
          "Exactly one book passphrase source is permitted per command.");
    }
    return candidateSource;
  }

  private static PassphraseSourceKind requireSingleReplacementPassphraseSource(
      @Nullable PassphraseSourceKind currentSource, PassphraseSourceKind candidateSource) {
    Objects.requireNonNull(candidateSource, "candidateSource");
    if (currentSource != null) {
      throw invalid(
          replacementOptionName(candidateSource),
          "Exactly one replacement book passphrase source is permitted per command.");
    }
    return candidateSource;
  }

  private static BookAccess.PassphraseSource passphraseSource(
      @Nullable PassphraseSourceKind passphraseSourceKind, @Nullable Path bookKeyFilePath) {
    return switch (Objects.requireNonNull(passphraseSourceKind, "passphraseSourceKind")) {
      case KEY_FILE ->
          new BookAccess.PassphraseSource.KeyFile(
              Objects.requireNonNull(bookKeyFilePath, "bookKeyFilePath"));
      case STANDARD_INPUT -> BookAccess.PassphraseSource.StandardInput.INSTANCE;
      case INTERACTIVE_PROMPT -> BookAccess.PassphraseSource.InteractivePrompt.INSTANCE;
    };
  }

  private static void validateDistinctPaths(
      Path bookFilePath, BookAccess.PassphraseSource passphraseSource, @Nullable Path requestFile) {
    Optional<Path> keyFilePath = keyFilePath(passphraseSource);
    if (keyFilePath.isPresent()
        && bookFilePath.toAbsolutePath().equals(keyFilePath.orElseThrow().toAbsolutePath())) {
      throw invalid(
          ProtocolOptions.BOOK_KEY_FILE,
          ProtocolOptions.BOOK_FILE
              + " and "
              + ProtocolOptions.BOOK_KEY_FILE
              + " must not point to the same path.");
    }
    if (requestFile != null && bookFilePath.toAbsolutePath().equals(requestFile.toAbsolutePath())) {
      throw invalid(
          ProtocolOptions.REQUEST_FILE,
          ProtocolOptions.BOOK_FILE
              + " and "
              + ProtocolOptions.REQUEST_FILE
              + " must not point to the same path.");
    }
    if (requestFile != null
        && keyFilePath.isPresent()
        && keyFilePath.orElseThrow().toAbsolutePath().equals(requestFile.toAbsolutePath())) {
      throw invalid(
          ProtocolOptions.BOOK_KEY_FILE,
          ProtocolOptions.BOOK_KEY_FILE
              + " and "
              + ProtocolOptions.REQUEST_FILE
              + " must not point to the same path.");
    }
  }

  private static void validateStandardInputUsage(
      BookAccess.PassphraseSource passphraseSource, @Nullable Path requestFile) {
    if (requestFile != null
        && ProtocolOptions.STDIN_TOKEN.equals(requestFile.toString())
        && isStandardInput(passphraseSource)) {
      throw invalid(
          ProtocolOptions.BOOK_PASSPHRASE_STDIN,
          "Standard input cannot supply both the book passphrase and the request JSON.");
    }
  }

  private static void validateDistinctRekeyPaths(
      Path bookFilePath,
      BookAccess.PassphraseSource currentPassphraseSource,
      BookAccess.PassphraseSource replacementPassphraseSource) {
    validateDistinctPaths(bookFilePath, currentPassphraseSource, null);
    validateDistinctPaths(bookFilePath, replacementPassphraseSource, null);
    Optional<Path> currentKeyFilePath = keyFilePath(currentPassphraseSource);
    Optional<Path> replacementKeyFilePath = keyFilePath(replacementPassphraseSource);
    if (currentKeyFilePath.isPresent()
        && replacementKeyFilePath.isPresent()
        && currentKeyFilePath
            .orElseThrow()
            .toAbsolutePath()
            .equals(replacementKeyFilePath.orElseThrow().toAbsolutePath())) {
      throw invalid(
          ProtocolOptions.NEW_BOOK_KEY_FILE,
          ProtocolOptions.BOOK_KEY_FILE
              + " and "
              + ProtocolOptions.NEW_BOOK_KEY_FILE
              + " must not point to the same path.");
    }
  }

  private static void validateRekeyStandardInputUsage(
      BookAccess.PassphraseSource currentPassphraseSource,
      BookAccess.PassphraseSource replacementPassphraseSource) {
    if (isStandardInput(currentPassphraseSource) && isStandardInput(replacementPassphraseSource)) {
      throw invalid(
          ProtocolOptions.NEW_BOOK_PASSPHRASE_STDIN,
          "Standard input cannot supply both the current and replacement book passphrases.");
    }
  }

  private static Optional<Path> keyFilePath(BookAccess.PassphraseSource passphraseSource) {
    return switch (passphraseSource) {
      case BookAccess.PassphraseSource.KeyFile keyFile -> Optional.of(keyFile.bookKeyFilePath());
      case BookAccess.PassphraseSource.StandardInput _ -> Optional.empty();
      case BookAccess.PassphraseSource.InteractivePrompt _ -> Optional.empty();
    };
  }

  private static boolean isStandardInput(BookAccess.PassphraseSource passphraseSource) {
    return switch (passphraseSource) {
      case BookAccess.PassphraseSource.KeyFile _ -> false;
      case BookAccess.PassphraseSource.StandardInput _ -> true;
      case BookAccess.PassphraseSource.InteractivePrompt _ -> false;
    };
  }

  private static int parseIntegerOption(String rawValue, String optionName) {
    try {
      return Integer.parseInt(rawValue);
    } catch (NumberFormatException exception) {
      throw invalid(optionName, "Option must be an integer: " + optionName, exception);
    }
  }

  private static LocalDate parseLocalDateOption(String rawValue, String optionName) {
    try {
      return LocalDate.parse(rawValue);
    } catch (java.time.DateTimeException exception) {
      throw invalid(optionName, "Option must be an ISO-8601 local date: " + optionName, exception);
    }
  }

  private static String requireValue(ListIterator<String> argumentIterator, String optionName) {
    if (!argumentIterator.hasNext()) {
      throw invalid(optionName, "Missing value for " + optionName + ".");
    }
    return argumentIterator.next();
  }

  private static CliArgumentsException invalid(String argument, String message) {
    return new CliArgumentsException(
        "invalid-request",
        argument,
        message,
        "Run 'fingrind "
            + ProtocolCatalog.operationName(OperationId.HELP)
            + "' to inspect the supported command syntax.");
  }

  private static CliArgumentsException invalid(String argument, String message, Throwable cause) {
    return new CliArgumentsException(
        "invalid-request",
        argument,
        message,
        "Run 'fingrind "
            + ProtocolCatalog.operationName(OperationId.HELP)
            + "' to inspect the supported command syntax.",
        cause);
  }

  /** Canonical passphrase-source selections accepted by the CLI parser. */
  private enum PassphraseSourceKind {
    KEY_FILE(ProtocolOptions.BOOK_KEY_FILE),
    STANDARD_INPUT(ProtocolOptions.BOOK_PASSPHRASE_STDIN),
    INTERACTIVE_PROMPT(ProtocolOptions.BOOK_PASSPHRASE_PROMPT);

    private final String optionName;

    PassphraseSourceKind(String optionName) {
      this.optionName = optionName;
    }

    private String optionName() {
      return optionName;
    }
  }

  /** Supported parser shapes for commands that address one selected book file. */
  private enum BookArgumentMode {
    BOOK_ONLY(false, false),
    REQUEST_BOUND(true, false),
    BOOK_WITH_COMMAND_ARGUMENTS(false, true);

    private final boolean acceptsRequestFile;
    private final boolean collectsCommandArguments;

    BookArgumentMode(boolean acceptsRequestFile, boolean collectsCommandArguments) {
      this.acceptsRequestFile = acceptsRequestFile;
      this.collectsCommandArguments = collectsCommandArguments;
    }

    private boolean acceptsRequestFile() {
      return acceptsRequestFile;
    }

    private boolean collectsCommandArguments() {
      return collectsCommandArguments;
    }
  }

  private static String replacementOptionName(PassphraseSourceKind passphraseSourceKind) {
    return switch (Objects.requireNonNull(passphraseSourceKind, "passphraseSourceKind")) {
      case KEY_FILE -> ProtocolOptions.NEW_BOOK_KEY_FILE;
      case STANDARD_INPUT -> ProtocolOptions.NEW_BOOK_PASSPHRASE_STDIN;
      case INTERACTIVE_PROMPT -> ProtocolOptions.NEW_BOOK_PASSPHRASE_PROMPT;
    };
  }

  private static CliArgumentsException unknownCommand(String commandName) {
    return new CliArgumentsException(
        "unknown-command",
        commandName,
        "Unsupported command: " + commandName,
        "Run 'fingrind "
            + ProtocolCatalog.operationName(OperationId.HELP)
            + "' to inspect the supported commands and examples.");
  }

  /** Parsed path arguments shared by commands that address one book file. */
  private record ParsedBookArguments(
      BookAccess bookAccess, @Nullable Path requestFile, List<String> commandArguments) {
    private ParsedBookArguments {
      Objects.requireNonNull(bookAccess, "bookAccess");
      commandArguments = List.copyOf(Objects.requireNonNull(commandArguments, "commandArguments"));
    }

    /** Returns the optional request file when the command expects one. */
    private java.util.Optional<Path> optionalRequestFile() {
      return java.util.Optional.ofNullable(requestFile);
    }
  }

  /** Factory for commands that only need a selected book file. */
  @FunctionalInterface
  private interface BookOnlyCommandFactory {
    /** Creates one CLI command for the provided book path. */
    CliCommand create(BookAccess bookAccess);
  }

  /** Factory for commands that need both a book file and a request payload path. */
  @FunctionalInterface
  private interface RequestBoundCommandFactory {
    /** Creates one CLI command for the provided book and request paths. */
    CliCommand create(BookAccess bookAccess, Path requestFile);
  }
}
