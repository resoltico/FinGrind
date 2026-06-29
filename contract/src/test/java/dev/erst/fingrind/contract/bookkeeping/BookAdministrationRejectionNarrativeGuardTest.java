package dev.erst.fingrind.contract.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountType;
import java.time.LocalDate;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Defensive coverage for administration narrative helper partition guards. */
class BookAdministrationRejectionNarrativeGuardTest {
  @Test
  void lifecycleMessage_rejectsNonLifecycleRejections() {
    assertUnsupportedRoute(
        new BookAdministrationRejection.AccountTypeConflict(
            new AccountCode("1000"), AccountType.ASSET, AccountType.LIABILITY),
        "Unsupported lifecycle rejection",
        BookAdministrationRejectionNarrative::lifecycleMessage);
  }

  @Test
  void accountCatalogMessage_rejectsNonCatalogRejections() {
    assertUnsupportedRoute(
        new BookAdministrationRejection.InterimResultSweepFutureDate(LocalDate.parse("2026-12-31")),
        "Unsupported account-catalog rejection",
        BookAdministrationRejectionNarrative::accountCatalogMessage);
  }

  @Test
  void closeWindowMessage_rejectsNonCloseRejections() {
    assertUnsupportedRoute(
        new BookAdministrationRejection.ParentAccountTypeConflict(
            new AccountCode("1010"),
            AccountType.ASSET,
            new AccountCode("1000"),
            AccountType.LIABILITY),
        "Unsupported close-window rejection",
        BookAdministrationRejectionNarrative::closeWindowMessage);
  }

  private static void assertUnsupportedRoute(
      BookAdministrationRejection rejection,
      String expectedMessage,
      java.util.function.Function<BookAdministrationRejection, String> route) {
    IllegalStateException failure =
        assertThrows(IllegalStateException.class, () -> route.apply(rejection));
    assertTrue(Objects.requireNonNull(failure.getMessage()).contains(expectedMessage));
  }
}
