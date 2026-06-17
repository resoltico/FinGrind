package dev.erst.fingrind.sqlite;

/** Intent for resolving one SQLite book secret through a public passphrase resolver seam. */
public enum SqlitePassphraseIntent {
  /** Resolve the existing secret for reading or mutating a previously initialized book. */
  EXISTING_SECRET,

  /**
   * Resolve one secret for replay-safe setup: confirm it for a missing book path and reuse it for
   * an existing protected book path.
   */
  PLAN_SETUP_SECRET,

  /** Resolve a newly chosen secret that should be confirmed before durable use. */
  NEW_SECRET
}
