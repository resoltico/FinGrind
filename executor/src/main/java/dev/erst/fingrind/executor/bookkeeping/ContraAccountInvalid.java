package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ContraAccountRelationshipViolation;
import java.util.Objects;

/** Refusal for a contra relationship that does not preserve the chart's reporting meaning. */
record ContraAccountInvalid(
    AccountCode accountCode,
    AccountCode contraOfAccountCode,
    ContraAccountRelationshipViolation violation)
    implements BookkeepingAdministrationRejection {
  ContraAccountInvalid {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(contraOfAccountCode, "contraOfAccountCode");
    Objects.requireNonNull(violation, "violation");
  }
}
