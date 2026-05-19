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
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
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
    PostEntryCommand command = basicValidCommand();
    return new Committed(
        new PostingId(postingId),
        command.requestProvenance().idempotencyKey(),
        command.journalEntry().effectiveDate(),
        CliFuzzFixtures.fixedClock().instant());
  }

  static CommitRejected commitRejected(PostingRejection rejection) {
    return new CommitRejected(new IdempotencyKey("idem-1"), rejection);
  }

  static PostingFact matchingPostingFact(PostEntryCommand command, PostingId postingId) {
    return new PostingFact(
        postingId,
        command.journalEntry(),
        command.postingLineage(),
        PostingKind.STANDARD,
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
    return """
        {
          "postingKind": "STANDARD",
          "effectiveDate": "2026-04-07",
          "lines": [
            {
              "accountCode": "1000",
              "side": "DEBIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1000"
              }
            },
            {
              "accountCode": "2000",
              "side": "CREDIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1000"
              }
            }
          ],
          "provenance": {
            "actorId": "actor-1",
            "actorType": "AGENT",
            "commandId": "command-1",
            "idempotencyKey": "idem-1",
            "causationId": "cause-1",
            "correlationId": "corr-1"
          }
        }
        """;
  }

  static DeclaredAccount declaredAccount(AccountCode accountCode, boolean active) {
    return new DeclaredAccount(
        accountCode,
        new AccountName("Synthetic " + accountCode.value()),
        AccountType.ASSET,
        AccountRole.ORDINARY,
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
          7, 1, 1, CliFuzzFixtures.fixedClock().instant(), CliFuzzFixtures.bookIdentity());
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
    public Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
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
    public java.util.List<RegisteredAccount> allAccounts() {
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
        account.accountRole(),
        account.accountTaxonomy(),
        account.active(),
        account.declaredAt());
  }

  private static AccountTaxonomy accountTaxonomy(AccountType accountType) {
    return switch (accountType) {
      case ASSET ->
          new AccountTaxonomy(
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
              Optional.empty());
      case LIABILITY ->
          new AccountTaxonomy(
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_LIABILITY),
              Optional.empty());
      case EQUITY ->
          new AccountTaxonomy(
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
              Optional.empty());
      case REVENUE ->
          new AccountTaxonomy(
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE));
      case EXPENSE ->
          new AccountTaxonomy(
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE));
    };
  }
}
