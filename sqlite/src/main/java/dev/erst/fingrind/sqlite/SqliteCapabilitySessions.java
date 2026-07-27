package dev.erst.fingrind.sqlite;

/** Capability-specific SQLite session wrappers over one internal session core. */
final class SqliteCapabilitySessions {
  private SqliteCapabilitySessions() {}

  static SqliteAdministrationSession administration(SqlitePostingFactStore store) {
    return new SqliteAdministrationCapabilitySession(store);
  }

  static SqliteReadSession read(SqlitePostingFactStore store) {
    return new SqliteReadCapabilitySession(store);
  }

  static SqlitePostingSession posting(SqlitePostingFactStore store) {
    return new SqlitePostingCapabilitySession(store);
  }

  static SqliteReportingPeriodCloseSession reportingPeriodClose(SqlitePostingFactStore store) {
    return new SqliteReportingPeriodCloseCapabilitySession(store);
  }

  static SqlitePlanExecutionSession planExecution(SqlitePostingFactStore store) {
    return new SqlitePlanExecutionCapabilitySession(store);
  }

  static SqlitePlanReadOnlySession planReadOnly(SqlitePostingFactStore store) {
    return new SqlitePlanReadOnlyCapabilitySession(store);
  }
}
