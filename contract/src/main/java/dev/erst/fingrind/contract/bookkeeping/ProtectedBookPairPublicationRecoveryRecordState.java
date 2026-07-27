package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.WireValue;
import java.util.List;

/** State of recovery-record evidence when no protected-book pair member was attempted. */
public enum ProtectedBookPairPublicationRecoveryRecordState implements WireValue {
  /**
   * A complete recovery record was force-confirmed before FinGrind refused a final-member
   * precondition, so neither final member was attempted.
   */
  DURABLY_RETAINED,

  /**
   * The live commit wrote and promoted the complete candidate, but forcing its parent directory did
   * not complete before any final-member primitive was invoked.
   */
  DURABILITY_UNCONFIRMED;

  @Override
  public String wireValue() {
    return switch (this) {
      case DURABLY_RETAINED -> "durably-retained";
      case DURABILITY_UNCONFIRMED -> "durability-unconfirmed";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(ProtectedBookPairPublicationRecoveryRecordState.class);
  }
}
