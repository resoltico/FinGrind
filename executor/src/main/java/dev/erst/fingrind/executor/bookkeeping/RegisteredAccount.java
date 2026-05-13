package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountSemantics;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.NormalBalance;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Internal bookkeeping account-registry aggregate snapshot. */
public record RegisteredAccount(
    AccountCode accountCode,
    AccountName accountName,
    AccountType accountType,
    AccountRole accountRole,
    boolean active,
    Instant declaredAt) {
  /** Validates one registered-account snapshot. */
  public RegisteredAccount {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(accountName, "accountName");
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(accountRole, "accountRole");
    Objects.requireNonNull(declaredAt, "declaredAt");
    AccountSemantics.validate(accountType, accountRole);
  }

  /** Returns the doctrinal journal side that increases this account. */
  public NormalBalance normalBalance() {
    return AccountSemantics.normalBalance(accountType, accountRole);
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
              declaration.accountType(),
              declaration.accountRole(),
              true,
              declaredAt));
    }
    if (existingAccount.accountType() != declaration.accountType()) {
      return new AccountDeclarationOutcome.Rejected(
          new BookkeepingAdministrationRejection.AccountTypeConflict(
              declaration.accountCode(), existingAccount.accountType(), declaration.accountType()));
    }
    if (existingAccount.accountRole() != declaration.accountRole()) {
      return new AccountDeclarationOutcome.Rejected(
          new BookkeepingAdministrationRejection.AccountRoleConflict(
              declaration.accountCode(), existingAccount.accountRole(), declaration.accountRole()));
    }
    return new AccountDeclarationOutcome.Declared(
        new RegisteredAccount(
            existingAccount.accountCode(),
            declaration.accountName(),
            existingAccount.accountType(),
            existingAccount.accountRole(),
            true,
            declaredAt));
  }
}
