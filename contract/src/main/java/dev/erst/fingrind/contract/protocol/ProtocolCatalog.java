package dev.erst.fingrind.contract.protocol;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** Core-owned protocol catalog for FinGrind public operation and model metadata. */
public final class ProtocolCatalog {
  private static final BookModelFacts BOOK_MODEL =
      new BookModelFacts(
          "one SQLite file equals one book",
          "one book belongs to one entity",
          ProtocolOptions.BOOK_FILE + " may point anywhere on the OS filesystem",
          "every book-bound command requires exactly one explicit passphrase source via "
              + ProtocolOptions.BOOK_KEY_FILE
              + ", "
              + ProtocolOptions.BOOK_PASSPHRASE_STDIN
              + ", or "
              + ProtocolOptions.BOOK_PASSPHRASE_PROMPT,
          "books must be opened explicitly before any posting or account declaration",
          "every posting line must reference a declared active account",
          "hard-break only: there is no migration layer, but "
              + OperationId.INSPECT_BOOK.wireName()
              + " exposes detected and supported book-format versions plus compatibility state before mutating commands run",
          "single-currency-per-entry");
  private static final CurrencyFacts CURRENCY =
      new CurrencyFacts(
          BOOK_MODEL.currencyScope(),
          "not-supported",
          "Every journal line inside one entry must share the same currencyCode. Mixed-currency entries are rejected and no multi-currency posting model exists yet.");
  private static final PreflightFacts PREFLIGHT =
      new PreflightFacts(
          "advisory",
          false,
          "Preflight validates the current request against the current book state, but it is not a commit guarantee because durable commit-time checks still run inside the write transaction.");
  private static final List<String> STORAGE_ENGINES = List.of("sqlite");
  private static final List<String> SUCCESS_STATUSES =
      List.of("ok", "preflight-accepted", "committed");
  private static final List<String> SUPPORTED_PUBLIC_CLI_BUNDLE_TARGETS =
      List.of("macos-aarch64", "macos-x86_64", "linux-x86_64", "linux-aarch64", "windows-x86_64");
  private static final List<String> UNSUPPORTED_PUBLIC_CLI_OPERATING_SYSTEMS = List.of();
  private static final List<ProtocolOperation> OPERATIONS =
      List.of(
          operation(
              OperationId.HELP,
              OperationCategory.DISCOVERY,
              "Help",
              List.of("--help", "-h"),
              List.of(),
              ExecutionMode.JSON_ENVELOPE,
              "Print command usage, examples, and workflow guidance.",
              List.of()),
          operation(
              OperationId.VERSION,
              OperationCategory.DISCOVERY,
              "Version",
              List.of("--version"),
              List.of(),
              ExecutionMode.JSON_ENVELOPE,
              "Print application identity, version, and description.",
              List.of()),
          operation(
              OperationId.CAPABILITIES,
              OperationCategory.DISCOVERY,
              "Capabilities",
              List.of(),
              List.of(),
              ExecutionMode.JSON_ENVELOPE,
              "Print the canonical machine-readable contract for commands, request shapes, and responses.",
              List.of()),
          operation(
              OperationId.PRINT_REQUEST_TEMPLATE,
              OperationCategory.DISCOVERY,
              "Print Request Template",
              List.of("--print-request-template"),
              List.of(),
              ExecutionMode.RAW_JSON,
              "Print a minimal valid posting request JSON document.",
              List.of(
                  "fingrind %s > request.json"
                      .formatted(OperationId.PRINT_REQUEST_TEMPLATE.wireName()))),
          operation(
              OperationId.PRINT_PLAN_TEMPLATE,
              OperationCategory.DISCOVERY,
              "Print Plan Template",
              List.of("--print-plan-template"),
              List.of(),
              ExecutionMode.RAW_JSON,
              "Print a minimal valid AI-agent ledger plan JSON document.",
              List.of(
                  "fingrind %s > plan.json".formatted(OperationId.PRINT_PLAN_TEMPLATE.wireName()))),
          operation(
              OperationId.GENERATE_BOOK_KEY_FILE,
              OperationCategory.ADMINISTRATION,
              "Generate Book Key File",
              List.of(),
              List.of(ProtocolOptions.BOOK_KEY_FILE + " <path>"),
              ExecutionMode.JSON_ENVELOPE,
              "Create one new owner-only UTF-8 book key file with a generated high-entropy passphrase.",
              List.of(
                  "fingrind %s %s ./secrets/acme.book-key"
                      .formatted(
                          OperationId.GENERATE_BOOK_KEY_FILE.wireName(),
                          ProtocolOptions.BOOK_KEY_FILE))),
          operation(
              OperationId.OPEN_BOOK,
              OperationCategory.ADMINISTRATION,
              "Open Book",
              List.of(),
              List.of(
                  ProtocolOptions.BOOK_FILE + " <path>",
                  ProtocolOptions.currentPassphraseSourceSyntax()),
              ExecutionMode.JSON_ENVELOPE,
              "Initialize a new book file with the canonical schema.",
              List.of(
                  "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key"
                      .formatted(
                          OperationId.OPEN_BOOK.wireName(),
                          ProtocolOptions.BOOK_FILE,
                          ProtocolOptions.BOOK_KEY_FILE),
                  "fingrind %s %s ./books/acme.sqlite %s"
                      .formatted(
                          OperationId.OPEN_BOOK.wireName(),
                          ProtocolOptions.BOOK_FILE,
                          ProtocolOptions.BOOK_PASSPHRASE_PROMPT),
                  "printf '%s\\n' 'acme-demo-passphrase' | fingrind %s %s ./books/acme.sqlite %s"
                      .formatted(
                          "%s",
                          OperationId.OPEN_BOOK.wireName(),
                          ProtocolOptions.BOOK_FILE,
                          ProtocolOptions.BOOK_PASSPHRASE_STDIN))),
          operation(
              OperationId.REKEY_BOOK,
              OperationCategory.ADMINISTRATION,
              "Rekey Book",
              List.of(),
              List.of(
                  ProtocolOptions.BOOK_FILE + " <path>",
                  ProtocolOptions.currentPassphraseSourceSyntax(),
                  ProtocolOptions.replacementPassphraseSourceSyntax()),
              ExecutionMode.JSON_ENVELOPE,
              "Rotate the passphrase that protects one existing book.",
              List.of(
                  "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s"
                      .formatted(
                          OperationId.REKEY_BOOK.wireName(),
                          ProtocolOptions.BOOK_FILE,
                          ProtocolOptions.BOOK_KEY_FILE,
                          ProtocolOptions.NEW_BOOK_PASSPHRASE_PROMPT))),
          operation(
              OperationId.DECLARE_ACCOUNT,
              OperationCategory.ADMINISTRATION,
              "Declare Account",
              List.of(),
              List.of(
                  ProtocolOptions.BOOK_FILE + " <path>",
                  ProtocolOptions.currentPassphraseSourceSyntax(),
                  ProtocolOptions.REQUEST_FILE + " <path|->"),
              ExecutionMode.JSON_ENVELOPE,
              "Declare or reactivate one account in the selected book.",
              List.of(
                  "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./docs/examples/declare-account-cash.json"
                      .formatted(
                          OperationId.DECLARE_ACCOUNT.wireName(),
                          ProtocolOptions.BOOK_FILE,
                          ProtocolOptions.BOOK_KEY_FILE,
                          ProtocolOptions.REQUEST_FILE),
                  "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./docs/examples/declare-account-revenue.json"
                      .formatted(
                          OperationId.DECLARE_ACCOUNT.wireName(),
                          ProtocolOptions.BOOK_FILE,
                          ProtocolOptions.BOOK_KEY_FILE,
                          ProtocolOptions.REQUEST_FILE))),
          operation(
              OperationId.INSPECT_BOOK,
              OperationCategory.QUERY,
              "Inspect Book",
              List.of(),
              List.of(
                  ProtocolOptions.BOOK_FILE + " <path>",
                  ProtocolOptions.currentPassphraseSourceSyntax()),
              ExecutionMode.JSON_ENVELOPE,
              "Inspect one selected book for lifecycle state, format version, and compatibility.",
              List.of(
                  "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key"
                      .formatted(
                          OperationId.INSPECT_BOOK.wireName(),
                          ProtocolOptions.BOOK_FILE,
                          ProtocolOptions.BOOK_KEY_FILE))),
          operation(
              OperationId.LIST_ACCOUNTS,
              OperationCategory.QUERY,
              "List Accounts",
              List.of(),
              List.of(
                  ProtocolOptions.BOOK_FILE + " <path>",
                  ProtocolOptions.currentPassphraseSourceSyntax(),
                  ProtocolOptions.optionalLimitSyntax(),
                  ProtocolOptions.optionalOffsetSyntax()),
              ExecutionMode.JSON_ENVELOPE,
              "List one stable page of declared accounts in the selected book.",
              List.of(
                  "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s %d"
                      .formatted(
                          OperationId.LIST_ACCOUNTS.wireName(),
                          ProtocolOptions.BOOK_FILE,
                          ProtocolOptions.BOOK_KEY_FILE,
                          ProtocolOptions.LIMIT,
                          ProtocolLimits.DEFAULT_PAGE_LIMIT))),
          operation(
              OperationId.GET_POSTING,
              OperationCategory.QUERY,
              "Get Posting",
              List.of(),
              List.of(
                  ProtocolOptions.BOOK_FILE + " <path>",
                  ProtocolOptions.currentPassphraseSourceSyntax(),
                  ProtocolOptions.POSTING_ID + " <posting-id>"),
              ExecutionMode.JSON_ENVELOPE,
              "Return one committed posting by durable posting identifier.",
              List.of(
                  "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 018f0e6d-7f7e-7b04-b93f-bc0b69f19d5b"
                      .formatted(
                          OperationId.GET_POSTING.wireName(),
                          ProtocolOptions.BOOK_FILE,
                          ProtocolOptions.BOOK_KEY_FILE,
                          ProtocolOptions.POSTING_ID))),
          operation(
              OperationId.LIST_POSTINGS,
              OperationCategory.QUERY,
              "List Postings",
              List.of(),
              List.of(
                  ProtocolOptions.BOOK_FILE + " <path>",
                  ProtocolOptions.currentPassphraseSourceSyntax(),
                  "[" + ProtocolOptions.ACCOUNT_CODE + " <account-code>]",
                  "[" + ProtocolOptions.EFFECTIVE_DATE_FROM + " <YYYY-MM-DD>]",
                  "[" + ProtocolOptions.EFFECTIVE_DATE_TO + " <YYYY-MM-DD>]",
                  ProtocolOptions.optionalLimitSyntax(),
                  ProtocolOptions.optionalOffsetSyntax()),
              ExecutionMode.JSON_ENVELOPE,
              "List one filtered page of committed postings in stable reverse-chronological order.",
              List.of(
                  "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 1000 %s 25"
                      .formatted(
                          OperationId.LIST_POSTINGS.wireName(),
                          ProtocolOptions.BOOK_FILE,
                          ProtocolOptions.BOOK_KEY_FILE,
                          ProtocolOptions.ACCOUNT_CODE,
                          ProtocolOptions.LIMIT))),
          operation(
              OperationId.ACCOUNT_BALANCE,
              OperationCategory.QUERY,
              "Account Balance",
              List.of(),
              List.of(
                  ProtocolOptions.BOOK_FILE + " <path>",
                  ProtocolOptions.currentPassphraseSourceSyntax(),
                  ProtocolOptions.ACCOUNT_CODE + " <account-code>",
                  "[" + ProtocolOptions.EFFECTIVE_DATE_FROM + " <YYYY-MM-DD>]",
                  "[" + ProtocolOptions.EFFECTIVE_DATE_TO + " <YYYY-MM-DD>]"),
              ExecutionMode.JSON_ENVELOPE,
              "Compute grouped per-currency balances for one declared account.",
              List.of(
                  "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s 1000"
                      .formatted(
                          OperationId.ACCOUNT_BALANCE.wireName(),
                          ProtocolOptions.BOOK_FILE,
                          ProtocolOptions.BOOK_KEY_FILE,
                          ProtocolOptions.ACCOUNT_CODE))),
          operation(
              OperationId.EXECUTE_PLAN,
              OperationCategory.WRITE,
              "Execute Plan",
              List.of(),
              List.of(
                  ProtocolOptions.BOOK_FILE + " <path>",
                  ProtocolOptions.currentPassphraseSourceSyntax(),
                  ProtocolOptions.REQUEST_FILE + " <path|->"),
              ExecutionMode.JSON_ENVELOPE,
              "Execute one ordered AI-agent ledger plan inside a single atomic book transaction.",
              List.of(
                  "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s plan.json"
                      .formatted(
                          OperationId.EXECUTE_PLAN.wireName(),
                          ProtocolOptions.BOOK_FILE,
                          ProtocolOptions.BOOK_KEY_FILE,
                          ProtocolOptions.REQUEST_FILE))),
          operation(
              OperationId.PREFLIGHT_ENTRY,
              OperationCategory.WRITE,
              "Preflight Entry",
              List.of(),
              List.of(
                  ProtocolOptions.BOOK_FILE + " <path>",
                  ProtocolOptions.currentPassphraseSourceSyntax(),
                  ProtocolOptions.REQUEST_FILE + " <path|->"),
              ExecutionMode.JSON_ENVELOPE,
              "Validate one posting request without committing it.",
              List.of(
                  "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s request.json"
                      .formatted(
                          OperationId.PREFLIGHT_ENTRY.wireName(),
                          ProtocolOptions.BOOK_FILE,
                          ProtocolOptions.BOOK_KEY_FILE,
                          ProtocolOptions.REQUEST_FILE))),
          operation(
              OperationId.POST_ENTRY,
              OperationCategory.WRITE,
              "Post Entry",
              List.of(),
              List.of(
                  ProtocolOptions.BOOK_FILE + " <path>",
                  ProtocolOptions.currentPassphraseSourceSyntax(),
                  ProtocolOptions.REQUEST_FILE + " <path|->"),
              ExecutionMode.JSON_ENVELOPE,
              "Commit one posting request into the selected SQLite book.",
              List.of(
                  "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s request.json"
                      .formatted(
                          OperationId.POST_ENTRY.wireName(),
                          ProtocolOptions.BOOK_FILE,
                          ProtocolOptions.BOOK_KEY_FILE,
                          ProtocolOptions.REQUEST_FILE))));
  private static final Map<OperationId, ProtocolOperation> BY_ID =
      OPERATIONS.stream()
          .collect(
              java.util.stream.Collectors.toUnmodifiableMap(
                  ProtocolOperation::id, java.util.function.Function.identity()));
  private static final Map<String, ProtocolOperation> BY_TOKEN =
      OPERATIONS.stream()
          .flatMap(ProtocolCatalog::tokensFor)
          .collect(
              java.util.stream.Collectors.toUnmodifiableMap(
                  Map.Entry::getKey, Map.Entry::getValue));

