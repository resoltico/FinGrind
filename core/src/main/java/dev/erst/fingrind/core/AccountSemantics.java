package dev.erst.fingrind.core;

import java.util.Objects;

/** Canonical doctrinal owner for account polarity, temporary-account behavior, and close policy. */
public final class AccountSemantics {
  private AccountSemantics() {}

  /** Validates the declared role for one account classification. */
  public static void validate(AccountType accountType, AccountRole accountRole) {
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(accountRole, "accountRole");
    if (accountRole == AccountRole.RETAINED_EARNINGS && accountType != AccountType.EQUITY) {
      throw new IllegalArgumentException("RETAINED_EARNINGS accounts must use accountType EQUITY.");
    }
  }

  /** Returns the canonical journal side that increases this account. */
  public static NormalBalance normalBalance(AccountType accountType, AccountRole accountRole) {
    validate(accountType, accountRole);
    return switch (accountRole) {
      case ORDINARY -> ordinaryNormalBalance(accountType);
      case CONTRA -> opposite(ordinaryNormalBalance(accountType));
      case RETAINED_EARNINGS -> NormalBalance.CREDIT;
    };
  }

  /** Returns whether the account participates in period-close temporary-balance clearing. */
  public static boolean closesIntoRetainedEarnings(AccountType accountType) {
    return switch (Objects.requireNonNull(accountType, "accountType")) {
      case REVENUE, EXPENSE -> true;
      case ASSET, LIABILITY, EQUITY -> false;
    };
  }

  /**
   * Returns the signed contribution of one balance to current-period profit or loss.
   *
   * <p>Positive values increase profit; negative values reduce profit.
   */
  public static long profitAndLossContributionMinorUnits(
      AccountType accountType, AccountRole accountRole, BalanceSide balanceSide, long amountMinor) {
    validate(accountType, accountRole);
    Objects.requireNonNull(balanceSide, "balanceSide");
    if (!closesIntoRetainedEarnings(accountType)) {
      throw new IllegalArgumentException(
          "Only REVENUE and EXPENSE accounts contribute to current-period profit or loss.");
    }
    if (amountMinor < 0) {
      throw new IllegalArgumentException("amountMinor must not be negative.");
    }
    long naturalSigned =
        matchesNormalBalance(balanceSide, normalBalance(accountType, accountRole))
            ? amountMinor
            : -amountMinor;
    long result = accountType == AccountType.REVENUE ? naturalSigned : -naturalSigned;
    return accountRole == AccountRole.CONTRA ? Math.negateExact(result) : result;
  }

  private static NormalBalance ordinaryNormalBalance(AccountType accountType) {
    return switch (Objects.requireNonNull(accountType, "accountType")) {
      case ASSET, EXPENSE -> NormalBalance.DEBIT;
      case LIABILITY, EQUITY, REVENUE -> NormalBalance.CREDIT;
    };
  }

  private static NormalBalance opposite(NormalBalance normalBalance) {
    return switch (Objects.requireNonNull(normalBalance, "normalBalance")) {
      case DEBIT -> NormalBalance.CREDIT;
      case CREDIT -> NormalBalance.DEBIT;
    };
  }

  private static boolean matchesNormalBalance(
      BalanceSide balanceSide, NormalBalance normalBalance) {
    return switch (Objects.requireNonNull(balanceSide, "balanceSide")) {
      case DEBIT -> normalBalance == NormalBalance.DEBIT;
      case CREDIT -> normalBalance == NormalBalance.CREDIT;
      case ZERO -> false;
    };
  }
}
