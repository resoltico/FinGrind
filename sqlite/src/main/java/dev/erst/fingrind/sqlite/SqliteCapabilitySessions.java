package dev.erst.fingrind.sqlite;

import java.util.Objects;

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

  static SqlitePostingFactStore storeOf(AutoCloseable session) {
    Objects.requireNonNull(session, "session");
    if (session instanceof SqlitePostingFactStore store) {
      return store;
    }
    if (session instanceof SqliteDelegatingSession delegatingSession) {
      return delegatingSession.store;
    }
    throw new IllegalArgumentException(
        "The supplied session is not one owned SQLite store or capability wrapper.");
  }
}
