package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountTaxonomyDoctrine;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.UnitOfMeasure;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Application command that replaces the mutable definition of one unreferenced account. */
public record AmendAccountCommand(
    AccountCode accountCode,
    AccountName accountName,
    AccountType accountType,
    AccountTaxonomy accountTaxonomy,
    @Nullable UnitOfMeasure unitOfMeasure) {
  /** Convenience constructor for a non-inventory account amendment. */
  public AmendAccountCommand(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountTaxonomy accountTaxonomy) {
    this(accountCode, accountName, accountType, accountTaxonomy, null);
  }

  /** Validates one account amendment. */
  public AmendAccountCommand {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(accountName, "accountName");
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(accountTaxonomy, "accountTaxonomy");
    AccountTaxonomyDoctrine.validate(accountType, accountTaxonomy);
    boolean inventoryAccount =
        AccountRole.from(accountType, accountTaxonomy) == AccountRole.INVENTORY;
    if (inventoryAccount && unitOfMeasure == null) {
      throw new IllegalArgumentException("Inventory account amendments require one unitOfMeasure.");
    }
    if (!inventoryAccount && unitOfMeasure != null) {
      throw new IllegalArgumentException(
          "Only inventory account amendments may carry one unitOfMeasure.");
    }
  }
}
