package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.ClosePeriodCommand;
import dev.erst.fingrind.contract.bookkeeping.ClosePeriodResult;
import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.BookAccess.PassphraseSource;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.PublicPathHint;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.core.PostingId;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Shared CLI workflow doubles and routing-oriented helpers for split command tests. */
class CliWorkflowDoubleSupport extends CliFixtureSupport {
  protected static BookAccess bookAccess(Path bookFilePath, Path bookKeyFilePath) {
    return new BookAccess(bookFilePath, new BookAccess.PassphraseSource.KeyFile(bookKeyFilePath));
  }

  /** Recording workflow used to assert CLI routing without opening SQLite. */
  static final class RecordingWorkflow implements CliBookWorkflow {
    private final List<BookAccess> openBookAccesses = new ArrayList<>();
    private final List<OpenBookCommand> openBookCommands = new ArrayList<>();
    private final List<BookAccess> rekeyBookAccesses = new ArrayList<>();
    private final List<BookAccess.PassphraseSource> rekeyReplacementPassphraseSources =
        new ArrayList<>();
    private final List<BookAccess> backupBookAccesses = new ArrayList<>();
    private final List<Path> backupFilePaths = new ArrayList<>();
    private final List<Path> backupBookKeyFilePaths = new ArrayList<>();
    private final List<Path> restoreBookFilePaths = new ArrayList<>();
    private final List<Path> restoreBackupFilePaths = new ArrayList<>();
    private final List<Path> restoreBackupBookKeyFilePaths = new ArrayList<>();
    private final List<Path> inspectRekeyRollbackBookFilePaths = new ArrayList<>();
    private final List<Path> restoreRekeyRollbackBookFilePaths = new ArrayList<>();
    private final List<Path> deleteRekeyRollbackBookFilePaths = new ArrayList<>();
    private final List<@Nullable Path> restoreRekeyRollbackArtifactPaths = new ArrayList<>();
    private final List<@Nullable Path> deleteRekeyRollbackArtifactPaths = new ArrayList<>();
    private final List<PassphraseSource> restoreRekeyRollbackExpectedPassphraseSources =
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
    private BackupBookResult backupBookResult =
        new BackupBookResult.BackedUp(
            hint(Path.of("books/unused.sqlite")),
            hint(Path.of("books/unused.backup.sqlite")),
            hint(Path.of("keys/unused.backup.key")));
    private RestoreBookResult restoreBookResult =
        new RestoreBookResult.Restored(
            hint(Path.of("books/unused.sqlite")),
            hint(Path.of("books/unused.backup.sqlite")),
            hint(Path.of("keys/unused.backup.key")));
    private RekeyRollbackResult recoverRekeyResult =
        new RekeyRollbackResult.Inspected(hint(Path.of("books/unused.sqlite")), List.of());
    private @Nullable LedgerPlanResult executePlanResult;

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
    public ContractDecision<OpenBookResult> openBook(
        BookAccess bookAccess, OpenBookCommand command) {
      openBookAccesses.add(bookAccess);
      openBookCommands.add(command);
      return accepted(openBookResult);
    }

    @Override
    public ContractDecision<RekeyBookResult> rekeyBook(
        BookAccess bookAccess, PassphraseSource replacementPassphraseSource) {
      rekeyBookAccesses.add(bookAccess);
      rekeyReplacementPassphraseSources.add(replacementPassphraseSource);
      return accepted(rekeyBookResult);
    }

    @Override
    public ContractDecision<BackupBookResult> backupBook(
        BookAccess bookAccess, Path backupFilePath, Path backupBookKeyFilePath) {
      backupBookAccesses.add(bookAccess);
      backupFilePaths.add(backupFilePath);
      backupBookKeyFilePaths.add(backupBookKeyFilePath);
      return accepted(backupBookResult);
    }

    @Override
    public ContractDecision<RestoreBookResult> restoreBook(
        Path bookFilePath, Path backupFilePath, Path backupBookKeyFilePath) {
      restoreBookFilePaths.add(bookFilePath);
      restoreBackupFilePaths.add(backupFilePath);
      restoreBackupBookKeyFilePaths.add(backupBookKeyFilePath);
      return accepted(restoreBookResult);
    }

