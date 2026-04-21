package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.contract.RekeyBookResult;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceReport;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.BookAdministrationSession;
import dev.erst.fingrind.executor.BookReadSession;
import dev.erst.fingrind.executor.LedgerPlanSession;
import dev.erst.fingrind.executor.PostingBookSession;
import dev.erst.fingrind.executor.PostingCommitResult;
import dev.erst.fingrind.executor.PostingDraft;
import dev.erst.fingrind.executor.PostingIdGenerator;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * SQLite-backed book session that keeps one in-process database handle per opened book.
 *
 * <p>This session is thread-confined. One CLI command owns one instance and uses it on one thread.
 */
public final class SqlitePostingFactStore implements LedgerPlanSession, AutoCloseable {
  private final Path bookPath;
  private final SqliteStoreAccessMode accessMode;
  private final SqlitePostingReadSupport postingReadSupport;
  private final SqliteReportReadSupport reportReadSupport;
  private final SqliteStoreReadOperations readOperations;
  private final SqliteStoreMutationOperations mutationOperations;
  private final BookAdministrationSession administrationView;
  private final PostingBookSession postingView;
  private final BookReadSession readView;
  private final SqliteStoreLifecycle lifecycle;

  /** Opens one SQLite-backed book boundary without mutating storage eagerly. */
  public SqlitePostingFactStore(Path bookPath, SqliteBookPassphrase bookPassphrase) {
    this(bookPath, bookPassphrase, SqliteStoreAccessMode.READ_WRITE_CREATE);
  }

  /** Opens one SQLite-backed book boundary with the selected storage access mode. */
  public SqlitePostingFactStore(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteStoreAccessMode accessMode) {
    this.bookPath = Objects.requireNonNull(bookPath, "bookPath").toAbsolutePath().normalize();
    Objects.requireNonNull(bookPassphrase, "bookPassphrase");
    this.accessMode = Objects.requireNonNull(accessMode, "accessMode");
    this.postingReadSupport = new SqlitePostingReadSupport();
    this.reportReadSupport = new SqliteReportReadSupport(postingReadSupport);
    this.readOperations = new SqliteStoreReadOperations(this);
    this.mutationOperations = new SqliteStoreMutationOperations(this);
    this.administrationView = new SqliteBookAdministrationSessionView(this);
    this.postingView = new SqlitePostingBookSessionView(this);
    this.readView = new SqliteBookReadSessionView(this);
    this.lifecycle =
        new SqliteStoreLifecycle(
            this.bookPath,
            bookPassphrase,
            this.accessMode,
            SqliteBookContract.BOOK_STATE_READER,
            SqliteBookContract.FORMAT_VERSION,
            SqliteBookContract.NOT_INITIALIZED_BOOK_MESSAGE);
  }

  SqlitePostingFactStore(BookAccess bookAccess) {
    this(bookAccess, SqliteStoreAccessMode.READ_WRITE_CREATE);
  }

  SqlitePostingFactStore(BookAccess bookAccess, SqliteStoreAccessMode accessMode) {
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
  public BookReadSession readSession() {
    return readView;
  }

  /** Inspects the selected SQLite book without requiring prior initialization. */
  public BookInspection inspectBook() {
    return readOperations.inspectBook();
  }

  /** Reports whether the selected SQLite book is initialized for posting and query operations. */
  public boolean isInitialized() {
    return readOperations.isInitialized();
  }

  /** Initializes one writable SQLite book or reports why initialization was refused. */
  public OpenBookResult openBook(Instant initializedAt) {
    return mutationOperations.openBook(initializedAt);
  }

  /** Finds one declared account by code when the selected book is initialized. */
  public Optional<DeclaredAccount> findAccount(AccountCode accountCode) {
    return readOperations.findAccount(accountCode);
  }

  /** Finds the supplied declared accounts by code when the selected book is initialized. */
  public Map<AccountCode, DeclaredAccount> findAccounts(Set<AccountCode> accountCodes) {
    return readOperations.findAccounts(accountCodes);
  }

  /** Declares or reactivates one account inside the selected writable SQLite book. */
  public DeclareAccountResult declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      NormalBalance normalBalance,
      Instant declaredAt) {
    return mutationOperations.declareAccount(accountCode, accountName, normalBalance, declaredAt);
  }

  /** Lists declared accounts using the requested page window. */
  public AccountPage listAccounts(ListAccountsQuery query) {
    return readOperations.listAccounts(query);
  }

  /** Finds one committed posting by idempotency key when it exists in the selected book. */
  public Optional<PostingFact> findExistingPosting(IdempotencyKey idempotencyKey) {
    return readOperations.findExistingPosting(idempotencyKey);
  }

