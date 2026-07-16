package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.tax.TaxObligationQuery;
import dev.erst.fingrind.contract.tax.TaxObligationResult;

/** Tax obligation reporting capability over one protected book. */
@FunctionalInterface
interface CliBookTaxReportReadWorkflow {
  /** Reports one bounded tax obligation for the selected declared tax registration. */
  ContractDecision<TaxObligationResult> taxObligation(
      BookAccess bookAccess, TaxObligationQuery query);
}
