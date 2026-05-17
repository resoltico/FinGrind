package dev.erst.fingrind.core;

import java.util.List;

/** Canonical provenance vocabulary for one exchange-rate observation. */
public enum ExchangeRateSourceKind implements WireValue {
  MANUAL,
  ECB,
  BANK,
  PLATFORM,
  CONTRACT,
  INVOICE,
  OTHER;

  @Override
  public String wireValue() {
    return switch (this) {
      case MANUAL -> "MANUAL";
      case ECB -> "ECB";
      case BANK -> "BANK";
      case PLATFORM -> "PLATFORM";
      case CONTRACT -> "CONTRACT";
      case INVOICE -> "INVOICE";
      case OTHER -> "OTHER";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(ExchangeRateSourceKind.class);
  }

  /** Parses one stable public wire value. */
  public static ExchangeRateSourceKind fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        ExchangeRateSourceKind.class, wireValue, "Unsupported exchangeRateSourceKind");
  }
}
