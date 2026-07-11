package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountTaxonomyDoctrine;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.UnitOfMeasure;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** One account currently declared in a book-local registry. */
public record DeclaredAccount(
    AccountCode accountCode,
    AccountName accountName,
    AccountType accountType,
    AccountTaxonomy accountTaxonomy,
    @Nullable UnitOfMeasure unitOfMeasure,
    boolean active,
    Instant declaredAt) {
  /** Convenience constructor for non-inventory account snapshots. */
  public DeclaredAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountTaxonomy accountTaxonomy,
      boolean active,
      Instant declaredAt) {
    this(accountCode, accountName, accountType, accountTaxonomy, null, active, declaredAt);
  }

  /** Validates one declared-account snapshot. */
  public DeclaredAccount {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(accountName, "accountName");
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(accountTaxonomy, "accountTaxonomy");
    Objects.requireNonNull(declaredAt, "declaredAt");
    AccountTaxonomyDoctrine.validate(accountType, accountTaxonomy);
    boolean inventoryAccount =
        AccountRole.from(accountType, accountTaxonomy) == AccountRole.INVENTORY;
    if (inventoryAccount && unitOfMeasure == null) {
      throw new IllegalArgumentException("Inventory account snapshots require one unitOfMeasure.");
    }
    if (!inventoryAccount && unitOfMeasure != null) {
      throw new IllegalArgumentException(
          "Only inventory account snapshots may carry one unitOfMeasure.");
    }
  }

  /** Returns the doctrinal journal side that increases this account. */
  public NormalBalance normalBalance() {
    return AccountTaxonomyDoctrine.normalBalance(accountType, accountTaxonomy);
  }

  /** Returns whether this declared account participates in cash and cash equivalents. */
  public boolean cashAndCashEquivalent() {
    return AccountTaxonomyDoctrine.cashAndCashEquivalent(accountType, accountTaxonomy);
  }
}
