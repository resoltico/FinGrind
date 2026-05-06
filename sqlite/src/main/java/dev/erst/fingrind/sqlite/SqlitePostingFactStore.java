package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.ContractFailureException;
import dev.erst.fingrind.contract.RekeyBookResult;
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
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
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
class SqlitePostingFactStore implements SqliteBookSession {
  private final SqliteThreadOwner threadOwner = new SqliteThreadOwner("SQLite book session");
  private final SqliteStoreContext context;
  final SqliteSessionSecret sessionSecret;
  final SqliteStoreLifecycle lifecycle;
  private final SqliteStoreReadOperations readOperations;
  private final SqliteStoreMutationOperations mutationOperations;
  private final BookAdministrationSession administrationView;
  private final PostingBookSession postingView;
  private final BookReadSession readView;

  /** Opens one SQLite-backed book boundary without mutating storage eagerly. */
  SqlitePostingFactStore(Path bookPath, SqliteBookPassphrase bookPassphrase) {
    this(bookPath, bookPassphrase, SqliteStoreAccessMode.READ_WRITE_CREATE);
  }

  /** Opens one SQLite-backed book boundary with the selected storage access mode. */
  SqlitePostingFactStore(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteStoreAccessMode accessMode) {
    this(bookPath, bookPassphrase, accessMode, SqliteNativeBootstrap::api);
  }

  SqlitePostingFactStore(BookAccess bookAccess) {
    this(bookAccess, SqliteStoreAccessMode.READ_WRITE_CREATE);
  }

  SqlitePostingFactStore(BookAccess bookAccess, SqliteStoreAccessMode accessMode) {
    this(bookAccess, accessMode, SqliteNativeBootstrap::api);
  }

  SqlitePostingFactStore(
      BookAccess bookAccess,
      SqliteStoreAccessMode accessMode,
      Supplier<SqliteNativeApi> sqliteApiSupplier) {
    this(
        Objects.requireNonNull(bookAccess, "bookAccess").bookFilePath(),
        SqliteStoreOperations.passphraseFor(bookAccess)
            .fold(
                resolvedPassphrase -> resolvedPassphrase,
                failure -> {
                  throw new ContractFailureException(failure);
                }),
        accessMode,
        sqliteApiSupplier);
  }

  SqlitePostingFactStore(
      Path bookPath,
      SqliteBookPassphrase bookPassphrase,
      SqliteStoreAccessMode accessMode,
      Supplier<SqliteNativeApi> sqliteApiSupplier) {
    this.context =
        new SqliteStoreContext(
            Objects.requireNonNull(bookPath, "bookPath"),
            Objects.requireNonNull(accessMode, "accessMode"),
            Objects.requireNonNull(sqliteApiSupplier, "sqliteApiSupplier"));
    this.sessionSecret =
        new SqliteSessionSecret(Objects.requireNonNull(bookPassphrase, "bookPassphrase"));
    this.lifecycle = new SqliteStoreLifecycle(this.context, sessionSecret);
    this.readOperations = new SqliteStoreReadOperations(context, lifecycle);
    this.mutationOperations = new SqliteStoreMutationOperations(context, lifecycle);
    this.administrationView = new SqliteBookAdministrationSessionView(mutationOperations);
    this.postingView = new SqlitePostingBookSessionView(readOperations, mutationOperations);
    this.readView = new SqliteBookReadSessionView(readOperations);
  }

  /** Opens and primes one SQLite-backed book session for explicit CLI/workflow result handling. */
  static ContractDecision<SqlitePostingFactStore> openResolved(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteStoreAccessMode accessMode) {
    return SqliteStoreOpening.openResolved(bookPath, bookPassphrase, accessMode);
  }

  ContractDecision<SqlitePostingFactStore> prime() {
    threadOwner.requireOwnerThread();
    return lifecycle
        .prime()
        .fold(ignored -> ContractDecision.accepted(this), ContractDecision::rejected);
  }

  @Override
  public BookAdministrationSession administrationSession() {
    threadOwner.requireOwnerThread();
    return administrationView;
  }

  @Override
  public PostingBookSession postingSession() {
    threadOwner.requireOwnerThread();
    return postingView;
  }

  @Override
  public BookReadSession readSession() {
    threadOwner.requireOwnerThread();
    return readView;
  }

  BookInspection inspectBook() {
    threadOwner.requireOwnerThread();
    return readOperations.inspectBook();
  }

  boolean isInitialized() {
    threadOwner.requireOwnerThread();
    return readOperations.isInitialized();
  }

