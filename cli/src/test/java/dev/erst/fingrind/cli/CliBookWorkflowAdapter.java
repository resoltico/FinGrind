package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
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
import dev.erst.fingrind.contract.bookkeeping.PeriodResultTransferCommand;
import dev.erst.fingrind.contract.bookkeeping.PeriodResultTransferResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.BookAccess.PassphraseSource;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.core.PostingId;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/** Default test workflow adapter that fails fast on any unexpected CLI workflow call. */
abstract class CliBookWorkflowAdapter implements CliBookWorkflow {
  @Override
  public ContractDecision<OpenBookResult> openBook(BookAccess bookAccess, OpenBookCommand command) {
    throw unexpectedInvocation("openBook");
  }

  @Override
  public ContractDecision<RekeyBookResult> rekeyBook(
      BookAccess bookAccess, PassphraseSource replacementPassphraseSource) {
    throw unexpectedInvocation("rekeyBook");
  }

  @Override
  public ContractDecision<BackupBookResult> backupBook(
      BookAccess bookAccess, Path backupFilePath, Path backupBookKeyFilePath) {
    throw unexpectedInvocation("backupBook");
  }

  @Override
  public ContractDecision<RestoreBookResult> restoreBook(
      Path bookFilePath, Path backupFilePath, Path backupBookKeyFilePath) {
    throw unexpectedInvocation("restoreBook");
  }

  @Override
  public ContractDecision<RekeyRollbackResult> inspectRekeyRollback(Path bookFilePath) {
    throw unexpectedInvocation("inspectRekeyRollback");
  }

  @Override
  public ContractDecision<RekeyRollbackResult> deleteRekeyRollback(
      BookAccess bookAccess, @Nullable Path rollbackArtifactPath) {
    throw unexpectedInvocation("deleteRekeyRollback");
  }

  @Override
  public ContractDecision<RekeyRollbackResult> restoreRekeyRollback(
      Path bookFilePath,
      @Nullable Path rollbackArtifactPath,
      PassphraseSource expectedPassphraseSource) {
    throw unexpectedInvocation("restoreRekeyRollback");
  }

  @Override
  public ContractDecision<DeclareAccountResult> declareAccount(
      BookAccess bookAccess, DeclareAccountCommand command) {
    throw unexpectedInvocation("declareAccount");
  }

  @Override
  public ContractDecision<PeriodResultTransferResult> transferPeriodResult(
      BookAccess bookAccess, PeriodResultTransferCommand command) {
    throw unexpectedInvocation("transferPeriodResult");
  }

  @Override
  public ContractDecision<ListAccountsResult> listAccounts(
      BookAccess bookAccess, ListAccountsQuery query) {
    throw unexpectedInvocation("listAccounts");
  }

  @Override
  public ContractDecision<BookInspection> inspectBook(BookAccess bookAccess) {
    throw unexpectedInvocation("inspectBook");
  }

  @Override
  public ContractDecision<GetPostingResult> getPosting(BookAccess bookAccess, PostingId postingId) {
    throw unexpectedInvocation("getPosting");
  }

  @Override
  public ContractDecision<ListPostingsResult> listPostings(
      BookAccess bookAccess, ListPostingsQuery query) {
    throw unexpectedInvocation("listPostings");
  }

  @Override
  public ContractDecision<AccountBalanceResult> accountBalance(
      BookAccess bookAccess, AccountBalanceQuery query) {
    throw unexpectedInvocation("accountBalance");
  }

  @Override
  public ContractDecision<TrialBalanceResult> trialBalance(
      BookAccess bookAccess, TrialBalanceQuery query) {
    throw unexpectedInvocation("trialBalance");
  }

  @Override
  public ContractDecision<AccountLedgerResult> accountLedger(
      BookAccess bookAccess, AccountLedgerQuery query) {
    throw unexpectedInvocation("accountLedger");
  }

  @Override
  public ContractDecision<PeriodSummaryResult> periodSummary(
      BookAccess bookAccess, PeriodSummaryQuery query) {
    throw unexpectedInvocation("periodSummary");
  }

  @Override
  public ContractDecision<FinancialPositionResult> financialPosition(
      BookAccess bookAccess, FinancialPositionQuery query) {
    throw unexpectedInvocation("financialPosition");
  }

  @Override
  public ContractDecision<IncomeStatementResult> incomeStatement(
      BookAccess bookAccess, IncomeStatementQuery query) {
    throw unexpectedInvocation("incomeStatement");
  }

  @Override
  public ContractDecision<ChangesInEquityResult> changesInEquity(
      BookAccess bookAccess, ChangesInEquityQuery query) {
    throw unexpectedInvocation("changesInEquity");
  }

  @Override
  public ContractDecision<LedgerPlanResult> executePlan(BookAccess bookAccess, LedgerPlan plan) {
    throw unexpectedInvocation("executePlan");
  }

  @Override
  public ContractDecision<PreflightEntryResult> preflight(
      BookAccess bookAccess, PostEntryCommand command) {
    throw unexpectedInvocation("preflight");
  }

  @Override
  public ContractDecision<CommitEntryResult> commit(
      BookAccess bookAccess, PostEntryCommand command) {
    throw unexpectedInvocation("commit");
  }

  protected RuntimeException unexpectedInvocation(String operationName) {
    return new IllegalStateException(operationName + " should not be called in this test");
  }
}
