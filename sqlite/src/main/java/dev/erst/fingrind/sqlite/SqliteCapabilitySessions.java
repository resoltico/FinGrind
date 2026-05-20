package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Capability-specific SQLite session wrappers over one internal session core. */
final class SqliteCapabilitySessions {
  private SqliteCapabilitySessions() {}

  static SqliteAdministrationSession administration(SqlitePostingFactStore store) {
    return new AdministrationSession(store);
  }

  static SqliteReadSession read(SqlitePostingFactStore store) {
    return new ReadSession(store);
  }

  static SqlitePostingSession posting(SqlitePostingFactStore store) {
    return new PostingSession(store);
  }

  static SqlitePeriodCloseSession periodClose(SqlitePostingFactStore store) {
    return new PeriodCloseSession(store);
  }

  static SqlitePlanExecutionSession planExecution(SqlitePostingFactStore store) {
    return new PlanExecutionSession(store);
  }

  static SqliteRekeySession rekey(SqlitePostingFactStore store) {
    return new RekeySession(store);
  }

  static SqlitePostingFactStore storeOf(AutoCloseable session) {
    Objects.requireNonNull(session, "session");
    if (session instanceof SqlitePostingFactStore store) {
      return store;
    }
    if (session instanceof DelegatingSession delegatingSession) {
      return delegatingSession.store;
    }
    throw new IllegalArgumentException(
        "The supplied session is not one owned SQLite store or capability wrapper.");
  }

  /** Shared base wrapper that binds one narrow session view to one SQLite store owner. */
  private static class DelegatingSession {
    final SqlitePostingFactStore store;

    DelegatingSession(SqlitePostingFactStore store) {
      this.store = Objects.requireNonNull(store, "store");
    }

    /** Closes the underlying SQLite store that backs this capability wrapper. */
    final void closeStore() {
      store.close();
    }
  }

  /** Administration-only wrapper over the shared SQLite store core. */
  private static final class AdministrationSession extends DelegatingSession
      implements SqliteAdministrationSession {
    AdministrationSession(SqlitePostingFactStore store) {
      super(store);
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      return store.inspectBook();
    }

    @Override
    public List<RegisteredAccount> allAccounts() {
      return store.allAccounts();
    }

    @Override
    public AccountRegistryPage listAccounts(AccountRegistryQuery query) {
      return store.listAccounts(query);
    }

    @Override
    public BookOpeningOutcome openBook(Instant initializedAt, BookIdentity bookIdentity) {
      return store.openBook(initializedAt, bookIdentity);
    }

    @Override
    public AccountDeclarationOutcome declareAccount(
        AccountCode accountCode,
        AccountName accountName,
        AccountType accountType,
        AccountRole accountRole,
        AccountTaxonomy accountTaxonomy,
        Instant declaredAt) {
      return store.declareAccount(
          accountCode, accountName, accountType, accountRole, accountTaxonomy, declaredAt);
    }

    @Override
    public void close() {
      closeStore();
    }
  }

  /** Read-only wrapper over the shared SQLite store core. */
  private static final class ReadSession extends DelegatingSession implements SqliteReadSession {
    ReadSession(SqlitePostingFactStore store) {
      super(store);
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      return store.inspectBook();
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      return store.findAccount(accountCode);
    }

    @Override
    public Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
      return store.findAccounts(accountCodes);
    }

    @Override
    public List<RegisteredAccount> allAccounts() {
      return store.allAccounts();
    }

    @Override
    public AccountRegistryPage listAccounts(AccountRegistryQuery query) {
      return store.listAccounts(query);
    }

