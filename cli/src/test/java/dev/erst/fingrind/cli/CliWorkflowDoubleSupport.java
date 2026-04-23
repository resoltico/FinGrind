package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerResult;
import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.CommitEntryResult;
import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.DeclareAccountCommand;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.GetPostingResult;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.LedgerPlanResult;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.ListPostingsResult;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryResult;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PreflightEntryResult;
import dev.erst.fingrind.contract.RekeyBookResult;
import dev.erst.fingrind.contract.TrialBalanceResult;
import dev.erst.fingrind.core.PostingId;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullUnmarked;

/** Shared CLI workflow doubles and routing-oriented helpers for split command tests. */
@NullUnmarked
class CliWorkflowDoubleSupport extends CliFixtureSupport {
  protected static BookAccess bookAccess(Path bookFilePath, Path bookKeyFilePath) {
    return new BookAccess(bookFilePath, new BookAccess.PassphraseSource.KeyFile(bookKeyFilePath));
  }

  /** Recording workflow used to assert CLI routing without opening SQLite. */
  static final class RecordingWorkflow implements CliBookWorkflow {
    private final List<BookAccess> openBookAccesses = new ArrayList<>();
    private final List<BookAccess> rekeyBookAccesses = new ArrayList<>();
    private final List<BookAccess.PassphraseSource> rekeyReplacementPassphraseSources =
        new ArrayList<>();
    private final List<BookAccess> declareAccountAccesses = new ArrayList<>();
    private final List<BookAccess> listAccountAccesses = new ArrayList<>();
    private final List<ListAccountsQuery> listAccountQueries = new ArrayList<>();
    private final List<BookAccess> executePlanAccesses = new ArrayList<>();
    private final List<BookAccess> preflightAccesses = new ArrayList<>();
    private final List<BookAccess> commitAccesses = new ArrayList<>();
    private final OpenBookResult openBookResult;
    private final RekeyBookResult rekeyBookResult;
    private final DeclareAccountResult declareAccountResult;
    private final ListAccountsResult listAccountsResult;
    private final PreflightEntryResult preflightResult;
    private final CommitEntryResult commitResult;
    private LedgerPlanResult executePlanResult;

    RecordingWorkflow(
        OpenBookResult openBookResult,
        RekeyBookResult rekeyBookResult,
        DeclareAccountResult declareAccountResult,
        ListAccountsResult listAccountsResult,
        PreflightEntryResult preflightResult,
        CommitEntryResult commitResult) {
      this.openBookResult = openBookResult;
      this.rekeyBookResult = rekeyBookResult;
      this.declareAccountResult = declareAccountResult;
      this.listAccountsResult = listAccountsResult;
      this.preflightResult = preflightResult;
      this.commitResult = commitResult;
    }

    @Override
    public ContractDecision<OpenBookResult> openBook(BookAccess bookAccess) {
      openBookAccesses.add(bookAccess);
      return accepted(openBookResult);
    }

    @Override
    public ContractDecision<RekeyBookResult> rekeyBook(
        BookAccess bookAccess, BookAccess.PassphraseSource replacementPassphraseSource) {
      rekeyBookAccesses.add(bookAccess);
      rekeyReplacementPassphraseSources.add(replacementPassphraseSource);
      return accepted(rekeyBookResult);
    }

    @Override
    public ContractDecision<DeclareAccountResult> declareAccount(
        BookAccess bookAccess, DeclareAccountCommand command) {
      declareAccountAccesses.add(bookAccess);
      return accepted(declareAccountResult);
    }

    @Override
    public ContractDecision<ListAccountsResult> listAccounts(
        BookAccess bookAccess, ListAccountsQuery query) {
      listAccountAccesses.add(bookAccess);
      listAccountQueries.add(query);
      return accepted(listAccountsResult);
    }

    @Override
    public ContractDecision<BookInspection> inspectBook(BookAccess bookAccess) {
      throw new AssertionError("inspectBook should not be called in this test");
    }

