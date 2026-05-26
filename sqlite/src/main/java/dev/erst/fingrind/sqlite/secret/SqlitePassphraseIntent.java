package dev.erst.fingrind.sqlite.secret;

/** Intent for resolving one SQLite book secret through a public passphrase resolver seam. */
public enum SqlitePassphraseIntent {
  /** Resolve the existing secret for reading or mutating a previously initialized book. */
  EXISTING_SECRET,

  /** Resolve a newly chosen secret that should be confirmed before durable use. */
  NEW_SECRET
}
