package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.BookAdministrationRejection;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.BookMigrationPolicy;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.PostingRequest;
import dev.erst.fingrind.contract.RekeyBookResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.BookAdministrationSession;
import dev.erst.fingrind.executor.BookQuerySession;
import dev.erst.fingrind.executor.LedgerPlanSession;
import dev.erst.fingrind.executor.PostingBookSession;
import dev.erst.fingrind.executor.PostingCommitResult;
import dev.erst.fingrind.executor.PostingDraft;
import dev.erst.fingrind.executor.PostingIdGenerator;
import dev.erst.fingrind.executor.PostingValidation;
import dev.erst.fingrind.executor.PostingValidationBook;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * SQLite-backed book session that keeps one in-process database handle per opened book.
 *
 * <p>This session is thread-confined. One CLI command owns one instance and uses it on one thread.
 */
public final class SqlitePostingFactStore implements LedgerPlanSession, AutoCloseable {
  static final int BOOK_APPLICATION_ID = 1_179_079_236; // "FGRD"
  private static final SqliteBookMigrationPlanner BOOK_MIGRATION_PLANNER =
      new SqliteBookMigrationPlanner(1, BookMigrationPolicy.SEQUENTIAL_IN_PLACE);
  static final int BOOK_FORMAT_VERSION = BOOK_MIGRATION_PLANNER.currentBookFormatVersion();

  private static final String ACCOUNT_TABLE = "account";
  private static final String BOOK_META_TABLE = "book_meta";
  private static final String JOURNAL_LINE_TABLE = "journal_line";
  private static final String NOT_INITIALIZED_BOOK_MESSAGE =
      "The selected SQLite file is not initialized as a FinGrind book.";
  private static final String POSTING_FACT_TABLE = "posting_fact";
  private static final BookMigrationPolicy BOOK_MIGRATION_POLICY = BOOK_MIGRATION_PLANNER.policy();
  private static final SqliteBookStateReader BOOK_STATE_READER =
      new SqliteBookStateReader(
          BOOK_APPLICATION_ID,
          BOOK_FORMAT_VERSION,
          ACCOUNT_TABLE,
          BOOK_META_TABLE,
          JOURNAL_LINE_TABLE,
          POSTING_FACT_TABLE);

  private final Path bookPath;
  private final AccessMode accessMode;
  private final SqlitePostingReadSupport postingReadSupport;
  private final BookAdministrationSession administrationView = new AdministrationView();
  private final PostingBookSession postingView = new PostingView();
  private final BookQuerySession queryView = new QueryView();
  private @Nullable SqliteBookPassphrase bookPassphrase;

  private @Nullable SqliteNativeDatabase database;
  private @Nullable SqliteBookStateSnapshot cachedBookState;
  private boolean closed;
  private boolean ledgerPlanTransactionActive;
  private boolean ledgerPlanTransactionBegunInDatabase;
  private @Nullable IllegalStateException terminalFailure;

  /** Opens one SQLite-backed book boundary without mutating storage eagerly. */
  public SqlitePostingFactStore(Path bookPath, SqliteBookPassphrase bookPassphrase) {
    this(bookPath, bookPassphrase, AccessMode.READ_WRITE_CREATE);
  }

  /** Opens one SQLite-backed book boundary with the selected storage access mode. */
  public SqlitePostingFactStore(
      Path bookPath, SqliteBookPassphrase bookPassphrase, AccessMode accessMode) {
    this.bookPath = Objects.requireNonNull(bookPath, "bookPath").toAbsolutePath().normalize();
    this.bookPassphrase = Objects.requireNonNull(bookPassphrase, "bookPassphrase");
    this.accessMode = Objects.requireNonNull(accessMode, "accessMode");
    this.postingReadSupport = new SqlitePostingReadSupport();
    this.database = null;
    this.cachedBookState = null;
    this.terminalFailure = null;
  }

  SqlitePostingFactStore(BookAccess bookAccess) {
    this(bookAccess, AccessMode.READ_WRITE_CREATE);
  }

  SqlitePostingFactStore(BookAccess bookAccess, AccessMode accessMode) {
    this(bookAccess.bookFilePath(), passphraseFor(bookAccess), accessMode);
  }

  @Override
  public BookAdministrationSession administrationSession() {
    return administrationView;
  }

  @Override
  public PostingBookSession postingSession() {
    return postingView;
  }

  @Override
  public BookQuerySession querySession() {
    return queryView;
  }

