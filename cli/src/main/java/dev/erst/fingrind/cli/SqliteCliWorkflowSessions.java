package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.executor.PostingApplicationService;
import dev.erst.fingrind.executor.UuidV7PostingIdGenerator;
import dev.erst.fingrind.sqlite.SqliteAdministrationSession;
import dev.erst.fingrind.sqlite.SqlitePlanExecutionSession;
import dev.erst.fingrind.sqlite.SqlitePlanReadOnlySession;
import dev.erst.fingrind.sqlite.SqlitePostingSession;
import dev.erst.fingrind.sqlite.SqliteReadSession;
import dev.erst.fingrind.sqlite.SqliteReportingPeriodCloseSession;
import java.time.Clock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/** Shared SQLite session helpers for focused CLI workflow adapters. */
final class SqliteCliWorkflowSessions {
  private SqliteCliWorkflowSessions() {}

  static <T> ContractDecision<T> withAdministrationSessionDecision(
      ContractDecision<SqliteAdministrationSession> decision,
      Function<SqliteAdministrationSession, ContractDecision<T>> work) {
    return withSessionDecision(decision, work, SqliteAdministrationSession::close);
  }

  /**
   * Closes an exclusive new-book session without allowing a close failure to erase a rejection that
   * already disclosed uncertain opening artifacts. Session close never authorizes removal of a
   * provisional caller-selected book path.
   */
  static <T> ContractDecision<T> withNewBookAdministrationSessionDecision(
      ContractDecision<SqliteAdministrationSession> decision,
      Function<SqliteAdministrationSession, ContractDecision<T>> work,
      BiFunction<ContractFailure, RuntimeException, ContractDecision<T>> rejectedCloseFailure,
      BiFunction<RuntimeException, RuntimeException, ContractDecision<T>> workAndCloseFailure,
      BiFunction<T, RuntimeException, ContractDecision<T>> acceptedCloseFailure) {
    return decision.fold(
        bookSession -> {
          ContractDecision<T> result;
          try {
            result = work.apply(bookSession);
          } catch (RuntimeException primaryFailure) {
            try {
              bookSession.close();
            } catch (RuntimeException closeFailure) {
              return workAndCloseFailure.apply(primaryFailure, closeFailure);
            }
            throw primaryFailure;
          } catch (Error primaryFailure) {
            suppressCloseFailure(SqliteAdministrationSession::close, bookSession, primaryFailure);
            throw primaryFailure;
          }
          try {
            bookSession.close();
            return result;
          } catch (RuntimeException closeFailure) {
            return result.fold(
                accepted -> acceptedCloseFailure.apply(accepted, closeFailure),
                rejection -> rejectedCloseFailure.apply(rejection, closeFailure));
          }
        },
        ContractDecision::rejected);
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

  static <T> ContractDecision<T> withReportingPeriodCloseSessionDecision(
      ContractDecision<SqliteReportingPeriodCloseSession> decision,
      Function<SqliteReportingPeriodCloseSession, ContractDecision<T>> work) {
    return withSessionDecision(decision, work, SqliteReportingPeriodCloseSession::close);
  }

  static <T> ContractDecision<T> withPlanExecutionSessionDecision(
      ContractDecision<SqlitePlanExecutionSession> decision,
      Function<SqlitePlanExecutionSession, ContractDecision<T>> work) {
    return withSessionDecision(decision, work, SqlitePlanExecutionSession::close);
  }

  static <T> ContractDecision<T> withPlanReadOnlySession(
      ContractDecision<SqlitePlanReadOnlySession> decision,
      Function<SqlitePlanReadOnlySession, T> work) {
    return withSession(decision, work, SqlitePlanReadOnlySession::close);
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
          ContractDecision<T> result;
          try {
            result = work.apply(bookSession);
          } catch (RuntimeException | Error primaryFailure) {
            suppressCloseFailure(closeSession, bookSession, primaryFailure);
            throw primaryFailure;
          }
          closeSession.accept(bookSession);
          return result;
        },
        ContractDecision::rejected);
  }

  /** Releases one session without allowing a close failure to replace a work failure. */
  private static <S> void suppressCloseFailure(
      Consumer<S> closeSession, S bookSession, Throwable primaryFailure) {
    try {
      closeSession.accept(bookSession);
    } catch (RuntimeException | Error closeFailure) {
      primaryFailure.addSuppressed(closeFailure);
    }
  }
}