    @Override
    public ContractDecision<RekeyRollbackResult> inspectRekeyRollback(Path bookFilePath) {
      inspectRekeyRollbackBookFilePaths.add(bookFilePath);
      return accepted(recoverRekeyResult);
    }

    @Override
    public ContractDecision<RekeyRollbackResult> deleteRekeyRollback(
        Path bookFilePath, @Nullable Path rollbackArtifactPath) {
      deleteRekeyRollbackBookFilePaths.add(bookFilePath);
      deleteRekeyRollbackArtifactPaths.add(rollbackArtifactPath);
      return accepted(recoverRekeyResult);
    }

    @Override
    public ContractDecision<RekeyRollbackResult> restoreRekeyRollback(
        Path bookFilePath,
        @Nullable Path rollbackArtifactPath,
        PassphraseSource expectedPassphraseSource) {
      restoreRekeyRollbackBookFilePaths.add(bookFilePath);
      restoreRekeyRollbackArtifactPaths.add(rollbackArtifactPath);
      restoreRekeyRollbackExpectedPassphraseSources.add(expectedPassphraseSource);
      return accepted(recoverRekeyResult);
    }

    @Override
    public ContractDecision<DeclareAccountResult> declareAccount(
        BookAccess bookAccess, DeclareAccountCommand command) {
      declareAccountAccesses.add(bookAccess);
      return accepted(declareAccountResult);
    }

