package dev.erst.fingrind.application;

import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.NormalBalance;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Canonical machine-readable contract descriptors for the FinGrind CLI surface. */
public final class MachineContract {
  private static final List<String> DISCOVERY_COMMANDS =
      List.of("help", "version", "capabilities", "print-request-template");
  private static final List<String> ADMINISTRATION_COMMANDS =
      List.of("generate-book-key-file", "open-book", "rekey-book", "declare-account");
  private static final List<String> QUERY_COMMANDS = List.of("list-accounts");
  private static final List<String> WRITE_COMMANDS = List.of("preflight-entry", "post-entry");
  private static final List<String> STORAGE_ENGINES = List.of("sqlite");
  private static final List<String> SUPPORTED_PUBLIC_CLI_BUNDLE_TARGETS =
      List.of("macos-aarch64", "macos-x86_64", "linux-x86_64", "linux-aarch64");
  private static final List<String> UNSUPPORTED_PUBLIC_CLI_OPERATING_SYSTEMS = List.of("windows");
  private static final List<String> SUCCESS_STATUSES =
      List.of("ok", "preflight-accepted", "committed");

  private MachineContract() {}

  /** Builds the canonical help descriptor. */
  public static HelpDescriptor help(
      ApplicationIdentity identity, EnvironmentDescriptor environment) {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(environment, "environment");
    return new HelpDescriptor(
        identity.application(),
        identity.version(),
        identity.description(),
        List.of(
            "fingrind help",
            "fingrind version",
            "fingrind capabilities",
            "fingrind print-request-template",
            "fingrind generate-book-key-file --book-key-file <path>",
            "fingrind open-book --book-file <path> [--book-key-file <path> | --book-passphrase-stdin | --book-passphrase-prompt]",
            "fingrind rekey-book --book-file <path> [--book-key-file <path> | --book-passphrase-stdin | --book-passphrase-prompt] [--new-book-key-file <path> | --new-book-passphrase-stdin | --new-book-passphrase-prompt]",
            "fingrind declare-account --book-file <path> [--book-key-file <path> | --book-passphrase-stdin | --book-passphrase-prompt] --request-file <path|->",
            "fingrind list-accounts --book-file <path> [--book-key-file <path> | --book-passphrase-stdin | --book-passphrase-prompt]",
            "fingrind preflight-entry --book-file <path> [--book-key-file <path> | --book-passphrase-stdin | --book-passphrase-prompt] --request-file <path|->",
            "fingrind post-entry --book-file <path> [--book-key-file <path> | --book-passphrase-stdin | --book-passphrase-prompt] --request-file <path|->"),
        bookModel(),
        commandDescriptors(),
        List.of(
            "fingrind generate-book-key-file --book-key-file ./secrets/acme.book-key",
            "fingrind open-book --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key",
            "fingrind open-book --book-file ./books/acme.sqlite --book-passphrase-prompt",
            "printf '%s\n' 'acme-demo-passphrase' | fingrind open-book --book-file ./books/acme.sqlite --book-passphrase-stdin",
            "fingrind rekey-book --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --new-book-passphrase-prompt",
            "fingrind declare-account --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --request-file ./docs/examples/declare-account-cash.json",
            "fingrind declare-account --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --request-file ./docs/examples/declare-account-revenue.json",
            "fingrind list-accounts --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key",
            "fingrind print-request-template > request.json",
            "fingrind preflight-entry --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --request-file request.json",
            "fingrind post-entry --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --request-file request.json"),
        exitCodes(),
        preflight(),
        currencyModel(),
        environment);
  }

  /** Builds the canonical capabilities descriptor. */
  public static CapabilitiesDescriptor capabilities(
      ApplicationIdentity identity, EnvironmentDescriptor environment, Instant timestamp) {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(environment, "environment");
    Objects.requireNonNull(timestamp, "timestamp");
    return new CapabilitiesDescriptor(
        identity.application(),
        identity.version(),
        STORAGE_ENGINES,
        "single-sqlite-file",
        DISCOVERY_COMMANDS,
        ADMINISTRATION_COMMANDS,
        QUERY_COMMANDS,
        WRITE_COMMANDS,
        requestInput(),
        requestShapes(),
        responseModel(),
        audit(),
        accountRegistry(),
        reversals(),
        preflight().semantics(),
        preflight(),
        currencyModel(),
        environment,
        timestamp.toString());
  }