  /** Finds one committed posting by posting identifier when it exists in the selected book. */
  public Optional<PostingFact> findPosting(PostingId postingId) {
    return readOperations.findPosting(postingId);
  }

  /** Finds the committed reversal for one prior posting when it exists in the selected book. */
  public Optional<PostingFact> findReversalFor(PostingId priorPostingId) {
    return readOperations.findReversalFor(priorPostingId);
  }

  /** Lists committed postings using the requested page window and optional filters. */
  public PostingPage listPostings(ListPostingsQuery query) {
    return readOperations.listPostings(query);
  }

  /** Computes the account balance snapshot for one declared account query. */
  public Optional<AccountBalanceSnapshot> accountBalance(AccountBalanceQuery query) {
    return readOperations.accountBalance(query);
  }

  /** Computes one canonical trial-balance report for the selected initialized book. */
  public TrialBalanceReport trialBalance(TrialBalanceQuery query) {
    return readOperations.trialBalance(query);
  }

  /** Computes one canonical account-ledger report for the selected declared account. */
  public AccountLedgerReport accountLedger(AccountLedgerQuery query, DeclaredAccount account) {
    return readOperations.accountLedger(query, account);
  }

  /** Computes one canonical bounded period summary for the selected initialized book. */
  public PeriodSummaryReport periodSummary(PeriodSummaryQuery query) {
    return readOperations.periodSummary(query);
  }

  /** Commits one posting draft atomically inside the selected writable SQLite book. */
  public PostingCommitResult commit(
      PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
    return mutationOperations.commit(postingDraft, postingIdGenerator);
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
    lifecycle.beginLedgerPlanTransaction();
  }

  @Override
  public void commitLedgerPlanTransaction() {
    lifecycle.commitLedgerPlanTransaction();
  }

  @Override
  public void rollbackLedgerPlanTransaction() {
    lifecycle.rollbackLedgerPlanTransaction();
  }

  @Override
  public void close() {
    lifecycle.close();
  }

  /** Rekeys one initialized FinGrind book and verifies the replacement passphrase durably. */
  public RekeyBookResult rekeyBook(SqliteBookPassphrase replacementPassphrase) {
    return mutationOperations.rekeyBook(replacementPassphrase);
  }

  boolean isInitializedBook(SqliteNativeDatabase activeDatabase) throws SqliteNativeException {
    return lifecycle.isInitializedBook(activeDatabase);
  }

  void requireInitializedBook(SqliteNativeDatabase activeDatabase) throws SqliteNativeException {
    lifecycle.requireInitializedBook(activeDatabase);
  }

  SqliteBookStateSnapshot stateSnapshot(SqliteNativeDatabase activeDatabase)
      throws SqliteNativeException {
    return lifecycle.stateSnapshot(activeDatabase);
  }

  SqliteSessionDatabase database() {
    return lifecycle.database();
  }

  SqliteNativeDatabase activeNativeDatabase() {
    return database().nativeDatabase();
  }

  void ensureOpenSession() {
    lifecycle.ensureOpenSession();
  }

  SqliteNativeDatabase initializedQueryDatabase() throws SqliteNativeException {
    return lifecycle.initializedQueryDatabase();
  }

  SqlitePostingReadSupport postingReadSupport() {
    return postingReadSupport;
  }

  SqliteReportReadSupport reportReadSupport() {
    return reportReadSupport;
  }

  SqliteTransactionOwnership beginImmediateIfNeeded(SqliteSessionDatabase activeDatabase)
      throws SqliteNativeException {
    return lifecycle.beginImmediateIfNeeded(activeDatabase);
  }

  static void closeOwnedDatabase(SqliteNativeDatabase database) throws SqliteNativeException {
    database.close();
  }

  static SqliteBookPassphrase passphraseFor(BookAccess bookAccess) {
    return SqliteStoreSupport.passphraseFor(bookAccess);
  }

  Path bookPath() {
    return lifecycle.bookPath();
  }

  SqliteStoreAccessMode accessMode() {
    return lifecycle.accessMode();
  }

  void cacheState(SqliteBookStateSnapshot snapshot) {
    lifecycle.cacheState(snapshot);
  }

  void clearDatabaseState() {
    lifecycle.clearDatabaseState();
  }

  void publishDatabase(SqliteNativeDatabase activeDatabase) {
    lifecycle.publishDatabase(activeDatabase);
  }

  SqliteStoreLifecycle lifecycle() {
    return lifecycle;
  }
}
