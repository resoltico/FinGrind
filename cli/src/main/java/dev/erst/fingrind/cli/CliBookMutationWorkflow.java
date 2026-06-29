package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseCommand;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseResult;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepCommand;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;

/** Mutation seam for durable bookkeeping and operational write flows. */
interface CliBookMutationWorkflow {
  /** Declares one new account into the selected protected book. */
  ContractDecision<DeclareAccountResult> declareAccount(
      BookAccess bookAccess, DeclareAccountCommand command);

  /** Declares or updates one owned tax registration in the selected protected book. */
  ContractDecision<DeclareTaxRegistrationResult> declareTaxRegistration(
      BookAccess bookAccess, DeclareTaxRegistrationCommand command);

  /** Closes one reporting period into the configured result-holding account. */
  ContractDecision<InterimResultSweepResult> interimResultSweep(
      BookAccess bookAccess, InterimResultSweepCommand command);

  /** Closes one fiscal year into capital and retained accumulated equity. */
  ContractDecision<FiscalYearCloseResult> fiscalYearClose(
      BookAccess bookAccess, FiscalYearCloseCommand command);

  /** Executes one declarative ledger plan against the selected book. */
  ContractDecision<LedgerPlanResult> executePlan(BookAccess bookAccess, LedgerPlan plan);

  /** Validates one posting command without committing it. */
  ContractDecision<PreflightEntryResult> preflight(BookAccess bookAccess, PostEntryCommand command);

  /** Commits one posting command into durable book storage. */
  ContractDecision<CommitEntryResult> commit(BookAccess bookAccess, PostEntryCommand command);
}
