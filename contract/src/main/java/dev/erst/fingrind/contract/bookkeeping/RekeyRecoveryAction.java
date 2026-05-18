package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.WireValue;
import java.util.List;

/** Stable CLI-visible actions supported by the recover-rekey maintenance workflow. */
public enum RekeyRecoveryAction implements WireValue {
  /** List sibling rollback artifacts without mutating the book path. */
  INSPECT,
  /** Replace the live book path with one selected rollback artifact. */
  RESTORE,
  /** Delete one selected rollback artifact without changing the live book path. */
  DELETE;

  @Override
  public String wireValue() {
    return switch (this) {
      case INSPECT -> "inspect";
      case RESTORE -> "restore";
      case DELETE -> "delete";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(RekeyRecoveryAction.class);
  }

  /** Parses one stable public wire value. */
  public static RekeyRecoveryAction fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        RekeyRecoveryAction.class, wireValue, "Unsupported rekey recovery action");
  }
}