  /** Inspects the selected SQLite book without requiring prior initialization. */
  public BookInspection inspectBook() {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return new BookInspection.Missing(BOOK_FORMAT_VERSION, BOOK_MIGRATION_POLICY);
    }
    try {
      SqliteNativeDatabase activeDatabase = database();
      SqliteBookStateSnapshot snapshot = stateSnapshot(activeDatabase);
      return switch (snapshot.state()) {
        case BLANK_SQLITE ->
            new BookInspection.Existing(
                BookInspection.Status.BLANK_SQLITE,
                snapshot.applicationId(),
                snapshot.userVersion(),
                BOOK_FORMAT_VERSION,
                BOOK_MIGRATION_POLICY);
        case INITIALIZED_FINGRIND ->
            new BookInspection.Initialized(
                snapshot.applicationId(),
                snapshot.userVersion(),
                BOOK_FORMAT_VERSION,
                BOOK_MIGRATION_POLICY,
                SqliteStatementQuerySupport.loadInitializedAt(activeDatabase).orElseThrow());
        case FOREIGN_SQLITE ->
            new BookInspection.Existing(
                BookInspection.Status.FOREIGN_SQLITE,
                snapshot.applicationId(),
                snapshot.userVersion(),
                BOOK_FORMAT_VERSION,
                BOOK_MIGRATION_POLICY);
        case UNSUPPORTED_FINGRIND_VERSION ->
            new BookInspection.Existing(
                BookInspection.Status.UNSUPPORTED_FORMAT_VERSION,
                snapshot.applicationId(),
                snapshot.userVersion(),
                BOOK_FORMAT_VERSION,
                BOOK_MIGRATION_POLICY);
        case INCOMPLETE_FINGRIND ->
            new BookInspection.Existing(
                BookInspection.Status.INCOMPLETE_FINGRIND,
                snapshot.applicationId(),
                snapshot.userVersion(),
                BOOK_FORMAT_VERSION,
                BOOK_MIGRATION_POLICY);
      };
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to inspect SQLite book.", exception);
    }
  }

  /** Reports whether the selected SQLite book is initialized for posting and query operations. */
  public boolean isInitialized() {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return false;
    }
    try {
      SqliteBookStateSnapshot snapshot = stateSnapshot(database());
      return switch (snapshot.state()) {
        case BLANK_SQLITE -> false;
        case INITIALIZED_FINGRIND -> true;
        case FOREIGN_SQLITE -> throw foreignBookFailure();
        case UNSUPPORTED_FINGRIND_VERSION ->
            throw unsupportedBookVersionFailure(snapshot.userVersion());
        case INCOMPLETE_FINGRIND -> throw incompleteBookFailure();
      };
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  /** Initializes one writable SQLite book or reports why initialization was refused. */
  @SuppressWarnings("PMD.CloseResource")
  public OpenBookResult openBook(Instant initializedAt) {
    ensureOpen();
    accessMode.requireWritableInitialization();
    SqliteSchemaManager.ensureParentDirectory(bookPath);
    SqliteNativeDatabase activeDatabase = database();
    boolean ownsTransaction = false;
    try {
      SqliteBookStateSnapshot snapshot = stateSnapshot(activeDatabase);
      OpenBookResult preexistingOutcome = snapshot.state().openBookResult(snapshot.userVersion());
      if (preexistingOutcome != null) {
        return preexistingOutcome;
      }

      ownsTransaction = beginImmediateIfNeeded(activeDatabase);
      SqliteSchemaManager.initializeBook(activeDatabase);
      SqliteMutationWriter.insertInitializedAt(activeDatabase, initializedAt);
      commitIfOwned(activeDatabase, ownsTransaction);
      cachedBookState =
          new SqliteBookStateSnapshot(
              BOOK_APPLICATION_ID, BOOK_FORMAT_VERSION, SqliteBookState.INITIALIZED_FINGRIND);
      return new OpenBookResult.Opened(initializedAt);
    } catch (SqliteNativeException exception) {
      rollbackIfOwned(activeDatabase, ownsTransaction);
      throw sqliteFailure("Failed to initialize SQLite book.", exception);
    }
  }

  /** Finds one declared account by code when the selected book is initialized. */
  public Optional<DeclaredAccount> findAccount(AccountCode accountCode) {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return Optional.empty();
    }
    try {
      SqliteNativeDatabase activeDatabase = database();
      if (stateSnapshot(activeDatabase).state() == SqliteBookState.BLANK_SQLITE) {
        return Optional.empty();
      }
      requireInitializedBook(activeDatabase);
      return SqliteStatementQuerySupport.findOneAccount(activeDatabase, accountCode);
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  /** Finds the supplied declared accounts by code when the selected book is initialized. */
  public Map<AccountCode, DeclaredAccount> findAccounts(Set<AccountCode> accountCodes) {
    ensureOpen();
    Set<AccountCode> requestedAccounts =
        new java.util.LinkedHashSet<>(Objects.requireNonNull(accountCodes, "accountCodes"));
    if (requestedAccounts.isEmpty() || Files.notExists(bookPath)) {
      return Map.of();
    }
    try {
      SqliteNativeDatabase activeDatabase = database();
      if (stateSnapshot(activeDatabase).state() == SqliteBookState.BLANK_SQLITE) {
        return Map.of();
      }
      requireInitializedBook(activeDatabase);
      return SqliteStatementQuerySupport.findAccounts(activeDatabase, requestedAccounts);
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  /** Declares or reactivates one account inside the selected writable SQLite book. */
  @SuppressWarnings("PMD.CloseResource")
  public DeclareAccountResult declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      NormalBalance normalBalance,
      Instant declaredAt) {
    ensureOpen();
    accessMode.requireWritableMutation();
    if (Files.notExists(bookPath)) {
      return new DeclareAccountResult.Rejected(
          new BookAdministrationRejection.BookNotInitialized());
    }
    SqliteNativeDatabase activeDatabase = database();
    boolean ownsTransaction = false;
    try {
      if (!isInitializedBook(activeDatabase)) {
        return new DeclareAccountResult.Rejected(
            new BookAdministrationRejection.BookNotInitialized());
      }

      ownsTransaction = beginImmediateIfNeeded(activeDatabase);
      Optional<DeclaredAccount> existingAccount =
          SqliteStatementQuerySupport.findOneAccount(activeDatabase, accountCode);
      if (existingAccount.isPresent()
          && existingAccount.orElseThrow().normalBalance() != normalBalance) {
        rollbackIfOwned(activeDatabase, ownsTransaction);
        return new DeclareAccountResult.Rejected(
            new BookAdministrationRejection.NormalBalanceConflict(
                accountCode, existingAccount.orElseThrow().normalBalance(), normalBalance));
      }

      DeclaredAccount declaredAccount =
          existingAccount
              .map(
                  account ->
                      new DeclaredAccount(
                          account.accountCode(),
                          accountName,
                          account.normalBalance(),
                          true,
                          account.declaredAt()))
              .orElseGet(
                  () ->
                      new DeclaredAccount(
                          accountCode, accountName, normalBalance, true, declaredAt));
      SqliteMutationWriter.upsertAccount(activeDatabase, declaredAccount);
      commitIfOwned(activeDatabase, ownsTransaction);
      return new DeclareAccountResult.Declared(declaredAccount);
    } catch (SqliteNativeException exception) {
      rollbackIfOwned(activeDatabase, ownsTransaction);
      throw sqliteFailure("Failed to declare SQLite book account.", exception);
    }
  }

  /** Lists declared accounts using the requested page window. */
  public AccountPage listAccounts(ListAccountsQuery query) {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return new AccountPage(List.of(), query.limit(), query.offset(), false);
    }
    try {
      SqliteNativeDatabase activeDatabase = database();
      if (stateSnapshot(activeDatabase).state() == SqliteBookState.BLANK_SQLITE) {
        return new AccountPage(List.of(), query.limit(), query.offset(), false);
      }
      requireInitializedBook(activeDatabase);
      return SqliteStatementQuerySupport.loadAccountPage(activeDatabase, query);
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  /** Finds one committed posting by idempotency key when it exists in the selected book. */
  public Optional<PostingFact> findExistingPosting(IdempotencyKey idempotencyKey) {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return Optional.empty();
    }
    try {
      SqliteNativeDatabase activeDatabase = database();
      if (stateSnapshot(activeDatabase).state() == SqliteBookState.BLANK_SQLITE) {
        return Optional.empty();
      }
      requireInitializedBook(activeDatabase);
      return postingReadSupport.findOnePosting(
          activeDatabase,
          SqlitePostingSql.FIND_POSTING_BY_IDEMPOTENCY,
          statement -> statement.bindText(1, idempotencyKey.value()));
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  /** Finds one committed posting by posting identifier when it exists in the selected book. */
  public Optional<PostingFact> findPosting(PostingId postingId) {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return Optional.empty();
    }
    try {
      SqliteNativeDatabase activeDatabase = database();
      if (stateSnapshot(activeDatabase).state() == SqliteBookState.BLANK_SQLITE) {
        return Optional.empty();
      }
      requireInitializedBook(activeDatabase);
      return postingReadSupport.findOnePosting(
          activeDatabase,
          SqlitePostingSql.FIND_POSTING_BY_ID,
          statement -> statement.bindText(1, postingId.value()));
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  /** Finds the committed reversal for one prior posting when it exists in the selected book. */
  public Optional<PostingFact> findReversalFor(PostingId priorPostingId) {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return Optional.empty();
    }
    try {
      SqliteNativeDatabase activeDatabase = database();
      if (stateSnapshot(activeDatabase).state() == SqliteBookState.BLANK_SQLITE) {
        return Optional.empty();
      }
      requireInitializedBook(activeDatabase);
      return postingReadSupport.findOnePosting(
          activeDatabase,
          SqlitePostingSql.FIND_REVERSAL_FOR,
          statement -> statement.bindText(1, priorPostingId.value()));
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  /** Lists committed postings using the requested page window and optional filters. */
  public PostingPage listPostings(ListPostingsQuery query) {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return new PostingPage(List.of(), query.limit(), Optional.empty());
    }
    try {
      SqliteNativeDatabase activeDatabase = database();
      if (stateSnapshot(activeDatabase).state() == SqliteBookState.BLANK_SQLITE) {
        return new PostingPage(List.of(), query.limit(), Optional.empty());
      }
      requireInitializedBook(activeDatabase);
      return postingReadSupport.loadPostingPage(activeDatabase, query);
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  /** Computes the account balance snapshot for one declared account query. */
  public Optional<AccountBalanceSnapshot> accountBalance(AccountBalanceQuery query) {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      throw new IllegalStateException(NOT_INITIALIZED_BOOK_MESSAGE);
    }
    try {
      SqliteNativeDatabase activeDatabase = database();
      requireInitializedBook(activeDatabase);
      return postingReadSupport.accountBalance(activeDatabase, query);
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  /** Commits one posting draft atomically inside the selected writable SQLite book. */
  @SuppressWarnings("PMD.CloseResource")
  public PostingCommitResult commit(
      PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
    ensureOpen();
    accessMode.requireWritableMutation();
    if (Files.notExists(bookPath)) {
      return new PostingCommitResult.Rejected(new PostingRejection.BookNotInitialized());
    }
    SqliteNativeDatabase activeDatabase = database();
    boolean ownsTransaction = false;
    try {
      ownsTransaction = beginImmediateIfNeeded(activeDatabase);
      Optional<PostingRejection> ordinaryOutcome =
          rejectionBeforeInsert(activeDatabase, postingDraft);
      if (ordinaryOutcome.isPresent()) {
        rollbackIfOwned(activeDatabase, ownsTransaction);
        return new PostingCommitResult.Rejected(ordinaryOutcome.orElseThrow());
      }
      PostingFact postingFact =
          postingDraft.materialize(
              Objects.requireNonNull(postingIdGenerator, "postingIdGenerator").nextPostingId());
      SqliteMutationWriter.insertPostingFact(activeDatabase, postingFact);
      SqliteMutationWriter.insertJournalLines(activeDatabase, postingFact);
      commitIfOwned(activeDatabase, ownsTransaction);
      return new PostingCommitResult.Committed(postingFact);
    } catch (SqliteNativeException exception) {
      rollbackIfOwned(activeDatabase, ownsTransaction);
      throw sqliteFailure("Failed to commit SQLite posting fact.", exception);
    }
  }

  /** Commits one fully materialized posting fact for fixture-oriented callers. */
  public PostingCommitResult commit(PostingFact postingFact) {
    Objects.requireNonNull(postingFact, "postingFact");
    return commit(
        new PostingDraft(
            postingFact.journalEntry(), postingFact.postingLineage(), postingFact.provenance()),
        postingFact::postingId);
  }

  @Override
  public void beginLedgerPlanTransaction() {
    ensureOpen();
    accessMode.requireWritableMutation();
    if (ledgerPlanTransactionActive) {
      throw new IllegalStateException("Ledger plan transaction is already active.");
    }
    ledgerPlanTransactionActive = true;
    ledgerPlanTransactionBegunInDatabase = false;
    if (accessMode.preservesMissingBookStateUntilMutation() && Files.notExists(bookPath)) {
      return;
    }
    SqliteSchemaManager.ensureParentDirectory(bookPath);
    try {
      database();
    } catch (IllegalStateException exception) {
      ledgerPlanTransactionActive = false;
      ledgerPlanTransactionBegunInDatabase = false;
      throw exception;
    }
  }

  @Override
  public void commitLedgerPlanTransaction() {
    ensureOpen();
    if (!ledgerPlanTransactionActive) {
      throw new IllegalStateException("No ledger plan transaction is active.");
    }
    if (!ledgerPlanTransactionBegunInDatabase) {
      ledgerPlanTransactionActive = false;
      return;
    }
    try {
      database().executeStatement("commit");
      ledgerPlanTransactionActive = false;
      ledgerPlanTransactionBegunInDatabase = false;
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to commit SQLite ledger plan transaction.", exception);
    }
  }

  @Override
  public void rollbackLedgerPlanTransaction() {
    if (!ledgerPlanTransactionActive) {
      return;
    }
    ledgerPlanTransactionActive = false;
    boolean rollbackDatabase = ledgerPlanTransactionBegunInDatabase;
    ledgerPlanTransactionBegunInDatabase = false;
    if (rollbackDatabase && database != null) {
      rollbackQuietly(database);
    }
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    try {
      if (database != null) {
        closeOwnedDatabase(database);
      }
      database = null;
      cachedBookState = null;
      closed = true;
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to close SQLite book connection.", exception);
    } finally {
      if (database == null) {
        closePendingPassphrase();
      }
    }
  }

  /** Rekeys one initialized FinGrind book and verifies the replacement passphrase durably. */
  @SuppressWarnings({"PMD.CloseResource", "PMD.UseTryWithResources"})
  public RekeyBookResult rekeyBook(SqliteBookPassphrase replacementPassphrase) {
    ensureOpen();
    accessMode.requireWritableMutation();
    SqliteBookPassphrase activeReplacementPassphrase =
        Objects.requireNonNull(replacementPassphrase, "replacementPassphrase");
    try {
      if (Files.notExists(bookPath)) {
        return new RekeyBookResult.Rejected(new BookAdministrationRejection.BookNotInitialized());
      }
      SqliteNativeDatabase activeDatabase = database();
      if (!isInitializedBook(activeDatabase)) {
        return new RekeyBookResult.Rejected(new BookAdministrationRejection.BookNotInitialized());
      }
      SqliteNativeLibrary.rekey(activeDatabase, activeReplacementPassphrase);
      closeOwnedDatabase(activeDatabase);
      database = null;
      cachedBookState = null;
      SqliteNativeDatabase reopenedDatabase = null;
      try {
        reopenedDatabase =
            SqliteConnectionSupport.configureOpenedDatabase(
                SqliteNativeLibrary.open(
                    bookPath, activeReplacementPassphrase, accessMode.nativeOpenMode()),
                accessMode);
        requireInitializedBook(reopenedDatabase);
        database = reopenedDatabase;
        return new RekeyBookResult.Rekeyed(bookPath);
      } catch (SqliteNativeException | RuntimeException exception) {
        closeReopenedDatabaseQuietly(reopenedDatabase);
        throw exception;
      }
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to rekey SQLite book.", exception);
    } finally {
      activeReplacementPassphrase.close();
    }
  }

  private Optional<PostingRejection> rejectionBeforeInsert(
      SqliteNativeDatabase activeDatabase, PostingRequest postingRequest) {
    return PostingValidation.rejectionFor(
        postingRequest, new TransactionValidationBook(activeDatabase));
  }

  private boolean isInitializedBook(SqliteNativeDatabase activeDatabase)
      throws SqliteNativeException {
    return stateSnapshot(activeDatabase).state() == SqliteBookState.INITIALIZED_FINGRIND;
  }

  private void requireInitializedBook(SqliteNativeDatabase activeDatabase)
      throws SqliteNativeException {
    SqliteBookStateSnapshot snapshot = stateSnapshot(activeDatabase);
    snapshot
        .state()
        .requireInitialized(
            snapshot.userVersion(), BOOK_FORMAT_VERSION, NOT_INITIALIZED_BOOK_MESSAGE);
  }

  private SqliteBookStateSnapshot stateSnapshot(SqliteNativeDatabase activeDatabase)
      throws SqliteNativeException {
    SqliteBookStateSnapshot snapshot = cachedBookState;
    if (snapshot != null) {
      return snapshot;
    }
    snapshot = BOOK_STATE_READER.snapshot(activeDatabase);
    cachedBookState = snapshot;
    return snapshot;
  }

  @SuppressWarnings("PMD.CloseResource")
  private SqliteNativeDatabase database() {
    SqliteNativeDatabase activeDatabase = database;
    if (activeDatabase != null) {
      return activeDatabase;
    }
    try (SqliteBookPassphrase passphrase = takeBookPassphrase()) {
      SqliteNativeDatabase openedDatabase =
          SqliteNativeLibrary.open(bookPath, passphrase, accessMode.nativeOpenMode());
      SqliteNativeDatabase configuredDatabase =
          SqliteConnectionSupport.configureOpenedDatabase(openedDatabase, accessMode);
      beginLedgerPlanTransactionIfNeeded(configuredDatabase);
      database = configuredDatabase;
      return configuredDatabase;
    } catch (SqliteNativeException exception) {
      throw rememberTerminalFailure(
          sqliteFailure("Failed to open SQLite book connection.", exception));
    } catch (IllegalStateException exception) {
      throw rememberTerminalFailure(exception);
    }
  }

  private void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("SQLite book session is already closed.");
    }
    IllegalStateException failure = terminalFailure;
    if (failure != null) {
      throw failure;
    }
  }

  private static void rollbackQuietly(SqliteNativeDatabase activeDatabase) {
    try {
      activeDatabase.executeStatement("rollback");
    } catch (SqliteNativeException ignored) {
      // Preserve the original failure or ordinary outcome.
    }
  }

  static void closeReopenedDatabaseQuietly(@Nullable SqliteNativeDatabase reopenedDatabase) {
    if (reopenedDatabase == null) {
      return;
    }
    try {
      reopenedDatabase.close();
    } catch (SqliteNativeException ignored) {
      ignored.resultName();
      // Preserve the original failure or ordinary outcome.
    }
  }

  private boolean beginImmediateIfNeeded(SqliteNativeDatabase activeDatabase)
      throws SqliteNativeException {
    if (ledgerPlanTransactionActive) {
      beginLedgerPlanTransactionIfNeeded(activeDatabase);
      return false;
    }
    activeDatabase.executeStatement("begin immediate");
    return true;
  }

  private void beginLedgerPlanTransactionIfNeeded(SqliteNativeDatabase activeDatabase)
      throws SqliteNativeException {
    if (ledgerPlanTransactionActive && !ledgerPlanTransactionBegunInDatabase) {
      activeDatabase.executeStatement("begin immediate");
      ledgerPlanTransactionBegunInDatabase = true;
    }
  }

  private static void commitIfOwned(SqliteNativeDatabase activeDatabase, boolean ownsTransaction)
      throws SqliteNativeException {
    if (ownsTransaction) {
      activeDatabase.executeStatement("commit");
    }
  }

  private static void rollbackIfOwned(
      SqliteNativeDatabase activeDatabase, boolean ownsTransaction) {
    if (ownsTransaction) {
      rollbackQuietly(activeDatabase);
    }
  }

  private static SqliteStorageFailureException sqliteFailure(
      String message, SqliteNativeException exception) {
    String detail = Objects.requireNonNullElse(exception.getMessage(), "SQLite native failure.");
    return new SqliteStorageFailureException(
        message + " " + exception.resultName() + ": " + detail, exception);
  }

  /** Narrow administration-session view over this SQLite-backed store. */
  private final class AdministrationView implements BookAdministrationSession {
    @Override
    public OpenBookResult openBook(Instant initializedAt) {
      return SqlitePostingFactStore.this.openBook(initializedAt);
    }

    @Override
    public DeclareAccountResult declareAccount(
        AccountCode accountCode,
        AccountName accountName,
        NormalBalance normalBalance,
        Instant declaredAt) {
      return SqlitePostingFactStore.this.declareAccount(
          accountCode, accountName, normalBalance, declaredAt);
    }

    @Override
    public void close() {
      SqlitePostingFactStore.this.close();
    }
  }

  /** Narrow posting-session view over this SQLite-backed store. */
  private final class PostingView implements PostingBookSession {
    @Override
    public boolean isInitialized() {
      return SqlitePostingFactStore.this.isInitialized();
    }

    @Override
    public Optional<DeclaredAccount> findAccount(AccountCode accountCode) {
      return SqlitePostingFactStore.this.findAccount(accountCode);
    }

    @Override
    public Map<AccountCode, DeclaredAccount> findAccounts(Set<AccountCode> accountCodes) {
      return SqlitePostingFactStore.this.findAccounts(accountCodes);
    }

    @Override
    public Optional<PostingFact> findExistingPosting(IdempotencyKey idempotencyKey) {
      return SqlitePostingFactStore.this.findExistingPosting(idempotencyKey);
    }

    @Override
    public Optional<PostingFact> findPosting(PostingId postingId) {
      return SqlitePostingFactStore.this.findPosting(postingId);
    }

    @Override
    public Optional<PostingFact> findReversalFor(PostingId priorPostingId) {
      return SqlitePostingFactStore.this.findReversalFor(priorPostingId);
    }

    @Override
    public PostingCommitResult commit(
        PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
      return SqlitePostingFactStore.this.commit(postingDraft, postingIdGenerator);
    }

    @Override
    public void close() {
      SqlitePostingFactStore.this.close();
    }
  }

  /** Narrow query-session view over this SQLite-backed store. */
  private final class QueryView implements BookQuerySession {
    @Override
    public BookInspection inspectBook() {
      return SqlitePostingFactStore.this.inspectBook();
    }

    @Override
    public boolean isInitialized() {
      return SqlitePostingFactStore.this.isInitialized();
    }

    @Override
    public AccountPage listAccounts(ListAccountsQuery query) {
      ensureOpen();
      try {
        return SqliteStatementQuerySupport.loadAccountPage(initializedQueryDatabase(), query);
      } catch (SqliteNativeException exception) {
        throw sqliteFailure("Failed to query SQLite book.", exception);
      }
    }

    @Override
    public Optional<DeclaredAccount> findAccount(AccountCode accountCode) {
      ensureOpen();
      try {
        return SqliteStatementQuerySupport.findOneAccount(initializedQueryDatabase(), accountCode);
      } catch (SqliteNativeException exception) {
        throw sqliteFailure("Failed to query SQLite book.", exception);
      }
    }

    @Override
    public Optional<PostingFact> findPosting(PostingId postingId) {
      ensureOpen();
      try {
        return postingReadSupport.findOnePosting(
            initializedQueryDatabase(),
            SqlitePostingSql.FIND_POSTING_BY_ID,
            statement -> statement.bindText(1, postingId.value()));
      } catch (SqliteNativeException exception) {
        throw sqliteFailure("Failed to query SQLite book.", exception);
      }
    }

    @Override
    public PostingPage listPostings(ListPostingsQuery query) {
      ensureOpen();
      try {
        return postingReadSupport.loadPostingPage(initializedQueryDatabase(), query);
      } catch (SqliteNativeException exception) {
        throw sqliteFailure("Failed to query SQLite book.", exception);
      }
    }

    @Override
    public Optional<AccountBalanceSnapshot> accountBalance(AccountBalanceQuery query) {
      ensureOpen();
      try {
        return postingReadSupport.accountBalance(initializedQueryDatabase(), query);
      } catch (SqliteNativeException exception) {
        throw sqliteFailure("Failed to query SQLite book.", exception);
      }
    }

    @Override
    public void close() {
      SqlitePostingFactStore.this.close();
    }

    private SqliteNativeDatabase initializedQueryDatabase() throws SqliteNativeException {
      if (Files.notExists(bookPath)) {
        throw new IllegalStateException(NOT_INITIALIZED_BOOK_MESSAGE);
      }
      SqliteNativeDatabase activeDatabase = database();
      requireInitializedBook(activeDatabase);
      return activeDatabase;
    }
  }

  /** Transaction-scoped validation view that rechecks posting invariants inside SQLite writes. */
  private final class TransactionValidationBook implements PostingValidationBook {
    private final SqliteNativeDatabase activeDatabase;

    private TransactionValidationBook(SqliteNativeDatabase activeDatabase) {
      this.activeDatabase = Objects.requireNonNull(activeDatabase, "activeDatabase");
    }

    @Override
    public boolean isInitialized() {
      try {
        return isInitializedBook(activeDatabase);
      } catch (SqliteNativeException exception) {
        throw sqliteFailure("Failed to query SQLite book.", exception);
      }
    }

    @Override
    public Optional<DeclaredAccount> findAccount(AccountCode accountCode) {
      return Optional.ofNullable(findAccounts(Set.of(accountCode)).get(accountCode));
    }

    @Override
    public Map<AccountCode, DeclaredAccount> findAccounts(Set<AccountCode> accountCodes) {
      try {
        return SqliteStatementQuerySupport.findAccounts(activeDatabase, accountCodes);
      } catch (SqliteNativeException exception) {
        throw sqliteFailure("Failed to query SQLite book.", exception);
      }
    }

    @Override
    public Optional<PostingFact> findExistingPosting(IdempotencyKey idempotencyKey) {
      return findPostingWithBinder(
          SqlitePostingSql.FIND_POSTING_BY_IDEMPOTENCY,
          statement -> statement.bindText(1, idempotencyKey.value()));
    }

    @Override
    public Optional<PostingFact> findPosting(PostingId postingId) {
      return findPostingWithBinder(
          SqlitePostingSql.FIND_POSTING_BY_ID,
          statement -> statement.bindText(1, postingId.value()));
    }

    @Override
    public Optional<PostingFact> findReversalFor(PostingId priorPostingId) {
      return findPostingWithBinder(
          SqlitePostingSql.FIND_REVERSAL_FOR,
          statement -> statement.bindText(1, priorPostingId.value()));
    }

    private Optional<PostingFact> findPostingWithBinder(
        String sql, SqliteStatementQuerySupport.Binder binder) {
      try {
        return postingReadSupport.findOnePosting(activeDatabase, sql, binder);
      } catch (SqliteNativeException exception) {
        throw sqliteFailure("Failed to query SQLite book.", exception);
      }
    }
  }

  private static SqliteBookPassphrase passphraseFor(BookAccess bookAccess) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    if (!(bookAccess.passphraseSource() instanceof BookAccess.PassphraseSource.KeyFile keyFile)) {
      throw new IllegalArgumentException(
          "SQLite same-package file-backed stores require a --book-key-file access selection.");
    }
    return SqliteBookKeyFile.load(keyFile.bookKeyFilePath());
  }

  private static void closeOwnedDatabase(SqliteNativeDatabase database)
      throws SqliteNativeException {
    database.close();
  }

  private void closePendingPassphrase() {
    if (bookPassphrase == null) {
      return;
    }
    takeBookPassphrase().close();
  }

  private SqliteBookPassphrase takeBookPassphrase() {
    SqliteBookPassphrase passphrase = bookPassphrase;
    bookPassphrase = null;
    if (passphrase == null) {
      throw new IllegalStateException("SQLite book passphrase is no longer available.");
    }
    return passphrase;
  }

  private IllegalStateException rememberTerminalFailure(IllegalStateException failure) {
    terminalFailure = Objects.requireNonNull(failure, "failure");
    return failure;
  }

  private static IllegalStateException foreignBookFailure() {
    return new IllegalStateException("The selected SQLite file is not a FinGrind book.");
  }

  private static IllegalStateException incompleteBookFailure() {
    return new IllegalStateException(
        "The selected FinGrind book is incomplete or corrupted and cannot be opened safely.");
  }

  private static IllegalStateException unsupportedBookVersionFailure(int loadedUserVersion) {
    return new IllegalStateException(
        "The selected FinGrind book format version "
            + loadedUserVersion
            + " is unsupported. Expected version "
            + BOOK_FORMAT_VERSION
            + ".");
  }

  @SuppressWarnings("PMD.CommentRequired")
  public enum AccessMode {
    READ_ONLY(SqliteNativeLibrary.OpenMode.READ_ONLY, true, false, false),
    READ_WRITE_EXISTING(SqliteNativeLibrary.OpenMode.READ_WRITE_EXISTING, false, true, false),
    READ_WRITE_CREATE(SqliteNativeLibrary.OpenMode.READ_WRITE_CREATE, false, true, true),
    PLAN_EXECUTION(SqliteNativeLibrary.OpenMode.READ_WRITE_CREATE, false, true, true);

    private final SqliteNativeLibrary.OpenMode nativeOpenMode;
    private final boolean queryOnly;
    private final boolean writable;
    private final boolean createsFiles;

    AccessMode(
        SqliteNativeLibrary.OpenMode nativeOpenMode,
        boolean queryOnly,
        boolean writable,
        boolean createsFiles) {
      this.nativeOpenMode = Objects.requireNonNull(nativeOpenMode, "nativeOpenMode");
      this.queryOnly = queryOnly;
      this.writable = writable;
      this.createsFiles = createsFiles;
    }

    SqliteNativeLibrary.OpenMode nativeOpenMode() {
      return nativeOpenMode;
    }

    int queryOnlyPragmaValue() {
      return queryOnly ? 1 : 0;
    }

    boolean writesJournalMode() {
      return writable;
    }

    boolean preservesMissingBookStateUntilMutation() {
      return this == PLAN_EXECUTION;
    }

    void requireWritableMutation() {
      if (!writable) {
        throw new IllegalStateException(
            "This FinGrind SQLite session is read-only and cannot mutate the book.");
      }
    }

    void requireWritableInitialization() {
      if (!createsFiles) {
        throw new IllegalStateException(
            "This FinGrind SQLite session cannot initialize or create a book file.");
      }
    }
  }
}