    @Override
    public ContractDecision<GetPostingResult> getPosting(
        BookAccess bookAccess, PostingId postingId) {
      throw new AssertionError("getPosting should not be called in this test");
    }

    @Override
    public ContractDecision<ListPostingsResult> listPostings(
        BookAccess bookAccess, ListPostingsQuery query) {
      throw new AssertionError("listPostings should not be called in this test");
    }

    @Override
    public ContractDecision<AccountBalanceResult> accountBalance(
        BookAccess bookAccess, AccountBalanceQuery query) {
      throw new AssertionError("accountBalance should not be called in this test");
    }

    @Override
    public ContractDecision<TrialBalanceResult> trialBalance(
        BookAccess bookAccess, dev.erst.fingrind.contract.TrialBalanceQuery query) {
      throw new AssertionError("trialBalance should not be called in this test");
    }

    @Override
    public ContractDecision<AccountLedgerResult> accountLedger(
        BookAccess bookAccess, AccountLedgerQuery query) {
      throw new AssertionError("accountLedger should not be called in this test");
    }

    @Override
    public ContractDecision<PeriodSummaryResult> periodSummary(
        BookAccess bookAccess, PeriodSummaryQuery query) {
      throw new AssertionError("periodSummary should not be called in this test");
    }

    @Override
    public ContractDecision<LedgerPlanResult> executePlan(BookAccess bookAccess, LedgerPlan plan) {
      executePlanAccesses.add(bookAccess);
      return accepted(
          executePlanResult == null ? successfulPlanResult(plan.planId()) : executePlanResult);
    }

    @Override
    public ContractDecision<PreflightEntryResult> preflight(
        BookAccess bookAccess, PostEntryCommand command) {
      preflightAccesses.add(bookAccess);
      return accepted(preflightResult);
    }

    @Override
    public ContractDecision<CommitEntryResult> commit(
        BookAccess bookAccess, PostEntryCommand command) {
      commitAccesses.add(bookAccess);
      return accepted(commitResult);
    }

    List<BookAccess> openBookAccesses() {
      return openBookAccesses;
    }

    List<BookAccess> declareAccountAccesses() {
      return declareAccountAccesses;
    }

    List<BookAccess> rekeyBookAccesses() {
      return rekeyBookAccesses;
    }

    List<BookAccess.PassphraseSource> rekeyReplacementPassphraseSources() {
      return rekeyReplacementPassphraseSources;
    }

    List<BookAccess> listAccountAccesses() {
      return listAccountAccesses;
    }

    List<ListAccountsQuery> listAccountQueries() {
      return listAccountQueries;
    }

    List<BookAccess> executePlanAccesses() {
      return executePlanAccesses;
    }

    List<BookAccess> preflightAccesses() {
      return preflightAccesses;
    }

    List<BookAccess> commitAccesses() {
      return commitAccesses;
    }

    boolean workflowInvoked() {
      return !openBookAccesses.isEmpty()
          || !rekeyBookAccesses.isEmpty()
          || !declareAccountAccesses.isEmpty()
          || !listAccountAccesses.isEmpty()
          || !executePlanAccesses.isEmpty()
          || !preflightAccesses.isEmpty()
          || !commitAccesses.isEmpty();
    }

    void setExecutePlanResult(LedgerPlanResult executePlanResult) {
      this.executePlanResult = executePlanResult;
    }
  }

  /** Workflow stub that always throws the same runtime failure. */
  static final class ExplodingWorkflow implements CliBookWorkflow {
    private final RuntimeException failure;

    ExplodingWorkflow(RuntimeException failure) {
      this.failure = failure;
    }

    @Override
    public ContractDecision<OpenBookResult> openBook(BookAccess bookAccess) {
      throw failure;
    }

