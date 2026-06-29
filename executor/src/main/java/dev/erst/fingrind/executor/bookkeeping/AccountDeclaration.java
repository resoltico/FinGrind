package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountCodePolicy;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountTaxonomyDoctrine;
import dev.erst.fingrind.core.AccountType;
import java.util.Objects;

/** Internal bookkeeping command for declaring or reactivating one account. */
public record AccountDeclaration(
    AccountCode accountCode,
    AccountName accountName,
    AccountType accountType,
    AccountTaxonomy accountTaxonomy) {
  /** Validates one bookkeeping account declaration. */
  public AccountDeclaration {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(accountName, "accountName");
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(accountTaxonomy, "accountTaxonomy");
    AccountTaxonomyDoctrine.validate(accountType, accountTaxonomy);
    AccountCodePolicy.validate(accountCode, accountType, accountTaxonomy);
  }

  /** Returns the doctrinal journal side that increases this account. */
  public dev.erst.fingrind.core.NormalBalance normalBalance() {
    return AccountTaxonomyDoctrine.normalBalance(accountType, accountTaxonomy);
  }
}
