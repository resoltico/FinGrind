package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRegistryDependency;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Owns admissibility and state transitions for durable Account Registry lifecycle actions. */
public final class AccountRegistryLifecyclePolicy {
  private AccountRegistryLifecyclePolicy() {}

  /** Amends an account only when no durable relationship depends on its current taxonomy. */
  public static AccountAmendmentDecision amend(
      @Nullable RegisteredAccount existingAccount,
      AccountDeclaration amendment,
      List<AccountRegistryDependency> dependencies) {
    Objects.requireNonNull(amendment, "amendment");
    List<AccountRegistryDependency> requiredDependencies = copyDependencies(dependencies);
    if (existingAccount == null) {
      return new AccountAmendmentDecision.Rejected(
          new AccountRegistryLifecycleRejection.AccountNotFound(amendment.accountCode()));
    }
    if (!requiredDependencies.isEmpty()) {
      return new AccountAmendmentDecision.Rejected(
          new AccountRegistryLifecycleRejection.AccountHasDependents(
              existingAccount.accountCode(), requiredDependencies));
    }
    RegisteredAccount amended = RegisteredAccount.amend(existingAccount, amendment);
    return amended.equals(existingAccount)
        ? new AccountAmendmentDecision.Unchanged(existingAccount)
        : new AccountAmendmentDecision.Amended(amended);
  }

  /** Retires an account only when its current balance is zero and no live binding remains. */
  public static AccountRetirementDecision retire(
      AccountCode accountCode,
      @Nullable RegisteredAccount existingAccount,
      List<AccountRegistryDependency> operationalDependencies,
      boolean currentBalanceZero) {
    Objects.requireNonNull(accountCode, "accountCode");
    List<AccountRegistryDependency> requiredDependencies =
        copyDependencies(operationalDependencies);
    if (existingAccount == null) {
      return new AccountRetirementDecision.Rejected(
          new AccountRegistryLifecycleRejection.AccountNotFound(accountCode));
    }
    if (!existingAccount.active()) {
      return new AccountRetirementDecision.Unchanged(existingAccount);
    }
    if (!currentBalanceZero) {
      return new AccountRetirementDecision.Rejected(
          new AccountRegistryLifecycleRejection.AccountBalanceNotZero(
              existingAccount.accountCode()));
    }
    if (!requiredDependencies.isEmpty()) {
      return new AccountRetirementDecision.Rejected(
          new AccountRegistryLifecycleRejection.AccountHasDependents(
              existingAccount.accountCode(), requiredDependencies));
    }
    return new AccountRetirementDecision.Retired(existingAccount.retire());
  }

  private static List<AccountRegistryDependency> copyDependencies(
      List<AccountRegistryDependency> dependencies) {
    return List.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
  }
}
