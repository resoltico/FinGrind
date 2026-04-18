package dev.erst.fingrind.contract.protocol;

/** Canonical public response status tokens emitted by FinGrind JSON envelopes. */
public final class ProtocolStatuses {
  /** Generic discovery, administration, and query success status. */
  public static final String OK = "ok";

  /** Single posting preflight success status. */
  public static final String PREFLIGHT_ACCEPTED = "preflight-accepted";

  /** Single posting durable commit success status. */
  public static final String COMMITTED = "committed";

  /** Ledger-plan durable commit success status. */
  public static final String PLAN_COMMITTED = "plan-committed";

  /** Ledger-plan deterministic rejection status. */
  public static final String PLAN_REJECTED = "plan-rejected";

  /** Ledger-plan assertion failure status. */
  public static final String PLAN_ASSERTION_FAILED = "plan-assertion-failed";

  /** Deterministic domain rejection status. */
  public static final String REJECTED = "rejected";

  /** Runtime, invocation, or invalid-request failure status. */
  public static final String ERROR = "error";

  private ProtocolStatuses() {}
}