  /** Builds the canonical version descriptor. */
  public static VersionDescriptor version(ApplicationIdentity identity) {
    Objects.requireNonNull(identity, "identity");
    return new VersionDescriptor(
        identity.application(), identity.version(), identity.description());
  }

  /** Builds the canonical minimal posting-request template descriptor. */
  public static PostingRequestTemplateDescriptor requestTemplate(Clock clock) {
    Objects.requireNonNull(clock, "clock");
    return new PostingRequestTemplateDescriptor(
        LocalDate.now(clock).toString(),
        List.of(
            new JournalLineTemplateDescriptor("1000", "DEBIT", "EUR", "10.00"),
            new JournalLineTemplateDescriptor("2000", "CREDIT", "EUR", "10.00")),
        new ProvenanceTemplateDescriptor(
            "operator-1", "USER", "command-1", "idem-1", "cause-1", null, null),
        null);
  }

  private static BookModelDescriptor bookModel() {
    return new BookModelDescriptor(
        "one SQLite file equals one book",
        "one book belongs to one entity",
        "--book-file may point anywhere on the OS filesystem",
        "every book-bound command requires exactly one explicit passphrase source via --book-key-file, --book-passphrase-stdin, or --book-passphrase-prompt",
        "books must be opened explicitly before any posting or account declaration",
        "every posting line must reference a declared active account",
        "there is no migration or compatibility layer during the hard-break phase",
        currencyModel().scope());
  }

  private static List<CommandDescriptor> commandDescriptors() {
    return List.of(
        new CommandDescriptor(
            "help",
            List.of("--help", "-h"),
            List.of(),
            "json-envelope",
            "Print command usage, examples, and workflow guidance."),
        new CommandDescriptor(
            "version",
            List.of("--version"),
            List.of(),
            "json-envelope",
            "Print application identity, version, and description."),
        new CommandDescriptor(
            "capabilities",
            List.of(),
            List.of(),
            "json-envelope",
            "Print the canonical machine-readable contract for commands, request shapes, and responses."),
        new CommandDescriptor(
            "print-request-template",
            List.of("--print-request-template"),
            List.of(),
            "raw-json",
            "Print a minimal valid posting request JSON document."),
        new CommandDescriptor(
            "generate-book-key-file",
            List.of(),
            List.of("--book-key-file <path>"),
            "json-envelope",
            "Create one new owner-only UTF-8 book key file with a generated high-entropy passphrase."),
        new CommandDescriptor(
            "open-book",
            List.of(),
            List.of(
                "--book-file <path>",
                "--book-key-file <path> | --book-passphrase-stdin | --book-passphrase-prompt"),
            "json-envelope",
            "Initialize a new book file with the canonical schema."),
        new CommandDescriptor(
            "rekey-book",
            List.of(),
            List.of(
                "--book-file <path>",
                "--book-key-file <path> | --book-passphrase-stdin | --book-passphrase-prompt",
                "--new-book-key-file <path> | --new-book-passphrase-stdin | --new-book-passphrase-prompt"),
            "json-envelope",
            "Rotate the passphrase that protects one existing book."),
        new CommandDescriptor(
            "declare-account",
            List.of(),
            List.of(
                "--book-file <path>",
                "--book-key-file <path> | --book-passphrase-stdin | --book-passphrase-prompt",
                "--request-file <path|->"),
            "json-envelope",
            "Declare or reactivate one account in the selected book."),
        new CommandDescriptor(
            "list-accounts",
            List.of(),
            List.of(
                "--book-file <path>",
                "--book-key-file <path> | --book-passphrase-stdin | --book-passphrase-prompt"),
            "json-envelope",
            "List every declared account in the selected book."),
        new CommandDescriptor(
            "preflight-entry",
            List.of(),
            List.of(
                "--book-file <path>",
                "--book-key-file <path> | --book-passphrase-stdin | --book-passphrase-prompt",
                "--request-file <path|->"),
            "json-envelope",
            "Validate one posting request without committing it."),
        new CommandDescriptor(
            "post-entry",
            List.of(),
            List.of(
                "--book-file <path>",
                "--book-key-file <path> | --book-passphrase-stdin | --book-passphrase-prompt",
                "--request-file <path|->"),
            "json-envelope",
            "Commit one posting request into the selected SQLite book."));
  }

