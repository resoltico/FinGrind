package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.ContractFailureException;
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

/** Internal dependency bundle and operation owner for one SQLite-backed book session. */
class SqliteStoreContext implements SqliteBookSession {
  private final Path bookPath;
  private final SqliteStoreAccessMode accessMode;
  private final SqlitePostingReader postingReader;
  private final SqliteReportReader reportReader;
  private final SqliteStoreReadOperations readOperations;
  private final SqliteStoreMutationOperations mutationOperations;
  private final SqliteStoreLifecycle lifecycle;
  private final Supplier<SqliteNativeApi> sqliteApiSupplier;
  private final BookAdministrationSession administrationView;
  private final PostingBookSession postingView;
  private final BookReadSession readView;

  SqliteStoreContext(Path bookPath, SqliteBookPassphrase bookPassphrase) {
    this(bookPath, bookPassphrase, SqliteStoreAccessMode.READ_WRITE_CREATE);
  }

  SqliteStoreContext(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteStoreAccessMode accessMode) {
    this(bookPath, bookPassphrase, accessMode, SqliteNativeBootstrap::api);
  }

  SqliteStoreContext(
      Path bookPath,
      SqliteBookPassphrase bookPassphrase,
      SqliteStoreAccessMode accessMode,
      Supplier<SqliteNativeApi> sqliteApiSupplier) {
    this.bookPath = Objects.requireNonNull(bookPath, "bookPath").toAbsolutePath().normalize();
    Objects.requireNonNull(bookPassphrase, "bookPassphrase");
    this.accessMode = Objects.requireNonNull(accessMode, "accessMode");
    this.sqliteApiSupplier = Objects.requireNonNull(sqliteApiSupplier, "sqliteApiSupplier");
    this.postingReader = new SqlitePostingReader();
    this.reportReader = new SqliteReportReader(postingReader);
    this.readOperations = new SqliteStoreReadOperations(this);
    this.mutationOperations = new SqliteStoreMutationOperations(this);
    this.lifecycle =
        new SqliteStoreLifecycle(
            this.bookPath,
            bookPassphrase,
            this.accessMode,
            SqliteBookContract.BOOK_STATE_READER,
            SqliteBookContract.FORMAT_VERSION,
            SqliteBookContract.NOT_INITIALIZED_BOOK_MESSAGE,
            this.sqliteApiSupplier);
    this.administrationView = new SqliteBookAdministrationSessionView(this);
    this.postingView = new SqlitePostingBookSessionView(this);
    this.readView = new SqliteBookReadSessionView(this);
  }

  SqliteStoreContext(BookAccess bookAccess, SqliteStoreAccessMode accessMode) {
    this(bookAccess, accessMode, SqliteNativeBootstrap::api);
  }

  SqliteStoreContext(
      BookAccess bookAccess,
      SqliteStoreAccessMode accessMode,
      Supplier<SqliteNativeApi> sqliteApiSupplier) {
    this(
        bookAccess.bookFilePath(),
        SqliteStoreOperations.passphraseFor(bookAccess)
            .fold(
                resolvedPassphrase -> resolvedPassphrase,
                failure -> {
                  throw new ContractFailureException(failure);
                }),
        accessMode,
        sqliteApiSupplier);
  }

  ContractDecision<SqliteStoreContext> prime() {
    return lifecycle
        .prime()
        .fold(ignored -> ContractDecision.accepted(this), ContractDecision::rejected);
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

  BookInspection inspectBook() {
    return readOperations.inspectBook();
  }

  boolean isInitialized() {
    return readOperations.isInitialized();
  }

  @Override
  public Optional<DeclaredAccount> findAccount(AccountCode accountCode) {
    return readOperations.findAccount(accountCode);
  }

  Map<AccountCode, DeclaredAccount> findAccounts(Set<AccountCode> accountCodes) {
    return readOperations.findAccounts(accountCodes);
  }

  AccountPage listAccounts(ListAccountsQuery query) {
    return readOperations.listAccounts(query);
  }

  @Override
  public Optional<PostingFact> findExistingPosting(IdempotencyKey idempotencyKey) {
    return readOperations.findExistingPosting(idempotencyKey);
  }

  Optional<PostingFact> findPosting(PostingId postingId) {
    return readOperations.findPosting(postingId);
  }

  Optional<PostingFact> findReversalFor(PostingId priorPostingId) {
    return readOperations.findReversalFor(priorPostingId);
  }

  PostingPage listPostings(ListPostingsQuery query) {
    return readOperations.listPostings(query);
  }

  Optional<AccountBalanceSnapshot> accountBalance(AccountBalanceQuery query) {
    return readOperations.accountBalance(query);
  }

  TrialBalanceReport trialBalance(TrialBalanceQuery query) {
    return readOperations.trialBalance(query);
  }

  AccountLedgerReport accountLedger(AccountLedgerQuery query, DeclaredAccount account) {
    return readOperations.accountLedger(query, account);
  }

  PeriodSummaryReport periodSummary(PeriodSummaryQuery query) {
    return readOperations.periodSummary(query);
  }

  OpenBookResult openBook(Instant initializedAt) {
    return mutationOperations.openBook(initializedAt);
  }

  DeclareAccountResult declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      NormalBalance normalBalance,
      Instant declaredAt) {
    return mutationOperations.declareAccount(accountCode, accountName, normalBalance, declaredAt);
  }

  PostingCommitResult commit(PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
    return mutationOperations.commit(postingDraft, postingIdGenerator);
  }

  RekeyBookResult rekeyBook(SqliteBookPassphrase replacementPassphrase) {
    return mutationOperations.rekeyBook(replacementPassphrase);
  }

  @Override
  public ContractDecision<RekeyBookResult> rekeyBook(
      BookAccess.PassphraseSource replacementPassphraseSource,
      SqlitePassphraseResolver passphraseResolver) {
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

  boolean isInitializedBook(SqliteNativeDatabase activeDatabase) {
    return lifecycle.isInitializedBook(activeDatabase);
  }

  void requireInitializedBook(SqliteNativeDatabase activeDatabase) {
    lifecycle.requireInitializedBook(activeDatabase);
  }

  SqliteBookStateSnapshot stateSnapshot(SqliteNativeDatabase activeDatabase) {
    return lifecycle.stateSnapshot(activeDatabase);
  }

  SqliteSessionDatabase database() {
    return lifecycle.database();
  }

  void ensureOpenSession() {
    lifecycle.ensureOpenSession();
  }

  SqliteNativeDatabase initializedQueryDatabase() {
    return lifecycle.initializedQueryDatabase();
  }

  SqlitePostingReader postingReader() {
    return postingReader;
  }

  SqliteReportReader reportReader() {
    return reportReader;
  }

  SqliteTransactionOwnership beginImmediateIfNeeded(SqliteSessionDatabase activeDatabase) {
    return lifecycle.beginImmediateIfNeeded(activeDatabase);
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

  SqliteSessionDatabase openConfiguredDatabase(SqliteBookPassphrase bookPassphrase) {
    return new SqliteSessionDatabase(
        SqliteConnectionConfigurer.configureOpenedDatabase(
            SqliteNativeConnections.open(
                bookPath(), bookPassphrase, accessMode().nativeOpenMode(), lifecycle.sqliteApi()),
            accessMode()));
  }

  SqliteStoreLifecycle lifecycle() {
    return lifecycle;
  }

  static void closeOwnedDatabase(SqliteNativeDatabase database) {
    database.close();
  }
}
