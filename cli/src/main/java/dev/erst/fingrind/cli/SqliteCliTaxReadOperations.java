package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsQuery;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsResult;
import dev.erst.fingrind.contract.tax.TaxObligationQuery;
import dev.erst.fingrind.contract.tax.TaxObligationResult;

/** SQLite implementation of declared-tax and tax-obligation reads. */
interface SqliteCliTaxReadOperations extends CliBookReadWorkflow, SqliteCliReadSessionOperations {
  @Override
  default ContractDecision<ListTaxRegistrationsResult> listTaxRegistrations(
      BookAccess bookAccess, ListTaxRegistrationsQuery query) {
    return withTaxRead(bookAccess, service -> service.listTaxRegistrations(query));
  }

  @Override
  default ContractDecision<TaxObligationResult> taxObligation(
      BookAccess bookAccess, TaxObligationQuery query) {
    return withTaxRead(bookAccess, service -> service.taxObligation(query));
  }
}
