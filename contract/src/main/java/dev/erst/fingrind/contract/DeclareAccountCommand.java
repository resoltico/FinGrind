package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import java.util.Objects;

/** Application command for declaring or reactivating one ledger account in a book. */
public record DeclareAccountCommand(
    AccountCode accountCode, AccountName accountName, NormalBalance normalBalance) {
  /** Validates one account-declaration command. */
  public DeclareAccountCommand {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(accountName, "accountName");
    Objects.requireNonNull(normalBalance, "normalBalance");
  }
}
