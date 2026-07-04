package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection.Initialized;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/** Shared execution support for initialized-book reporting-period close workflows. */
final class ReportingPeriodCloseExecutionSupport {
  private final BookLifecycleReader lifecycleReader;
  private final PostingIdGenerator postingIdGenerator;
  private final Clock clock;

  ReportingPeriodCloseExecutionSupport(
      BookLifecycleReader lifecycleReader, PostingIdGenerator postingIdGenerator, Clock clock) {
    this.lifecycleReader = Objects.requireNonNull(lifecycleReader, "lifecycleReader");
    this.postingIdGenerator = Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  <P, O> O execute(
      ReportingPeriod reportingPeriod,
      Supplier<O> notInitializedOutcome,
      Function<BookIdentity, P> plannerFactory,
      CloseOperation<P, O> closeOperation) {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(notInitializedOutcome, "notInitializedOutcome");
    Objects.requireNonNull(plannerFactory, "plannerFactory");
    Objects.requireNonNull(closeOperation, "closeOperation");
    if (!lifecycleReader.allowsInitializedWorkflow()) {
      return notInitializedOutcome.get();
    }
    BookIdentity bookIdentity = lifecycleReader.requireInitializedBookIdentity();
    P planner = plannerFactory.apply(bookIdentity);
    Instant executedAt = clock.instant();
    return closeOperation.execute(
        reportingPeriod,
        bookIdentity,
        planner,
        executedAt.atZone(ZoneOffset.UTC).toLocalDate(),
        executedAt,
        postingIdGenerator);
  }

  <P, O> O execute(
      Supplier<O> notInitializedOutcome,
      Function<BookIdentity, P> plannerFactory,
      PreparedCloseOperation<P, O> closeOperation) {
    Objects.requireNonNull(notInitializedOutcome, "notInitializedOutcome");
    Objects.requireNonNull(plannerFactory, "plannerFactory");
    Objects.requireNonNull(closeOperation, "closeOperation");
    BookLifecycleInspection inspection = lifecycleReader.inspectBook();
    if (!inspection.allowsInitializedWorkflow()) {
      return notInitializedOutcome.get();
    }
    BookIdentity bookIdentity = BookLifecycleInspection.requireInitializedBookIdentity(inspection);
    P planner = plannerFactory.apply(bookIdentity);
    Instant executedAt = clock.instant();
    LocalDate bookStartDate = requireInitializedInspection(inspection).bookStartDate();
    return closeOperation.execute(
        bookIdentity,
        bookStartDate,
        planner,
        executedAt.atZone(ZoneOffset.UTC).toLocalDate(),
        executedAt,
        postingIdGenerator);
  }

  static Initialized requireInitializedInspection(BookLifecycleInspection inspection) {
    Objects.requireNonNull(inspection, "inspection");
    return switch (inspection) {
      case Initialized initialized -> initialized;
      case BookLifecycleInspection.Missing _ ->
          throw new IllegalStateException(
              "Prepared close operations require one initialized book inspection.");
      case BookLifecycleInspection.Existing _ ->
          throw new IllegalStateException(
              "Prepared close operations require one initialized book inspection.");
    };
  }

  /**
   * Executes one prepared close operation after shared lifecycle, identity, and time context has
   * already been resolved.
   */
  @FunctionalInterface
  interface CloseOperation<P, O> {
    /** Executes one close operation with the shared resolved context for this request. */
    O execute(
        ReportingPeriod reportingPeriod,
        BookIdentity bookIdentity,
        P planner,
        LocalDate currentUtcDate,
        Instant executedAt,
        PostingIdGenerator postingIdGenerator);
  }

  /** Executes one close operation whose reporting window is derived from live book facts. */
  @FunctionalInterface
  interface PreparedCloseOperation<P, O> {
    /** Executes one close operation after shared book identity and time context are resolved. */
    O execute(
        BookIdentity bookIdentity,
        LocalDate bookStartDate,
        P planner,
        LocalDate currentUtcDate,
        Instant executedAt,
        PostingIdGenerator postingIdGenerator);
  }
}