  @Override
  public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
    threadOwner.requireOwnerThread();
    return readOperations.findAccount(accountCode);
  }

  Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
    threadOwner.requireOwnerThread();
    return readOperations.findAccounts(accountCodes);
  }

  AccountRegistryPage listAccounts(AccountRegistryQuery query) {
    threadOwner.requireOwnerThread();
    return readOperations.listAccounts(query);
  }

  @Override
  public Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
    threadOwner.requireOwnerThread();
    return readOperations.findExistingPosting(idempotencyKey);
  }

  Optional<CommittedPosting> findPosting(PostingId postingId) {
    threadOwner.requireOwnerThread();
    return readOperations.findPosting(postingId);
  }

  Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
    threadOwner.requireOwnerThread();
    return readOperations.findReversalFor(priorPostingId);
  }

  PostingHistoryPage listPostings(PostingHistoryQuery query) {
    threadOwner.requireOwnerThread();
    return readOperations.listPostings(query);
  }

  Optional<AccountBalanceView> accountBalance(AccountBalanceCriteria query) {
    threadOwner.requireOwnerThread();
    return readOperations.accountBalance(query);
  }

  TrialBalanceView trialBalance(TrialBalanceCriteria query) {
    threadOwner.requireOwnerThread();
    return readOperations.trialBalance(query);
  }

  AccountLedgerView accountLedger(AccountLedgerCriteria query, RegisteredAccount account) {
    threadOwner.requireOwnerThread();
    return readOperations.accountLedger(query, account);
  }

  PeriodSummaryView periodSummary(PeriodSummaryCriteria query) {
    threadOwner.requireOwnerThread();
    return readOperations.periodSummary(query);
  }

  BookOpeningOutcome openBook(Instant initializedAt) {
    threadOwner.requireOwnerThread();
    return mutationOperations.openBook(initializedAt);
  }

  AccountDeclarationOutcome declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      NormalBalance normalBalance,
      Instant declaredAt) {
    threadOwner.requireOwnerThread();
    return mutationOperations.declareAccount(accountCode, accountName, normalBalance, declaredAt);
  }

  PostingCommitResult commit(PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
    threadOwner.requireOwnerThread();
    return mutationOperations.commit(postingDraft, postingIdGenerator);
  }

  RekeyBookResult rekeyBook(SqliteBookPassphrase replacementPassphrase) {
    threadOwner.requireOwnerThread();
    return mutationOperations.rekeyBook(replacementPassphrase);
  }

  @Override
  public ContractDecision<RekeyBookResult> rekeyBook(
      BookAccess.PassphraseSource replacementPassphraseSource,
      SqlitePassphraseResolver passphraseResolver) {
    threadOwner.requireOwnerThread();
    Objects.requireNonNull(replacementPassphraseSource, "replacementPassphraseSource");
    Objects.requireNonNull(passphraseResolver, "passphraseResolver");
    return passphraseResolver
        .resolve(bookPath(), replacementPassphraseSource, SqlitePassphraseIntent.NEW_SECRET)
        .fold(
            replacementPassphrase -> ContractDecision.accepted(rekeyBook(replacementPassphrase)),
            ContractDecision::rejected);
  }

  @Override
  public void beginLedgerPlanTransaction() {
    threadOwner.requireOwnerThread();
    lifecycle.beginLedgerPlanTransaction();
  }

  @Override
  public void commitLedgerPlanTransaction() {
    threadOwner.requireOwnerThread();
    lifecycle.commitLedgerPlanTransaction();
  }

  @Override
  public void rollbackLedgerPlanTransaction() {
    threadOwner.requireOwnerThread();
    lifecycle.rollbackLedgerPlanTransaction();
  }

  @Override
  public void close() {
    threadOwner.requireOwnerThread();
    lifecycle.close();
  }

  void requireInitializedBook(SqliteNativeDatabase activeDatabase) {
    threadOwner.requireOwnerThread();
    lifecycle.requireInitializedBook(activeDatabase);
  }

  Path bookPath() {
    threadOwner.requireOwnerThread();
    return context.bookPath();
  }

  SqliteStoreAccessMode accessMode() {
    threadOwner.requireOwnerThread();
    return context.accessMode();
  }

  SqlitePostingReader postingReader() {
    threadOwner.requireOwnerThread();
    return context.postingReader();
  }

  SqliteNativeDatabase activeNativeDatabase() {
    threadOwner.requireOwnerThread();
    return lifecycle.database();
  }

  static ContractDecision<SqliteBookPassphrase> passphraseFor(BookAccess bookAccess) {
    return passphraseDecisionFor(bookAccess);
  }

  static ContractDecision<SqliteBookPassphrase> passphraseDecisionFor(BookAccess bookAccess) {
    return SqliteStoreOperations.passphraseFor(bookAccess);
  }

  /** Commits one fully materialized posting fact for fixture-oriented callers. */
  PostingCommitResult commit(CommittedPosting postingFact) {
    threadOwner.requireOwnerThread();
    Objects.requireNonNull(postingFact, "postingFact");
    return commit(
        new PostingDraft(
            postingFact.journalEntry(), postingFact.postingLineage(), postingFact.provenance()),
        postingFact::postingId);
  }
}