  private static List<ExitCodeDescriptor> exitCodes() {
    return List.of(
        new ExitCodeDescriptor(0, "successful command"),
        new ExitCodeDescriptor(2, "invalid request or deterministic rejection"),
        new ExitCodeDescriptor(1, "runtime or environment failure"));
  }

  private static RequestInputDescriptor requestInput() {
    return new RequestInputDescriptor(
        "--book-file",
        List.of("--book-key-file", "--book-passphrase-stdin", "--book-passphrase-prompt"),
        "--request-file",
        "-",
        "single SQLite book file for one entity",
        List.of(
            "key-file route: one UTF-8 passphrase file for the selected encrypted book; the file must be a regular non-symlink file on a POSIX filesystem with owner-only permissions (0400 or 0600)",
            "standard-input route: read one UTF-8 passphrase payload from standard input; this cannot be combined with --request-file -, and rekey-book cannot use standard input for both current and replacement secrets",
            "interactive-prompt route: prompt for the passphrase on the controlling terminal without echo; replacement prompt entry requires confirmation",
            "all passphrase routes strip one trailing LF or CRLF, reject empty secrets, and reject control characters so one secret remains reproducible across file, stdin, and prompt usage"),
        List.of(
            "request JSON must be one object document",
            "unknown request fields are rejected at every object level",
            "duplicate JSON object keys are rejected",
            "legacy forbidden fields such as correction, reversal.kind, provenance.recordedAt, and provenance.sourceChannel remain hard-broken"));
  }

  private static RequestShapesDescriptor requestShapes() {
    return new RequestShapesDescriptor(postEntryShape(), declareAccountShape());
  }

  private static PostEntryRequestShapeDescriptor postEntryShape() {
    return new PostEntryRequestShapeDescriptor(
        List.of(
            requiredField(
                PostEntryTopLevelFields.EFFECTIVE_DATE,
                "ISO-8601 local date that makes the journal entry effective."),
            requiredField(
                PostEntryTopLevelFields.LINES,
                "Balanced non-empty array of journal lines for one currency."),
            requiredField(
                PostEntryTopLevelFields.PROVENANCE,
                "Caller-supplied request provenance captured before commit."),
            optionalField(
                PostEntryTopLevelFields.REVERSAL,
                "Optional reversal target descriptor for additive reversal postings."),
            forbiddenField(
                PostEntryTopLevelFields.CORRECTION,
                "Legacy correction payloads are hard-broken and no longer accepted.")),
        List.of(
            requiredField(
                JournalLineFields.ACCOUNT_CODE,
                "Declared book-local account code for this journal line."),
            requiredField(JournalLineFields.SIDE, "Journal side that carries the line amount."),
            requiredField(
                JournalLineFields.CURRENCY_CODE,
                "Three-letter ISO currency code shared by every line in the entry."),
            requiredField(
                JournalLineFields.AMOUNT,
                "Plain decimal string greater than zero without exponent notation.")),
        List.of(
            requiredField(
                ProvenanceFields.ACTOR_ID,
                "Stable identifier of the actor that initiated the request."),
            requiredField(
                ProvenanceFields.ACTOR_TYPE,
                "Actor classification from the live actorType enum vocabulary."),
            requiredField(
                ProvenanceFields.COMMAND_ID, "Caller-generated command identity for this request."),
            requiredField(
                ProvenanceFields.IDEMPOTENCY_KEY,
                "Book-local idempotency key used to detect duplicate commit attempts."),
            requiredField(
                ProvenanceFields.CAUSATION_ID,
                "Caller-supplied causation identifier for upstream traceability."),
            optionalField(
                ProvenanceFields.CORRELATION_ID,
                "Optional correlation identifier for joining related calls."),
            optionalField(
                ProvenanceFields.REASON,
                "Human-readable reversal reason required only when reversal is present."),
            forbiddenField(
                ProvenanceFields.RECORDED_AT,
                "Committed audit timestamps are generated by FinGrind, not caller input."),
            forbiddenField(
                ProvenanceFields.SOURCE_CHANNEL,
                "Committed source channel is generated by FinGrind, not caller input.")),
        List.of(
            requiredField(
                ReversalFields.PRIOR_POSTING_ID,
                "Existing committed posting identifier that this request reverses."),
            forbiddenField(
                ReversalFields.KIND,
                "Legacy reversal-kind routing is removed; FinGrind is reversal-only.")),
        List.of(
            enumVocabulary("lineSide", JournalLine.EntrySide.values()),
            enumVocabulary("actorType", ActorType.values())));
  }