    @Override
    public Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
      return store.findExistingPosting(idempotencyKey);
    }

    @Override
    public Optional<CommittedPosting> findPosting(PostingId postingId) {
      return store.findPosting(postingId);
    }

    @Override
    public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
      return store.findReversalFor(priorPostingId);
    }

    @Override
    public PostingHistoryPage listPostings(PostingHistoryQuery query) {
      return store.listPostings(query);
    }

    @Override
    public Optional<AccountBalanceView> accountBalance(AccountBalanceCriteria query) {
      return store.accountBalance(query);
    }

    @Override
    public List<AccountCurrencyTotals> accountTotals(
        EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
      return store.accountTotals(effectiveDateRange, postingCoverage);
    }

    @Override
    public TrialBalanceView trialBalance(TrialBalanceCriteria query) {
      return store.trialBalance(query);
    }

    @Override
    public AccountLedgerView accountLedger(AccountLedgerCriteria query, RegisteredAccount account) {
      return store.accountLedger(query, account);
    }

    @Override
    public PeriodSummaryView periodSummary(PeriodSummaryCriteria query) {
      return store.periodSummary(query);
    }

    @Override
    public void close() {
      closeStore();
    }
  }

  /** Posting-capable wrapper over the shared SQLite store core. */
  private static class PostingSession extends DelegatingSession implements SqlitePostingSession {
    PostingSession(SqlitePostingFactStore store) {
      super(store);
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      return store.inspectBook();
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      return store.findAccount(accountCode);
    }

    @Override
    public Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
      return store.findAccounts(accountCodes);
    }

    @Override
    public List<RegisteredAccount> allAccounts() {
      return store.allAccounts();
    }

    @Override
    public AccountRegistryPage listAccounts(AccountRegistryQuery query) {
      return store.listAccounts(query);
    }

    @Override
    public Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
      return store.findExistingPosting(idempotencyKey);
    }

    @Override
    public Optional<CommittedPosting> findPosting(PostingId postingId) {
      return store.findPosting(postingId);
    }

    @Override
    public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
      return store.findReversalFor(priorPostingId);
    }

    @Override
    public PostingHistoryPage listPostings(PostingHistoryQuery query) {
      return store.listPostings(query);
    }

    @Override
    public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
      return store.postings(effectiveDateRange);
    }

    @Override
    public Optional<LocalDate> earliestPostingEffectiveDate() {
      return store.earliestPostingEffectiveDate();
    }

    @Override
    public Optional<LocalDate> closedThroughEffectiveDate() {
      return store.closedThroughEffectiveDate();
    }

    @Override
    public Optional<AccountBalanceView> accountBalance(AccountBalanceCriteria query) {
      return store.accountBalance(query);
    }

    @Override
    public List<AccountCurrencyTotals> accountTotals(
        EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
      return store.accountTotals(effectiveDateRange, postingCoverage);
    }

    @Override
    public TrialBalanceView trialBalance(TrialBalanceCriteria query) {
      return store.trialBalance(query);
    }

    @Override
    public AccountLedgerView accountLedger(AccountLedgerCriteria query, RegisteredAccount account) {
      return store.accountLedger(query, account);
    }

    @Override
    public PeriodSummaryView periodSummary(PeriodSummaryCriteria query) {
      return store.periodSummary(query);
    }

    @Override
    public BookOpeningOutcome openBook(Instant initializedAt, BookIdentity bookIdentity) {
      return store.openBook(initializedAt, bookIdentity);
    }

    @Override
    public AccountDeclarationOutcome declareAccount(
        AccountCode accountCode,
        AccountName accountName,
        AccountType accountType,
        AccountRole accountRole,
        AccountTaxonomy accountTaxonomy,
        Instant declaredAt) {
      return store.declareAccount(
          accountCode, accountName, accountType, accountRole, accountTaxonomy, declaredAt);
    }

    @Override
    public PostingCommitResult commit(
        PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
      return store.commit(postingDraft, postingIdGenerator);
    }

    @Override
    public void close() {
      closeStore();
    }
  }

  /** Period-close wrapper over the shared SQLite store core. */
  private static final class PeriodCloseSession extends DelegatingSession
      implements SqlitePeriodCloseSession {
    PeriodCloseSession(SqlitePostingFactStore store) {
      super(store);
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      return store.inspectBook();
    }

    @Override
    public List<RegisteredAccount> allAccounts() {
      return store.allAccounts();
    }

    @Override
    public AccountRegistryPage listAccounts(AccountRegistryQuery query) {
      return store.listAccounts(query);
    }

    @Override
    public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
      return store.postings(effectiveDateRange);
    }

    @Override
    public Optional<LocalDate> earliestPostingEffectiveDate() {
      return store.earliestPostingEffectiveDate();
    }

    @Override
    public Optional<LocalDate> closedThroughEffectiveDate() {
      return store.closedThroughEffectiveDate();
    }

    @Override
    public PeriodCloseOutcome closePeriod(
        PeriodCloseDraft periodCloseDraft, PostingIdGenerator postingIdGenerator) {
      return store.closePeriod(periodCloseDraft, postingIdGenerator);
    }

    @Override
    public void close() {
      closeStore();
    }
  }

  /** Ledger-plan wrapper that adds plan-transaction control to posting capabilities. */
  private static final class PlanExecutionSession extends PostingSession
      implements SqlitePlanExecutionSession {
    PlanExecutionSession(SqlitePostingFactStore store) {
      super(store);
    }

    @Override
    public void beginLedgerPlanTransaction() {
      store.beginLedgerPlanTransaction();
    }

    @Override
    public void commitLedgerPlanTransaction() {
      store.commitLedgerPlanTransaction();
    }

    @Override
    public void rollbackLedgerPlanTransaction() {
      store.rollbackLedgerPlanTransaction();
    }
  }

  /** Rekey-only wrapper over the shared SQLite store core. */
  private static final class RekeySession extends DelegatingSession implements SqliteRekeySession {
    RekeySession(SqlitePostingFactStore store) {
      super(store);
    }

    @Override
    public ContractDecision<RekeyBookResult> rekeyBook(
        BookAccess.PassphraseSource replacementPassphraseSource,
        SqlitePassphraseResolver passphraseResolver,
        Instant rekeyedAt) {
      return store.rekeyBook(replacementPassphraseSource, passphraseResolver, rekeyedAt);
    }

    @Override
    public void close() {
      closeStore();
    }
  }
}
