package dev.erst.fingrind.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.Committed;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import dev.erst.fingrind.jazzer.support.JazzerPostEntryResultFixtures;
import dev.erst.fingrind.sqlite.SqliteReadSession;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class SqliteRoundTripWorkflowTestSupport {
  private SqliteRoundTripWorkflowTestSupport() {}

  static Committed committed(String postingId) {
    return committed(postingId, false);
  }

  static Committed committed(String postingId, boolean idempotentReplay) {
    PostEntryCommand command = basicValidCommand();
    return JazzerPostEntryResultFixtures.committed(command, postingId, idempotentReplay);
  }

  static CommitRejected commitRejected(PostingRejection rejection) {
    return new CommitRejected(new IdempotencyKey("idem-1"), rejection);
  }

  static PostingFact matchingPostingFact(PostEntryCommand command, PostingId postingId) {
    return new PostingFact(
        postingId,
        CliFuzzFixtures.journalEntry(command),
        CliFuzzFixtures.postingLineage(command),
        postingKind(command),
        postingOriginKind(command),
        command.evidence(),
        new CommittedProvenance(
            command.requestProvenance(),
            CliFuzzFixtures.fixedClock().instant(),
            command.sourceChannel()));
  }

  static dev.erst.fingrind.contract.runtime.ContractFailure contractFailure(String message) {
    return ContractErrors.Descriptor.INVALID_REQUEST.failure(
        message, "repair the synthetic request", "--request-file");
  }

  static PostEntryCommand basicValidCommand() {
    return CliFuzzFixtures.readPostEntryCommand(basicValidRequest().getBytes(UTF_8));
  }

  static String basicValidRequest() {
    return CliFuzzHarnessTestSupport.cashRevenueRequestJson(
        new CliFuzzHarnessTestSupport.CashRevenueRequestInput(
            "2026-04-07",
            "1000",
            "2000",
            "EUR",
            "1000",
            new CliFuzzHarnessTestSupport.RequestContext(
                "document-idem-1",
                "cash-receipt",
                "2026-04-07",
                "actor-1",
                "AGENT",
                "command-1",
                "idem-1",
                "cause-1",
                "corr-1")));
  }

  static DeclaredAccount declaredAccount(AccountCode accountCode, boolean active) {
    return new DeclaredAccount(
        accountCode,
        new AccountName("Synthetic " + accountCode.value()),
        AccountType.ASSET,
        accountTaxonomy(AccountType.ASSET),
        active,
        CliFuzzFixtures.fixedClock().instant());
  }

  static Future<ConcurrentCommitOutcome> exceptionalFuture(Exception exception) {
    return new Future<>() {
      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
      }

      @Override
      public boolean isCancelled() {
        return false;
      }

      @Override
      public boolean isDone() {
        return true;
      }

      @Override
      public ConcurrentCommitOutcome get() throws ExecutionException {
        throw assertInstanceOf(ExecutionException.class, exception);
      }

      @Override
      public ConcurrentCommitOutcome get(long timeout, TimeUnit unit) throws ExecutionException {
        throw assertInstanceOf(ExecutionException.class, exception);
      }
    };
  }

  static Future<ConcurrentCommitOutcome> timeoutFuture(TimeoutException exception) {
    return new Future<>() {
      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
      }

      @Override
      public boolean isCancelled() {
        return false;
      }

      @Override
      public boolean isDone() {
        return true;
      }

      @Override
      public ConcurrentCommitOutcome get() {
        throw new UnsupportedOperationException("Use timed get only.");
      }

      @Override
      public ConcurrentCommitOutcome get(long timeout, TimeUnit unit) throws TimeoutException {
        throw exception;
      }
    };
  }

  static Future<ConcurrentCommitOutcome> interruptedFuture(InterruptedException exception) {
    return new Future<>() {
      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
      }

      @Override
      public boolean isCancelled() {
        return false;
      }

      @Override
      public boolean isDone() {
        return true;
      }

      @Override
      public ConcurrentCommitOutcome get() {
        throw new UnsupportedOperationException("Use timed get only.");
      }

      @Override
      public ConcurrentCommitOutcome get(long timeout, TimeUnit unit) throws InterruptedException {
        throw exception;
      }
    };
  }

  static void assertMessageContains(Throwable throwable, String expectedFragment) {
    assertTrue(Objects.requireNonNullElse(throwable.getMessage(), "").contains(expectedFragment));
  }

  private static PostingKind postingKind(PostEntryCommand command) {
    return CliFuzzFixtures.bookkeepingCommand(Objects.requireNonNull(command, "command"))
        .postingKind();
  }

  private static PostingOriginKind postingOriginKind(PostEntryCommand command) {
    return CliFuzzFixtures.bookkeepingCommand(Objects.requireNonNull(command, "command"))
        .postingOriginKind();
  }

  static final class StubSqliteReadSession implements SqliteReadSession {
    private final Optional<RegisteredAccount> account;

    StubSqliteReadSession(Optional<DeclaredAccount> account) {
      this.account =
          Objects.requireNonNull(account, "account")
              .map(SqliteRoundTripWorkflowTestSupport::registeredAccount);
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      return new BookLifecycleInspection.Initialized(
          7, 1, 1, CliFuzzFixtures.fixedClock().instant(), CliFuzzWorkflowFixtures.bookIdentity());
    }

    @Override
    public AccountRegistryPage listAccounts(AccountRegistryQuery query) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PostingHistoryPage listPostings(PostingHistoryQuery query) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      return account;
    }

    @Override
    public Optional<StoredRequestPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<CommittedPosting> findPosting(PostingId postingId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<dev.erst.fingrind.contract.tax.DeclaredTaxRegistration> findTaxRegistration(
        dev.erst.fingrind.contract.tax.TaxRegistrationId taxRegistrationId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public java.util.List<RegisteredAccount> allAccounts() {
      throw new UnsupportedOperationException();
    }

    @Override
    public java.util.List<dev.erst.fingrind.contract.tax.DeclaredTaxRegistration>
        allTaxRegistrations() {
      throw new UnsupportedOperationException();
    }

    @Override
    public dev.erst.fingrind.contract.tax.TaxRegistrationPage listTaxRegistrations(
        dev.erst.fingrind.contract.tax.ListTaxRegistrationsQuery query) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<AccountBalanceView> accountBalance(AccountBalanceCriteria query) {
      throw new UnsupportedOperationException();
    }

    @Override
    public java.util.List<AccountCurrencyTotals> accountTotals(
        EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
      throw new UnsupportedOperationException();
    }

    @Override
    public java.util.List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
      throw new UnsupportedOperationException();
    }

    @Override
    public java.util.List<dev.erst.fingrind.executor.bookkeeping.InventoryValuationMovementRecord>
        inventoryValuationMovements(Optional<java.time.LocalDate> effectiveDateAsOf) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<java.time.LocalDate> earliestPostingEffectiveDate() {
      return Optional.empty();
    }

    @Override
    public Optional<java.time.LocalDate> transferredThroughEffectiveDate() {
      return Optional.empty();
    }

    @Override
    public Optional<java.time.LocalDate> latestPostingEffectiveDate() {
      return Optional.empty();
    }

    @Override
    public TrialBalanceView trialBalance(TrialBalanceCriteria query) {
      throw new UnsupportedOperationException();
    }

    @Override
    public AccountLedgerView accountLedger(AccountLedgerCriteria query, RegisteredAccount account) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PeriodSummaryView periodSummary(PeriodSummaryCriteria query) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void close() {}
  }

  private static RegisteredAccount registeredAccount(DeclaredAccount account) {
    return new RegisteredAccount(
        account.accountCode(),
        account.accountName(),
        account.accountType(),
        account.accountTaxonomy(),
        account.active(),
        account.declaredAt());
  }

  private static AccountTaxonomy accountTaxonomy(AccountType accountType) {
    return switch (accountType) {
      case ASSET ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
              Optional.empty(),
              Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT));
      case LIABILITY ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_LIABILITY),
              Optional.empty(),
              Optional.empty());
      case EQUITY ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
              Optional.empty(),
              Optional.empty());
      case REVENUE ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE),
              Optional.empty());
      case EXPENSE ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE),
              Optional.empty());
    };
  }
}
