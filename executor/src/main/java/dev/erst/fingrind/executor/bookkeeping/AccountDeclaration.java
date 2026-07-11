package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountCodePolicy;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountTaxonomyDoctrine;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.UnitOfMeasure;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Internal bookkeeping command for declaring or reactivating one account. */
public record AccountDeclaration(
    AccountCode accountCode,
    AccountName accountName,
    AccountType accountType,
    AccountTaxonomy accountTaxonomy,
    @Nullable UnitOfMeasure unitOfMeasure) {
  /** Convenience constructor for non-inventory account declarations. */
  public AccountDeclaration(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountTaxonomy accountTaxonomy) {
    this(accountCode, accountName, accountType, accountTaxonomy, null);
  }

  /** Validates one bookkeeping account declaration. */
  public AccountDeclaration {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(accountName, "accountName");
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(accountTaxonomy, "accountTaxonomy");
    AccountTaxonomyDoctrine.validate(accountType, accountTaxonomy);
    AccountCodePolicy.validate(accountCode, accountType, accountTaxonomy);
    boolean inventoryAccount =
        AccountRole.from(accountType, accountTaxonomy) == AccountRole.INVENTORY;
    if (inventoryAccount && unitOfMeasure == null) {
      throw new IllegalArgumentException(
          "Inventory account declarations require one unitOfMeasure.");
    }
    if (!inventoryAccount && unitOfMeasure != null) {
      throw new IllegalArgumentException(
          "Only inventory account declarations may carry one unitOfMeasure.");
    }
  }

  /** Returns the doctrinal journal side that increases this account. */
  public dev.erst.fingrind.core.NormalBalance normalBalance() {
    return AccountTaxonomyDoctrine.normalBalance(accountType, accountTaxonomy);
  }
}
