package dev.erst.fingrind.contract;

/** Journal verbosity requested for a ledger-plan execution. */
public enum LedgerJournalLevel {
  /** Include final plan and per-step outcomes. */
  SUMMARY,
  /** Include outcomes plus the compact facts needed by an AI agent to continue safely. */
  NORMAL,
  /** Include all currently available execution facts. */
  VERBOSE
}
