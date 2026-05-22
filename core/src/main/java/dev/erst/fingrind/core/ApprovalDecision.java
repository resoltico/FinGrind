package dev.erst.fingrind.core;

import java.util.List;

/** Canonical approval decision retained as part of posting evidence. */
public enum ApprovalDecision implements WireValue {
  APPROVED,
  REJECTED;

  @Override
  public String wireValue() {
    return switch (this) {
      case APPROVED -> "APPROVED";
      case REJECTED -> "REJECTED";
    };
  }

  /** Returns every stable approval-decision wire value. */
  public static List<String> wireValues() {
    return WireValue.wireValues(ApprovalDecision.class);
  }

  /** Parses one stable approval-decision wire value. */
  public static ApprovalDecision fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        ApprovalDecision.class, wireValue, "Unsupported approvalDecision");
  }
}
