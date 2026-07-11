package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementResult;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationQuery;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsQuery;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsResult;
import dev.erst.fingrind.contract.tax.TaxObligationQuery;
import dev.erst.fingrind.contract.tax.TaxObligationResult;
import dev.erst.fingrind.core.PostingId;

/** Read seam for inspection, listing, and reporting over one selected book. */
interface CliBookReadWorkflow {
  /** Inspects one protected book without mutating its durable contents. */
  ContractDecision<BookInspection> inspectBook(BookAccess bookAccess);

  /** Lists declared accounts with one cursor window. */
  ContractDecision<ListAccountsResult> listAccounts(BookAccess bookAccess, ListAccountsQuery query);

  /** Lists declared tax registrations with one cursor window. */
  ContractDecision<ListTaxRegistrationsResult> listTaxRegistrations(
      BookAccess bookAccess, ListTaxRegistrationsQuery query);

  /** Reads one posting by durable posting id. */
  ContractDecision<GetPostingResult> getPosting(BookAccess bookAccess, PostingId postingId);

  /** Lists postings with the selected filters and cursor window. */
  ContractDecision<ListPostingsResult> listPostings(BookAccess bookAccess, ListPostingsQuery query);

  /** Reports one bounded tax obligation for the selected declared tax registration. */
  ContractDecision<TaxObligationResult> taxObligation(
      BookAccess bookAccess, TaxObligationQuery query);

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

  /** Reports exact per-account inventory valuation through an optional effective-date cutoff. */
  ContractDecision<InventoryValuationResult> inventoryValuation(
      BookAccess bookAccess, InventoryValuationQuery query);

  /** Reports one statement of cash receipts and payments projection. */
  ContractDecision<CashFlowStatementResult> cashFlowStatement(
      BookAccess bookAccess, CashFlowStatementQuery query);

  /** Reports one changes-in-equity projection. */
  ContractDecision<ChangesInEquityResult> changesInEquity(
      BookAccess bookAccess, ChangesInEquityQuery query);
}
