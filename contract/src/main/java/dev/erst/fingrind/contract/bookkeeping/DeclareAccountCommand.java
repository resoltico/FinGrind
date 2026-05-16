package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountSemantics;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import java.util.Objects;

/** Application command for declaring or reactivating one ledger account in a book. */
public record DeclareAccountCommand(
    AccountCode accountCode,
    AccountName accountName,
    AccountType accountType,
    AccountRole accountRole,
    AccountTaxonomy accountTaxonomy) {
  /** Validates one account-declaration command. */
  public DeclareAccountCommand {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(accountName, "accountName");
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(accountRole, "accountRole");
    Objects.requireNonNull(accountTaxonomy, "accountTaxonomy");
    AccountSemantics.validate(accountType, accountRole, accountTaxonomy);
  }
}
