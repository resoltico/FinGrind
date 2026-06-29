package dev.erst.fingrind.core;

import java.util.Objects;

/** Canonical doctrine owner for temporary-account closing and current-period contribution. */
public final class ProfitAndLossAccountDoctrine {
  private ProfitAndLossAccountDoctrine() {}

  /** Returns whether the account type participates in temporary profit-or-loss closing. */
  public static boolean closesTemporaryProfitAndLossAccountType(AccountType accountType) {
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
      AccountType accountType,
      AccountTaxonomy accountTaxonomy,
      BalanceSide balanceSide,
      long amountMinor) {
    AccountTaxonomyDoctrine.validate(accountType, accountTaxonomy);
    Objects.requireNonNull(balanceSide, "balanceSide");
    if (!closesTemporaryProfitAndLossAccountType(accountType)) {
      throw new IllegalArgumentException(
          "Only REVENUE and EXPENSE accounts contribute to current-period profit or loss.");
    }
    if (amountMinor < 0) {
      throw new IllegalArgumentException("amountMinor must not be negative.");
    }
    if (balanceSide == BalanceSide.ZERO) {
      if (amountMinor == 0L) {
        return 0L;
      }
      throw new IllegalArgumentException("ZERO balanceSide requires amountMinor to be zero.");
    }
    long naturalSigned =
        (balanceSide == BalanceSide.DEBIT)
                == (AccountTaxonomyDoctrine.normalBalance(accountType, accountTaxonomy)
                    == NormalBalance.DEBIT)
            ? amountMinor
            : -amountMinor;
    return accountType == AccountType.REVENUE ? naturalSigned : -naturalSigned;
  }
}
