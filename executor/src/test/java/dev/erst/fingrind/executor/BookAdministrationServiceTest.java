package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.DeclareAccountCommand;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link BookAdministrationService}. */
class BookAdministrationServiceTest {
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-07T10:15:30Z"), ZoneOffset.UTC);

  @Test
  void constructor_rejectsNullBookSession() {
    assertThrows(
        NullPointerException.class, () -> new BookAdministrationService(null, FIXED_CLOCK));
  }

  @Test
  void openBook_delegatesToBookSession() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookAdministrationService service = new BookAdministrationService(bookSession, FIXED_CLOCK);

      org.junit.jupiter.api.Assertions.assertEquals(
          new OpenBookResult.Opened(FIXED_CLOCK.instant()), service.openBook());
    }
  }

  @Test
  void declareAccount_delegatesToBookSession() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookAdministrationService service = new BookAdministrationService(bookSession, FIXED_CLOCK);
      service.openBook();

      DeclareAccountResult result =
          service.declareAccount(
              new DeclareAccountCommand(
                  new AccountCode("1000"), new AccountName("Cash"), NormalBalance.DEBIT));

      org.junit.jupiter.api.Assertions.assertEquals(
          new DeclareAccountResult.Declared(
              new DeclaredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  NormalBalance.DEBIT,
                  true,
                  FIXED_CLOCK.instant())),
          result);
    }
  }
}
