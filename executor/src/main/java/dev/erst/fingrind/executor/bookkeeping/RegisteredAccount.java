package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Internal bookkeeping account-registry aggregate snapshot. */
public record RegisteredAccount(
    AccountCode accountCode,
    AccountName accountName,
    NormalBalance normalBalance,
    boolean active,
    Instant declaredAt) {
  /** Validates one registered-account snapshot. */
  public RegisteredAccount {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(accountName, "accountName");
    Objects.requireNonNull(normalBalance, "normalBalance");
    Objects.requireNonNull(declaredAt, "declaredAt");
  }

  /**
   * Declares one account from the current registry state.
   *
   * <p>The bookkeeping invariant is local to one account identity: a redeclaration may reactivate
   * an account and update the display name, but it may not change the normal balance.
   */
  public static AccountDeclarationOutcome declare(
      @Nullable RegisteredAccount existingAccount,
      AccountDeclaration declaration,
      Instant declaredAt) {
    Objects.requireNonNull(declaration, "declaration");
    Objects.requireNonNull(declaredAt, "declaredAt");
    if (existingAccount == null) {
      return new AccountDeclarationOutcome.Declared(
          new RegisteredAccount(
              declaration.accountCode(),
              declaration.accountName(),
              declaration.normalBalance(),
              true,
              declaredAt));
    }
    if (existingAccount.normalBalance() != declaration.normalBalance()) {
      return new AccountDeclarationOutcome.Rejected(
          new BookkeepingAdministrationRejection.NormalBalanceConflict(
              declaration.accountCode(),
              existingAccount.normalBalance(),
              declaration.normalBalance()));
    }
    return new AccountDeclarationOutcome.Declared(
        new RegisteredAccount(
            existingAccount.accountCode(),
            declaration.accountName(),
            existingAccount.normalBalance(),
            true,
            declaredAt));
  }
}
