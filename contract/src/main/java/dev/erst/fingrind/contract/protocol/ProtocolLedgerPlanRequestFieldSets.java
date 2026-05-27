package dev.erst.fingrind.contract.protocol;

import java.util.Set;

/** Canonical executable ledger-plan request-field sets. */
public final class ProtocolLedgerPlanRequestFieldSets {
  private static final Set<String> LEDGER_PLAN_FIELDS =
      Set.copyOf(ProtocolLedgerPlanFields.planFields());
  private static final Set<String> LEDGER_STEP_FIELDS =
      Set.copyOf(ProtocolLedgerPlanFields.stepFields());
  private static final Set<String> LEDGER_QUERY_FIELDS =
      Set.copyOf(ProtocolLedgerPlanFields.queryFields());
  private static final Set<String> LEDGER_ASSERTION_FIELDS =
      Set.copyOf(ProtocolLedgerPlanFields.assertionFields());

  private ProtocolLedgerPlanRequestFieldSets() {}

  /** Returns the accepted top-level fields for ledger-plan documents. */
  public static Set<String> ledgerPlanFields() {
    return LEDGER_PLAN_FIELDS;
  }

  /** Returns the accepted nested fields for ledger-plan step objects. */
  public static Set<String> ledgerStepFields() {
    return LEDGER_STEP_FIELDS;
  }

  /** Returns the accepted nested fields for ledger-plan query objects. */
  public static Set<String> ledgerQueryFields() {
    return LEDGER_QUERY_FIELDS;
  }

  /** Returns the accepted nested fields for ledger-plan assertion objects. */
  public static Set<String> ledgerAssertionFields() {
    return LEDGER_ASSERTION_FIELDS;
  }
}
