package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountSemantics;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.time.Instant;
import java.util.Objects;

/** Shared test-only helpers for expressing legacy normal-balance fixtures in account-role terms. */
public final class ExecutorAccountingTestSupport {
  private ExecutorAccountingTestSupport() {}

  /**
   * Derives the doctrinal role implied by one legacy fixture balance.
   *
   * <p>Tests that need retained earnings must request {@link AccountRole#RETAINED_EARNINGS}
   * explicitly rather than relying on this ordinary/contra projection.
   */
  public static AccountRole accountRole(AccountType accountType, NormalBalance normalBalance) {
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(normalBalance, "normalBalance");
    return AccountSemantics.normalBalance(accountType, AccountRole.ORDINARY) == normalBalance
        ? AccountRole.ORDINARY
        : AccountRole.CONTRA;
  }

  /** Builds one published declared-account snapshot from a legacy normal-balance fixture. */
  public static DeclaredAccount declaredAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      NormalBalance normalBalance,
      boolean active,
      Instant declaredAt) {
    return new DeclaredAccount(
        accountCode,
        accountName,
        accountType,
        accountRole(accountType, normalBalance),
        active,
        declaredAt);
  }

  /** Builds one local registered-account snapshot from a legacy normal-balance fixture. */
  public static RegisteredAccount registeredAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      NormalBalance normalBalance,
      boolean active,
      Instant declaredAt) {
    return new RegisteredAccount(
        accountCode,
        accountName,
        accountType,
        accountRole(accountType, normalBalance),
        active,
        declaredAt);
  }
}
