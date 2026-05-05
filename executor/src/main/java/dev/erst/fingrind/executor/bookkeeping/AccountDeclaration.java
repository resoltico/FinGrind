package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import java.util.Objects;

/** Internal bookkeeping command for declaring or reactivating one account. */
public record AccountDeclaration(
    AccountCode accountCode, AccountName accountName, NormalBalance normalBalance) {
  /** Validates one bookkeeping account declaration. */
  public AccountDeclaration {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(accountName, "accountName");
    Objects.requireNonNull(normalBalance, "normalBalance");
  }
}
