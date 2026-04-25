package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Canonical public success-status tokens emitted by FinGrind JSON envelopes. */
public enum ProtocolSuccessStatus implements WireValue {
  /** Generic discovery, administration, and query success status. */
  OK("ok"),
  /** Single posting preflight success status. */
  PREFLIGHT_ACCEPTED("preflight-accepted"),
  /** Single posting durable commit success status. */
  COMMITTED("committed"),
  /** Ledger-plan durable commit success status. */
  PLAN_COMMITTED("plan-committed");

  private final String wireValue;

  ProtocolSuccessStatus(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  /** Returns the stable machine-readable value for this success status. */
  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable success-status token in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(ProtocolSuccessStatus.class);
  }

  /** Parses one stable success-status token. */
  public static ProtocolSuccessStatus fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        ProtocolSuccessStatus.class, wireValue, "Unsupported protocol success status");
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
