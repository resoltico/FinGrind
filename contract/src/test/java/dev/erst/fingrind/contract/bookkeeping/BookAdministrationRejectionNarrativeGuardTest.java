package dev.erst.fingrind.contract.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
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
        new BookAdministrationRejection.PeriodResultTransferFutureDate(
            LocalDate.parse("2026-12-31")),
        "Unsupported account-catalog rejection",
        BookAdministrationRejectionNarrative::accountCatalogMessage);
  }

  @Test
  void transferHorizonMessage_rejectsNonTransferRejections() {
    assertUnsupportedRoute(
        new BookAdministrationRejection.ParentAccountRoleConflict(
            new AccountCode("1010"),
            AccountRole.ORDINARY,
            new AccountCode("1000"),
            AccountRole.POLARITY_INVERTED),
        "Unsupported transfer-horizon rejection",
        BookAdministrationRejectionNarrative::transferHorizonMessage);
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
