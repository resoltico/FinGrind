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
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/** Lifecycle seam for opening, protecting, backing up, and rolling back books. */
interface CliBookLifecycleWorkflow {
  /** Opens or initializes one protected book through the selected access route. */
  ContractDecision<OpenBookResult> openBook(BookAccess bookAccess, OpenBookCommand command);

  /** Replaces the passphrase material protecting one existing book. */
  ContractDecision<RekeyBookResult> rekeyBook(
      BookAccess bookAccess, PassphraseSource replacementPassphraseSource);

  /** Creates a backup copy plus its companion key artifact. */
  ContractDecision<BackupBookResult> backupBook(
      BookAccess bookAccess, Path backupFilePath, Path backupBookKeyFilePath);

  /** Restores one protected book from a backup artifact set. */
  ContractDecision<RestoreBookResult> restoreBook(
      Path bookFilePath, Path backupFilePath, Path backupBookKeyFilePath);

  /** Reads rollback metadata for the most recent interrupted rekey flow. */
  ContractDecision<RekeyRollbackResult> inspectRekeyRollback(Path bookFilePath);

  /** Deletes rollback artifacts once the operator accepts their removal. */
  ContractDecision<RekeyRollbackResult> deleteRekeyRollback(
      BookAccess bookAccess, @Nullable Path rollbackArtifactPath);

  /** Restores the rollback snapshot back into the primary book location. */
  ContractDecision<RekeyRollbackResult> restoreRekeyRollback(
      Path bookFilePath,
      @Nullable Path rollbackArtifactPath,
      PassphraseSource expectedPassphraseSource);
}

/** Mutation seam for durable bookkeeping and operational write flows. */
interface CliBookMutationWorkflow {
  /** Declares one new account into the selected protected book. */
  ContractDecision<DeclareAccountResult> declareAccount(
      BookAccess bookAccess, DeclareAccountCommand command);

  /** Closes one reporting period into the configured result-holding account. */
  ContractDecision<PeriodResultTransferResult> transferPeriodResult(
      BookAccess bookAccess, PeriodResultTransferCommand command);

  /** Executes one declarative ledger plan against the selected book. */
  ContractDecision<LedgerPlanResult> executePlan(BookAccess bookAccess, LedgerPlan plan);

  /** Validates one posting command without committing it. */
  ContractDecision<PreflightEntryResult> preflight(BookAccess bookAccess, PostEntryCommand command);

  /** Commits one posting command into durable book storage. */
  ContractDecision<CommitEntryResult> commit(BookAccess bookAccess, PostEntryCommand command);
}

/** Read seam for inspection, listing, and reporting over one selected book. */
interface CliBookReadWorkflow {
  /** Inspects one protected book without mutating its durable contents. */
  ContractDecision<BookInspection> inspectBook(BookAccess bookAccess);

  /** Lists declared accounts with one cursor window. */
  ContractDecision<ListAccountsResult> listAccounts(BookAccess bookAccess, ListAccountsQuery query);

  /** Reads one posting by durable posting id. */
  ContractDecision<GetPostingResult> getPosting(
      BookAccess bookAccess, dev.erst.fingrind.core.PostingId postingId);

  /** Lists postings with the selected filters and cursor window. */
  ContractDecision<ListPostingsResult> listPostings(BookAccess bookAccess, ListPostingsQuery query);

  /** Reports one account balance snapshot for the selected query window. */
  ContractDecision<AccountBalanceResult> accountBalance(
      BookAccess bookAccess, AccountBalanceQuery query);

  /** Reports one trial balance for the selected period and coverage. */
  ContractDecision<TrialBalanceResult> trialBalance(BookAccess bookAccess, TrialBalanceQuery query);

  /** Reports one account ledger export for the selected account and window. */
  ContractDecision<AccountLedgerResult> accountLedger(
      BookAccess bookAccess, AccountLedgerQuery query);

  /** Reports one period summary for the selected coverage and boundaries. */
  ContractDecision<PeriodSummaryResult> periodSummary(
      BookAccess bookAccess, PeriodSummaryQuery query);

  /** Reports one statement of financial position projection. */
  ContractDecision<FinancialPositionResult> financialPosition(
      BookAccess bookAccess, FinancialPositionQuery query);

  /** Reports one income statement projection. */
  ContractDecision<IncomeStatementResult> incomeStatement(
      BookAccess bookAccess, IncomeStatementQuery query);

  /** Reports one changes-in-equity projection. */
  ContractDecision<ChangesInEquityResult> changesInEquity(
      BookAccess bookAccess, ChangesInEquityQuery query);
}

/** Composite workflow role used by test doubles that exercise every CLI book seam together. */
interface CliBookWorkflow
    extends CliBookLifecycleWorkflow, CliBookMutationWorkflow, CliBookReadWorkflow {}
