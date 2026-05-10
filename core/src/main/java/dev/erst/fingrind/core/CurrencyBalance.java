package dev.erst.fingrind.core;

import java.util.Objects;

/** Exact per-currency grouped balance bucket derived from debit and credit totals. */
public final class CurrencyBalance {
  private final Money debitTotal;
  private final Money creditTotal;
  private final Money netAmount;
  private final BalanceSide balanceSide;

  private CurrencyBalance(
      Money debitTotal, Money creditTotal, Money netAmount, BalanceSide balanceSide) {
    this.debitTotal = Objects.requireNonNull(debitTotal, "debitTotal");
    this.creditTotal = Objects.requireNonNull(creditTotal, "creditTotal");
    this.netAmount = Objects.requireNonNull(netAmount, "netAmount");
    this.balanceSide = Objects.requireNonNull(balanceSide, "balanceSide");
  }

  /** Derives one consistent balance bucket from debit and credit totals in one currency unit. */
  public static CurrencyBalance ofTotals(Money debitTotal, Money creditTotal) {
    Objects.requireNonNull(debitTotal, "debitTotal");
    Objects.requireNonNull(creditTotal, "creditTotal");
    if (!debitTotal.currencyUnit().equals(creditTotal.currencyUnit())) {
      throw new IllegalArgumentException("Currency balance totals must share one currency unit.");
    }
    Money netAmount;
    BalanceSide balanceSide;
    int comparison = debitTotal.compareTo(creditTotal);
    if (comparison > 0) {
      netAmount = debitTotal.minus(creditTotal);
      balanceSide = BalanceSide.DEBIT;
    } else if (comparison < 0) {
      netAmount = creditTotal.minus(debitTotal);
      balanceSide = BalanceSide.CREDIT;
    } else {
      netAmount = Money.zero(debitTotal.currencyUnit());
      balanceSide = BalanceSide.ZERO;
    }
    return new CurrencyBalance(debitTotal, creditTotal, netAmount, balanceSide);
  }

  /** Returns the grouped debit total. */
  public Money debitTotal() {
    return debitTotal;
  }

  /** Returns the grouped credit total. */
  public Money creditTotal() {
    return creditTotal;
  }

  /** Returns the absolute net amount. */
  public Money netAmount() {
    return netAmount;
  }

  /** Returns the sign carrier for the net amount. */
  public BalanceSide balanceSide() {
    return balanceSide;
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || (other instanceof CurrencyBalance that
            && debitTotal.equals(that.debitTotal)
            && creditTotal.equals(that.creditTotal));
  }

  @Override
  public int hashCode() {
    return Objects.hash(debitTotal, creditTotal);
  }

  @Override
  public String toString() {
    return "CurrencyBalance[debitTotal="
        + debitTotal
        + ", creditTotal="
        + creditTotal
        + ", netAmount="
        + netAmount
        + ", balanceSide="
        + balanceSide
        + "]";
  }
}