    @Override
    public ContractDecision<RekeyBookResult> rekeyBook(
        BookAccess bookAccess, BookAccess.PassphraseSource replacementPassphraseSource) {
      throw failure;
    }

    @Override
    public ContractDecision<DeclareAccountResult> declareAccount(
        BookAccess bookAccess, DeclareAccountCommand command) {
      throw failure;
    }

    @Override
    public ContractDecision<ListAccountsResult> listAccounts(
        BookAccess bookAccess, ListAccountsQuery query) {
      throw failure;
    }

    @Override
    public ContractDecision<BookInspection> inspectBook(BookAccess bookAccess) {
      throw failure;
    }

    @Override
    public ContractDecision<GetPostingResult> getPosting(
        BookAccess bookAccess, PostingId postingId) {
      throw failure;
    }

    @Override
    public ContractDecision<ListPostingsResult> listPostings(
        BookAccess bookAccess, ListPostingsQuery query) {
      throw failure;
    }

    @Override
    public ContractDecision<AccountBalanceResult> accountBalance(
        BookAccess bookAccess, AccountBalanceQuery query) {
      throw failure;
    }

    @Override
    public ContractDecision<TrialBalanceResult> trialBalance(
        BookAccess bookAccess, dev.erst.fingrind.contract.TrialBalanceQuery query) {
      throw failure;
    }

    @Override
    public ContractDecision<AccountLedgerResult> accountLedger(
        BookAccess bookAccess, AccountLedgerQuery query) {
      throw failure;
    }

    @Override
    public ContractDecision<PeriodSummaryResult> periodSummary(
        BookAccess bookAccess, PeriodSummaryQuery query) {
      throw failure;
    }

    @Override
    public ContractDecision<LedgerPlanResult> executePlan(BookAccess bookAccess, LedgerPlan plan) {
      throw failure;
    }

    @Override
    public ContractDecision<PreflightEntryResult> preflight(
        BookAccess bookAccess, PostEntryCommand command) {
      throw failure;
    }

    @Override
    public ContractDecision<CommitEntryResult> commit(
        BookAccess bookAccess, PostEntryCommand command) {
      throw failure;
    }
  }

  /** Workflow stub that always throws an invalid-request style exception. */
  protected static final class IllegalArgumentWorkflow implements CliBookWorkflow {
    @Override
    public ContractDecision<OpenBookResult> openBook(BookAccess bookAccess) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public ContractDecision<RekeyBookResult> rekeyBook(
        BookAccess bookAccess, BookAccess.PassphraseSource replacementPassphraseSource) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public ContractDecision<DeclareAccountResult> declareAccount(
        BookAccess bookAccess, DeclareAccountCommand command) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public ContractDecision<ListAccountsResult> listAccounts(
        BookAccess bookAccess, ListAccountsQuery query) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public ContractDecision<BookInspection> inspectBook(BookAccess bookAccess) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public ContractDecision<GetPostingResult> getPosting(
        BookAccess bookAccess, PostingId postingId) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public ContractDecision<ListPostingsResult> listPostings(
        BookAccess bookAccess, ListPostingsQuery query) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public ContractDecision<AccountBalanceResult> accountBalance(
        BookAccess bookAccess, AccountBalanceQuery query) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public ContractDecision<TrialBalanceResult> trialBalance(
        BookAccess bookAccess, dev.erst.fingrind.contract.TrialBalanceQuery query) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public ContractDecision<AccountLedgerResult> accountLedger(
        BookAccess bookAccess, AccountLedgerQuery query) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public ContractDecision<PeriodSummaryResult> periodSummary(
        BookAccess bookAccess, PeriodSummaryQuery query) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public ContractDecision<LedgerPlanResult> executePlan(BookAccess bookAccess, LedgerPlan plan) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public ContractDecision<PreflightEntryResult> preflight(
        BookAccess bookAccess, PostEntryCommand command) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public ContractDecision<CommitEntryResult> commit(
        BookAccess bookAccess, PostEntryCommand command) {
      throw new IllegalArgumentException("workflow boom");
    }
  }

