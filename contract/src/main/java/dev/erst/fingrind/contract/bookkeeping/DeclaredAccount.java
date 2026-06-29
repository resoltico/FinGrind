package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountTaxonomyDoctrine;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.NormalBalance;
import java.time.Instant;
import java.util.Objects;

/** One account currently declared in a book-local registry. */
public record DeclaredAccount(
    AccountCode accountCode,
    AccountName accountName,
    AccountType accountType,
    AccountTaxonomy accountTaxonomy,
    boolean active,
    Instant declaredAt) {
  /** Validates one declared-account snapshot. */
  public DeclaredAccount {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(accountName, "accountName");
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(accountTaxonomy, "accountTaxonomy");
    Objects.requireNonNull(declaredAt, "declaredAt");
    AccountTaxonomyDoctrine.validate(accountType, accountTaxonomy);
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
