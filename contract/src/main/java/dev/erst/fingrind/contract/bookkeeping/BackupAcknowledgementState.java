package dev.erst.fingrind.contract.bookkeeping;

/** Exact disposition of a published backup's source-book acknowledgement attempt. */
public enum BackupAcknowledgementState {
  /** This invocation appended the new backup-created attestation operation. */
  ACKNOWLEDGED("acknowledged", true, false),
  /**
   * An explicit resume completed the acknowledgement; the exact operation may have been appended by
   * this invocation or already have been present.
   */
  RESUMED("resumed", false, false),
  /** The exact acknowledgement operation was already present, so this invocation appended none. */
  ALREADY_PRESENT("already-present", false, true);

  private final String wireValue;
  private final boolean requiresAttestationCommit;
  private final boolean prohibitsAttestationCommit;

  BackupAcknowledgementState(
      String wireValue, boolean requiresAttestationCommit, boolean prohibitsAttestationCommit) {
    this.wireValue = wireValue;
    this.requiresAttestationCommit = requiresAttestationCommit;
    this.prohibitsAttestationCommit = prohibitsAttestationCommit;
  }

  /** Returns the stable lowercase public token. */
  public String wireValue() {
    return wireValue;
  }

  boolean requiresAttestationCommit() {
    return requiresAttestationCommit;
  }

  boolean prohibitsAttestationCommit() {
    return prohibitsAttestationCommit;
  }
}