  private static DeclareAccountRequestShapeDescriptor declareAccountShape() {
    return new DeclareAccountRequestShapeDescriptor(
        List.of(
            requiredField(
                DeclareAccountFields.ACCOUNT_CODE,
                "Book-local account code used by journal lines."),
            requiredField(
                DeclareAccountFields.ACCOUNT_NAME,
                "Non-blank display name for the declared account."),
            requiredField(
                DeclareAccountFields.NORMAL_BALANCE,
                "Side of the journal equation that increases the account.")),
        List.of(enumVocabulary("normalBalance", NormalBalance.values())));
  }

  private static ResponseModelDescriptor responseModel() {
    return new ResponseModelDescriptor(
        SUCCESS_STATUSES,
        "rejected",
        "error",
        rejectionDescriptors(),
        List.of(
            new FieldDescriptor("status", "Literal rejection status."),
            new FieldDescriptor("code", "Stable machine rejection code."),
            new FieldDescriptor("message", "Human-readable explanation of the rejection."),
            new FieldDescriptor(
                "details", "Optional structured rejection-specific detail object.")),
        List.of(
            new FieldDescriptor("status", "Literal rejection status."),
            new FieldDescriptor("code", "Stable machine rejection code."),
            new FieldDescriptor("message", "Human-readable explanation of the rejection."),
            new FieldDescriptor(
                "idempotencyKey", "The caller-supplied idempotency key from the rejected request."),
            new FieldDescriptor(
                "details", "Optional structured rejection-specific detail object.")),
        List.of(
            new FieldDescriptor("status", "Literal runtime or invalid-request error status."),
            new FieldDescriptor("code", "Stable machine error code."),
            new FieldDescriptor("message", "Human-readable explanation of the error."),
            new FieldDescriptor("hint", "Optional operator hint for repairing the invocation."),
            new FieldDescriptor(
                "argument", "Optional argument name associated with the failure.")));
  }

  private static List<RejectionDescriptor> rejectionDescriptors() {
    return java.util.stream.Stream.concat(
            BookAdministrationRejection.descriptors().stream(),
            PostingRejection.descriptors().stream())
        .collect(
            Collectors.collectingAndThen(
                Collectors.toMap(
                    RejectionDescriptor::code,
                    Function.identity(),
                    (left, right) -> left,
                    LinkedHashMap::new),
                byCode -> List.copyOf(byCode.values())));
  }

  private static AuditDescriptor audit() {
    return new AuditDescriptor(
        List.of(
            new FieldDescriptor(
                ProvenanceFields.ACTOR_ID,
                "Stable identifier of the actor that initiated the request."),
            new FieldDescriptor(
                ProvenanceFields.ACTOR_TYPE,
                "Actor classification from the live actorType enum vocabulary."),
            new FieldDescriptor(
                ProvenanceFields.COMMAND_ID, "Caller-generated command identity for this request."),
            new FieldDescriptor(
                ProvenanceFields.IDEMPOTENCY_KEY,
                "Book-local idempotency key supplied by the caller."),
            new FieldDescriptor(
                ProvenanceFields.CAUSATION_ID, "Caller-supplied causation identifier."),
            new FieldDescriptor(
                ProvenanceFields.CORRELATION_ID,
                "Optional caller-supplied correlation identifier."),
            new FieldDescriptor(
                ProvenanceFields.REASON,
                "Optional caller-supplied reversal reason when reversal is present.")),
        List.of(
            new FieldDescriptor(
                ProvenanceFields.RECORDED_AT,
                "Commit timestamp generated by FinGrind at durable write time."),
            new FieldDescriptor(
                ProvenanceFields.SOURCE_CHANNEL,
                "Committed request channel generated by FinGrind.")));
  }

