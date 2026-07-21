package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.TEST_AUTHORIZER;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.openBookCommand;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.workflow.LedgerAssertion;
import dev.erst.fingrind.contract.workflow.LedgerFact;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanId;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import dev.erst.fingrind.contract.workflow.LedgerStepId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearClosePlanner;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepDraft;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepOutcome;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlanner;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.spi.AccountCatalogStore;
import dev.erst.fingrind.executor.spi.BookAdministrationStore;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import dev.erst.fingrind.executor.spi.LedgerPlanTransaction;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingCommitStore;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import dev.erst.fingrind.executor.spi.ReportingPeriodCloseStore;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import dev.erst.fingrind.executor.spi.TaxAdministrationStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** Shared fixtures and seam doubles for split {@link LedgerPlanService} tests. */
final class LedgerPlanServiceTestSupport {
  static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-17T10:15:30Z"), ZoneOffset.UTC);

  private LedgerPlanServiceTestSupport() {}

  static void assertAssertionFailure(InMemoryBookSession bookSession, LedgerAssertion assertion) {
    LedgerPlanResult result =
        service(bookSession)
            .execute(
                new LedgerPlan(
                    planId("plan-assert"),
                    List.of(new LedgerStep.Assert(stepId("assert"), assertion))), ExecutorAccountingTestSupport.TEST_AUTHORIZER);

    assertEquals(LedgerPlanStatus.ASSERTION_FAILED, result.status());
  }

  static InMemoryBookSession initializedBook() {
    InMemoryBookSession bookSession = new InMemoryBookSession();
    bookSession.openBook(FIXED_CLOCK.instant(), bookIdentity(), List.of());
    return bookSession;
  }

  static InMemoryBookSession bookWithCommittedPosting() {
    InMemoryBookSession bookSession = initializedBook();
    bookSession.declareAccount(
        new AccountCode("1000"),
        new AccountName("Cash"),
        AccountType.ASSET,
        accountTaxonomy(AccountType.ASSET, NormalBalance.DEBIT),
        FIXED_CLOCK.instant());
    bookSession.declareAccount(
        new AccountCode("2000"),
        new AccountName("Revenue"),
        AccountType.REVENUE,
        accountTaxonomy(AccountType.REVENUE, NormalBalance.CREDIT),
        FIXED_CLOCK.instant());
    PostEntryResult committed =
        new PostingApplicationService(
                bookSession,
                bookSession,
                () -> new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
                FIXED_CLOCK)
            .commit(postEntryCommand("idem-setup"), TEST_AUTHORIZER);
    assertEquals(PostEntryResult.Committed.class, committed.getClass());
    return bookSession;
  }

  static <
          T extends
              LedgerPlanTransaction & dev.erst.fingrind.executor.spi.AccountCatalogStore
                  & BookAdministrationStore & BookkeepingReadStore & PostingValidationStore
                  & PostingCommitStore & TaxAdministrationStore>
      LedgerPlanService service(T bookSession) {
    return new LedgerPlanService(
        bookSession,
        bookSession,
        bookSession,
        bookSession,
        bookSession,
        bookSession,
        bookSession,
        () -> new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
        FIXED_CLOCK);
  }

  static LedgerPlanId planId(String value) {
    return new LedgerPlanId(value);
  }

  static LedgerStepId stepId(String value) {
    return new LedgerStepId(value);
  }

  static LedgerStep.EnsureBook openBookStep(String value) {
    return new LedgerStep.EnsureBook(stepId(value), openBookCommand());
  }

  static boolean textFact(LedgerFact fact, String name, String value) {
    return fact instanceof LedgerFact.Text text
        && name.equals(text.name())
        && value.equals(text.value());
  }

  static boolean moneyFact(LedgerFact fact, String name, MonetaryAmount value) {
    return fact instanceof LedgerFact.Money money
        && name.equals(money.name())
        && value.equals(money.value());
  }

  static boolean countFact(LedgerFact fact, String name, int value) {
    return fact instanceof LedgerFact.Count count
        && name.equals(count.name())
        && value == count.value();
  }

  static boolean flagFact(LedgerFact fact, String name, boolean value) {
    return fact instanceof LedgerFact.Flag flag
        && name.equals(flag.name())
        && value == flag.value();
  }

  static boolean groupFact(
      LedgerFact fact,
      String groupName,
      String firstName,
      String firstValue,
      String secondName,
      String secondValue) {
    return fact instanceof LedgerFact.Group group
        && groupName.equals(group.name())
        && group.facts().stream().anyMatch(child -> textFact(child, firstName, firstValue))
        && group.facts().stream().anyMatch(child -> textFact(child, secondName, secondValue));
  }

