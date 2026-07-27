package dev.erst.fingrind.sqlite;

/** SQLite wrapper that exposes only read operations and the read-only plan transaction boundary. */
final class SqlitePlanReadOnlyCapabilitySession extends SqliteReadCapabilitySession
    implements SqlitePlanReadOnlyCapabilityView {
  SqlitePlanReadOnlyCapabilitySession(SqlitePostingFactStore store) {
    super(store);
  }
}