  private static AccountRegistryDescriptor accountRegistry() {
    return new AccountRegistryDescriptor(
        true,
        "redeclaration may update the display name and reactivate an inactive account, but will not amend normalBalance",
        List.of(
            new FieldDescriptor(
                DeclareAccountFields.ACCOUNT_CODE, "Book-local account code to declare."),
            new FieldDescriptor(
                DeclareAccountFields.ACCOUNT_NAME, "Non-blank display name for the account."),
            new FieldDescriptor(
                DeclareAccountFields.NORMAL_BALANCE, "Journal side that increases the account.")),
        List.of(
            new FieldDescriptor("accountCode", "Declared book-local account code."),
            new FieldDescriptor("accountName", "Current display name of the account."),
            new FieldDescriptor("normalBalance", "Declared normal balance side."),
            new FieldDescriptor("active", "Whether the account currently accepts postings."),
            new FieldDescriptor("declaredAt", "Clock timestamp of the first declaration.")),
        List.of(enumVocabulary("normalBalance", NormalBalance.values())));
  }

  private static ReversalDescriptor reversals() {
    return new ReversalDescriptor(
        "reversal-only",
        List.of(
            "book-must-be-initialized",
            "every-line-account-must-be-declared-and-active",
            "target-must-exist-in-book",
            "reason-required",
            "reason-forbidden-without-reversal",
            "one-reversal-per-target",
            "reversal-must-negate-target"));
  }

  private static PreflightDescriptor preflight() {
    return new PreflightDescriptor(
        "advisory",
        false,
        "Preflight validates the current request against the current book state, but it is not a commit guarantee because durable commit-time checks still run inside the write transaction.");
  }

  private static CurrencyDescriptor currencyModel() {
    return new CurrencyDescriptor(
        "single-currency-per-entry",
        "not-supported",
        "Every journal line inside one entry must share the same currencyCode. Mixed-currency entries are rejected and no multi-currency posting model exists yet.");
  }

  private static EnumVocabularyDescriptor enumVocabulary(String name, Enum<?>[] values) {
    return new EnumVocabularyDescriptor(
        name, java.util.Arrays.stream(values).map(Enum::name).toList());
  }

  private static RequestFieldDescriptor requiredField(String name, String description) {
    return new RequestFieldDescriptor(name, "required", description);
  }

  private static RequestFieldDescriptor optionalField(String name, String description) {
    return new RequestFieldDescriptor(name, "optional", description);
  }

  private static RequestFieldDescriptor forbiddenField(String name, String description) {
    return new RequestFieldDescriptor(name, "forbidden", description);
  }

  /** Stable identity fields that appear on discovery descriptors. */
  public record ApplicationIdentity(String application, String version, String description) {}

  /** Descriptor for the help payload. */
  public record HelpDescriptor(
      String application,
      String version,
      String description,
      List<String> usage,
      BookModelDescriptor bookModel,
      List<CommandDescriptor> commands,
      List<String> quickStart,
      List<ExitCodeDescriptor> exitCodes,
      PreflightDescriptor preflight,
      CurrencyDescriptor currencyModel,
      EnvironmentDescriptor environment) {}