  static boolean groupFact(
      LedgerFact fact,
      String groupName,
      String firstName,
      String firstValue,
      String secondName,
      MonetaryAmount secondValue) {
    return fact instanceof LedgerFact.Group group
        && groupName.equals(group.name())
        && group.facts().stream().anyMatch(child -> textFact(child, firstName, firstValue))
        && group.facts().stream().anyMatch(child -> moneyFact(child, secondName, secondValue));
  }

  static DeclareAccountCommand account(
      String accountCode,
      String accountName,
      AccountType accountType,
      NormalBalance normalBalance) {
    return new DeclareAccountCommand(
        new AccountCode(accountCode),
        new AccountName(accountName),
        accountType,
        accountTaxonomy(accountType, normalBalance));
  }

  static PostEntryCommand postEntryCommand(String idempotencyKey) {
    return new PostEntryCommand(
        new BookkeepingEntry.SaleSettled(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1000"),
            new AccountCode("2000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null,
            null,
            null,
            null,
            null),
        accountingEvidence(idempotencyKey),
        new RequestProvenance(
            new CommandId("20aea0ba-3b2e-3428-af5b-f9ee3094522c"),
            new IdempotencyKey(idempotencyKey),
            new CausationId("cause-1"),
            Optional.of(new CorrelationId("corr-1"))),
        SourceChannel.CLI);
  }

  static MonetaryAmount monetaryAmount(String currencyCode, String amountText) {
    return MonetaryAmount.of(Money.parse(currencyCode, amountText));
  }

  /** Composite workflow session shape used only by ledger-plan executor tests. */
  interface LedgerPlanSession
      extends LedgerPlanTransaction,
          BookAdministrationStore,
          BookkeepingReadStore,
          PostingValidationStore,
          PostingCommitStore,
          ReportingPeriodCloseStore,
          AccountCatalogStore,
          TaxAdministrationStore,
          AutoCloseable {}

  /**
   * Shared delegating workflow session so failure fixtures only override the behavior under test.
   */
  abstract static class DelegatingAtomicBookStore implements LedgerPlanSession {
    protected final InMemoryBookSession delegate = new InMemoryBookSession();

    @Override
    public BookLifecycleInspection inspectBook() {
      return delegate.inspectBook();
    }

    public BookOpeningOutcome openBook(
        Instant initializedAt,
        dev.erst.fingrind.core.BookIdentity bookIdentity,
        List<AccountDeclaration> seededAccounts) {
      return delegate.openBook(initializedAt, bookIdentity, seededAccounts);
    }

    @Override
    public BookOpeningOutcome openAttestedBook(
        Instant initializedAt,
        dev.erst.fingrind.core.BookIdentity bookIdentity,
        List<AccountDeclaration> seededAccounts,
        dev.erst.fingrind.core.attestation.AttestationEvidence genesisEvidence) {
      return delegate.openAttestedBook(
          initializedAt, bookIdentity, seededAccounts, genesisEvidence);
    }

    @Override
    public AccountDeclarationOutcome declareAccount(
        AccountDeclaration declaration,
        Instant declaredAt,
        dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer attestationAuthorizer) {
      return delegate.declareAccount(declaration, declaredAt, attestationAuthorizer);
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.AccountAmendmentOutcome amendAccount(
        AccountDeclaration amendment,
        Instant amendedAt,
        dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer attestationAuthorizer) {
      return delegate.amendAccount(amendment, amendedAt, attestationAuthorizer);
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.AccountRetirementOutcome retireAccount(
        AccountCode accountCode,
        Instant retiredAt,
        dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer attestationAuthorizer) {
      return delegate.retireAccount(accountCode, retiredAt, attestationAuthorizer);
    }

    @Override
    public dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult declareTaxRegistration(
        dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand command,
        Instant declaredAt,
        dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer attestationAuthorizer) {
      return delegate.declareTaxRegistration(command, declaredAt, attestationAuthorizer);
    }

    @Override
    public java.util.Optional<dev.erst.fingrind.executor.bookkeeping.RegisteredAccount> findAccount(
        AccountCode accountCode) {
      return delegate.findAccount(accountCode);
    }

    @Override
    public java.util.Optional<dev.erst.fingrind.contract.tax.DeclaredTaxRegistration>
        findTaxRegistration(dev.erst.fingrind.contract.tax.TaxRegistrationId taxRegistrationId) {
      return delegate.findTaxRegistration(taxRegistrationId);
    }

    @Override
    public java.util.Optional<StoredRequestPosting> findExistingPosting(
        dev.erst.fingrind.core.IdempotencyKey idempotencyKey) {
      return delegate.findExistingPosting(idempotencyKey);
    }

    @Override
    public java.util.Optional<dev.erst.fingrind.executor.bookkeeping.CommittedPosting> findPosting(
        PostingId postingId) {
      return delegate.findPosting(postingId);
    }

    @Override
    public java.util.Optional<dev.erst.fingrind.executor.bookkeeping.CommittedPosting>
        findReversalFor(PostingId priorPostingId) {
      return delegate.findReversalFor(priorPostingId);
    }

    @Override
    public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
      return delegate.postings(effectiveDateRange);
    }

    @Override
    public List<AccountCurrencyTotals> accountTotals(
        EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
      return delegate.accountTotals(effectiveDateRange, postingCoverage);
    }

    @Override
    public Optional<LocalDate> latestPostingEffectiveDate() {
      return delegate.latestPostingEffectiveDate();
    }

    @Override
    public List<dev.erst.fingrind.executor.bookkeeping.RegisteredAccount> allAccounts() {
      return delegate.allAccounts();
    }

    @Override
    public List<dev.erst.fingrind.executor.bookkeeping.InventoryValuationMovementRecord>
        inventoryValuationMovements(Optional<LocalDate> effectiveDateAsOf) {
      return delegate.inventoryValuationMovements(effectiveDateAsOf);
    }

    @Override
    public Optional<LocalDate> earliestPostingEffectiveDate() {
      return delegate.earliestPostingEffectiveDate();
    }

    @Override
    public Optional<LocalDate> transferredThroughEffectiveDate() {
      return delegate.transferredThroughEffectiveDate();
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage listAccounts(
        dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery query) {
      return delegate.listAccounts(query);
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage listPostings(
        dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery query) {
      return delegate.listPostings(query);
    }

    @Override
    public java.util.Optional<dev.erst.fingrind.executor.bookkeeping.AccountBalanceView>
        accountBalance(dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria query) {
      return delegate.accountBalance(query);
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.TrialBalanceView trialBalance(
        dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria query) {
      return delegate.trialBalance(query);
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.AccountLedgerView accountLedger(
        dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria query,
        dev.erst.fingrind.executor.bookkeeping.RegisteredAccount account) {
      return delegate.accountLedger(query, account);
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView periodSummary(
        dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria query) {
      return delegate.periodSummary(query);
    }

    @Override
    public PostingCommitResult commit(
        PostingDraft postingDraft,
        PostingIdGenerator postingIdGenerator,
        dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer attestationAuthorizer) {
      return delegate.commit(postingDraft, postingIdGenerator, attestationAuthorizer);
    }

    @Override
    public InterimResultSweepOutcome interimResultSweep(
        dev.erst.fingrind.core.ReportingPeriod reportingPeriod,
        dev.erst.fingrind.core.BookIdentity bookIdentity,
        InterimResultSweepPlanner planner,
        LocalDate currentUtcDate,
        Instant sweptAt,
        PostingIdGenerator postingIdGenerator,
        dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer attestationAuthorizer) {
      return delegate.interimResultSweep(
          reportingPeriod,
          bookIdentity,
          planner,
          currentUtcDate,
          sweptAt,
          postingIdGenerator,
          attestationAuthorizer);
    }

    @Override
    public InterimResultSweepOutcome interimResultSweep(
        LocalDate throughEffectiveDate,
        LocalDate bookStartDate,
        dev.erst.fingrind.core.BookIdentity bookIdentity,
        InterimResultSweepPlanner planner,
        LocalDate currentUtcDate,
        Instant sweptAt,
        PostingIdGenerator postingIdGenerator,
        dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer attestationAuthorizer) {
      return delegate.interimResultSweep(
          throughEffectiveDate,
          bookStartDate,
          bookIdentity,
          planner,
          currentUtcDate,
          sweptAt,
          postingIdGenerator,
          attestationAuthorizer);
    }

    @Override
    public FiscalYearCloseOutcome fiscalYearClose(
        dev.erst.fingrind.core.ReportingPeriod reportingPeriod,
        dev.erst.fingrind.core.BookIdentity bookIdentity,
        FiscalYearClosePlanner planner,
        LocalDate currentUtcDate,
        Instant closedAt,
        PostingIdGenerator postingIdGenerator,
        dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer attestationAuthorizer) {
      return delegate.fiscalYearClose(
          reportingPeriod,
          bookIdentity,
          planner,
          currentUtcDate,
          closedAt,
          postingIdGenerator,
          attestationAuthorizer);
    }

    InterimResultSweepOutcome interimResultSweep(
        InterimResultSweepDraft interimResultSweepDraft, PostingIdGenerator postingIdGenerator) {
      return delegate.interimResultSweep(
          interimResultSweepDraft, postingIdGenerator, TEST_AUTHORIZER);
    }

    @Override
    public void beginLedgerPlanTransaction() {
      delegate.beginLedgerPlanTransaction();
    }

    @Override
    public void commitLedgerPlanTransaction() {
      delegate.commitLedgerPlanTransaction();
    }

    @Override
    public void rollbackLedgerPlanTransaction() {
      delegate.rollbackLedgerPlanTransaction();
    }

    @Override
    public void close() {
      delegate.close();
    }
  }

  /** In-memory session that throws during open-book to exercise rollback-on-runtime-failure. */
  static final class ThrowingLedgerPlanSession extends DelegatingAtomicBookStore {
    private boolean rollbackCalled;

    @Override
    public BookOpeningOutcome openBook(
        Instant initializedAt,
        dev.erst.fingrind.core.BookIdentity bookIdentity,
        List<AccountDeclaration> seededAccounts) {
      throw new IllegalStateException("boom");
    }

    @Override
    public void rollbackLedgerPlanTransaction() {
      rollbackCalled = true;
      delegate.rollbackLedgerPlanTransaction();
    }

    boolean rollbackCalled() {
      return rollbackCalled;
    }
  }

  /** Test-only seam split that throws during declare-account after a successful open-book step. */
  static final class DeclareRuntimeFailingLedgerPlanSession extends DelegatingAtomicBookStore {
    private boolean rollbackCalled;

    @Override
    public AccountDeclarationOutcome declareAccount(
        AccountDeclaration declaration,
        Instant declaredAt,
        dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer attestationAuthorizer) {
      throw new IllegalStateException("declare boom");
    }

    @Override
    public void rollbackLedgerPlanTransaction() {
      rollbackCalled = true;
      delegate.rollbackLedgerPlanTransaction();
    }

    boolean rollbackCalled() {
      return rollbackCalled;
    }
  }

  /** Test-only seam split that throws before any ledger-plan transaction begins. */
  static final class BeginFailingLedgerPlanSession extends DelegatingAtomicBookStore {
    @Override
    public void beginLedgerPlanTransaction() {
      throw new IllegalStateException("begin boom");
    }
  }

  /** Test-only seam split that throws while checking initialization before the first step runs. */
  static final class InitializationCheckFailingLedgerPlanSession extends DelegatingAtomicBookStore {
    private boolean rollbackCalled;

    @Override
    public BookLifecycleInspection inspectBook() {
      throw new IllegalStateException("initialization boom");
    }

    @Override
    public void rollbackLedgerPlanTransaction() {
      rollbackCalled = true;
      delegate.rollbackLedgerPlanTransaction();
    }

    boolean rollbackCalled() {
      return rollbackCalled;
    }
  }

  /** Test-only seam split that keeps queries uninitialized after a successful open-book step. */
  static final class ListAccountsRejectingLedgerPlanSession extends DelegatingAtomicBookStore {
    @Override
    public BookLifecycleInspection inspectBook() {
      return new BookLifecycleInspection.Missing(1);
    }
  }

  /** Test-only seam split that throws during commit so the outer finally rollback path runs. */
  static final class CommitFailingLedgerPlanSession extends DelegatingAtomicBookStore {
    private boolean rollbackCalled;

    @Override
    public void commitLedgerPlanTransaction() {
      throw new IllegalStateException("commit boom");
    }

    @Override
    public void rollbackLedgerPlanTransaction() {
      rollbackCalled = true;
      delegate.rollbackLedgerPlanTransaction();
    }

    boolean rollbackCalled() {
      return rollbackCalled;
    }
  }

  /** Test-only seam split that throws during rollback after a deterministic plan failure. */
  static final class RollbackFailingLedgerPlanSession extends DelegatingAtomicBookStore {
    @Override
    public void rollbackLedgerPlanTransaction() {
      throw new IllegalStateException("rollback boom");
    }
  }

  /**
   * Test-only seam split that throws during step execution and then throws again during rollback.
   */
  static final class RuntimeRollbackFailingLedgerPlanSession extends DelegatingAtomicBookStore {

    @Override
    public BookOpeningOutcome openBook(
        Instant initializedAt,
        dev.erst.fingrind.core.BookIdentity bookIdentity,
        List<AccountDeclaration> seededAccounts) {
      throw new IllegalStateException("boom");
    }

    @Override
    public void rollbackLedgerPlanTransaction() {
      throw new IllegalStateException("rollback boom");
    }
  }
}
