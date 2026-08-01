package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.TEST_AUTHORIZER;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.initializedLifecycleInspection;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.ClosedFiscalYearRecord;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearClosePlanner;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import dev.erst.fingrind.executor.spi.ReportingPeriodCloseStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Direct coverage for the fiscal-year-close application service wrapper. */
class FiscalYearCloseServiceTest {
  private static final Instant FIXED_INSTANT = Instant.parse("2026-12-31T23:59:59Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
  private static final int FISCAL_YEAR_LABEL = 2026;
  private static final ReportingPeriod FISCAL_YEAR =
      new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"));

  @Test
  void fiscalYearClose_rejectsUninitializedBookWithoutCallingStore() {
    AtomicBoolean storeCalled = new AtomicBoolean(false);
    FiscalYearCloseService service =
        new FiscalYearCloseService(
            () -> new BookLifecycleInspection.Missing(1),
            new RecordingStore() {
              @Override
              public FiscalYearCloseOutcome fiscalYearClose(
                  ReportingPeriod reportingPeriod,
                  dev.erst.fingrind.core.BookIdentity resolvedBookIdentity,
                  FiscalYearClosePlanner planner,
                  LocalDate currentUtcDate,
                  Instant closedAt,
                  PostingIdGenerator postingIdGenerator,
                  dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer
                      attestationAuthorizer) {
                storeCalled.set(true);
                throw new AssertionError("closeStore should not be called");
              }
            },
            () -> new PostingId("f69a68be-269e-3c0f-96ac-2e3f7d806a8b"),
            FIXED_CLOCK);

    assertEquals(
        new FiscalYearCloseOutcome.Rejected(
            new BookkeepingAdministrationRejection.BookNotInitialized()),
        service.fiscalYearClose(FISCAL_YEAR_LABEL, TEST_AUTHORIZER));
    assertFalse(storeCalled.get());
  }

  @Test
  void fiscalYearClose_reportingPeriodOverloadRejectsUninitializedBookWithoutCallingStore() {
    AtomicBoolean storeCalled = new AtomicBoolean(false);
    FiscalYearCloseService service =
        new FiscalYearCloseService(
            () -> new BookLifecycleInspection.Missing(1),
            new RecordingStore() {
              @Override
              public FiscalYearCloseOutcome fiscalYearClose(
                  ReportingPeriod reportingPeriod,
                  dev.erst.fingrind.core.BookIdentity resolvedBookIdentity,
                  FiscalYearClosePlanner planner,
                  LocalDate currentUtcDate,
                  Instant closedAt,
                  PostingIdGenerator postingIdGenerator,
                  dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer
                      attestationAuthorizer) {
                storeCalled.set(true);
                throw new AssertionError("closeStore should not be called");
              }
            },
            () -> new PostingId("f69a68be-269e-3c0f-96ac-2e3f7d806a8b"),
            FIXED_CLOCK);

    assertEquals(
        new FiscalYearCloseOutcome.Rejected(
            new BookkeepingAdministrationRejection.BookNotInitialized()),
        service.fiscalYearClose(FISCAL_YEAR, TEST_AUTHORIZER));
    assertFalse(storeCalled.get());
  }

  @Test
  void fiscalYearClose_passesResolvedExecutionContextIntoStore() {
    RecordingStore store = new RecordingStore();
    PostingIdGenerator postingIdGenerator =
        () -> new PostingId("f69a68be-269e-3c0f-96ac-2e3f7d806a8b");
    FiscalYearCloseService service =
        new FiscalYearCloseService(
            () -> initializedLifecycleInspection(1001, 1, 1, FIXED_INSTANT),
            store,
            postingIdGenerator,
            FIXED_CLOCK);

    FiscalYearCloseOutcome outcome = service.fiscalYearClose(FISCAL_YEAR_LABEL, TEST_AUTHORIZER);

    assertEquals(store.outcome, outcome);
    assertEquals(FISCAL_YEAR, store.reportingPeriod);
    assertEquals(bookIdentity(), store.bookIdentity);
    assertEquals(LocalDate.parse("2026-12-31"), store.currentUtcDate);
    assertEquals(FIXED_INSTANT, store.closedAt);
    assertSame(postingIdGenerator, store.postingIdGenerator);
    assertNotNull(store.planner);
  }

  @Test
  void fiscalYearClose_derivesThePartialFirstFiscalYearSegmentFromBookStart() {
    BookIdentity baseline = bookIdentity();
    BookIdentity midYearBook =
        new BookIdentity(
            baseline.entityProfile(),
            baseline.bookDoctrine(),
            baseline.functionalCurrency(),
            baseline.fiscalYearStart(),
            LocalDate.parse("2026-07-01"));
    BookLifecycleInspection.Initialized inspection =
        new BookLifecycleInspection.Initialized(1001, 1, 1, FIXED_INSTANT, midYearBook);
    RecordingStore store = new RecordingStore();
    FiscalYearCloseService service =
        new FiscalYearCloseService(
            () -> inspection,
            store,
            () -> new PostingId("f69a68be-269e-3c0f-96ac-2e3f7d806a8b"),
            FIXED_CLOCK);

    service.fiscalYearClose(FISCAL_YEAR_LABEL, TEST_AUTHORIZER);

    assertEquals(
        new ReportingPeriod(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-12-31")),
        store.reportingPeriod);
  }

  @Test
  void fiscalYearClose_reportingPeriodOverloadPassesExplicitWindowIntoStore() {
    RecordingStore store = new RecordingStore();
    PostingIdGenerator postingIdGenerator =
        () -> new PostingId("f69a68be-269e-3c0f-96ac-2e3f7d806a8b");
    FiscalYearCloseService service =
        new FiscalYearCloseService(
            () -> initializedLifecycleInspection(1001, 1, 1, FIXED_INSTANT),
            store,
            postingIdGenerator,
            FIXED_CLOCK);

    FiscalYearCloseOutcome outcome = service.fiscalYearClose(FISCAL_YEAR, TEST_AUTHORIZER);

    assertEquals(store.outcome, outcome);
    assertEquals(FISCAL_YEAR, store.reportingPeriod);
    assertEquals(bookIdentity(), store.bookIdentity);
    assertEquals(LocalDate.parse("2026-12-31"), store.currentUtcDate);
    assertEquals(FIXED_INSTANT, store.closedAt);
    assertSame(postingIdGenerator, store.postingIdGenerator);
    assertNotNull(store.planner);
  }

  /** Recording close-store double that captures the execution context passed by the service. */
  private static class RecordingStore implements ReportingPeriodCloseStore {
    private final FiscalYearCloseOutcome outcome =
        new FiscalYearCloseOutcome.Closed(
            new ClosedFiscalYearRecord(
                1,
                FISCAL_YEAR,
                new AccountCode("3000"),
                new AccountCode("3200"),
                new AccountCode("3300"),
                FIXED_INSTANT,
                List.of(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))),
            false);

    private @Nullable ReportingPeriod reportingPeriod;
    private @Nullable BookIdentity bookIdentity;
    private @Nullable FiscalYearClosePlanner planner;
    private @Nullable LocalDate currentUtcDate;
    private @Nullable Instant closedAt;
    private @Nullable PostingIdGenerator postingIdGenerator;

    @Override
    public dev.erst.fingrind.executor.bookkeeping.InterimResultSweepOutcome interimResultSweep(
        ReportingPeriod reportingPeriod,
        dev.erst.fingrind.core.BookIdentity bookIdentity,
        dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlanner planner,
        LocalDate currentUtcDate,
        Instant sweptAt,
        PostingIdGenerator postingIdGenerator,
        dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer attestationAuthorizer) {
      throw new UnsupportedOperationException("Interim-result sweep is not under test here.");
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.InterimResultSweepOutcome interimResultSweep(
        LocalDate throughEffectiveDate,
        dev.erst.fingrind.core.BookIdentity bookIdentity,
        dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlanner planner,
        LocalDate currentUtcDate,
        Instant sweptAt,
        PostingIdGenerator postingIdGenerator,
        dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer attestationAuthorizer) {
      throw new UnsupportedOperationException("Interim-result sweep is not under test here.");
    }

    @Override
    public java.util.List<dev.erst.fingrind.executor.bookkeeping.CommittedPosting> postings(
        dev.erst.fingrind.core.EffectiveDateRange effectiveDateRange) {
      return java.util.List.of();
    }

    @Override
    public java.util.Optional<LocalDate> earliestPostingEffectiveDate() {
      return java.util.Optional.empty();
    }

    @Override
    public java.util.Optional<LocalDate> transferredThroughEffectiveDate() {
      return java.util.Optional.empty();
    }

    @Override
    public FiscalYearCloseOutcome fiscalYearClose(
        ReportingPeriod reportingPeriod,
        dev.erst.fingrind.core.BookIdentity resolvedBookIdentity,
        FiscalYearClosePlanner planner,
        LocalDate currentUtcDate,
        Instant closedAt,
        PostingIdGenerator postingIdGenerator,
        dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer attestationAuthorizer) {
      this.reportingPeriod = reportingPeriod;
      this.bookIdentity = resolvedBookIdentity;
      this.planner = planner;
      this.currentUtcDate = currentUtcDate;
      this.closedAt = closedAt;
      this.postingIdGenerator = postingIdGenerator;
      return outcome;
    }
  }
}