  /** Descriptor for the capabilities payload. */
  public record CapabilitiesDescriptor(
      String application,
      String version,
      List<String> storage,
      String bookBoundary,
      List<String> discoveryCommands,
      List<String> administrationCommands,
      List<String> queryCommands,
      List<String> writeCommands,
      RequestInputDescriptor requestInput,
      RequestShapesDescriptor requestShapes,
      ResponseModelDescriptor responseModel,
      AuditDescriptor audit,
      AccountRegistryDescriptor accountRegistry,
      ReversalDescriptor reversals,
      String preflightSemantics,
      PreflightDescriptor preflight,
      CurrencyDescriptor currencyModel,
      EnvironmentDescriptor environment,
      String timestamp) {}

  /** Descriptor for the version payload. */
  public record VersionDescriptor(String application, String version, String description) {}

  /** Canonical request-template document for print-request-template. */
  public record PostingRequestTemplateDescriptor(
      String effectiveDate,
      List<JournalLineTemplateDescriptor> lines,
      ProvenanceTemplateDescriptor provenance,
      ReversalTemplateDescriptor reversal) {}

  /** Canonical request-template journal-line descriptor. */
  public record JournalLineTemplateDescriptor(
      String accountCode, String side, String currencyCode, String amount) {}

  /** Canonical request-template provenance descriptor. */
  public record ProvenanceTemplateDescriptor(
      String actorId,
      String actorType,
      String commandId,
      String idempotencyKey,
      String causationId,
      String correlationId,
      String reason) {}

  /** Canonical request-template reversal descriptor. */
  public record ReversalTemplateDescriptor(String priorPostingId) {}

  /** Descriptor for the machine-readable book model. */
  public record BookModelDescriptor(
      String boundary,
      String entityScope,
      String filesystem,
      String credential,
      String initialization,
      String accountRegistry,
      String migration,
      String currencyScope) {}

  /** Descriptor for one advertised CLI command. */
  public record CommandDescriptor(
      String name, List<String> aliases, List<String> options, String output, String summary) {}

  /** Descriptor for one process exit code. */
  public record ExitCodeDescriptor(int code, String meaning) {}

  /** Descriptor for request-file and book-file input plumbing. */
  public record RequestInputDescriptor(
      String bookFileOption,
      List<String> bookPassphraseOptions,
      String requestFileOption,
      String stdinToken,
      String bookFileSemantics,
      List<String> bookPassphraseSemantics,
      List<String> requestDocumentSemantics) {}

  /** Descriptor grouping the current request shapes. */
  public record RequestShapesDescriptor(
      PostEntryRequestShapeDescriptor postEntry,
      DeclareAccountRequestShapeDescriptor declareAccount) {}

  /** Descriptor for the post-entry and preflight-entry request shape. */
  public record PostEntryRequestShapeDescriptor(
      List<RequestFieldDescriptor> topLevelFields,
      List<RequestFieldDescriptor> lineFields,
      List<RequestFieldDescriptor> provenanceFields,
      List<RequestFieldDescriptor> reversalFields,
      List<EnumVocabularyDescriptor> enumVocabularies) {}

  /** Descriptor for the declare-account request shape. */
  public record DeclareAccountRequestShapeDescriptor(
      List<RequestFieldDescriptor> topLevelFields,
      List<EnumVocabularyDescriptor> enumVocabularies) {}

  /** One request field with live presence and description metadata. */
  public record RequestFieldDescriptor(String name, String presence, String description) {}

  /** One general field descriptor for envelopes or emitted payloads. */
  public record FieldDescriptor(String name, String description) {}

  /** One live enum vocabulary descriptor. */
  public record EnumVocabularyDescriptor(String name, List<String> values) {}

  /** Descriptor for the stable response contract. */
  public record ResponseModelDescriptor(
      List<String> successStatuses,
      String rejectionStatus,
      String errorStatus,
      List<RejectionDescriptor> rejections,
      List<FieldDescriptor> rejectionFields,
      List<FieldDescriptor> postEntryRejectionFields,
      List<FieldDescriptor> errorFields) {}

  /** One stable machine rejection descriptor. */
  public record RejectionDescriptor(String code, String description) {}

  /** Descriptor for caller-supplied versus committed audit fields. */
  public record AuditDescriptor(
      List<FieldDescriptor> requestProvenanceFields, List<FieldDescriptor> committedFields) {}

