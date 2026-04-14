package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.application.BookAccess;
import dev.erst.fingrind.application.BookAdministrationRejection;
import dev.erst.fingrind.application.BookSession;
import dev.erst.fingrind.application.DeclareAccountResult;
import dev.erst.fingrind.application.DeclaredAccount;
import dev.erst.fingrind.application.OpenBookResult;
import dev.erst.fingrind.application.PostingCommitResult;
import dev.erst.fingrind.application.PostingFact;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * SQLite-backed book session that keeps one in-process database handle per opened book.
 *
 * <p>This session is thread-confined. One CLI command owns one instance and uses it on one thread.
 */
public final class SqlitePostingFactStore implements BookSession {
  static final int BOOK_APPLICATION_ID = 1_179_079_236; // "FGRD"
  static final int BOOK_FORMAT_VERSION = 1;

  private static final String ACCOUNT_TABLE = "account";
  private static final String BOOK_META_TABLE = "book_meta";
  private static final String JOURNAL_LINE_TABLE = "journal_line";
  private static final String NOT_INITIALIZED_BOOK_MESSAGE =
      "The selected SQLite file is not initialized as a FinGrind book.";
  private static final String POSTING_FACT_TABLE = "posting_fact";

  private final Path bookPath;
  private final AccessMode accessMode;
  private SqliteBookPassphrase bookPassphrase;

