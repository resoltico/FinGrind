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

/** Execution seam for routing CLI commands through the selected book adapter. */
interface CliBookWorkflow {
  /** Opens the selected book and installs the canonical FinGrind schema when possible. */
  ContractDecision<OpenBookResult> openBook(BookAccess bookAccess, OpenBookCommand command);

  /** Rotates the passphrase that protects one existing book file. */
  ContractDecision<RekeyBookResult> rekeyBook(
      BookAccess bookAccess, PassphraseSource replacementPassphraseSource);

  /** Exports one closed encrypted-book backup pair. */
  ContractDecision<BackupBookResult> backupBook(
      BookAccess bookAccess, Path backupFilePath, Path backupBookKeyFilePath);

  /** Restores one encrypted-book backup pair onto one selected live book path. */
  ContractDecision<RestoreBookResult> restoreBook(
      Path bookFilePath, Path backupFilePath, Path backupBookKeyFilePath);

  /** Inspects stale sibling rekey rollback artifacts for the selected book path. */
  ContractDecision<RekeyRollbackResult> inspectRekeyRollback(Path bookFilePath);

  /** Deletes one selected sibling rekey rollback artifact. */
  ContractDecision<RekeyRollbackResult> deleteRekeyRollback(
      BookAccess bookAccess, @Nullable Path rollbackArtifactPath);

  /** Restores one selected sibling rekey rollback artifact onto the live book path. */
  ContractDecision<RekeyRollbackResult> restoreRekeyRollback(
      Path bookFilePath,
      @Nullable Path rollbackArtifactPath,
      PassphraseSource expectedPassphraseSource);

  /** Declares or reactivates one account inside the selected book. */
  ContractDecision<DeclareAccountResult> declareAccount(
      BookAccess bookAccess, DeclareAccountCommand command);

  /** Closes one contiguous reporting period into the policy-selected closing equity account. */
  ContractDecision<ClosePeriodResult> closePeriod(
      BookAccess bookAccess, ClosePeriodCommand command);

  /** Inspects one selected book for lifecycle and compatibility state. */
  ContractDecision<BookInspection> inspectBook(BookAccess bookAccess);

  /** Lists the declared accounts currently stored in the selected book. */
  ContractDecision<ListAccountsResult> listAccounts(BookAccess bookAccess, ListAccountsQuery query);

  /** Returns one committed posting by durable posting identity. */
  ContractDecision<GetPostingResult> getPosting(
      BookAccess bookAccess, dev.erst.fingrind.core.PostingId postingId);

  /** Lists one filtered page of committed postings. */
  ContractDecision<ListPostingsResult> listPostings(BookAccess bookAccess, ListPostingsQuery query);

  /** Computes per-currency balances for one declared account. */
  ContractDecision<AccountBalanceResult> accountBalance(
      BookAccess bookAccess, AccountBalanceQuery query);

  /** Computes the trial balance for one selected book. */
  ContractDecision<TrialBalanceResult> trialBalance(BookAccess bookAccess, TrialBalanceQuery query);

  /** Computes the running ledger for one selected account. */
  ContractDecision<AccountLedgerResult> accountLedger(
      BookAccess bookAccess, AccountLedgerQuery query);

  /** Computes the bounded period summary for one selected book. */
  ContractDecision<PeriodSummaryResult> periodSummary(
      BookAccess bookAccess, PeriodSummaryQuery query);

  /** Computes one statement of financial position. */
  ContractDecision<FinancialPositionResult> financialPosition(
      BookAccess bookAccess, FinancialPositionQuery query);

  /** Computes one bounded income statement. */
  ContractDecision<IncomeStatementResult> incomeStatement(
      BookAccess bookAccess, IncomeStatementQuery query);

  /** Computes one bounded statement of changes in equity. */
  ContractDecision<ChangesInEquityResult> changesInEquity(
      BookAccess bookAccess, ChangesInEquityQuery query);

  /** Executes one ordered AI-agent ledger plan atomically. */
  ContractDecision<LedgerPlanResult> executePlan(BookAccess bookAccess, LedgerPlan plan);

  /** Validates a posting request without mutating the selected book. */
  ContractDecision<PreflightEntryResult> preflight(BookAccess bookAccess, PostEntryCommand command);

  /** Commits a posting request into the selected book. */
  ContractDecision<CommitEntryResult> commit(BookAccess bookAccess, PostEntryCommand command);
}
