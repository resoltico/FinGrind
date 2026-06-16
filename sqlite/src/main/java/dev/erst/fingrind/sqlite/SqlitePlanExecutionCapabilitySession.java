package dev.erst.fingrind.sqlite;

/** Ledger-plan wrapper that adds plan-transaction control to posting capabilities. */
final class SqlitePlanExecutionCapabilitySession extends SqlitePostingCapabilitySession
    implements SqlitePlanExecutionCapabilityView {
  SqlitePlanExecutionCapabilitySession(SqlitePostingFactStore store) {
    super(store);
  }
}
