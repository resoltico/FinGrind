package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.*;
import dev.erst.fingrind.core.*;
import dev.erst.fingrind.executor.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/**
 * SQLite-backed book session that keeps one in-process database handle per opened book.
 *
 * <p>This session is thread-confined. One CLI command owns one instance and uses it on one thread.
 */
public final class SqlitePostingFactStore implements LedgerPlanSession {
  static final int BOOK_APPLICATION_ID = 1_179_079_236; // "FGRD"
  static final int BOOK_FORMAT_VERSION = 1;

  private static final String ACCOUNT_TABLE = "account";
  private static final String BOOK_META_TABLE = "book_meta";
  private static final String JOURNAL_LINE_TABLE = "journal_line";
  private static final String REQUIRED_JOURNAL_MODE = "delete";
  private static final int REQUIRED_SYNCHRONOUS_MODE = 3;
  private static final String NOT_INITIALIZED_BOOK_MESSAGE =
      "The selected SQLite file is not initialized as a FinGrind book.";
  private static final String POSTING_FACT_TABLE = "posting_fact";

  private final Path bookPath;
  private final AccessMode accessMode;
  private @Nullable SqliteBookPassphrase bookPassphrase;

  private @Nullable SqliteNativeDatabase database;
  private boolean closed;
  private boolean ledgerPlanTransactionActive;
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
    this.database = null;
    this.terminalFailure = null;
  }

  SqlitePostingFactStore(BookAccess bookAccess) {
    this(bookAccess, AccessMode.READ_WRITE_CREATE);
  }

  SqlitePostingFactStore(BookAccess bookAccess, AccessMode accessMode) {
    this(bookAccess.bookFilePath(), passphraseFor(bookAccess), accessMode);
  }

  @Override
  public BookInspection inspectBook() {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return new BookInspection(
          BookInspection.Status.MISSING,
          false,
          false,
          true,
          null,
          null,
          BOOK_FORMAT_VERSION,
          "hard-break-no-migration",
          null);
    }
    try {
      SqliteNativeDatabase activeDatabase = database();
      int applicationId = querySingleInt(activeDatabase, "pragma application_id");
      int userVersion = querySingleInt(activeDatabase, "pragma user_version");
      BookState state = bookState(activeDatabase);
      return switch (state) {
        case BLANK_SQLITE ->
            new BookInspection(
                BookInspection.Status.BLANK_SQLITE,
                false,
                false,
                true,
                applicationId,
                userVersion,
                BOOK_FORMAT_VERSION,
                "hard-break-no-migration",
                null);
        case INITIALIZED_FINGRIND ->
            new BookInspection(
                BookInspection.Status.INITIALIZED,
                true,
                true,
                false,
                applicationId,
                userVersion,
                BOOK_FORMAT_VERSION,
                "hard-break-no-migration",
                loadInitializedAt(activeDatabase).orElse(null));
        case FOREIGN_SQLITE ->
            new BookInspection(
                BookInspection.Status.FOREIGN_SQLITE,
                false,
                false,
                false,
                applicationId,
                userVersion,
                BOOK_FORMAT_VERSION,
                "hard-break-no-migration",
                null);
        case UNSUPPORTED_FINGRIND_VERSION ->
            new BookInspection(
                BookInspection.Status.UNSUPPORTED_FORMAT_VERSION,
                false,
                false,
                false,
                applicationId,
                userVersion,
                BOOK_FORMAT_VERSION,
                "hard-break-no-migration",
                null);
        case INCOMPLETE_FINGRIND ->
            new BookInspection(
                BookInspection.Status.INCOMPLETE_FINGRIND,
                false,
                false,
                false,
                applicationId,
                userVersion,
                BOOK_FORMAT_VERSION,
                "hard-break-no-migration",
                null);
      };
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to inspect SQLite book.", exception);
    }
  }

  @Override
  public boolean isInitialized() {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return false;
    }
    try {
      SqliteNativeDatabase activeDatabase = database();
      return switch (bookState(activeDatabase)) {
        case BLANK_SQLITE -> false;
        case INITIALIZED_FINGRIND -> true;
        case FOREIGN_SQLITE -> throw foreignBookFailure();
        case UNSUPPORTED_FINGRIND_VERSION -> throw unsupportedBookVersionFailure(activeDatabase);
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
    SqliteNativeDatabase activeDatabase = database();
    boolean ownsTransaction = false;
    try {
      BookState state = bookState(activeDatabase);
      if (state == BookState.INITIALIZED_FINGRIND) {
        return new OpenBookResult.Rejected(
            new BookAdministrationRejection.BookAlreadyInitialized());
      }
      if (state == BookState.FOREIGN_SQLITE) {
        return new OpenBookResult.Rejected(new BookAdministrationRejection.BookContainsSchema());
      }
      if (state == BookState.UNSUPPORTED_FINGRIND_VERSION) {
        throw unsupportedBookVersionFailure(activeDatabase);
      }
      if (state == BookState.INCOMPLETE_FINGRIND) {
        throw incompleteBookFailure();
      }

      ownsTransaction = beginImmediateIfNeeded(activeDatabase);
      SqliteSchemaManager.initializeBook(activeDatabase);
      insertInitializedAt(activeDatabase, initializedAt);
      commitIfOwned(activeDatabase, ownsTransaction);
      return new OpenBookResult.Opened(initializedAt);
    } catch (SqliteNativeException exception) {
      rollbackIfOwned(activeDatabase, ownsTransaction);
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
    SqliteNativeDatabase activeDatabase = database();
    boolean ownsTransaction = false;
    try {
      if (!isInitializedBook(activeDatabase)) {
        return new DeclareAccountResult.Rejected(
            new BookAdministrationRejection.BookNotInitialized());
      }

      ownsTransaction = beginImmediateIfNeeded(activeDatabase);
      Optional<DeclaredAccount> existingAccount = findOneAccount(activeDatabase, accountCode);
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
      upsertAccount(activeDatabase, declaredAccount);
      commitIfOwned(activeDatabase, ownsTransaction);
      return new DeclareAccountResult.Declared(declaredAccount);
    } catch (SqliteNativeException exception) {
      rollbackIfOwned(activeDatabase, ownsTransaction);
      throw sqliteFailure("Failed to declare SQLite book account.", exception);
    }
  }

  @Override
  public AccountPage listAccounts(ListAccountsQuery query) {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return new AccountPage(List.of(), query.limit(), query.offset(), false);
    }
    try {
      SqliteNativeDatabase activeDatabase = database();
      if (bookState(activeDatabase) == BookState.BLANK_SQLITE) {
        return new AccountPage(List.of(), query.limit(), query.offset(), false);
      }
      requireInitializedBook(activeDatabase);
      return loadAccountPage(activeDatabase, query);
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public Optional<PostingFact> findExistingPosting(IdempotencyKey idempotencyKey) {
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
  public Optional<PostingFact> findPosting(PostingId postingId) {
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
  public PostingPage listPostings(ListPostingsQuery query) {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return new PostingPage(List.of(), query.limit(), query.offset(), false);
    }
    try {
      SqliteNativeDatabase activeDatabase = database();
      if (bookState(activeDatabase) == BookState.BLANK_SQLITE) {
        return new PostingPage(List.of(), query.limit(), query.offset(), false);
      }
      requireInitializedBook(activeDatabase);
      return loadPostingPage(activeDatabase, query);
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public AccountBalanceSnapshot accountBalance(AccountBalanceQuery query) {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      throw new IllegalStateException(NOT_INITIALIZED_BOOK_MESSAGE);
    }
    try {
      SqliteNativeDatabase activeDatabase = database();
      requireInitializedBook(activeDatabase);
      DeclaredAccount account =
          findOneAccount(activeDatabase, query.accountCode())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Account is not declared: " + query.accountCode().value()));
      return new AccountBalanceSnapshot(
          account,
          query.effectiveDateFrom(),
          query.effectiveDateTo(),
          loadCurrencyBalances(activeDatabase, query, account));
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
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
      insertPostingFact(activeDatabase, postingFact);
      insertJournalLines(activeDatabase, postingFact);
      commitIfOwned(activeDatabase, ownsTransaction);
      return new PostingCommitResult.Committed(postingFact);
    } catch (SqliteNativeException exception) {
      rollbackIfOwned(activeDatabase, ownsTransaction);
      throw sqliteFailure("Failed to commit SQLite posting fact.", exception);
    }
  }

  @Override
  public void beginLedgerPlanTransaction() {
    ensureOpen();
    accessMode.requireWritableMutation();
    if (ledgerPlanTransactionActive) {
      throw new IllegalStateException("Ledger plan transaction is already active.");
    }
    SqliteSchemaManager.ensureParentDirectory(bookPath);
    SqliteNativeDatabase activeDatabase = database();
    try {
      activeDatabase.executeStatement("begin immediate");
      ledgerPlanTransactionActive = true;
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to begin SQLite ledger plan transaction.", exception);
    }
  }

  @Override
  public void commitLedgerPlanTransaction() {
    ensureOpen();
    if (!ledgerPlanTransactionActive) {
      throw new IllegalStateException("No ledger plan transaction is active.");
    }
    SqliteNativeDatabase activeDatabase = database();
    try {
      activeDatabase.executeStatement("commit");
      ledgerPlanTransactionActive = false;
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to commit SQLite ledger plan transaction.", exception);
    }
  }

  @Override
  public void rollbackLedgerPlanTransaction() {
    if (!ledgerPlanTransactionActive) {
      return;
    }
    SqliteNativeDatabase activeDatabase = database;
    ledgerPlanTransactionActive = false;
    if (activeDatabase != null) {
      rollbackQuietly(activeDatabase);
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
      SqliteNativeDatabase activeDatabase = database();
      if (!isInitializedBook(activeDatabase)) {
        return new RekeyBookResult.Rejected(new BookAdministrationRejection.BookNotInitialized());
      }
      SqliteNativeLibrary.rekey(activeDatabase, activeReplacementPassphrase);
      closeOwnedDatabase(activeDatabase);
      databaseHandleClosedForRekey = true;
      SqliteNativeDatabase reopenedDatabase =
          configureOpenedDatabase(
              SqliteNativeLibrary.open(
                  bookPath, activeReplacementPassphrase, accessMode.nativeOpenMode()),
              accessMode);
      database = reopenedDatabase;
      databaseHandleClosedForRekey = false;
      requireInitializedBook(reopenedDatabase);
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

  private Optional<PostingRejection> rejectionBeforeInsert(
      SqliteNativeDatabase activeDatabase, PostingRequest postingRequest) {
    return PostingValidation.rejectionFor(
        postingRequest, new TransactionValidationBook(activeDatabase));
  }

  private PostingPage loadPostingPage(SqliteNativeDatabase activeDatabase, ListPostingsQuery query)
      throws SqliteNativeException {
    List<PostingFact> postings = new ArrayList<>();
    boolean filterAccount = query.accountCode().isPresent();
    boolean filterEffectiveDateFrom = query.effectiveDateFrom().isPresent();
    boolean filterEffectiveDateTo = query.effectiveDateTo().isPresent();
    String sql =
        SqlitePostingSql.listPostings(
            filterAccount, filterEffectiveDateFrom, filterEffectiveDateTo);
    try (SqliteNativeStatement statement = activeDatabase.prepare(sql)) {
      bindPostingPageQuery(
          statement, query, filterAccount, filterEffectiveDateFrom, filterEffectiveDateTo);
      while (statement.step() == SqliteNativeLibrary.SQLITE_ROW) {
        postings.add(loadPostingRow(activeDatabase, statement));
      }
    }
    boolean hasMore = postings.size() > query.limit();
    List<PostingFact> pageItems = hasMore ? postings.subList(0, query.limit()) : postings;
    return new PostingPage(pageItems, query.limit(), query.offset(), hasMore);
  }

  @SuppressWarnings("PMD.UseConcurrentHashMap")
  private List<CurrencyBalance> loadCurrencyBalances(
      SqliteNativeDatabase activeDatabase, AccountBalanceQuery query, DeclaredAccount account)
      throws SqliteNativeException {
    boolean filterEffectiveDateFrom = query.effectiveDateFrom().isPresent();
    boolean filterEffectiveDateTo = query.effectiveDateTo().isPresent();
    String sql =
        SqlitePostingSql.loadAccountLinesForBalance(filterEffectiveDateFrom, filterEffectiveDateTo);
    // This accumulator never escapes the query call, so a plain local map is the correct tool.
    Map<CurrencyCode, Totals> totalsByCurrency = new HashMap<>();
    try (SqliteNativeStatement statement = activeDatabase.prepare(sql)) {
      bindAccountBalanceQuery(statement, query, filterEffectiveDateFrom, filterEffectiveDateTo);
      while (statement.step() == SqliteNativeLibrary.SQLITE_ROW) {
        JournalLine.EntrySide side = readEntrySide(statement);
        CurrencyCode currencyCode = readCurrencyCode(statement);
        BigDecimal amount = readAmount(statement);
        Totals totals =
            totalsByCurrency.computeIfAbsent(currencyCode, SqlitePostingFactStore::newTotals);
        if (side == JournalLine.EntrySide.DEBIT) {
          totals.debit = totals.debit.add(amount);
          continue;
        }
        totals.credit = totals.credit.add(amount);
      }
    }
    List<CurrencyBalance> balances = new ArrayList<>();
    List<Map.Entry<CurrencyCode, Totals>> orderedTotals =
        totalsByCurrency.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().value()))
            .toList();
    for (Map.Entry<CurrencyCode, Totals> entry : orderedTotals) {
      balances.add(balance(entry.getKey(), entry.getValue(), account.normalBalance()));
    }
    return List.copyOf(balances);
  }

  private static void bindPostingPageQuery(
      SqliteNativeStatement statement,
      ListPostingsQuery query,
      boolean filterAccount,
      boolean filterEffectiveDateFrom,
      boolean filterEffectiveDateTo)
      throws SqliteNativeException {
    int bindIndex = 1;
    if (filterAccount) {
      statement.bindText(bindIndex, query.accountCode().orElseThrow().value());
      bindIndex++;
    }
    if (filterEffectiveDateFrom) {
      statement.bindText(bindIndex, query.effectiveDateFrom().orElseThrow().toString());
      bindIndex++;
    }
    if (filterEffectiveDateTo) {
      statement.bindText(bindIndex, query.effectiveDateTo().orElseThrow().toString());
      bindIndex++;
    }
    statement.bindInt(bindIndex, query.limit() + 1);
    bindIndex++;
    statement.bindInt(bindIndex, query.offset());
  }

  private PostingFact loadPostingRow(
      SqliteNativeDatabase activeDatabase, SqliteNativeStatement statement)
      throws SqliteNativeException {
    PostingId postingId =
        new PostingId(SqlitePostingMapper.requiredText(statement, SqlitePostingSql.COL_POSTING_ID));
    return SqlitePostingMapper.postingFact(statement, loadLines(activeDatabase, postingId));
  }

  private static void bindAccountBalanceQuery(
      SqliteNativeStatement statement,
      AccountBalanceQuery query,
      boolean filterEffectiveDateFrom,
      boolean filterEffectiveDateTo)
      throws SqliteNativeException {
    int bindIndex = 1;
    statement.bindText(bindIndex, query.accountCode().value());
    bindIndex++;
    if (filterEffectiveDateFrom) {
      statement.bindText(bindIndex, query.effectiveDateFrom().orElseThrow().toString());
      bindIndex++;
    }
    if (filterEffectiveDateTo) {
      statement.bindText(bindIndex, query.effectiveDateTo().orElseThrow().toString());
    }
  }

  private static JournalLine.EntrySide readEntrySide(SqliteNativeStatement statement) {
    return JournalLine.EntrySide.valueOf(SqlitePostingMapper.requiredText(statement, 0));
  }

  private static CurrencyCode readCurrencyCode(SqliteNativeStatement statement) {
    return new CurrencyCode(SqlitePostingMapper.requiredText(statement, 1));
  }

  private static BigDecimal readAmount(SqliteNativeStatement statement) {
    return new BigDecimal(SqlitePostingMapper.requiredText(statement, 2));
  }

  private static Totals newTotals(CurrencyCode ignoredCurrencyCode) {
    return new Totals();
  }

  private static CurrencyBalance balance(
      CurrencyCode currencyCode, Totals totals, NormalBalance accountNormalBalance) {
    BigDecimal net = totals.debit.subtract(totals.credit);
    BigDecimal absoluteNet = net.abs();
    NormalBalance balanceSide = net.signum() >= 0 ? NormalBalance.DEBIT : NormalBalance.CREDIT;
    if (absoluteNet.signum() == 0) {
      balanceSide = accountNormalBalance;
    }
    return new CurrencyBalance(
        new Money(currencyCode, totals.debit),
        new Money(currencyCode, totals.credit),
        new Money(currencyCode, absoluteNet),
        balanceSide);
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
    SqliteNativeStatement statement = activeDatabase.prepare(sql);
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
    SqliteNativeStatement statement = activeDatabase.prepare(SqlitePostingSql.FIND_ACCOUNT_BY_CODE);
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

  private AccountPage loadAccountPage(SqliteNativeDatabase activeDatabase, ListAccountsQuery query)
      throws SqliteNativeException {
    List<DeclaredAccount> accounts = new ArrayList<>();
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.listAccounts())) {
      statement.bindInt(1, query.limit() + 1);
      statement.bindInt(2, query.offset());
      while (statement.step() == SqliteNativeLibrary.SQLITE_ROW) {
        accounts.add(SqlitePostingMapper.declaredAccount(statement));
      }
    }
    boolean hasMore = accounts.size() > query.limit();
    List<DeclaredAccount> pageItems = hasMore ? accounts.subList(0, query.limit()) : accounts;
    return new AccountPage(pageItems, query.limit(), query.offset(), hasMore);
  }

  private boolean existsRow(SqliteNativeDatabase activeDatabase, String sql, SqliteBinder binder)
      throws SqliteNativeException {
    try (SqliteNativeStatement statement = activeDatabase.prepare(sql)) {
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

  @SuppressWarnings("PMD.UseTryWithResources")
  private Optional<Instant> loadInitializedAt(SqliteNativeDatabase activeDatabase)
      throws SqliteNativeException {
    SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.FIND_BOOK_INITIALIZED_AT);
    try {
      statement.bindText(1, SqlitePostingSql.INITIALIZED_AT_META_KEY);
      if (statement.step() == SqliteNativeLibrary.SQLITE_DONE) {
        return Optional.empty();
      }
      return Optional.of(Instant.parse(SqlitePostingMapper.requiredText(statement, 0)));
    } finally {
      statement.close();
    }
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
    SqliteNativeStatement statement = activeDatabase.prepare(sql);
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

  @SuppressWarnings("PMD.UseTryWithResources")
  private static String querySingleText(SqliteNativeDatabase activeDatabase, String sql)
      throws SqliteNativeException {
    SqliteNativeStatement statement = activeDatabase.prepare(sql);
    try {
      if (statement.step() != SqliteNativeLibrary.SQLITE_ROW) {
        throw new IllegalStateException("SQLite text query returned no rows: " + sql);
      }
      String value =
          Objects.requireNonNull(
              statement.columnText(0), "SQLite text query returned NULL: " + sql);
      if (statement.step() != SqliteNativeLibrary.SQLITE_DONE) {
        throw new IllegalStateException("SQLite text query returned more than one row: " + sql);
      }
      return value;
    } finally {
      statement.close();
    }
  }

  private List<JournalLine> loadLines(SqliteNativeDatabase activeDatabase, PostingId postingId)
      throws SqliteNativeException {
    try (SqliteNativeStatement statement = activeDatabase.prepare(SqlitePostingSql.LOAD_LINES)) {
      statement.bindText(1, postingId.value());
      return SqlitePostingMapper.journalLines(statement);
    }
  }

  private void insertInitializedAt(SqliteNativeDatabase activeDatabase, Instant initializedAt)
      throws SqliteNativeException {
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.INSERT_BOOK_INITIALIZED_AT)) {
      statement.bindText(1, SqlitePostingSql.INITIALIZED_AT_META_KEY);
      statement.bindText(2, initializedAt.toString());
      statement.step();
    }
  }

  private void upsertAccount(SqliteNativeDatabase activeDatabase, DeclaredAccount account)
      throws SqliteNativeException {
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.UPSERT_ACCOUNT)) {
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
        activeDatabase.prepare(SqlitePostingSql.INSERT_POSTING_FACT)) {
      statement.bindText(1, postingFact.postingId().value());
      statement.bindText(2, postingFact.journalEntry().effectiveDate().toString());
      statement.bindText(3, postingFact.provenance().recordedAt().toString());
      statement.bindText(4, requestProvenance.actorId().value());
      statement.bindText(5, requestProvenance.actorType().name());
      statement.bindText(6, requestProvenance.commandId().value());
      statement.bindText(7, requestProvenance.idempotencyKey().value());
      statement.bindText(8, requestProvenance.causationId().value());
      bindOptionalText(
          statement, 9, requestProvenance.correlationId().map(value -> value.value()).orElse(null));
      bindOptionalText(
          statement, 10, requestProvenance.reason().map(value -> value.value()).orElse(null));
      statement.bindText(11, postingFact.provenance().sourceChannel().name());
      bindOptionalText(
          statement,
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
          activeDatabase.prepare(SqlitePostingSql.INSERT_JOURNAL_LINE)) {
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

  private static void bindOptionalText(
      SqliteNativeStatement statement, int parameterIndex, @Nullable String value)
      throws SqliteNativeException {
    statement.bindText(parameterIndex, value);
  }

  private SqliteNativeDatabase database() {
    SqliteNativeDatabase activeDatabase = database;
    if (activeDatabase != null) {
      return activeDatabase;
    }
    try (SqliteBookPassphrase passphrase = takeBookPassphrase()) {
      SqliteNativeDatabase openedDatabase =
          SqliteNativeLibrary.open(bookPath, passphrase, accessMode.nativeOpenMode());
      SqliteNativeDatabase configuredDatabase = configureOpenedDatabase(openedDatabase, accessMode);
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

  private boolean beginImmediateIfNeeded(SqliteNativeDatabase activeDatabase)
      throws SqliteNativeException {
    if (ledgerPlanTransactionActive) {
      return false;
    }
    activeDatabase.executeStatement("begin immediate");
    return true;
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

  private static IllegalStateException sqliteFailure(
      String message, SqliteNativeException exception) {
    String detail = Objects.requireNonNullElse(exception.getMessage(), "SQLite native failure.");
    return new IllegalStateException(
        message + " " + exception.resultName() + ": " + detail, exception);
  }

  /** Running debit and credit totals for one account/currency balance bucket. */
  private static final class Totals {
    private BigDecimal debit = BigDecimal.ZERO;
    private BigDecimal credit = BigDecimal.ZERO;
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
      try {
        return findOneAccount(activeDatabase, accountCode);
      } catch (SqliteNativeException exception) {
        throw sqliteFailure("Failed to query SQLite book.", exception);
      }
    }

    @Override
    public Optional<PostingFact> findExistingPosting(IdempotencyKey idempotencyKey) {
      return findOnePosting(
          activeDatabase,
          SqlitePostingSql.FIND_POSTING_BY_IDEMPOTENCY,
          statement -> statement.bindText(1, idempotencyKey.value()));
    }

    @Override
    public Optional<PostingFact> findPosting(PostingId postingId) {
      return findOnePosting(
          activeDatabase,
          SqlitePostingSql.FIND_POSTING_BY_ID,
          statement -> statement.bindText(1, postingId.value()));
    }

    @Override
    public Optional<PostingFact> findReversalFor(PostingId priorPostingId) {
      return findOnePosting(
          activeDatabase,
          SqlitePostingSql.FIND_REVERSAL_FOR,
          statement -> statement.bindText(1, priorPostingId.value()));
    }
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
          %spragma synchronous = extra;
          pragma trusted_schema = off;
          pragma secure_delete = on;
          pragma temp_store = memory;
          pragma memory_security = fill;
          pragma query_only = %d;
          """
              .formatted(
                  accessMode.writesJournalMode() ? "pragma journal_mode = delete;\n" : "",
                  accessMode.queryOnlyPragmaValue()));
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
    if (!REQUIRED_JOURNAL_MODE.equalsIgnoreCase(
        querySingleText(openedDatabase, "pragma journal_mode"))) {
      throw new IllegalStateException("SQLite connection failed to enforce journal_mode=DELETE.");
    }
    if (querySingleInt(openedDatabase, "pragma synchronous") != REQUIRED_SYNCHRONOUS_MODE) {
      throw new IllegalStateException("SQLite connection failed to enforce synchronous=EXTRA.");
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

    boolean writesJournalMode() {
      return writable;
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
