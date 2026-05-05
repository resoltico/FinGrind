package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link BookAdministrationService}. */
@NullUnmarked
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
          new BookOpeningOutcome.Opened(FIXED_CLOCK.instant()), service.openBook());
    }
  }

  @Test
  void declareAccount_delegatesToBookSession() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookAdministrationService service = new BookAdministrationService(bookSession, FIXED_CLOCK);
      service.openBook();

      AccountDeclarationOutcome result =
          service.declareAccount(
              new AccountDeclaration(
                  new AccountCode("1000"), new AccountName("Cash"), NormalBalance.DEBIT));

      org.junit.jupiter.api.Assertions.assertEquals(
          new AccountDeclarationOutcome.Declared(
              new RegisteredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  NormalBalance.DEBIT,
                  true,
                  FIXED_CLOCK.instant())),
          result);
    }
  }
}
