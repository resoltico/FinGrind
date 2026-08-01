package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ContraAccountRelationshipViolation;
import java.util.Objects;

/** Rejection for a contra relationship that cannot preserve the declared chart taxonomy. */
public record ContraAccountInvalid(
    AccountCode accountCode,
    AccountCode contraOfAccountCode,
    ContraAccountRelationshipViolation violation)
    implements BookAdministrationRejection {
  public ContraAccountInvalid {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(contraOfAccountCode, "contraOfAccountCode");
    Objects.requireNonNull(violation, "violation");
  }
}
