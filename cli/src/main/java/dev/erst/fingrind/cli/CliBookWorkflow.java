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
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceResult;

/** Execution seam for routing CLI commands through the selected book adapter. */
interface CliBookWorkflow {
  /** Opens the selected book and installs the canonical FinGrind schema when possible. */
  ContractDecision<OpenBookResult> openBook(BookAccess bookAccess);

  /** Rotates the passphrase that protects one existing book file. */
  ContractDecision<RekeyBookResult> rekeyBook(
      BookAccess bookAccess, BookAccess.PassphraseSource replacementPassphraseSource);

  /** Declares or reactivates one account inside the selected book. */
  ContractDecision<DeclareAccountResult> declareAccount(
      BookAccess bookAccess, DeclareAccountCommand command);

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

  /** Executes one ordered AI-agent ledger plan atomically. */
  ContractDecision<LedgerPlanResult> executePlan(BookAccess bookAccess, LedgerPlan plan);

  /** Validates a posting request without mutating the selected book. */
  ContractDecision<PreflightEntryResult> preflight(BookAccess bookAccess, PostEntryCommand command);

  /** Commits a posting request into the selected book. */
  ContractDecision<CommitEntryResult> commit(BookAccess bookAccess, PostEntryCommand command);
}