  private SqliteNativeDatabase database;
  private boolean closed;
  private IllegalStateException terminalFailure;

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
  }

  SqlitePostingFactStore(BookAccess bookAccess) {
    this(bookAccess, AccessMode.READ_WRITE_CREATE);
  }

  SqlitePostingFactStore(BookAccess bookAccess, AccessMode accessMode) {
    this(bookAccess.bookFilePath(), passphraseFor(bookAccess), accessMode);
  }

  @Override
  public boolean isInitialized() {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return false;
    }
    try {
      return switch (bookState(database())) {
        case BLANK_SQLITE -> false;
        case INITIALIZED_FINGRIND -> true;
        case FOREIGN_SQLITE -> throw foreignBookFailure();
        case UNSUPPORTED_FINGRIND_VERSION -> throw unsupportedBookVersionFailure(database);
        case INCOMPLETE_FINGRIND -> throw incompleteBookFailure();
      };
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public OpenBookResult openBook(Instant initializedAt) {
    ensureOpen();
    accessMode.requireWritableInitialization();
    SqliteSchemaManager.ensureParentDirectory(bookPath);
    database();
    try {
      BookState state = bookState(database);
      if (state == BookState.INITIALIZED_FINGRIND) {
        return new OpenBookResult.Rejected(
            new BookAdministrationRejection.BookAlreadyInitialized());
      }
      if (state == BookState.FOREIGN_SQLITE) {
        return new OpenBookResult.Rejected(new BookAdministrationRejection.BookContainsSchema());
      }
      if (state == BookState.UNSUPPORTED_FINGRIND_VERSION) {
        throw unsupportedBookVersionFailure(database);
      }
      if (state == BookState.INCOMPLETE_FINGRIND) {
        throw incompleteBookFailure();
      }

      database.executeStatement("begin immediate");
      SqliteSchemaManager.initializeBook(database);
      insertInitializedAt(database, initializedAt);
      database.executeStatement("commit");
      return new OpenBookResult.Opened(initializedAt);
    } catch (SqliteNativeException exception) {
      rollbackQuietly(database);
      throw sqliteFailure("Failed to initialize SQLite book.", exception);
    }
  }

  @Override
  public Optional<DeclaredAccount> findAccount(AccountCode accountCode) {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return Optional.empty();
    }
    try {
      SqliteNativeDatabase activeDatabase = database();
      if (bookState(activeDatabase) == BookState.BLANK_SQLITE) {
        return Optional.empty();
      }
      requireInitializedBook(activeDatabase);
      return findOneAccount(activeDatabase, accountCode);
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
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
    database();
    try {
      if (!isInitializedBook(database)) {
        return new DeclareAccountResult.Rejected(
            new BookAdministrationRejection.BookNotInitialized());
      }

      database.executeStatement("begin immediate");
      Optional<DeclaredAccount> existingAccount = findOneAccount(database, accountCode);
      if (existingAccount.isPresent()
          && existingAccount.orElseThrow().normalBalance() != normalBalance) {
        rollbackQuietly(database);
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
      upsertAccount(database, declaredAccount);
      database.executeStatement("commit");
      return new DeclareAccountResult.Declared(declaredAccount);
    } catch (SqliteNativeException exception) {
      rollbackQuietly(database);
      throw sqliteFailure("Failed to declare SQLite book account.", exception);
    }
  }

  @Override
  public List<DeclaredAccount> listAccounts() {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return List.of();
    }
    try {
      SqliteNativeDatabase activeDatabase = database();
      if (bookState(activeDatabase) == BookState.BLANK_SQLITE) {
        return List.of();
      }
      requireInitializedBook(activeDatabase);
      return loadAccounts(activeDatabase);
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public Optional<PostingFact> findByIdempotency(IdempotencyKey idempotencyKey) {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return Optional.empty();
    }
    try {
      SqliteNativeDatabase activeDatabase = database();
      if (bookState(activeDatabase) == BookState.BLANK_SQLITE) {
        return Optional.empty();
      }
      requireInitializedBook(activeDatabase);
      return findOnePosting(
          activeDatabase,
          SqlitePostingSql.FIND_POSTING_BY_IDEMPOTENCY,
          statement -> statement.bindText(1, idempotencyKey.value()));
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public Optional<PostingFact> findByPostingId(PostingId postingId) {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return Optional.empty();
    }
    try {
      SqliteNativeDatabase activeDatabase = database();
      if (bookState(activeDatabase) == BookState.BLANK_SQLITE) {
        return Optional.empty();
      }
      requireInitializedBook(activeDatabase);
      return findOnePosting(
          activeDatabase,
          SqlitePostingSql.FIND_POSTING_BY_ID,
          statement -> statement.bindText(1, postingId.value()));
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public Optional<PostingFact> findReversalFor(PostingId priorPostingId) {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return Optional.empty();
    }
    try {
      SqliteNativeDatabase activeDatabase = database();
      if (bookState(activeDatabase) == BookState.BLANK_SQLITE) {
        return Optional.empty();
      }
      requireInitializedBook(activeDatabase);
      return findOnePosting(
          activeDatabase,
          SqlitePostingSql.FIND_REVERSAL_FOR,
          statement -> statement.bindText(1, priorPostingId.value()));
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public PostingCommitResult commit(PostingFact postingFact) {
    ensureOpen();
    accessMode.requireWritableMutation();
    if (Files.notExists(bookPath)) {
      return new PostingCommitResult.BookNotInitialized();
    }
    database();
    try {
      if (!isInitializedBook(database)) {
        return new PostingCommitResult.BookNotInitialized();
      }
      database.executeStatement("begin immediate");
      Optional<PostingCommitResult> ordinaryOutcome =
          ordinaryOutcomeBeforeInsert(database, postingFact);
      if (ordinaryOutcome.isPresent()) {
        rollbackQuietly(database);
        return ordinaryOutcome.orElseThrow();
      }
      insertPostingFact(database, postingFact);
      insertJournalLines(database, postingFact);
      database.executeStatement("commit");
      return new PostingCommitResult.Committed(postingFact);
    } catch (SqliteNativeException exception) {
      rollbackQuietly(database);
      throw sqliteFailure("Failed to commit SQLite posting fact.", exception);
    }
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    SqliteNativeDatabase activeDatabase = database;
    database = null;
    try {
      if (activeDatabase == null) {
        return;
      }
      closeOwnedDatabase(activeDatabase);
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to close SQLite book connection.", exception);
    } finally {
      closePendingPassphrase();
    }
  }

  /** Rekeys one initialized FinGrind book and verifies the replacement passphrase durably. */
  @SuppressWarnings("PMD.UseTryWithResources")
  public RekeyBookResult rekeyBook(SqliteBookPassphrase replacementPassphrase) {
    ensureOpen();
    accessMode.requireWritableMutation();
    SqliteBookPassphrase activeReplacementPassphrase =
        Objects.requireNonNull(replacementPassphrase, "replacementPassphrase");
    boolean databaseHandleClosedForRekey = false;
    try {
      if (Files.notExists(bookPath)) {
        return new RekeyBookResult.Rejected(new BookAdministrationRejection.BookNotInitialized());
      }
      database();
      if (!isInitializedBook(database)) {
        return new RekeyBookResult.Rejected(new BookAdministrationRejection.BookNotInitialized());
      }
      SqliteNativeLibrary.rekey(database, activeReplacementPassphrase);
      closeOwnedDatabase(database);
      databaseHandleClosedForRekey = true;
      database =
          configureOpenedDatabase(
              SqliteNativeLibrary.open(
                  bookPath, activeReplacementPassphrase, accessMode.nativeOpenMode()),
              accessMode);
      databaseHandleClosedForRekey = false;
      requireInitializedBook(database);
      return new RekeyBookResult.Rekeyed(bookPath);
    } catch (SqliteNativeException exception) {
      if (databaseHandleClosedForRekey) {
        database = null;
      }
      throw sqliteFailure("Failed to rekey SQLite book.", exception);
    } finally {
      activeReplacementPassphrase.close();
    }
  }

  private Optional<PostingCommitResult> ordinaryOutcomeBeforeInsert(
      SqliteNativeDatabase activeDatabase, PostingFact postingFact) throws SqliteNativeException {
    if (!isInitializedBook(activeDatabase)) {
      return Optional.of(new PostingCommitResult.BookNotInitialized());
    }

    Optional<PostingCommitResult> accountOutcome =
        accountOutcomeBeforeInsert(activeDatabase, postingFact);
    if (accountOutcome.isPresent()) {
      return accountOutcome;
    }

    if (existsRow(
        activeDatabase,
        SqlitePostingSql.EXISTS_POSTING_BY_IDEMPOTENCY,
        statement ->
            statement.bindText(
                1, postingFact.provenance().requestProvenance().idempotencyKey().value()))) {
      return Optional.of(
          new PostingCommitResult.DuplicateIdempotency(
              postingFact.provenance().requestProvenance().idempotencyKey()));
    }

    ReversalReference reversalReference = postingFact.reversalReference().orElse(null);
    if (reversalReference != null) {
      PostingId priorPostingId = reversalReference.priorPostingId();
      if (existsRow(
          activeDatabase,
          SqlitePostingSql.EXISTS_REVERSAL_FOR,
          statement -> statement.bindText(1, priorPostingId.value()))) {
        return Optional.of(new PostingCommitResult.DuplicateReversalTarget(priorPostingId));
      }
    }
    return Optional.empty();
  }

  private Optional<PostingCommitResult> accountOutcomeBeforeInsert(
      SqliteNativeDatabase activeDatabase, PostingFact postingFact) throws SqliteNativeException {
    for (JournalLine line : postingFact.journalEntry().lines()) {
      Optional<DeclaredAccount> account = findOneAccount(activeDatabase, line.accountCode());
      if (account.isEmpty()) {
        return unknownAccount(line.accountCode());
      }
      if (!account.orElseThrow().active()) {
        return inactiveAccount(line.accountCode());
      }
    }
    return Optional.empty();
  }

  private static Optional<PostingCommitResult> unknownAccount(AccountCode accountCode) {
    return Optional.of(new PostingCommitResult.UnknownAccount(accountCode));
  }

  private static Optional<PostingCommitResult> inactiveAccount(AccountCode accountCode) {
    return Optional.of(new PostingCommitResult.InactiveAccount(accountCode));
  }

  private Optional<PostingFact> findOnePosting(
      SqliteNativeDatabase activeDatabase, String sql, SqliteBinder binder) {
    try {
      return executeFindOnePosting(activeDatabase, sql, binder);
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @SuppressWarnings("PMD.UseTryWithResources")
  private Optional<PostingFact> executeFindOnePosting(
      SqliteNativeDatabase activeDatabase, String sql, SqliteBinder binder)
      throws SqliteNativeException {
    // This path closes a single-use statement deterministically without try-with-resources so the
    // helper stays free of compiler-generated close branches under the module's 100% coverage gate.
    SqliteNativeStatement statement = SqliteNativeLibrary.prepare(activeDatabase, sql);
    try {
      binder.bind(statement);
      int resultCode = statement.step();
      if (resultCode == SqliteNativeLibrary.SQLITE_DONE) {
        return Optional.empty();
      }
      PostingId postingId =
          new PostingId(
              SqlitePostingMapper.requiredText(statement, SqlitePostingSql.COL_POSTING_ID));
      return Optional.of(
          SqlitePostingMapper.postingFact(statement, loadLines(activeDatabase, postingId)));
    } finally {
      statement.close();
    }
  }

  @SuppressWarnings("PMD.UseTryWithResources")
  private Optional<DeclaredAccount> findOneAccount(
      SqliteNativeDatabase activeDatabase, AccountCode accountCode) throws SqliteNativeException {
    // This path closes a single-use statement deterministically without try-with-resources so the
    // helper stays free of compiler-generated close branches under the module's 100% coverage gate.
    SqliteNativeStatement statement =
        SqliteNativeLibrary.prepare(activeDatabase, SqlitePostingSql.FIND_ACCOUNT_BY_CODE);
    try {
      statement.bindText(1, accountCode.value());
      if (statement.step() == SqliteNativeLibrary.SQLITE_DONE) {
        return Optional.empty();
      }
      return Optional.of(SqlitePostingMapper.declaredAccount(statement));
    } finally {
      statement.close();
    }
  }

  private List<DeclaredAccount> loadAccounts(SqliteNativeDatabase activeDatabase)
      throws SqliteNativeException {
    List<DeclaredAccount> accounts = new ArrayList<>();
    try (SqliteNativeStatement statement =
        SqliteNativeLibrary.prepare(activeDatabase, SqlitePostingSql.LIST_ACCOUNTS)) {
      while (statement.step() == SqliteNativeLibrary.SQLITE_ROW) {
        accounts.add(SqlitePostingMapper.declaredAccount(statement));
      }
    }
    return accounts;
  }

  private boolean existsRow(SqliteNativeDatabase activeDatabase, String sql, SqliteBinder binder)
      throws SqliteNativeException {
    try (SqliteNativeStatement statement = SqliteNativeLibrary.prepare(activeDatabase, sql)) {
      binder.bind(statement);
      return statement.step() == SqliteNativeLibrary.SQLITE_ROW;
    }
  }

  private boolean existsTable(SqliteNativeDatabase activeDatabase, String tableName)
      throws SqliteNativeException {
    return existsRow(
        activeDatabase,
        SqlitePostingSql.TABLE_EXISTS,
        statement -> statement.bindText(1, tableName));
  }

  private boolean hasUserSchemaObjects(SqliteNativeDatabase activeDatabase)
      throws SqliteNativeException {
    return existsRow(activeDatabase, SqlitePostingSql.USER_SCHEMA_EXISTS, statement -> {});
  }

  private boolean hasCanonicalTables(SqliteNativeDatabase activeDatabase)
      throws SqliteNativeException {
    return existsTable(activeDatabase, BOOK_META_TABLE)
        && existsTable(activeDatabase, ACCOUNT_TABLE)
        && existsTable(activeDatabase, POSTING_FACT_TABLE)
        && existsTable(activeDatabase, JOURNAL_LINE_TABLE);
  }

  private boolean hasInitializedMarker(SqliteNativeDatabase activeDatabase)
      throws SqliteNativeException {
    return existsTable(activeDatabase, BOOK_META_TABLE)
        && existsRow(
            activeDatabase,
            SqlitePostingSql.BOOK_INITIALIZED_EXISTS,
            statement -> statement.bindText(1, SqlitePostingSql.INITIALIZED_AT_META_KEY));
  }

  private boolean isInitializedBook(SqliteNativeDatabase activeDatabase)
      throws SqliteNativeException {
    return bookState(activeDatabase) == BookState.INITIALIZED_FINGRIND;
  }

  private void requireInitializedBook(SqliteNativeDatabase activeDatabase)
      throws SqliteNativeException {
    BookState state = bookState(activeDatabase);
    if (state == BookState.INITIALIZED_FINGRIND) {
      return;
    }
    if (state == BookState.BLANK_SQLITE) {
      throw new IllegalStateException(NOT_INITIALIZED_BOOK_MESSAGE);
    }
    if (state == BookState.FOREIGN_SQLITE) {
      throw foreignBookFailure();
    }
    if (state == BookState.UNSUPPORTED_FINGRIND_VERSION) {
      throw unsupportedBookVersionFailure(activeDatabase);
    }
    throw incompleteBookFailure();
  }

  private BookState bookState(SqliteNativeDatabase activeDatabase) throws SqliteNativeException {
    int applicationId = querySingleInt(activeDatabase, "pragma application_id");
    int userVersion = querySingleInt(activeDatabase, "pragma user_version");
    if (applicationId == 0 && userVersion == 0 && !hasUserSchemaObjects(activeDatabase)) {
      return BookState.BLANK_SQLITE;
    }
    if (applicationId == BOOK_APPLICATION_ID) {
      if (userVersion != BOOK_FORMAT_VERSION) {
        return BookState.UNSUPPORTED_FINGRIND_VERSION;
      }
      if (hasCanonicalTables(activeDatabase) && hasInitializedMarker(activeDatabase)) {
        return BookState.INITIALIZED_FINGRIND;
      }
      return BookState.INCOMPLETE_FINGRIND;
    }
    return BookState.FOREIGN_SQLITE;
  }

  private static int querySingleInt(SqliteNativeDatabase activeDatabase, String sql)
      throws SqliteNativeException {
    OptionalInt value = queryOptionalInt(activeDatabase, sql);
    if (value.isEmpty()) {
      throw new IllegalStateException("SQLite integer query returned no rows: " + sql);
    }
    return value.orElseThrow();
  }

  @SuppressWarnings("PMD.UseTryWithResources")
  private static OptionalInt queryOptionalInt(SqliteNativeDatabase activeDatabase, String sql)
      throws SqliteNativeException {
    SqliteNativeStatement statement = SqliteNativeLibrary.prepare(activeDatabase, sql);
    try {
      if (statement.step() != SqliteNativeLibrary.SQLITE_ROW) {
        return OptionalInt.empty();
      }
      int value = statement.columnInt(0);
      if (statement.step() != SqliteNativeLibrary.SQLITE_DONE) {
        throw new IllegalStateException("SQLite integer query returned more than one row: " + sql);
      }
      return OptionalInt.of(value);
    } finally {
      statement.close();
    }
  }

  private List<JournalLine> loadLines(SqliteNativeDatabase activeDatabase, PostingId postingId)
      throws SqliteNativeException {
    try (SqliteNativeStatement statement =
        SqliteNativeLibrary.prepare(activeDatabase, SqlitePostingSql.LOAD_LINES)) {
      statement.bindText(1, postingId.value());
      return SqlitePostingMapper.journalLines(statement);
    }
  }

  private void insertInitializedAt(SqliteNativeDatabase activeDatabase, Instant initializedAt)
      throws SqliteNativeException {
    try (SqliteNativeStatement statement =
        SqliteNativeLibrary.prepare(activeDatabase, SqlitePostingSql.INSERT_BOOK_INITIALIZED_AT)) {
      statement.bindText(1, SqlitePostingSql.INITIALIZED_AT_META_KEY);
      statement.bindText(2, initializedAt.toString());
      statement.step();
    }
  }

  private void upsertAccount(SqliteNativeDatabase activeDatabase, DeclaredAccount account)
      throws SqliteNativeException {
    try (SqliteNativeStatement statement =
        SqliteNativeLibrary.prepare(activeDatabase, SqlitePostingSql.UPSERT_ACCOUNT)) {
      statement.bindText(1, account.accountCode().value());
      statement.bindText(2, account.accountName().value());
      statement.bindText(3, account.normalBalance().name());
      statement.bindInt(4, Boolean.compare(account.active(), false));
      statement.bindText(5, account.declaredAt().toString());
      statement.step();
    }
  }

  private void insertPostingFact(SqliteNativeDatabase activeDatabase, PostingFact postingFact)
      throws SqliteNativeException {
    RequestProvenance requestProvenance = postingFact.provenance().requestProvenance();
    try (SqliteNativeStatement statement =
        SqliteNativeLibrary.prepare(activeDatabase, SqlitePostingSql.INSERT_POSTING_FACT)) {
      statement.bindText(1, postingFact.postingId().value());
      statement.bindText(2, postingFact.journalEntry().effectiveDate().toString());
      statement.bindText(3, postingFact.provenance().recordedAt().toString());
      statement.bindText(4, requestProvenance.actorId().value());
      statement.bindText(5, requestProvenance.actorType().name());
      statement.bindText(6, requestProvenance.commandId().value());
      statement.bindText(7, requestProvenance.idempotencyKey().value());
      statement.bindText(8, requestProvenance.causationId().value());
      statement.bindText(
          9, requestProvenance.correlationId().map(value -> value.value()).orElse(null));
      statement.bindText(10, requestProvenance.reason().map(value -> value.value()).orElse(null));
      statement.bindText(11, postingFact.provenance().sourceChannel().name());
      statement.bindText(
          12,
          postingFact
              .reversalReference()
              .map(reference -> reference.priorPostingId().value())
              .orElse(null));
      statement.step();
    }
  }

  private void insertJournalLines(SqliteNativeDatabase activeDatabase, PostingFact postingFact)
      throws SqliteNativeException {
    List<JournalLine> lines = postingFact.journalEntry().lines();
    for (int index = 0; index < lines.size(); index++) {
      JournalLine line = lines.get(index);
      try (SqliteNativeStatement statement =
          SqliteNativeLibrary.prepare(activeDatabase, SqlitePostingSql.INSERT_JOURNAL_LINE)) {
        statement.bindText(1, postingFact.postingId().value());
        statement.bindInt(2, index);
        statement.bindText(3, line.accountCode().value());
        statement.bindText(4, line.side().name());
        statement.bindText(5, line.amount().currencyCode().value());
        statement.bindText(6, line.amount().amount().toPlainString());
        statement.step();
      }
    }
  }

  private SqliteNativeDatabase database() {
    if (database != null) {
      return database;
    }
    try (SqliteBookPassphrase passphrase = takeBookPassphrase()) {
      SqliteNativeDatabase openedDatabase =
          SqliteNativeLibrary.open(bookPath, passphrase, accessMode.nativeOpenMode());
      database = configureOpenedDatabase(openedDatabase, accessMode);
      return database;
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
    if (terminalFailure != null) {
      throw terminalFailure;
    }
  }

  private static void rollbackQuietly(SqliteNativeDatabase activeDatabase) {
    try {
      activeDatabase.executeStatement("rollback");
    } catch (SqliteNativeException ignored) {
      // Preserve the original failure or ordinary outcome.
    }
  }

  private static IllegalStateException sqliteFailure(
      String message, SqliteNativeException exception) {
    String detail = Objects.requireNonNullElse(exception.getMessage(), "SQLite native failure.");
    return new IllegalStateException(
        message + " " + exception.resultName() + ": " + detail, exception);
  }

  @SuppressWarnings("PMD.CommentRequired")
  @FunctionalInterface
  private interface SqliteBinder {
    @SuppressWarnings("PMD.CommentRequired")
    void bind(SqliteNativeStatement statement) throws SqliteNativeException;
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
    bookPassphrase.close();
    bookPassphrase = null;
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

  private static IllegalStateException unsupportedBookVersionFailure(
      SqliteNativeDatabase activeDatabase) throws SqliteNativeException {
    int loadedUserVersion = querySingleInt(activeDatabase, "pragma user_version");
    return new IllegalStateException(
        "The selected FinGrind book format version "
            + loadedUserVersion
            + " is unsupported. Expected version "
            + BOOK_FORMAT_VERSION
            + ".");
  }

  static SqliteNativeDatabase configureOpenedDatabase(
      SqliteNativeDatabase openedDatabase, AccessMode accessMode) throws SqliteNativeException {
    try {
      Objects.requireNonNull(accessMode, "accessMode");
      openedDatabase.executeScript(
          """
          pragma foreign_keys = on;
          pragma trusted_schema = off;
          pragma secure_delete = on;
          pragma temp_store = memory;
          pragma memory_security = fill;
          pragma query_only = %d;
          """
              .formatted(accessMode.queryOnlyPragmaValue()));
      assertOpenConfiguration(openedDatabase, accessMode);
      return openedDatabase;
    } catch (SqliteNativeException | RuntimeException exception) {
      closeAfterConfigurationFailure(openedDatabase);
      throw exception;
    }
  }

  private static void assertOpenConfiguration(
      SqliteNativeDatabase openedDatabase, AccessMode accessMode) throws SqliteNativeException {
    if (querySingleInt(openedDatabase, "pragma foreign_keys") != 1) {
      throw new IllegalStateException("SQLite connection failed to keep foreign_keys enabled.");
    }
    if (querySingleInt(openedDatabase, "pragma trusted_schema") != 0) {
      throw new IllegalStateException("SQLite connection failed to disable trusted_schema.");
    }
    if (querySingleInt(openedDatabase, "pragma secure_delete") != 1) {
      throw new IllegalStateException("SQLite connection failed to enable secure_delete.");
    }
    if (querySingleInt(openedDatabase, "pragma temp_store") != 2) {
      throw new IllegalStateException("SQLite connection failed to force temp_store=MEMORY.");
    }
    requireOptionalPragmaValue(
        queryOptionalInt(openedDatabase, "pragma memory_security"),
        1,
        "SQLite connection failed to enable memory_security=fill.");
    if (querySingleInt(openedDatabase, "pragma query_only") != accessMode.queryOnlyPragmaValue()) {
      throw new IllegalStateException(
          "SQLite connection failed to enforce the expected query_only setting.");
    }
  }

  static void closeAfterConfigurationFailure(SqliteNativeDatabase openedDatabase) {
    try {
      openedDatabase.close();
    } catch (SqliteNativeException ignored) {
      // Preserve the original open/configuration failure.
    }
  }

  static void requireOptionalPragmaValue(
      OptionalInt actualValue, int expectedValue, String failureMessage) {
    Objects.requireNonNull(actualValue, "actualValue");
    Objects.requireNonNull(failureMessage, "failureMessage");
    if (actualValue.isPresent() && actualValue.orElseThrow() != expectedValue) {
      throw new IllegalStateException(failureMessage);
    }
  }

  @SuppressWarnings("PMD.CommentRequired")
  public enum AccessMode {
    READ_ONLY(SqliteNativeLibrary.OpenMode.READ_ONLY, true, false, false),
    READ_WRITE_EXISTING(SqliteNativeLibrary.OpenMode.READ_WRITE_EXISTING, false, true, false),
    READ_WRITE_CREATE(SqliteNativeLibrary.OpenMode.READ_WRITE_CREATE, false, true, true);

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

  @SuppressWarnings("PMD.CommentRequired")
  private enum BookState {
    BLANK_SQLITE,
    INITIALIZED_FINGRIND,
    FOREIGN_SQLITE,
    UNSUPPORTED_FINGRIND_VERSION,
    INCOMPLETE_FINGRIND
  }
}