  private ProtocolCatalog() {}

  /** Returns every operation in stable help and capabilities order. */
  public static List<ProtocolOperation> operations() {
    return OPERATIONS;
  }

  /** Returns the operation descriptor for a canonical operation identifier. */
  public static ProtocolOperation operation(OperationId operationId) {
    return Objects.requireNonNull(
        BY_ID.get(Objects.requireNonNull(operationId, "operationId")), "operation");
  }

  /** Returns the stable wire name for one canonical operation identifier. */
  public static String operationName(OperationId operationId) {
    return operation(operationId).id().wireName();
  }

  /** Finds an operation by canonical operation name or public alias. */
  public static Optional<ProtocolOperation> findByToken(String token) {
    return Optional.ofNullable(BY_TOKEN.get(Objects.requireNonNull(token, "token")));
  }

  /** Returns operation names in stable order for one capabilities group. */
  public static List<String> operationNames(OperationCategory category) {
    Objects.requireNonNull(category, "category");
    return OPERATIONS.stream()
        .filter(operation -> operation.category() == category)
        .map(operation -> operation.id().wireName())
        .toList();
  }

  /** Returns the canonical storage engine identifiers. */
  public static List<String> storageEngines() {
    return STORAGE_ENGINES;
  }

  /** Returns the canonical success-status identifiers. */
  public static List<String> successStatuses() {
    return SUCCESS_STATUSES;
  }

