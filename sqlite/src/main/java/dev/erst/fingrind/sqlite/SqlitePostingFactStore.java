package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.ContractDecision;
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
import java.util.function.Supplier;

/**
 * SQLite-backed book session that keeps one in-process database handle per opened book.
 *
 * <p>This session is thread-confined. One CLI command owns one instance and uses it on one thread.
 */
final class SqlitePostingFactStore implements SqliteBookSession {
  private final SqliteStoreContext context;
  private final BookAdministrationSession administrationView;
  private final PostingBookSession postingView;
  private final BookReadSession readView;

  /** Opens one SQLite-backed book boundary without mutating storage eagerly. */
  SqlitePostingFactStore(Path bookPath, SqliteBookPassphrase bookPassphrase) {
    this(new SqliteStoreContext(bookPath, bookPassphrase));
  }

  /** Opens one SQLite-backed book boundary with the selected storage access mode. */
  SqlitePostingFactStore(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteStoreAccessMode accessMode) {
    this(new SqliteStoreContext(bookPath, bookPassphrase, accessMode));
  }

  SqlitePostingFactStore(BookAccess bookAccess) {
    this(bookAccess, SqliteStoreAccessMode.READ_WRITE_CREATE);
  }

  SqlitePostingFactStore(BookAccess bookAccess, SqliteStoreAccessMode accessMode) {
    this(new SqliteStoreContext(bookAccess, accessMode));
  }

  SqlitePostingFactStore(
      BookAccess bookAccess,
      SqliteStoreAccessMode accessMode,
      Supplier<SqliteNativeApi> sqliteApiSupplier) {
    this(new SqliteStoreContext(bookAccess, accessMode, sqliteApiSupplier));
  }

  private SqlitePostingFactStore(SqliteStoreContext context) {
    this.context = Objects.requireNonNull(context, "context");
    this.administrationView = new SqliteBookAdministrationSessionView(context);
    this.postingView = new SqlitePostingBookSessionView(context);
    this.readView = new SqliteBookReadSessionView(context);
  }

  /** Opens and primes one SQLite-backed book session for explicit CLI/workflow result handling. */
  static ContractDecision<SqlitePostingFactStore> openResolved(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteStoreAccessMode accessMode) {
    return SqliteStoreOpening.openResolved(bookPath, bookPassphrase, accessMode);
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
  BookInspection inspectBook() {
    return context.inspectBook();
  }

  /** Reports whether the selected SQLite book is initialized for posting and query operations. */
  boolean isInitialized() {
    return context.isInitialized();
  }

  /** Initializes one writable SQLite book or reports why initialization was refused. */
  OpenBookResult openBook(Instant initializedAt) {
    return context.openBook(initializedAt);
  }

  /** Finds one declared account by code when the selected book is initialized. */
  @Override
  public Optional<DeclaredAccount> findAccount(AccountCode accountCode) {
    return context.findAccount(accountCode);
  }

  /** Finds the supplied declared accounts by code when the selected book is initialized. */
  Map<AccountCode, DeclaredAccount> findAccounts(Set<AccountCode> accountCodes) {
    return context.findAccounts(accountCodes);
  }

  /** Declares or reactivates one account inside the selected writable SQLite book. */
  DeclareAccountResult declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      NormalBalance normalBalance,
      Instant declaredAt) {
    return context.declareAccount(accountCode, accountName, normalBalance, declaredAt);
  }

  /** Lists declared accounts using the requested page window. */
  AccountPage listAccounts(ListAccountsQuery query) {
    return context.listAccounts(query);
  }

  /** Finds one committed posting by idempotency key when it exists in the selected book. */
  @Override
  public Optional<PostingFact> findExistingPosting(IdempotencyKey idempotencyKey) {
    return context.findExistingPosting(idempotencyKey);
  }

  /** Finds one committed posting by posting identifier when it exists in the selected book. */
  Optional<PostingFact> findPosting(PostingId postingId) {
    return context.findPosting(postingId);
  }

  /** Finds the committed reversal for one prior posting when it exists in the selected book. */
  Optional<PostingFact> findReversalFor(PostingId priorPostingId) {
    return context.findReversalFor(priorPostingId);
  }

  /** Lists committed postings using the requested page window and optional filters. */
  PostingPage listPostings(ListPostingsQuery query) {
    return context.listPostings(query);
  }

  /** Computes the account balance snapshot for one declared account query. */
  Optional<AccountBalanceSnapshot> accountBalance(AccountBalanceQuery query) {
    return context.accountBalance(query);
  }

  /** Computes one canonical trial-balance report for the selected initialized book. */
  TrialBalanceReport trialBalance(TrialBalanceQuery query) {
    return context.trialBalance(query);
  }

  /** Computes one canonical account-ledger report for the selected declared account. */
  AccountLedgerReport accountLedger(AccountLedgerQuery query, DeclaredAccount account) {
    return context.accountLedger(query, account);
  }

  /** Computes one canonical bounded period summary for the selected initialized book. */
  PeriodSummaryReport periodSummary(PeriodSummaryQuery query) {
    return context.periodSummary(query);
  }

  /** Commits one posting draft atomically inside the selected writable SQLite book. */
  PostingCommitResult commit(PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
    return context.commit(postingDraft, postingIdGenerator);
  }

  /** Commits one fully materialized posting fact for fixture-oriented callers. */
  PostingCommitResult commit(PostingFact postingFact) {
    Objects.requireNonNull(postingFact, "postingFact");
    return commit(
        new PostingDraft(
            postingFact.journalEntry(), postingFact.postingLineage(), postingFact.provenance()),
        postingFact::postingId);
  }

  @Override
  public void beginLedgerPlanTransaction() {
    context.beginLedgerPlanTransaction();
  }

  @Override
  public void commitLedgerPlanTransaction() {
    context.commitLedgerPlanTransaction();
  }

  @Override
  public void rollbackLedgerPlanTransaction() {
    context.rollbackLedgerPlanTransaction();
  }

  @Override
  public void close() {
    context.close();
  }

  /** Rekeys one initialized FinGrind book and verifies the replacement passphrase durably. */
  @Override
  public RekeyBookResult rekeyBook(SqliteBookPassphrase replacementPassphrase) {
    return context.rekeyBook(replacementPassphrase);
  }

  static ContractDecision<SqliteBookPassphrase> passphraseFor(BookAccess bookAccess) {
    return passphraseDecisionFor(bookAccess);
  }

  static ContractDecision<SqliteBookPassphrase> passphraseDecisionFor(BookAccess bookAccess) {
    return SqliteStoreOperations.passphraseFor(bookAccess);
  }

  Path bookPath() {
    return context.bookPath();
  }

  /** Returns the active native database handle when one has already been opened. */
  SqliteNativeDatabase activeNativeDatabase() {
    return context.database().nativeDatabase();
  }

  /** Returns the posting reader owned by this SQLite session. */
  SqlitePostingReader postingReader() {
    return context.postingReader();
  }

  /** Requires the supplied native database to represent an initialized FinGrind book. */
  void requireInitializedBook(SqliteNativeDatabase activeDatabase) {
    context.requireInitializedBook(activeDatabase);
  }

  /** Returns the lifecycle owner backing this store for same-package infrastructure access. */
  SqliteStoreLifecycle lifecycle() {
    return context.lifecycle();
  }

  /** Returns the internal context bundle backing this store. */
  SqliteStoreContext context() {
    return context;
  }
}
