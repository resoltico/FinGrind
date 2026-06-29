package dev.erst.fingrind.contract.fx;

import dev.erst.fingrind.core.WireValue;
import java.util.List;

/** Canonical treatment vocabulary for owned foreign-exchange accounting facts. */
public enum ForeignExchangeTreatmentKind implements WireValue {
  SPOT_SETTLEMENT,
  REALIZED_SETTLEMENT,
  UNREALIZED_REMEASUREMENT;

  /** Returns the stable public wire value for this treatment kind. */
  @Override
  public String wireValue() {
    return switch (this) {
      case SPOT_SETTLEMENT -> "SPOT_SETTLEMENT";
      case REALIZED_SETTLEMENT -> "REALIZED_SETTLEMENT";
      case UNREALIZED_REMEASUREMENT -> "UNREALIZED_REMEASUREMENT";
    };
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(ForeignExchangeTreatmentKind.class);
  }

  /** Parses one stable public wire value. */
  public static ForeignExchangeTreatmentKind fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        ForeignExchangeTreatmentKind.class,
        wireValue,
        "Unsupported foreign-exchange treatment kind");
  }
}