  /** Returns the structured hard book-model facts. */
  public static BookModelFacts bookModel() {
    return BOOK_MODEL;
  }

  /** Returns the structured currency-model facts. */
  public static CurrencyFacts currency() {
    return CURRENCY;
  }

  /** Returns the structured preflight semantics. */
  public static PreflightFacts preflight() {
    return PREFLIGHT;
  }

  /** Returns supported self-contained public CLI bundle targets. */
  public static List<String> supportedPublicCliBundleTargets() {
    return SUPPORTED_PUBLIC_CLI_BUNDLE_TARGETS;
  }

  /** Returns operating systems outside the current self-contained public CLI contract. */
  public static List<String> unsupportedPublicCliOperatingSystems() {
    return UNSUPPORTED_PUBLIC_CLI_OPERATING_SYSTEMS;
  }

  private static ProtocolOperation operation(
      OperationId id,
      OperationCategory category,
      String displayLabel,
      List<String> aliases,
      List<String> options,
      ExecutionMode executionMode,
      String analysisSummary,
      List<String> examples) {
    String usage = "fingrind " + id.wireName();
    if (!options.isEmpty()) {
      usage =
          usage
              + " "
              + options.stream()
                  .map(ProtocolCatalog::usageOption)
                  .collect(java.util.stream.Collectors.joining(" "));
    }
    return new ProtocolOperation(
        id,
        category,
        displayLabel,
        aliases,
        options,
        executionMode,
        usage,
        analysisSummary,
        examples);
  }

  private static String usageOption(String option) {
    return option.equals(ProtocolOptions.currentPassphraseSourceSyntax())
            || option.equals(ProtocolOptions.replacementPassphraseSourceSyntax())
        ? "[" + option + "]"
        : option;
  }

  private static Stream<Map.Entry<String, ProtocolOperation>> tokensFor(
      ProtocolOperation operation) {
    return Stream.concat(
        Stream.of(new AbstractMap.SimpleImmutableEntry<>(operation.id().wireName(), operation)),
        operation.aliases().stream()
            .map(alias -> new AbstractMap.SimpleImmutableEntry<>(alias, operation)));
  }
}
