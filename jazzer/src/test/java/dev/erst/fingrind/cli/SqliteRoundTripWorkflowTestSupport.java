package dev.erst.fingrind.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.ContractErrors;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.PostEntryResult.Committed;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
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
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import dev.erst.fingrind.sqlite.SqliteBookSession;
import dev.erst.fingrind.sqlite.SqlitePassphraseResolver;
import java.time.Instant;
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
        new CommittedProvenance(
            command.requestProvenance(),
            CliFuzzFixtures.fixedClock().instant(),
            command.sourceChannel()));
  }

  static dev.erst.fingrind.contract.ContractFailure contractFailure(String message) {
    return ContractErrors.Descriptor.INVALID_REQUEST.failure(
        message, "repair the synthetic request", "--request-file");
  }

  static PostEntryCommand basicValidCommand() {
    return CliFuzzFixtures.readPostEntryCommand(basicValidRequest().getBytes(UTF_8));
  }

  static String basicValidRequest() {
    return """
        {
          "effectiveDate": "2026-04-07",
          "lines": [
            {
              "accountCode": "1000",
              "side": "DEBIT",
              "currencyCode": "EUR",
              "amount": "10.00"
            },
            {
              "accountCode": "2000",
              "side": "CREDIT",
              "currencyCode": "EUR",
              "amount": "10.00"
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
        NormalBalance.DEBIT,
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

  static final class StubSqliteBookSession implements SqliteBookSession {
    private final Optional<RegisteredAccount> account;

    StubSqliteBookSession(Optional<DeclaredAccount> account) {
      this.account =
          Objects.requireNonNull(account, "account")
              .map(SqliteRoundTripWorkflowTestSupport::registeredAccount);
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      return new BookLifecycleInspection.Initialized(
          7, 1, 1, CliFuzzFixtures.fixedClock().instant());
    }

    @Override
    public BookOpeningOutcome openBook(Instant initializedAt) {
      throw new UnsupportedOperationException();
    }

    @Override
    public AccountDeclarationOutcome declareAccount(
        AccountCode accountCode,
        AccountName accountName,
        NormalBalance normalBalance,
        Instant declaredAt) {
      throw new UnsupportedOperationException();
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
    public Optional<AccountBalanceView> accountBalance(AccountBalanceCriteria query) {
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
    public PostingCommitResult commit(
        PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
      throw new UnsupportedOperationException();
    }

    @Override
    public dev.erst.fingrind.contract.ContractDecision<dev.erst.fingrind.contract.RekeyBookResult>
        rekeyBook(
            BookAccess.PassphraseSource replacementPassphraseSource,
            SqlitePassphraseResolver passphraseResolver) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void close() {}

    @Override
    public void beginLedgerPlanTransaction() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void commitLedgerPlanTransaction() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void rollbackLedgerPlanTransaction() {
      throw new UnsupportedOperationException();
    }
  }

  private static RegisteredAccount registeredAccount(DeclaredAccount account) {
    return new RegisteredAccount(
        account.accountCode(),
        account.accountName(),
        account.normalBalance(),
        account.active(),
        account.declaredAt());
  }
}
