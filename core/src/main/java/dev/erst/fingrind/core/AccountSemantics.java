package dev.erst.fingrind.core;

import java.util.Objects;
import java.util.Optional;

/** Canonical doctrinal owner for account polarity, temporary-account behavior, and close policy. */
public final class AccountSemantics {
  private AccountSemantics() {}

  /** Validates the declared role for one account classification. */
  public static void validate(AccountType accountType, AccountRole accountRole) {
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(accountRole, "accountRole");
  }

  /** Validates one declared account role plus taxonomy for the selected classification. */
  public static void validate(
      AccountType accountType, AccountRole accountRole, AccountTaxonomy accountTaxonomy) {
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(accountRole, "accountRole");
    Objects.requireNonNull(accountTaxonomy, "accountTaxonomy");
    Objects.requireNonNull(accountTaxonomy.nodeKind(), "accountTaxonomy.nodeKind");
    Optional<FinancialPositionLineClassification> financialPositionLineClassification =
        accountTaxonomy.financialPositionLineClassification();
    Optional<ProfitAndLossLineClassification> profitAndLossLineClassification =
        accountTaxonomy.profitAndLossLineClassification();
    if (!closesTemporaryProfitAndLossAccountType(accountType)) {
      if (financialPositionLineClassification.isEmpty()) {
        throw new IllegalArgumentException(
            "Financial-position classification is required for balance-sheet accounts.");
      }
      if (profitAndLossLineClassification.isPresent()) {
        throw new IllegalArgumentException(
            "Profit-and-loss classification must be absent for balance-sheet accounts.");
      }
      FinancialPositionLineClassification declaredClassification =
          financialPositionLineClassification.orElseThrow();
      if (declaredClassification == FinancialPositionLineClassification.CURRENT_PERIOD_RESULT) {
        throw new IllegalArgumentException(
            "CURRENT_PERIOD_RESULT is reserved for derived statement rows and must not be declared on accounts.");
      }
      if (declaredClassification.accountType() != accountType) {
        throw new IllegalArgumentException(
            "Financial-position classification must match the declared accountType.");
      }
      return;
    }
    if (profitAndLossLineClassification.isEmpty()) {
      throw new IllegalArgumentException(
          "Profit-and-loss classification is required for nominal accounts.");
    }
    if (financialPositionLineClassification.isPresent()) {
      throw new IllegalArgumentException(
          "Financial-position classification must be absent for nominal accounts.");
    }
    if (profitAndLossLineClassification.orElseThrow().accountType() != accountType) {
      throw new IllegalArgumentException(
          "Profit-and-loss classification must match the declared accountType.");
    }
  }

  /** Returns whether this taxonomy may accept direct postings. */
  public static boolean allowsPosting(AccountTaxonomy accountTaxonomy) {
    Objects.requireNonNull(accountTaxonomy, "accountTaxonomy");
    return accountTaxonomy.nodeKind().allowsPosting();
  }

  /** Returns whether this taxonomy may own child accounts. */
  public static boolean allowsChildren(AccountTaxonomy accountTaxonomy) {
    Objects.requireNonNull(accountTaxonomy, "accountTaxonomy");
    return accountTaxonomy.nodeKind().allowsChildren();
  }

  /** Returns whether one parent-child hierarchy edge preserves the declared reporting meaning. */
  public static boolean parentChildHierarchyCompatible(
      AccountType accountType,
      AccountRole parentAccountRole,
      AccountTaxonomy parentAccountTaxonomy,
      AccountRole childAccountRole,
      AccountTaxonomy childAccountTaxonomy) {
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(parentAccountRole, "parentAccountRole");
    Objects.requireNonNull(parentAccountTaxonomy, "parentAccountTaxonomy");
    Objects.requireNonNull(childAccountRole, "childAccountRole");
    Objects.requireNonNull(childAccountTaxonomy, "childAccountTaxonomy");
    if (parentAccountRole != childAccountRole) {
      return false;
    }
    return closesTemporaryProfitAndLossAccountType(accountType)
        ? parentAccountTaxonomy
            .profitAndLossLineClassification()
            .equals(childAccountTaxonomy.profitAndLossLineClassification())
        : parentAccountTaxonomy
            .financialPositionLineClassification()
            .equals(childAccountTaxonomy.financialPositionLineClassification());
  }

  /** Returns the canonical journal side that increases this account. */
  public static NormalBalance normalBalance(AccountType accountType, AccountRole accountRole) {
    return switch (accountRole) {
      case ORDINARY -> ordinaryNormalBalance(accountType);
      case CONTRA -> opposite(ordinaryNormalBalance(accountType));
    };
  }

  /** Returns whether the account type participates in temporary profit-or-loss close clearing. */
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
      AccountType accountType, AccountRole accountRole, BalanceSide balanceSide, long amountMinor) {
    validate(accountType, accountRole);
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
                == (normalBalance(accountType, accountRole) == NormalBalance.DEBIT)
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
}
