package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountCodePolicy;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountSemantics;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import java.util.Objects;

/** Internal bookkeeping command for declaring or reactivating one account. */
public record AccountDeclaration(
    AccountCode accountCode,
    AccountName accountName,
    AccountType accountType,
    AccountRole accountRole,
    AccountTaxonomy accountTaxonomy) {
  /** Validates one bookkeeping account declaration. */
  public AccountDeclaration {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(accountName, "accountName");
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(accountRole, "accountRole");
    Objects.requireNonNull(accountTaxonomy, "accountTaxonomy");
    AccountSemantics.validate(accountType, accountRole, accountTaxonomy);
    AccountCodePolicy.validate(accountCode, accountType, accountRole, accountTaxonomy);
  }

  /** Returns the doctrinal journal side that increases this account. */
  public dev.erst.fingrind.core.NormalBalance normalBalance() {
    return AccountSemantics.normalBalance(accountType, accountRole);
  }
}
