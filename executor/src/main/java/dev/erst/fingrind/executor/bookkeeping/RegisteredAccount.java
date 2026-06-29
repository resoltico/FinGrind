package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountTaxonomyDoctrine;
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
    AccountTaxonomy accountTaxonomy,
    boolean active,
    Instant declaredAt) {
  /** Validates one registered-account snapshot. */
  public RegisteredAccount {
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

  /**
   * Declares one account from the current registry state.
   *
   * <p>The bookkeeping invariant is local to one account identity: a redeclaration may reactivate
   * an account and update the display name, but it may not change the normal balance.
   */
  public static RegisteredAccount declareNew(AccountDeclaration declaration, Instant declaredAt) {
    Objects.requireNonNull(declaration, "declaration");
    Objects.requireNonNull(declaredAt, "declaredAt");
    return new RegisteredAccount(
        declaration.accountCode(),
        declaration.accountName(),
        declaration.accountType(),
        declaration.accountTaxonomy(),
        true,
        declaredAt);
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
      return new AccountDeclarationOutcome.Declared(declareNew(declaration, declaredAt));
    }
    if (existingAccount.accountType() != declaration.accountType()) {
      return new AccountDeclarationOutcome.Rejected(
          new BookkeepingAdministrationRejection.AccountTypeConflict(
              declaration.accountCode(), existingAccount.accountType(), declaration.accountType()));
    }
    if (!existingAccount.accountTaxonomy().equals(declaration.accountTaxonomy())) {
      return new AccountDeclarationOutcome.Rejected(
          new BookkeepingAdministrationRejection.AccountTaxonomyConflict(
              declaration.accountCode(),
              existingAccount.accountTaxonomy(),
              declaration.accountTaxonomy()));
    }
    RegisteredAccount requestedState =
        new RegisteredAccount(
            existingAccount.accountCode(),
            declaration.accountName(),
            existingAccount.accountType(),
            existingAccount.accountTaxonomy(),
            true,
            existingAccount.declaredAt());
    if (!existingAccount.active()) {
      return new AccountDeclarationOutcome.Reactivated(requestedState);
    }
    if (!existingAccount.accountName().equals(declaration.accountName())) {
      return new AccountDeclarationOutcome.Renamed(requestedState);
    }
    return new AccountDeclarationOutcome.Unchanged(existingAccount);
  }
}
