package dev.erst.fingrind.core.attestation;

/** Closed autonomous workflow kinds that use the CLOSE_PERIOD capability. */
enum AttestationSystemWorkflowKind {
  INTERIM_RESULT_SWEEP(false),
  FISCAL_YEAR_CLOSE(true);

  private final boolean requiresCapitalAndRetainedResultAccounts;

  AttestationSystemWorkflowKind(boolean requiresCapitalAndRetainedResultAccounts) {
    this.requiresCapitalAndRetainedResultAccounts = requiresCapitalAndRetainedResultAccounts;
  }

  boolean requiresCapitalAndRetainedResultAccounts() {
    return requiresCapitalAndRetainedResultAccounts;
  }
}