  /** Descriptor for the book-local account registry contract. */
  public record AccountRegistryDescriptor(
      boolean requiresOpenBook,
      String redeclarationBehavior,
      List<FieldDescriptor> declareAccountFields,
      List<FieldDescriptor> listFields,
      List<EnumVocabularyDescriptor> enumVocabularies) {}

  /** Descriptor for the reversal model. */
  public record ReversalDescriptor(String model, List<String> requirements) {}

  /** Descriptor for preflight semantics. */
  public record PreflightDescriptor(
      String semantics, boolean isCommitGuarantee, String description) {}

  /** Descriptor for currency support. */
  public record CurrencyDescriptor(String scope, String multiCurrencyStatus, String description) {}

  /** Descriptor for the active SQLite runtime environment. */
  public record EnvironmentDescriptor(
      String runtimeDistribution,
      String publicCliDistribution,
      List<String> supportedPublicCliBundleTargets,
      List<String> unsupportedPublicCliOperatingSystems,
      String sourceCheckoutJava,
      String storageDriver,
      String storageEngine,
      String bookProtectionMode,
      String defaultBookCipher,
      String sqliteLibraryMode,
      String sqliteLibraryEnvironmentVariable,
      String sqliteLibraryBundleHomeSystemProperty,
      List<String> requiredSqliteCompileOptions,
      boolean sqliteCompileOptionsVerified,
      String requiredMinimumSqliteVersion,
      String requiredSqlite3mcVersion,
      String sqliteRuntimeStatus,
      String loadedSqliteVersion,
      String loadedSqlite3mcVersion,
      String sqliteRuntimeIssue) {}

  /** Supported self-contained public CLI bundle targets. */
  public static List<String> supportedPublicCliBundleTargets() {
    return SUPPORTED_PUBLIC_CLI_BUNDLE_TARGETS;
  }

  /** Operating systems intentionally outside the current self-contained public CLI contract. */
  public static List<String> unsupportedPublicCliOperatingSystems() {
    return UNSUPPORTED_PUBLIC_CLI_OPERATING_SYSTEMS;
  }

  /** Canonical top-level posting request fields shared by the parser and capabilities surface. */
  public static final class PostEntryTopLevelFields {
    public static final String EFFECTIVE_DATE = "effectiveDate";
    public static final String LINES = "lines";
    public static final String PROVENANCE = "provenance";
    public static final String REVERSAL = "reversal";
    public static final String CORRECTION = "correction";

    private PostEntryTopLevelFields() {}
  }

  /** Canonical journal-line request fields shared by the parser and capabilities surface. */
  public static final class JournalLineFields {
    public static final String ACCOUNT_CODE = "accountCode";
    public static final String SIDE = "side";
    public static final String CURRENCY_CODE = "currencyCode";
    public static final String AMOUNT = "amount";

    private JournalLineFields() {}
  }

  /** Canonical provenance request fields shared by the parser and capabilities surface. */
  public static final class ProvenanceFields {
    public static final String ACTOR_ID = "actorId";
    public static final String ACTOR_TYPE = "actorType";
    public static final String COMMAND_ID = "commandId";
    public static final String IDEMPOTENCY_KEY = "idempotencyKey";
    public static final String CAUSATION_ID = "causationId";
    public static final String CORRELATION_ID = "correlationId";
    public static final String REASON = "reason";
    public static final String RECORDED_AT = "recordedAt";
    public static final String SOURCE_CHANNEL = "sourceChannel";

    private ProvenanceFields() {}
  }

  /** Canonical reversal request fields shared by the parser and capabilities surface. */
  public static final class ReversalFields {
    public static final String PRIOR_POSTING_ID = "priorPostingId";
    public static final String KIND = "kind";

    private ReversalFields() {}
  }

  /** Canonical declare-account request fields shared by the parser and capabilities surface. */
  public static final class DeclareAccountFields {
    public static final String ACCOUNT_CODE = "accountCode";
    public static final String ACCOUNT_NAME = "accountName";
    public static final String NORMAL_BALANCE = "normalBalance";

    private DeclareAccountFields() {}
  }
}
