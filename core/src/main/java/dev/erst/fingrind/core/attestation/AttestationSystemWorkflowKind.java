package dev.erst.fingrind.core.attestation;

import java.util.Locale;

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

  static AttestationSystemWorkflowKind forWireToken(
      String wireToken, AttestationAuthorizationFailure failure) {
    for (AttestationSystemWorkflowKind workflowKind : values()) {
      if (workflowKind.wireToken().equals(wireToken)) {
        return workflowKind;
      }
    }
    throw new AttestationAuthorizationException(failure);
  }

  String wireToken() {
    return name().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