    @Override
    public ContractDecision<ClosePeriodResult> closePeriod(
        BookAccess bookAccess, ClosePeriodCommand command) {
      throw new AssertionError("closePeriod should not be called in this test");
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
        BookAccess bookAccess, dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery query) {
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
    public ContractDecision<FinancialPositionResult> financialPosition(
        BookAccess bookAccess, FinancialPositionQuery query) {
      throw new AssertionError("financialPosition should not be called in this test");
    }

    @Override
    public ContractDecision<IncomeStatementResult> incomeStatement(
        BookAccess bookAccess, IncomeStatementQuery query) {
      throw new AssertionError("incomeStatement should not be called in this test");
    }

    @Override
    public ContractDecision<ChangesInEquityResult> changesInEquity(
        BookAccess bookAccess, ChangesInEquityQuery query) {
      throw new AssertionError("changesInEquity should not be called in this test");
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

    List<OpenBookCommand> openBookCommands() {
      return openBookCommands;
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

    List<BookAccess> backupBookAccesses() {
      return backupBookAccesses;
    }

    List<Path> backupFilePaths() {
      return backupFilePaths;
    }

    List<Path> backupBookKeyFilePaths() {
      return backupBookKeyFilePaths;
    }

    List<Path> restoreBookFilePaths() {
      return restoreBookFilePaths;
    }

    List<Path> restoreBackupFilePaths() {
      return restoreBackupFilePaths;
    }

    List<Path> restoreBackupBookKeyFilePaths() {
      return restoreBackupBookKeyFilePaths;
    }

    List<Path> inspectRekeyRollbackBookFilePaths() {
      return inspectRekeyRollbackBookFilePaths;
    }

    List<Path> restoreRekeyRollbackBookFilePaths() {
      return restoreRekeyRollbackBookFilePaths;
    }

    List<Path> deleteRekeyRollbackBookFilePaths() {
      return deleteRekeyRollbackBookFilePaths;
    }

    List<@Nullable Path> restoreRekeyRollbackArtifactPaths() {
      return restoreRekeyRollbackArtifactPaths;
    }

    List<@Nullable Path> deleteRekeyRollbackArtifactPaths() {
      return deleteRekeyRollbackArtifactPaths;
    }

    List<PassphraseSource> restoreRekeyRollbackExpectedPassphraseSources() {
      return restoreRekeyRollbackExpectedPassphraseSources;
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
          || !backupBookAccesses.isEmpty()
          || !restoreBookFilePaths.isEmpty()
          || !inspectRekeyRollbackBookFilePaths.isEmpty()
          || !restoreRekeyRollbackBookFilePaths.isEmpty()
          || !deleteRekeyRollbackBookFilePaths.isEmpty()
          || !declareAccountAccesses.isEmpty()
          || !listAccountAccesses.isEmpty()
          || !executePlanAccesses.isEmpty()
          || !preflightAccesses.isEmpty()
          || !commitAccesses.isEmpty();
    }

    void setExecutePlanResult(LedgerPlanResult executePlanResult) {
      this.executePlanResult = executePlanResult;
    }

    void setBackupBookResult(BackupBookResult backupBookResult) {
      this.backupBookResult = backupBookResult;
    }

    void setRestoreBookResult(RestoreBookResult restoreBookResult) {
      this.restoreBookResult = restoreBookResult;
    }

    void setRekeyRollbackResult(RekeyRollbackResult recoverRekeyResult) {
      this.recoverRekeyResult = recoverRekeyResult;
    }
  }

  /** Workflow stub that always throws the same runtime failure. */
  static final class ExplodingWorkflow implements CliBookWorkflow {
    private final RuntimeException failure;

    ExplodingWorkflow(RuntimeException failure) {
      this.failure = failure;
    }

    @Override
    public ContractDecision<OpenBookResult> openBook(
        BookAccess bookAccess, OpenBookCommand command) {
      throw failure;
    }

    @Override
    public ContractDecision<RekeyBookResult> rekeyBook(
        BookAccess bookAccess, PassphraseSource replacementPassphraseSource) {
      throw failure;
    }

    @Override
    public ContractDecision<BackupBookResult> backupBook(
        BookAccess bookAccess, Path backupFilePath, Path backupBookKeyFilePath) {
      throw failure;
    }

    @Override
    public ContractDecision<RestoreBookResult> restoreBook(
        Path bookFilePath, Path backupFilePath, Path backupBookKeyFilePath) {
      throw failure;
    }

    @Override
    public ContractDecision<RekeyRollbackResult> inspectRekeyRollback(Path bookFilePath) {
      throw failure;
    }

    @Override
    public ContractDecision<RekeyRollbackResult> deleteRekeyRollback(
        Path bookFilePath, @Nullable Path rollbackArtifactPath) {
      throw failure;
    }

    @Override
    public ContractDecision<RekeyRollbackResult> restoreRekeyRollback(
        Path bookFilePath,
        @Nullable Path rollbackArtifactPath,
        PassphraseSource expectedPassphraseSource) {
      throw failure;
    }

    @Override
    public ContractDecision<DeclareAccountResult> declareAccount(
        BookAccess bookAccess, DeclareAccountCommand command) {
      throw failure;
    }

    @Override
    public ContractDecision<ClosePeriodResult> closePeriod(
        BookAccess bookAccess, ClosePeriodCommand command) {
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
        BookAccess bookAccess, dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery query) {
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
    public ContractDecision<FinancialPositionResult> financialPosition(
        BookAccess bookAccess, FinancialPositionQuery query) {
      throw failure;
    }

    @Override
    public ContractDecision<IncomeStatementResult> incomeStatement(
        BookAccess bookAccess, IncomeStatementQuery query) {
      throw failure;
    }

    @Override
    public ContractDecision<ChangesInEquityResult> changesInEquity(
        BookAccess bookAccess, ChangesInEquityQuery query) {
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
    public ContractDecision<OpenBookResult> openBook(
        BookAccess bookAccess, OpenBookCommand command) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public ContractDecision<RekeyBookResult> rekeyBook(
        BookAccess bookAccess, PassphraseSource replacementPassphraseSource) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public ContractDecision<BackupBookResult> backupBook(
        BookAccess bookAccess, Path backupFilePath, Path backupBookKeyFilePath) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public ContractDecision<RestoreBookResult> restoreBook(
        Path bookFilePath, Path backupFilePath, Path backupBookKeyFilePath) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public ContractDecision<RekeyRollbackResult> inspectRekeyRollback(Path bookFilePath) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public ContractDecision<RekeyRollbackResult> deleteRekeyRollback(
        Path bookFilePath, @Nullable Path rollbackArtifactPath) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public ContractDecision<RekeyRollbackResult> restoreRekeyRollback(
        Path bookFilePath,
        @Nullable Path rollbackArtifactPath,
        PassphraseSource expectedPassphraseSource) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public ContractDecision<DeclareAccountResult> declareAccount(
        BookAccess bookAccess, DeclareAccountCommand command) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public ContractDecision<ClosePeriodResult> closePeriod(
        BookAccess bookAccess, ClosePeriodCommand command) {
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
        BookAccess bookAccess, dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery query) {
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
    public ContractDecision<FinancialPositionResult> financialPosition(
        BookAccess bookAccess, FinancialPositionQuery query) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public ContractDecision<IncomeStatementResult> incomeStatement(
        BookAccess bookAccess, IncomeStatementQuery query) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public ContractDecision<ChangesInEquityResult> changesInEquity(
        BookAccess bookAccess, ChangesInEquityQuery query) {
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
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized()),
        trialBalanceResult,
        new AccountLedgerResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized()),
        new PeriodSummaryResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized()),
        new FinancialPositionResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized()),
        new IncomeStatementResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized()),
        new ChangesInEquityResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized()));
  }