  protected static CliBookWorkflow reportingWorkflow(TrialBalanceResult trialBalanceResult) {
    return reportingWorkflow(
        new AccountBalanceResult.Rejected(
            new dev.erst.fingrind.contract.BookQueryRejection.BookNotInitialized()),
        trialBalanceResult,
        new AccountLedgerResult.Rejected(
            new dev.erst.fingrind.contract.BookQueryRejection.BookNotInitialized()),
        new PeriodSummaryResult.Rejected(
            new dev.erst.fingrind.contract.BookQueryRejection.BookNotInitialized()));
  }

  protected static CliBookWorkflow reportingWorkflow(
      AccountBalanceResult accountBalanceResult,
      TrialBalanceResult trialBalanceResult,
      AccountLedgerResult accountLedgerResult,
      PeriodSummaryResult periodSummaryResult) {
    return new CliBookWorkflow() {
      @Override
      public ContractDecision<OpenBookResult> openBook(BookAccess bookAccess) {
        throw new AssertionError("openBook should not be called in this test");
      }

      @Override
      public ContractDecision<RekeyBookResult> rekeyBook(
          BookAccess bookAccess, BookAccess.PassphraseSource replacementPassphraseSource) {
        throw new AssertionError("rekeyBook should not be called in this test");
      }

      @Override
      public ContractDecision<DeclareAccountResult> declareAccount(
          BookAccess bookAccess, DeclareAccountCommand command) {
        throw new AssertionError("declareAccount should not be called in this test");
      }

      @Override
      public ContractDecision<BookInspection> inspectBook(BookAccess bookAccess) {
        throw new AssertionError("inspectBook should not be called in this test");
      }

      @Override
      public ContractDecision<ListAccountsResult> listAccounts(
          BookAccess bookAccess, ListAccountsQuery query) {
        throw new AssertionError("listAccounts should not be called in this test");
      }

      @Override
      public ContractDecision<GetPostingResult> getPosting(
          BookAccess bookAccess, PostingId postingId) {
        throw new AssertionError("getPosting should not be called in this test");
      }

      @Override
      public ContractDecision<ListPostingsResult> listPostings(
          BookAccess bookAccess, ListPostingsQuery query) {
        throw new AssertionError("listPostings should not be called in this test");
      }

      @Override
      public ContractDecision<AccountBalanceResult> accountBalance(
          BookAccess bookAccess, AccountBalanceQuery query) {
        return accepted(accountBalanceResult);
      }

      @Override
      public ContractDecision<TrialBalanceResult> trialBalance(
          BookAccess bookAccess, dev.erst.fingrind.contract.TrialBalanceQuery query) {
        return accepted(trialBalanceResult);
      }

      @Override
      public ContractDecision<AccountLedgerResult> accountLedger(
          BookAccess bookAccess, AccountLedgerQuery query) {
        return accepted(accountLedgerResult);
      }

      @Override
      public ContractDecision<PeriodSummaryResult> periodSummary(
          BookAccess bookAccess, PeriodSummaryQuery query) {
        return accepted(periodSummaryResult);
      }

      @Override
      public ContractDecision<LedgerPlanResult> executePlan(
          BookAccess bookAccess, LedgerPlan plan) {
        throw new AssertionError("executePlan should not be called in this test");
      }

      @Override
      public ContractDecision<PreflightEntryResult> preflight(
          BookAccess bookAccess, PostEntryCommand command) {
        throw new AssertionError("preflight should not be called in this test");
      }

      @Override
      public ContractDecision<CommitEntryResult> commit(
          BookAccess bookAccess, PostEntryCommand command) {
        throw new AssertionError("commit should not be called in this test");
      }
    };
  }

  protected static <T> ContractDecision<T> accepted(T value) {
    return ContractDecision.accepted(value);
  }
}
