package dev.erst.fingrind.core;

import java.util.List;

/** Canonical statement-of-financial-position taxonomy for one declared account. */
public enum FinancialPositionLineClassification implements WireValue {
  CURRENT_ASSET(AccountType.ASSET, NormalBalance.DEBIT, AccountRole.AUX, false),
  INVENTORY(AccountType.ASSET, NormalBalance.DEBIT, AccountRole.INVENTORY, false),
  NONCURRENT_ASSET(AccountType.ASSET, NormalBalance.DEBIT, AccountRole.AUX, false),
  TRADE_RECEIVABLE(AccountType.ASSET, NormalBalance.DEBIT, AccountRole.RECEIVABLE, false),
  CURRENT_LIABILITY(AccountType.LIABILITY, NormalBalance.CREDIT, AccountRole.AUX, false),
  NONCURRENT_LIABILITY(AccountType.LIABILITY, NormalBalance.CREDIT, AccountRole.AUX, false),
  TRADE_PAYABLE(AccountType.LIABILITY, NormalBalance.CREDIT, AccountRole.PAYABLE, false),
  EQUITY_CONTRIBUTION(
      AccountType.EQUITY, NormalBalance.CREDIT, AccountRole.EQUITY_CONTRIBUTED, false),
  EQUITY_WITHDRAWAL(AccountType.EQUITY, NormalBalance.DEBIT, AccountRole.EQUITY_DRAWS, false),
  RESULT_HOLDING(AccountType.EQUITY, NormalBalance.CREDIT, AccountRole.AUX, true),
  RETAINED_ACCUMULATED(AccountType.EQUITY, NormalBalance.CREDIT, AccountRole.AUX, true),
  RESERVE(AccountType.EQUITY, NormalBalance.CREDIT, AccountRole.AUX, false),
  OTHER_EQUITY(AccountType.EQUITY, NormalBalance.CREDIT, AccountRole.AUX, false);

  private final AccountType accountType;
  private final NormalBalance normalBalance;
  private final AccountRole classifierRole;
  private final boolean reservedForCloseOperations;

  FinancialPositionLineClassification(
      AccountType accountType,
      NormalBalance normalBalance,
      AccountRole classifierRole,
      boolean reservedForCloseOperations) {
    this.accountType = accountType;
    this.normalBalance = normalBalance;
    this.classifierRole = classifierRole;
    this.reservedForCloseOperations = reservedForCloseOperations;
  }

  /** Returns the canonical account type this classification belongs to. */
  public AccountType accountType() {
    return accountType;
  }

  /** Returns the normal balance implied by this declared financial-position classification. */
  public NormalBalance normalBalance() {
    return normalBalance;
  }

  /** Returns the stable public wire value for this classification. */
  @Override
  public String wireValue() {
    return name();
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(FinancialPositionLineClassification.class);
  }

  /** Returns the public wire values that are valid for declared account taxonomy. */
  public static List<String> declaredAccountWireValues() {
    return wireValues();
  }

  /** Parses one stable public wire value. */
  public static FinancialPositionLineClassification fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        FinancialPositionLineClassification.class,
        wireValue,
        "Unsupported financialPositionLineClassification");
  }

  AccountRole classifierRole() {
    return classifierRole;
  }

  boolean reservedForCloseOperations() {
    return reservedForCloseOperations;
  }
}
