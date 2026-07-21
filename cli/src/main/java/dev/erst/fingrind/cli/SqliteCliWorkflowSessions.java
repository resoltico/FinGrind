package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.executor.PostingApplicationService;
import dev.erst.fingrind.executor.UuidV7PostingIdGenerator;
import dev.erst.fingrind.sqlite.SqliteAdministrationSession;
import dev.erst.fingrind.sqlite.SqlitePlanExecutionSession;
import dev.erst.fingrind.sqlite.SqlitePostingSession;
import dev.erst.fingrind.sqlite.SqliteReadSession;
import dev.erst.fingrind.sqlite.SqliteReportingPeriodCloseSession;
import java.time.Clock;
import java.util.function.Function;

/** Shared SQLite session helpers for focused CLI workflow adapters. */
final class SqliteCliWorkflowSessions {
  private SqliteCliWorkflowSessions() {}

  static <T> ContractDecision<T> withAdministrationSession(
      ContractDecision<SqliteAdministrationSession> decision,
      Function<SqliteAdministrationSession, T> work) {
    return decision.fold(
        bookSession -> {
          try (SqliteAdministrationSession ignored = bookSession) {
            return ContractDecision.accepted(work.apply(bookSession));
          }
        },
        ContractDecision::rejected);
  }

  static <T> ContractDecision<T> withAdministrationSessionDecision(
      ContractDecision<SqliteAdministrationSession> decision,
      Function<SqliteAdministrationSession, ContractDecision<T>> work) {
    return decision.fold(
        bookSession -> {
          try (SqliteAdministrationSession ignored = bookSession) {
            return work.apply(bookSession);
          }
        },
        ContractDecision::rejected);
  }

  static <T> ContractDecision<T> withReadSession(
      ContractDecision<SqliteReadSession> decision, Function<SqliteReadSession, T> work) {
    return decision.fold(
        bookSession -> {
          try (SqliteReadSession ignored = bookSession) {
            return ContractDecision.accepted(work.apply(bookSession));
          }
        },
        ContractDecision::rejected);
  }

  static <T> ContractDecision<T> withPostingSession(
      ContractDecision<SqlitePostingSession> decision, Function<SqlitePostingSession, T> work) {
    return decision.fold(
        bookSession -> {
          try (SqlitePostingSession ignored = bookSession) {
            return ContractDecision.accepted(work.apply(bookSession));
          }
        },
        ContractDecision::rejected);
  }

  static <T> ContractDecision<T> withPostingSessionDecision(
      ContractDecision<SqlitePostingSession> decision,
      Function<SqlitePostingSession, ContractDecision<T>> work) {
    return decision.fold(
        bookSession -> {
          try (SqlitePostingSession ignored = bookSession) {
            return work.apply(bookSession);
          }
        },
        ContractDecision::rejected);
  }

  static <T> ContractDecision<T> withReportingPeriodCloseSession(
      ContractDecision<SqliteReportingPeriodCloseSession> decision,
      Function<SqliteReportingPeriodCloseSession, T> work) {
    return decision.fold(
        bookSession -> {
          try (SqliteReportingPeriodCloseSession ignored = bookSession) {
            return ContractDecision.accepted(work.apply(bookSession));
          }
        },
        ContractDecision::rejected);
  }

  static <T> ContractDecision<T> withReportingPeriodCloseSessionDecision(
      ContractDecision<SqliteReportingPeriodCloseSession> decision,
      Function<SqliteReportingPeriodCloseSession, ContractDecision<T>> work) {
    return decision.fold(
        bookSession -> {
          try (SqliteReportingPeriodCloseSession ignored = bookSession) {
            return work.apply(bookSession);
          }
        },
        ContractDecision::rejected);
  }

  static <T> ContractDecision<T> withPlanExecutionSession(
      ContractDecision<SqlitePlanExecutionSession> decision,
      Function<SqlitePlanExecutionSession, T> work) {
    return decision.fold(
        bookSession -> {
          try (SqlitePlanExecutionSession ignored = bookSession) {
            return ContractDecision.accepted(work.apply(bookSession));
          }
        },
        ContractDecision::rejected);
  }

  static <T> ContractDecision<T> withPlanExecutionSessionDecision(
      ContractDecision<SqlitePlanExecutionSession> decision,
      Function<SqlitePlanExecutionSession, ContractDecision<T>> work) {
    return decision.fold(
        bookSession -> {
          try (SqlitePlanExecutionSession ignored = bookSession) {
            return work.apply(bookSession);
          }
        },
        ContractDecision::rejected);
  }

  static PostingApplicationService postingApplicationService(
      SqlitePostingSession bookSession, Clock clock) {
    return new PostingApplicationService(
        bookSession, bookSession, new UuidV7PostingIdGenerator(), clock);
  }
}
