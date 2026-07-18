package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.FIXED_CLOCK;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.openBookStep;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.planId;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.service;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.textFact;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.core.BookDoctrine;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Branch coverage for ensure-book replay and conflict behavior in ledger-plan execution. */
class LedgerPlanServiceEnsureBookCoverageTest {
  @Test
  void execute_rejectsEnsureBookWhenExistingIdentityDiffers() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(FIXED_CLOCK.instant(), alternateBookIdentity(), List.of());

      var result =
          service(bookSession)
              .execute(new LedgerPlan(planId("plan-conflict"), List.of(openBookStep("open"))));

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          "ensure-book-identity-conflict",
          result.journal().steps().getFirst().requiredFailure().code());
    }
  }

  @Test
  void execute_replaysEnsureBookWhenAlreadyInitializedRaceResolvesToRequestedIdentity() {
    try (AlreadyInitializedRaceSession bookSession =
        new AlreadyInitializedRaceSession(initializedInspection(bookIdentity()))) {
      var result =
          service(bookSession)
              .execute(new LedgerPlan(planId("plan-replay"), List.of(openBookStep("open"))));

      assertEquals(LedgerPlanStatus.SUCCEEDED, result.status());
      assertTrue(
          result.journal().steps().getFirst().facts().stream()
              .anyMatch(fact -> textFact(fact, "entityName", bookIdentity().entityName().value())));
    }
  }

  @Test
  void execute_rejectsEnsureBookWhenAlreadyInitializedRaceResolvesToDifferentIdentity() {
    try (AlreadyInitializedRaceSession bookSession =
        new AlreadyInitializedRaceSession(initializedInspection(alternateBookIdentity()))) {
      var result =
          service(bookSession)
              .execute(
                  new LedgerPlan(planId("plan-replay-conflict"), List.of(openBookStep("open"))));

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          "ensure-book-identity-conflict",
          result.journal().steps().getFirst().requiredFailure().code());
    }
  }

  @Test
  void execute_surfacesOriginalRejectionWhenAlreadyInitializedRaceDoesNotRevealABook() {
    try (AlreadyInitializedRaceSession bookSession =
        new AlreadyInitializedRaceSession(new BookLifecycleInspection.Missing(1))) {
      var result =
          service(bookSession)
              .execute(
                  new LedgerPlan(planId("plan-replay-missing"), List.of(openBookStep("open"))));

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          BookAdministrationRejection.wireCode(
              new BookAdministrationRejection.BookAlreadyInitialized()),
          result.journal().steps().getFirst().requiredFailure().code());
    }
  }

  @Test
  void execute_surfacesNonReplayableOpenBookRejections() {
    try (SchemaRejectingOpenBookSession bookSession = new SchemaRejectingOpenBookSession()) {
      var result =
          service(bookSession)
              .execute(new LedgerPlan(planId("plan-schema"), List.of(openBookStep("open"))));

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          BookAdministrationRejection.wireCode(
              new BookAdministrationRejection.BookContainsSchema()),
          result.journal().steps().getFirst().requiredFailure().code());
    }
  }

  private static BookLifecycleInspection.Initialized initializedInspection(
      BookIdentity bookIdentity) {
    return new BookLifecycleInspection.Initialized(1, 1, 1, FIXED_CLOCK.instant(), bookIdentity);
  }

  private static BookIdentity alternateBookIdentity() {
    BookDoctrine doctrine = BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE;
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Other Studio")),
        doctrine,
        CurrencyUnit.of("USD"),
        FiscalYearStart.parse("04-01"),
        java.time.LocalDate.parse("2026-01-01"));
  }

  /**
   * Test double that simulates one already-initialized race after an initial missing inspection.
   */
  private static final class AlreadyInitializedRaceSession
      extends LedgerPlanServiceTestSupport.DelegatingAtomicBookStore {
    private final BookLifecycleInspection followupInspection;
    private int inspectionCount;

    private AlreadyInitializedRaceSession(BookLifecycleInspection followupInspection) {
      this.followupInspection = followupInspection;
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      inspectionCount += 1;
      return inspectionCount == 1 ? new BookLifecycleInspection.Missing(1) : followupInspection;
    }

    @Override
    public BookOpeningOutcome openBook(
        Instant initializedAt,
        BookIdentity bookIdentity,
        List<dev.erst.fingrind.executor.bookkeeping.AccountDeclaration> seededAccounts) {
      return new BookOpeningOutcome.Rejected(
          new BookkeepingAdministrationRejection.BookAlreadyInitialized());
    }
  }

  /** Test double that rejects book opening with one schema-present rejection. */
  private static final class SchemaRejectingOpenBookSession
      extends LedgerPlanServiceTestSupport.DelegatingAtomicBookStore {
    @Override
    public BookLifecycleInspection inspectBook() {
      return new BookLifecycleInspection.Missing(1);
    }

    @Override
    public BookOpeningOutcome openBook(
        Instant initializedAt,
        BookIdentity bookIdentity,
        List<dev.erst.fingrind.executor.bookkeeping.AccountDeclaration> seededAccounts) {
      return new BookOpeningOutcome.Rejected(
          new BookkeepingAdministrationRejection.BookContainsSchema());
    }
  }
}
