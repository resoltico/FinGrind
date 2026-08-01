package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.ReportingPeriod;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.Provider;
import java.security.Security;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Coverage-focused tests for private close-planner helpers that guard durable evidence facts. */
class InterimResultSweepPlannerTest {
  private static final MethodHandle SHA256_HEX = plannerHelper("sha256Hex");

  @Test
  @ResourceLock("java.security.providers")
  void sha256Hex_reportsUnavailableDigestAlgorithm() {
    Provider[] originalProviders = Security.getProviders();
    try {
      removeSha256Providers();
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class, () -> sha256Hex("interim-result-sweep-material"));

      assertEquals("SHA-256 is unavailable in this Java runtime.", exception.getMessage());
    } finally {
      restoreProviders(originalProviders);
    }
  }

  @Test
  void reportingPeriod_usesBookIdentityStartWhenNoPriorSweepExists() {
    InterimResultSweepPlanner planner = planner();

    ReportingPeriod reportingPeriod =
        planner.reportingPeriod(LocalDate.parse("2026-04-07"), bookIdentity(), Optional.empty());

    assertEquals(
        new ReportingPeriod(bookIdentity().bookStartEffectiveDate(), LocalDate.parse("2026-04-07")),
        reportingPeriod);
  }

  @Test
  void reportingPeriod_startsOneDayAfterThePriorSweepBoundary() {
    InterimResultSweepPlanner planner = planner();

    ReportingPeriod reportingPeriod =
        planner.reportingPeriod(
            LocalDate.parse("2026-04-07"),
            bookIdentity(),
            Optional.of(LocalDate.parse("2026-04-05")));

    assertEquals(
        new ReportingPeriod(LocalDate.parse("2026-04-06"), LocalDate.parse("2026-04-07")),
        reportingPeriod);
  }

  @Test
  void closeHorizonRejection_rejectsAnInitialExplicitPeriodThatSkipsBookStart() {
    InterimResultSweepPlanner planner = planner();

    assertEquals(
        Optional.of(
            new BookkeepingAdministrationRejection.InterimResultSweepMustStartAt(
                bookIdentity().bookStartEffectiveDate())),
        planner.closeHorizonRejection(
            new ReportingPeriod(LocalDate.parse("2026-04-03"), LocalDate.parse("2026-04-07")),
            bookIdentity(),
            LocalDate.parse("2026-04-07"),
            Optional.empty()));
  }

  @Test
  void closeHorizonRejection_throughDateOverloadRejectsFutureDates() {
    InterimResultSweepPlanner planner = planner();

    assertEquals(
        Optional.of(
            new BookkeepingAdministrationRejection.InterimResultSweepFutureDate(
                LocalDate.parse("2026-04-08"))),
        planner.closeHorizonRejection(
            LocalDate.parse("2026-04-08"),
            bookIdentity(),
            LocalDate.parse("2026-04-07"),
            Optional.empty()));
  }

  @Test
  void closeHorizonRejection_throughDateOverloadRejectsDatesBeforeTheNextSweepFloor() {
    InterimResultSweepPlanner planner = planner();

    assertEquals(
        Optional.of(
            new BookkeepingAdministrationRejection.InterimResultSweepMustStartAt(
                LocalDate.parse("2026-04-09"))),
        planner.closeHorizonRejection(
            LocalDate.parse("2026-04-07"),
            bookIdentity(),
            LocalDate.parse("2026-04-30"),
            Optional.of(LocalDate.parse("2026-04-08"))));
  }

  private static String sha256Hex(String value) {
    try {
      return (String) SHA256_HEX.invokeExact(value);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError("Failed to invoke close-planner digest helper.", throwable);
    }
  }

  private static MethodHandle plannerHelper(String methodName) {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(
              InterimResultSweepDraftFactory.class, MethodHandles.lookup());
      return lookup.findStatic(
          InterimResultSweepDraftFactory.class,
          methodName,
          MethodType.methodType(String.class, String.class));
    } catch (IllegalAccessException | NoSuchMethodException exception) {
      throw new LinkageError("Failed to bind close-planner helper: " + methodName, exception);
    }
  }

  private static void removeSha256Providers() {
    for (Provider provider : Security.getProviders()) {
      if (provider.getService("MessageDigest", "SHA-256") != null) {
        Security.removeProvider(provider.getName());
      }
    }
  }

  private static void restoreProviders(Provider[] providers) {
    for (Provider provider : Security.getProviders()) {
      Security.removeProvider(provider.getName());
    }
    for (int index = 0; index < providers.length; index++) {
      Security.insertProviderAt(providers[index], index + 1);
    }
  }

  private static InterimResultSweepPlanner planner() {
    return InterimResultSweepPlanner.forBookIdentity(bookIdentity());
  }
}
