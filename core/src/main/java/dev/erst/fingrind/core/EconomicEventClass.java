package dev.erst.fingrind.core;

import java.util.List;

/** Total classification vocabulary for resolved journals. */
public enum EconomicEventClass implements WireValue {
  SETTLED_SALE(true),
  CREDIT_SALE(true),
  SETTLED_PURCHASE(true),
  CREDIT_PURCHASE(true),
  INVENTORY_CAPITALIZATION(true),
  INVENTORY_WRITE_DOWN(true),
  INVENTORY_SHRINKAGE(true),
  INVENTORY_COUNT_INCREASE(true),
  SETTLED_EXPENSE(true),
  CREDIT_EXPENSE(true),
  AR_SETTLEMENT(true),
  AP_SETTLEMENT(true),
  OWNER_CONTRIBUTION(true),
  OWNER_WITHDRAWAL(true),
  OPENING(true),
  REVERSAL(true),
  COMPOUND_OPERATIONAL(false),
  ADJUSTMENT(false);

  private final boolean typedSingleton;

  EconomicEventClass(boolean typedSingleton) {
    this.typedSingleton = typedSingleton;
  }

  /** Returns whether this class represents one typed operational or structural singleton. */
  public boolean typedSingleton() {
    return typedSingleton;
  }

  @Override
  public String wireValue() {
    return name();
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(EconomicEventClass.class);
  }

  /** Parses one stable wire value. */
  public static EconomicEventClass fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        EconomicEventClass.class, wireValue, "Unsupported economicEventClass");
  }
}
