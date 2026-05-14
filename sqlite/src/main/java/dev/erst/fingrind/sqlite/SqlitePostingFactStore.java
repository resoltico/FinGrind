package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseDraft;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
    this(bookPath, bookPassphrase, accessMode, sqliteApiSupplier, SqliteCommitFaultHook.NONE);
  }

  SqlitePostingFactStore(
      Path bookPath,
      SqliteBookPassphrase bookPassphrase,
      SqliteStoreAccessMode accessMode,
      Supplier<SqliteNativeApi> sqliteApiSupplier,
      SqliteCommitFaultHook commitFaultHook) {
    this.context =
        new SqliteStoreContext(
            Objects.requireNonNull(bookPath, "bookPath"),
            Objects.requireNonNull(accessMode, "accessMode"),
            Objects.requireNonNull(sqliteApiSupplier, "sqliteApiSupplier"));
    this.sessionSecret =
        new SqliteSessionSecret(Objects.requireNonNull(bookPassphrase, "bookPassphrase"));
    this.lifecycle = new SqliteStoreLifecycle(this.context, sessionSecret);
    this.readOperations = new SqliteStoreReadOperations(context, lifecycle);
    this.mutationOperations =
        new SqliteStoreMutationOperations(
            context, lifecycle, Objects.requireNonNull(commitFaultHook, "commitFaultHook"));
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
  public BookLifecycleInspection inspectBook() {
    threadOwner.requireOwnerThread();
    return readOperations.inspectBook();
  }

  @Override
  public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
    threadOwner.requireOwnerThread();
    return readOperations.findAccount(accountCode);
  }

  @Override
  public Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
    threadOwner.requireOwnerThread();
    return readOperations.findAccounts(accountCodes);
  }

  @Override
  public List<RegisteredAccount> allAccounts() {
    threadOwner.requireOwnerThread();
    return readOperations.allAccounts();
  }

  @Override
  public AccountRegistryPage listAccounts(AccountRegistryQuery query) {
    threadOwner.requireOwnerThread();
    return readOperations.listAccounts(query);
  }

  @Override
  public Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
    threadOwner.requireOwnerThread();
    return readOperations.findExistingPosting(idempotencyKey);
  }

  @Override
  public Optional<CommittedPosting> findPosting(PostingId postingId) {
    threadOwner.requireOwnerThread();
    return readOperations.findPosting(postingId);
  }

  @Override
  public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
    threadOwner.requireOwnerThread();
    return readOperations.findReversalFor(priorPostingId);
  }

  @Override
  public PostingHistoryPage listPostings(PostingHistoryQuery query) {
    threadOwner.requireOwnerThread();
    return readOperations.listPostings(query);
  }

  @Override
  public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
    threadOwner.requireOwnerThread();
    return readOperations.postings(effectiveDateRange);
  }

  @Override
  public Optional<LocalDate> earliestPostingEffectiveDate() {
    threadOwner.requireOwnerThread();
    return readOperations.earliestPostingEffectiveDate();
  }

  @Override
  public Optional<LocalDate> closedThroughEffectiveDate() {
    threadOwner.requireOwnerThread();
    return readOperations.closedThroughEffectiveDate();
  }

  @Override
  public Optional<AccountBalanceView> accountBalance(AccountBalanceCriteria query) {
    threadOwner.requireOwnerThread();
    return readOperations.accountBalance(query);
  }

  @Override
  public List<AccountCurrencyTotals> accountTotals(
      EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
    threadOwner.requireOwnerThread();
    return readOperations.accountTotals(effectiveDateRange, postingCoverage);
  }

  @Override
  public TrialBalanceView trialBalance(TrialBalanceCriteria query) {
    threadOwner.requireOwnerThread();
    return readOperations.trialBalance(query);
  }

  @Override
  public AccountLedgerView accountLedger(AccountLedgerCriteria query, RegisteredAccount account) {
    threadOwner.requireOwnerThread();
    return readOperations.accountLedger(query, account);
  }

  @Override
  public PeriodSummaryView periodSummary(PeriodSummaryCriteria query) {
    threadOwner.requireOwnerThread();
    return readOperations.periodSummary(query);
  }

  @Override
  public BookOpeningOutcome openBook(Instant initializedAt, BookIdentity bookIdentity) {
    threadOwner.requireOwnerThread();
    return mutationOperations.openBook(initializedAt, bookIdentity);
  }

  @Override
  public AccountDeclarationOutcome declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountRole accountRole,
      Instant declaredAt) {
    threadOwner.requireOwnerThread();
    return mutationOperations.declareAccount(
        accountCode, accountName, accountType, accountRole, declaredAt);
  }

  @Override
  public PostingCommitResult commit(
      PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
    threadOwner.requireOwnerThread();
    return mutationOperations.commit(postingDraft, postingIdGenerator);
  }

  @Override
  public PeriodCloseOutcome closePeriod(
      PeriodCloseDraft periodCloseDraft, PostingIdGenerator postingIdGenerator) {
    threadOwner.requireOwnerThread();
    return mutationOperations.closePeriod(periodCloseDraft, postingIdGenerator);
  }

  RekeyBookResult rekeyBook(SqliteBookPassphrase replacementPassphrase, Instant rekeyedAt) {
    threadOwner.requireOwnerThread();
    return mutationOperations.rekeyBook(replacementPassphrase, rekeyedAt);
  }

  @Override
  public ContractDecision<RekeyBookResult> rekeyBook(
      BookAccess.PassphraseSource replacementPassphraseSource,
      SqlitePassphraseResolver passphraseResolver,
      Instant rekeyedAt) {
    threadOwner.requireOwnerThread();
    Objects.requireNonNull(replacementPassphraseSource, "replacementPassphraseSource");
    Objects.requireNonNull(passphraseResolver, "passphraseResolver");
    Objects.requireNonNull(rekeyedAt, "rekeyedAt");
    return passphraseResolver
        .resolve(bookPath(), replacementPassphraseSource, SqlitePassphraseIntent.NEW_SECRET)
        .fold(
            replacementPassphrase ->
                ContractDecision.accepted(rekeyBook(replacementPassphrase, rekeyedAt)),
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
}
