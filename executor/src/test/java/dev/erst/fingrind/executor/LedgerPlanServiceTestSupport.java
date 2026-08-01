package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.TEST_AUTHORIZER;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
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
import dev.erst.fingrind.core.attestation.AttestationAdmissionRejectedException;
import dev.erst.fingrind.core.attestation.AttestationAuthorizationFailure;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationStaleHeadException;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PlanAccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.PlanTaxRegistrationMutationOutcome;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.LedgerPlanExecutionStore;
import dev.erst.fingrind.executor.spi.PlanPostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Shared fixtures and seam doubles for split {@link LedgerPlanService} tests. */
final class LedgerPlanServiceTestSupport {
  static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-17T10:15:30Z"), ZoneOffset.UTC);
  private static final String RUNTIME_FAILURE_MESSAGE = "boom";

  private LedgerPlanServiceTestSupport() {}

  static void assertAssertionFailure(InMemoryBookSession bookSession, LedgerAssertion assertion) {
    LedgerPlanResult result =
        service(bookSession)
            .execute(
                new LedgerPlan(
                    planId("plan-assert"),
                    List.of(new LedgerStep.Assert(stepId("assert"), assertion))),
                ExecutorAccountingTestSupport.TEST_AUTHORIZER);

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

  static <T extends LedgerPlanExecutionStore> LedgerPlanService service(T bookSession) {
    return new LedgerPlanService(
        bookSession, () -> new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"), FIXED_CLOCK);
  }

  static <T extends dev.erst.fingrind.executor.spi.LedgerPlanReadOnlyExecutionStore>
      LedgerPlanReadOnlyService readOnlyService(T bookSession) {
    return new LedgerPlanReadOnlyService(bookSession, FIXED_CLOCK);
  }

  static LedgerPlanId planId(String value) {
    return new LedgerPlanId(value);
  }

  static LedgerStepId stepId(String value) {
    return new LedgerStepId(value);
  }

  static LedgerStep.InspectBook inspectBookStep(String value) {
    return new LedgerStep.InspectBook(stepId(value));
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
      extends LedgerPlanExecutionStore,
          dev.erst.fingrind.executor.spi.LedgerPlanReadOnlyExecutionStore,
          AutoCloseable {}

  /**
   * Shared delegating workflow session so failure fixtures only override the behavior under test.
   */
  abstract static class DelegatingAtomicBookStore implements LedgerPlanSession {
    protected final InMemoryBookSession delegate = new InMemoryBookSession();

    protected DelegatingAtomicBookStore() {
      delegate.openBook(FIXED_CLOCK.instant(), bookIdentity(), List.of());
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      return delegate.inspectBook();
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
    public java.util.Map<PostingId, dev.erst.fingrind.contract.bookkeeping.AttestationCommit>
        attestationCommitsFor(java.util.Set<PostingId> postingIds) {
      return delegate.attestationCommitsFor(postingIds);
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
    public void beginLedgerPlanTransaction(
        String planId, AttestationPlanOperationAuthorizer attestationAuthorizer) {
      delegate.beginLedgerPlanTransaction(planId, attestationAuthorizer);
    }

    @Override
    public void beginReadOnlyLedgerPlanTransaction(String planId) {
      delegate.beginReadOnlyLedgerPlanTransaction(planId);
    }

    @Override
    public void enterLedgerPlanStep(int stepOrder) {
      delegate.enterLedgerPlanStep(stepOrder);
    }

    @Override
    public boolean hasCompletedLedgerPlanChildren() {
      return delegate.hasCompletedLedgerPlanChildren();
    }

    @Override
    public PlanAccountDeclarationOutcome declareAccountForPlan(
        AccountDeclaration declaration,
        Instant declaredAt,
        AttestationPlanOperationAuthorizer attestationAuthorizer) {
      return delegate.declareAccountForPlan(declaration, declaredAt, attestationAuthorizer);
    }

    @Override
    public PlanTaxRegistrationMutationOutcome declareTaxRegistrationForPlan(
        dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand command,
        Instant declaredAt,
        AttestationPlanOperationAuthorizer attestationAuthorizer) {
      return delegate.declareTaxRegistrationForPlan(command, declaredAt, attestationAuthorizer);
    }

    @Override
    public PlanPostingCommitResult commitForPlan(
        PostingDraft postingDraft,
        PostingIdGenerator postingIdGenerator,
        AttestationPlanOperationAuthorizer attestationAuthorizer) {
      return delegate.commitForPlan(postingDraft, postingIdGenerator, attestationAuthorizer);
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
    public dev.erst.fingrind.contract.bookkeeping.AttestationCommit appendPlanAttestation(
        Instant recordedAt, AttestationPlanOperationAuthorizer attestationAuthorizer) {
      return delegate.appendPlanAttestation(recordedAt, attestationAuthorizer);
    }

    @Override
    public void close() {
      delegate.close();
    }
  }

  /**
   * In-memory session that throws while listing accounts to exercise rollback-on-runtime-failure.
   */
  static final class ThrowingLedgerPlanSession extends DelegatingAtomicBookStore {
    private boolean rollbackCalled;

    @Override
    public dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage listAccounts(
        dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery query) {
      throw new IllegalStateException(RUNTIME_FAILURE_MESSAGE);
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

  /** Test seam that throws during declare-account after a successful open-book step. */
  static final class DeclareRuntimeFailingLedgerPlanSession extends DelegatingAtomicBookStore {
    private boolean rollbackCalled;

    @Override
    public PlanAccountDeclarationOutcome declareAccountForPlan(
        AccountDeclaration declaration,
        Instant declaredAt,
        AttestationPlanOperationAuthorizer attestationAuthorizer) {
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

  /** Test seam that loses attestation admission while a mutating child executes. */
  static final class DeclareStaleHeadLedgerPlanSession extends DelegatingAtomicBookStore {
    private final AttestationStaleHeadException staleHead =
        new AttestationStaleHeadException(new byte[32], new byte[32], BigInteger.ONE);
    private final boolean failsRollback;
    private boolean rollbackCalled;

    DeclareStaleHeadLedgerPlanSession() {
      this(false);
    }

    DeclareStaleHeadLedgerPlanSession(boolean failsRollback) {
      this.failsRollback = failsRollback;
    }

    @Override
    public PlanAccountDeclarationOutcome declareAccountForPlan(
        AccountDeclaration declaration,
        Instant declaredAt,
        AttestationPlanOperationAuthorizer attestationAuthorizer) {
      throw staleHead;
    }

    @Override
    public void rollbackLedgerPlanTransaction() {
      rollbackCalled = true;
      if (failsRollback) {
        throw new IllegalStateException("rollback boom");
      }
      delegate.rollbackLedgerPlanTransaction();
    }

    AttestationStaleHeadException staleHead() {
      return staleHead;
    }

    boolean rollbackCalled() {
      return rollbackCalled;
    }
  }

  /** Test seam that rejects a child mutation with its exact live-head authorization failure. */
  static final class DeclareAdmissionRejectedLedgerPlanSession extends DelegatingAtomicBookStore {
    private final AttestationAdmissionRejectedException admissionRejected =
        AttestationAdmissionRejectedException.from(AttestationAuthorizationFailure.QUORUM_BELOW);
    private final boolean failsRollback;
    private boolean rollbackCalled;

    DeclareAdmissionRejectedLedgerPlanSession() {
      this(false);
    }

    DeclareAdmissionRejectedLedgerPlanSession(boolean failsRollback) {
      this.failsRollback = failsRollback;
    }

    @Override
    public PlanAccountDeclarationOutcome declareAccountForPlan(
        AccountDeclaration declaration,
        Instant declaredAt,
        AttestationPlanOperationAuthorizer attestationAuthorizer) {
      throw admissionRejected;
    }

    @Override
    public void rollbackLedgerPlanTransaction() {
      rollbackCalled = true;
      if (failsRollback) {
        throw new IllegalStateException("rollback boom");
      }
      delegate.rollbackLedgerPlanTransaction();
    }

    AttestationAdmissionRejectedException admissionRejected() {
      return admissionRejected;
    }

    boolean rollbackCalled() {
      return rollbackCalled;
    }
  }

  /** Test seam that loses attestation admission after all mutating children have succeeded. */
  static final class AggregateStaleHeadLedgerPlanSession extends DelegatingAtomicBookStore {
    private final AttestationStaleHeadException staleHead =
        new AttestationStaleHeadException(new byte[32], new byte[32], BigInteger.ONE);
    private boolean rollbackCalled;

    @Override
    public dev.erst.fingrind.contract.bookkeeping.AttestationCommit appendPlanAttestation(
        Instant recordedAt, AttestationPlanOperationAuthorizer attestationAuthorizer) {
      throw staleHead;
    }

    @Override
    public void rollbackLedgerPlanTransaction() {
      rollbackCalled = true;
      delegate.rollbackLedgerPlanTransaction();
    }

    AttestationStaleHeadException staleHead() {
      return staleHead;
    }

    boolean rollbackCalled() {
      return rollbackCalled;
    }
  }

  /** Test seam that rejects the aggregate plan admission with its exact authorization failure. */
  static final class AggregateAdmissionRejectedLedgerPlanSession extends DelegatingAtomicBookStore {
    private final AttestationAdmissionRejectedException admissionRejected =
        AttestationAdmissionRejectedException.from(
            AttestationAuthorizationFailure.CREDENTIAL_PURPOSE_INVALID);
    private boolean rollbackCalled;

    @Override
    public dev.erst.fingrind.contract.bookkeeping.AttestationCommit appendPlanAttestation(
        Instant recordedAt, AttestationPlanOperationAuthorizer attestationAuthorizer) {
      throw admissionRejected;
    }

    @Override
    public void rollbackLedgerPlanTransaction() {
      rollbackCalled = true;
      delegate.rollbackLedgerPlanTransaction();
    }

    AttestationAdmissionRejectedException admissionRejected() {
      return admissionRejected;
    }

    boolean rollbackCalled() {
      return rollbackCalled;
    }
  }

  /**
   * Test session whose aggregate plan append makes the plan's new posting visible to the
   * authenticated posting-commitment projection.
   */
  static final class AggregateAttestationPublishingLedgerPlanSession
      extends DelegatingAtomicBookStore {
    private static final PostingId PLAN_POSTING_ID =
        new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69");
    private static final dev.erst.fingrind.contract.bookkeeping.AttestationCommit PLAN_COMMIT =
        new dev.erst.fingrind.contract.bookkeeping.AttestationCommit(
            BigInteger.valueOf(42), "b".repeat(64));
    private boolean aggregateAttestationAppended;
    private boolean queriedBeforeAggregateAttestation;

    AggregateAttestationPublishingLedgerPlanSession() {
      delegate.declareAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          AccountType.ASSET,
          accountTaxonomy(AccountType.ASSET, NormalBalance.DEBIT),
          FIXED_CLOCK.instant());
      delegate.declareAccount(
          new AccountCode("2000"),
          new AccountName("Revenue"),
          AccountType.REVENUE,
          accountTaxonomy(AccountType.REVENUE, NormalBalance.CREDIT),
          FIXED_CLOCK.instant());
    }

    @Override
    public dev.erst.fingrind.contract.bookkeeping.AttestationCommit appendPlanAttestation(
        Instant recordedAt, AttestationPlanOperationAuthorizer attestationAuthorizer) {
      delegate.appendPlanAttestation(recordedAt, attestationAuthorizer);
      aggregateAttestationAppended = true;
      return PLAN_COMMIT;
    }

    @Override
    public Map<PostingId, dev.erst.fingrind.contract.bookkeeping.AttestationCommit>
        attestationCommitsFor(Set<PostingId> postingIds) {
      if (!aggregateAttestationAppended) {
        queriedBeforeAggregateAttestation = true;
        return Map.of();
      }
      return postingIds.contains(PLAN_POSTING_ID) ? Map.of(PLAN_POSTING_ID, PLAN_COMMIT) : Map.of();
    }

    @Override
    public void rollbackLedgerPlanTransaction() {
      aggregateAttestationAppended = false;
      delegate.rollbackLedgerPlanTransaction();
    }

    dev.erst.fingrind.contract.bookkeeping.AttestationCommit planCommit() {
      return PLAN_COMMIT;
    }

    boolean queriedBeforeAggregateAttestation() {
      return queriedBeforeAggregateAttestation;
    }
  }

  /**
   * Bound execution-store fixture with one pre-existing posting whose authenticated commitment is
   * available to plan query projection.
   */
  static final class StoredPostingCommitmentLedgerPlanSession extends DelegatingAtomicBookStore {
    private final PostingId postingId;
    private final AttestationCommit attestationCommit;

    StoredPostingCommitmentLedgerPlanSession(
        PostingId postingId, AttestationCommit attestationCommit) {
      this.postingId = postingId;
      this.attestationCommit = attestationCommit;
      delegate.declareAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          AccountType.ASSET,
          accountTaxonomy(AccountType.ASSET, NormalBalance.DEBIT),
          FIXED_CLOCK.instant());
      delegate.declareAccount(
          new AccountCode("2000"),
          new AccountName("Revenue"),
          AccountType.REVENUE,
          accountTaxonomy(AccountType.REVENUE, NormalBalance.CREDIT),
          FIXED_CLOCK.instant());
      PostEntryResult committed =
          new PostingApplicationService(delegate, delegate, () -> this.postingId, FIXED_CLOCK)
              .commit(postEntryCommand("idem-setup"), TEST_AUTHORIZER);
      assertEquals(PostEntryResult.Committed.class, committed.getClass());
    }

    @Override
    public Map<PostingId, AttestationCommit> attestationCommitsFor(Set<PostingId> postingIds) {
      return postingIds.contains(postingId) ? Map.of(postingId, attestationCommit) : Map.of();
    }
  }

  /** Test seam that throws before any ledger-plan transaction begins. */
  static final class BeginFailingLedgerPlanSession extends DelegatingAtomicBookStore {
    @Override
    public void beginLedgerPlanTransaction(
        String planId, AttestationPlanOperationAuthorizer attestationAuthorizer) {
      throw new IllegalStateException("begin boom");
    }
  }

  /** Test seam that throws while checking initialization before the first step runs. */
  static final class InitializationCheckFailingLedgerPlanSession extends DelegatingAtomicBookStore {
    private final RuntimeException initializationFailure;
    private boolean rollbackCalled;
    private int rollbackCalls;

    InitializationCheckFailingLedgerPlanSession() {
      this(new IllegalStateException("initialization boom"));
    }

    InitializationCheckFailingLedgerPlanSession(RuntimeException initializationFailure) {
      this.initializationFailure = java.util.Objects.requireNonNull(initializationFailure);
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      throw initializationFailure;
    }

    @Override
    public void rollbackLedgerPlanTransaction() {
      rollbackCalled = true;
      rollbackCalls++;
      delegate.rollbackLedgerPlanTransaction();
    }

    boolean rollbackCalled() {
      return rollbackCalled;
    }

    int rollbackCalls() {
      return rollbackCalls;
    }
  }

  /** Test seam that keeps queries uninitialized after a successful open-book step. */
  static final class ListAccountsRejectingLedgerPlanSession extends DelegatingAtomicBookStore {
    @Override
    public BookLifecycleInspection inspectBook() {
      return new BookLifecycleInspection.Missing(1);
    }
  }

  /** Simulates the book becoming unavailable between plan admission and a read-only step. */
  static final class InitializationChangingLedgerPlanSession extends DelegatingAtomicBookStore {
    private int initializationChecks;

    @Override
    public boolean allowsInitializedWorkflow() {
      initializationChecks++;
      return initializationChecks == 1;
    }
  }

  /** Test seam that throws during commit so the outer finally rollback path runs. */
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

  /** Test seam that throws during rollback after a deterministic plan failure. */
  static final class RollbackFailingLedgerPlanSession extends DelegatingAtomicBookStore {
    @Override
    public void rollbackLedgerPlanTransaction() {
      throw new IllegalStateException("rollback boom");
    }
  }

  /** Test seam that throws during step execution and then throws again during rollback. */
  static final class RuntimeRollbackFailingLedgerPlanSession extends DelegatingAtomicBookStore {

    @Override
    public dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage listAccounts(
        dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery query) {
      throw new IllegalStateException(RUNTIME_FAILURE_MESSAGE);
    }

    @Override
    public void rollbackLedgerPlanTransaction() {
      throw new IllegalStateException("rollback boom");
    }
  }
}
