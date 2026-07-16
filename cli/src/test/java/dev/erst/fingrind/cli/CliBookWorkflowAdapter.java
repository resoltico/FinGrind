package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleQuery;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleResult;
import dev.erst.fingrind.contract.bookkeeping.AmendAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.AmendAccountResult;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementResult;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseCommand;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseResult;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepCommand;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationQuery;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationResult;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterResult;
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
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.bookkeeping.RetireAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.RetireAccountResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.BookAccess.PassphraseSource;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsQuery;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsResult;
import dev.erst.fingrind.contract.tax.TaxObligationQuery;
import dev.erst.fingrind.contract.tax.TaxObligationResult;
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
      BookAccess bookAccess, Path newBookKeyFilePath) {
    throw unexpectedInvocation("rekeyBook");
  }

  @Override
  public ContractDecision<BackupBookResult> backupBook(
      BookAccess bookAccess, Path backupFilePath, Path backupBookKeyFilePath) {
    throw unexpectedInvocation("backupBook");
  }

  @Override
  public ContractDecision<RestoreBookResult> restoreBook(
      Path bookFilePath,
      Path newBookKeyFilePath,
      Path backupFilePath,
      Path backupKeyFilePath,
      boolean replaceExistingBook) {
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
  public ContractDecision<AmendAccountResult> amendAccount(
      BookAccess bookAccess, AmendAccountCommand command) {
    throw unexpectedInvocation("amendAccount");
  }

  @Override
  public ContractDecision<RetireAccountResult> retireAccount(
      BookAccess bookAccess, RetireAccountCommand command) {
    throw unexpectedInvocation("retireAccount");
  }

  @Override
  public ContractDecision<DeclareTaxRegistrationResult> declareTaxRegistration(
      BookAccess bookAccess, DeclareTaxRegistrationCommand command) {
    throw unexpectedInvocation("declareTaxRegistration");
  }

  @Override
  public ContractDecision<InterimResultSweepResult> interimResultSweep(
      BookAccess bookAccess, InterimResultSweepCommand command) {
    throw unexpectedInvocation("interimResultSweep");
  }

  @Override
  public ContractDecision<FiscalYearCloseResult> fiscalYearClose(
      BookAccess bookAccess, FiscalYearCloseCommand command) {
    throw unexpectedInvocation("fiscalYearClose");
  }

  @Override
  public ContractDecision<ListAccountsResult> listAccounts(
      BookAccess bookAccess, ListAccountsQuery query) {
    throw unexpectedInvocation("listAccounts");
  }

  @Override
  public ContractDecision<ListTaxRegistrationsResult> listTaxRegistrations(
      BookAccess bookAccess, ListTaxRegistrationsQuery query) {
    throw unexpectedInvocation("listTaxRegistrations");
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
  public ContractDecision<TaxObligationResult> taxObligation(
      BookAccess bookAccess, TaxObligationQuery query) {
    throw unexpectedInvocation("taxObligation");
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
  public ContractDecision<InventoryValuationResult> inventoryValuation(
      BookAccess bookAccess, InventoryValuationQuery query) {
    throw unexpectedInvocation("inventoryValuation");
  }

  @Override
  public ContractDecision<AccrualCutoffScheduleResult> accrualCutoffSchedule(
      BookAccess bookAccess, AccrualCutoffScheduleQuery query) {
    throw unexpectedInvocation("accrualCutoffSchedule");
  }

  @Override
  public ContractDecision<LatvianPayrollRegisterResult> latvianPayrollRegister(
      BookAccess bookAccess, LatvianPayrollRegisterQuery query) {
    throw unexpectedInvocation("latvianPayrollRegister");
  }

  @Override
  public ContractDecision<FixedAssetRegisterResult> fixedAssetRegister(
      BookAccess bookAccess, FixedAssetRegisterQuery query) {
    throw unexpectedInvocation("fixedAssetRegister");
  }

  @Override
  public ContractDecision<FinancingRegisterResult> financingRegister(
      BookAccess bookAccess, FinancingRegisterQuery query) {
    throw unexpectedInvocation("financingRegister");
  }

  @Override
  public ContractDecision<RealizedForeignExchangeRegisterResult> realizedForeignExchangeRegister(
      BookAccess bookAccess, RealizedForeignExchangeRegisterQuery query) {
    throw unexpectedInvocation("realizedForeignExchangeRegister");
  }

  @Override
  public ContractDecision<CashFlowStatementResult> cashFlowStatement(
      BookAccess bookAccess, CashFlowStatementQuery query) {
    throw unexpectedInvocation("cashFlowStatement");
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
