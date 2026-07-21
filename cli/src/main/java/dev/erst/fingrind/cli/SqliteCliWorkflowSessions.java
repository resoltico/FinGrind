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
import java.util.function.Consumer;
import java.util.function.Function;

/** Shared SQLite session helpers for focused CLI workflow adapters. */
final class SqliteCliWorkflowSessions {
  private SqliteCliWorkflowSessions() {}

  static <T> ContractDecision<T> withAdministrationSession(
      ContractDecision<SqliteAdministrationSession> decision,
      Function<SqliteAdministrationSession, T> work) {
    return withSession(decision, work, SqliteAdministrationSession::close);
  }

  static <T> ContractDecision<T> withAdministrationSessionDecision(
      ContractDecision<SqliteAdministrationSession> decision,
      Function<SqliteAdministrationSession, ContractDecision<T>> work) {
    return withSessionDecision(decision, work, SqliteAdministrationSession::close);
  }

  static <T> ContractDecision<T> withReadSession(
      ContractDecision<SqliteReadSession> decision, Function<SqliteReadSession, T> work) {
    return withSession(decision, work, SqliteReadSession::close);
  }

  static <T> ContractDecision<T> withPostingSession(
      ContractDecision<SqlitePostingSession> decision, Function<SqlitePostingSession, T> work) {
    return withSession(decision, work, SqlitePostingSession::close);
  }

  static <T> ContractDecision<T> withPostingSessionDecision(
      ContractDecision<SqlitePostingSession> decision,
      Function<SqlitePostingSession, ContractDecision<T>> work) {
    return withSessionDecision(decision, work, SqlitePostingSession::close);
  }

  static <T> ContractDecision<T> withReportingPeriodCloseSession(
      ContractDecision<SqliteReportingPeriodCloseSession> decision,
      Function<SqliteReportingPeriodCloseSession, T> work) {
    return withSession(decision, work, SqliteReportingPeriodCloseSession::close);
  }

  static <T> ContractDecision<T> withReportingPeriodCloseSessionDecision(
      ContractDecision<SqliteReportingPeriodCloseSession> decision,
      Function<SqliteReportingPeriodCloseSession, ContractDecision<T>> work) {
    return withSessionDecision(decision, work, SqliteReportingPeriodCloseSession::close);
  }

  static <T> ContractDecision<T> withPlanExecutionSession(
      ContractDecision<SqlitePlanExecutionSession> decision,
      Function<SqlitePlanExecutionSession, T> work) {
    return withSession(decision, work, SqlitePlanExecutionSession::close);
  }

  static <T> ContractDecision<T> withPlanExecutionSessionDecision(
      ContractDecision<SqlitePlanExecutionSession> decision,
      Function<SqlitePlanExecutionSession, ContractDecision<T>> work) {
    return withSessionDecision(decision, work, SqlitePlanExecutionSession::close);
  }

  static PostingApplicationService postingApplicationService(
      SqlitePostingSession bookSession, Clock clock) {
    return new PostingApplicationService(
        bookSession, bookSession, new UuidV7PostingIdGenerator(), clock);
  }

  private static <S, T> ContractDecision<T> withSession(
      ContractDecision<S> decision, Function<S, T> work, Consumer<S> closeSession) {
    return withSessionDecision(
        decision, bookSession -> ContractDecision.accepted(work.apply(bookSession)), closeSession);
  }

  private static <S, T> ContractDecision<T> withSessionDecision(
      ContractDecision<S> decision,
      Function<S, ContractDecision<T>> work,
      Consumer<S> closeSession) {
    return decision.fold(
        bookSession -> {
          try {
            return work.apply(bookSession);
          } finally {
            closeSession.accept(bookSession);
          }
        },
        ContractDecision::rejected);
  }
}