  protected static CliBookWorkflow reportingWorkflow(
      AccountBalanceResult accountBalanceResult,
      TrialBalanceResult trialBalanceResult,
      AccountLedgerResult accountLedgerResult,
      PeriodSummaryResult periodSummaryResult) {
    return reportingWorkflow(
        accountBalanceResult,
        trialBalanceResult,
        accountLedgerResult,
        periodSummaryResult,
        new FinancialPositionResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized()),
        new IncomeStatementResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized()),
        new ChangesInEquityResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized()));
  }

  protected static CliBookWorkflow reportingWorkflow(
      AccountBalanceResult accountBalanceResult,
      TrialBalanceResult trialBalanceResult,
      AccountLedgerResult accountLedgerResult,
      PeriodSummaryResult periodSummaryResult,
      FinancialPositionResult financialPositionResult,
      IncomeStatementResult incomeStatementResult,
      ChangesInEquityResult changesInEquityResult) {
    return new CliBookWorkflow() {
      @Override
      public ContractDecision<OpenBookResult> openBook(
          BookAccess bookAccess, OpenBookCommand command) {
        throw new AssertionError("openBook should not be called in this test");
      }

      @Override
      public ContractDecision<RekeyBookResult> rekeyBook(
          BookAccess bookAccess, PassphraseSource replacementPassphraseSource) {
        throw new AssertionError("rekeyBook should not be called in this test");
      }

      @Override
      public ContractDecision<BackupBookResult> backupBook(
          BookAccess bookAccess, Path backupFilePath, Path backupBookKeyFilePath) {
        throw new AssertionError("backupBook should not be called in this test");
      }

      @Override
      public ContractDecision<RestoreBookResult> restoreBook(
          Path bookFilePath, Path backupFilePath, Path backupBookKeyFilePath) {
        throw new AssertionError("restoreBook should not be called in this test");
      }

      @Override
      public ContractDecision<RekeyRollbackResult> inspectRekeyRollback(Path bookFilePath) {
        throw new AssertionError("inspectRekeyRollback should not be called in this test");
      }

      @Override
      public ContractDecision<RekeyRollbackResult> deleteRekeyRollback(
          Path bookFilePath, @Nullable Path rollbackArtifactPath) {
        throw new AssertionError("deleteRekeyRollback should not be called in this test");
      }

      @Override
      public ContractDecision<RekeyRollbackResult> restoreRekeyRollback(
          Path bookFilePath,
          @Nullable Path rollbackArtifactPath,
          PassphraseSource expectedPassphraseSource) {
        throw new AssertionError("restoreRekeyRollback should not be called in this test");
      }

      @Override
      public ContractDecision<DeclareAccountResult> declareAccount(
          BookAccess bookAccess, DeclareAccountCommand command) {
        throw new AssertionError("declareAccount should not be called in this test");
      }

      @Override
      public ContractDecision<ClosePeriodResult> closePeriod(
          BookAccess bookAccess, ClosePeriodCommand command) {
        throw new AssertionError("closePeriod should not be called in this test");
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
          BookAccess bookAccess, dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery query) {
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
      public ContractDecision<FinancialPositionResult> financialPosition(
          BookAccess bookAccess, FinancialPositionQuery query) {
        return accepted(financialPositionResult);
      }

      @Override
      public ContractDecision<IncomeStatementResult> incomeStatement(
          BookAccess bookAccess, IncomeStatementQuery query) {
        return accepted(incomeStatementResult);
      }

      @Override
      public ContractDecision<ChangesInEquityResult> changesInEquity(
          BookAccess bookAccess, ChangesInEquityQuery query) {
        return accepted(changesInEquityResult);
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

  protected static PublicPathHint hint(Path path) {
    return PublicPathHint.fromPath(path);
  }
}
