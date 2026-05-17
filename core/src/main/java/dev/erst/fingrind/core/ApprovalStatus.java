package dev.erst.fingrind.core;

import java.util.List;

/** Canonical approval-state vocabulary for source evidence and business-event authorization. */
public enum ApprovalStatus implements WireValue {
  APPROVED,
  REJECTED,
  PENDING,
  NOT_REQUIRED;

  @Override
  public String wireValue() {
    return switch (this) {
      case APPROVED -> "APPROVED";
      case REJECTED -> "REJECTED";
      case PENDING -> "PENDING";
      case NOT_REQUIRED -> "NOT_REQUIRED";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(ApprovalStatus.class);
  }

  /** Parses one stable public wire value. */
  public static ApprovalStatus fromWireValue(String wireValue) {
    return WireValue.fromWireValue(ApprovalStatus.class, wireValue, "Unsupported approvalStatus");
  }
}
