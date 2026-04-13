package dev.erst.fingrind.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

      assertEquals(new OpenBookResult.Opened(FIXED_CLOCK.instant()), service.openBook());
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

      assertEquals(
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

  @Test
  void listAccounts_rejectsUninitializedBook() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookAdministrationService service = new BookAdministrationService(bookSession, FIXED_CLOCK);

      assertEquals(
          new ListAccountsResult.Rejected(new BookAdministrationRejection.BookNotInitialized()),
          service.listAccounts());
    }
  }

  @Test
  void listAccounts_returnsListedAccountsWhenInitialized() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookAdministrationService service = new BookAdministrationService(bookSession, FIXED_CLOCK);
      service.openBook();
      service.declareAccount(
          new DeclareAccountCommand(
              new AccountCode("1000"), new AccountName("Cash"), NormalBalance.DEBIT));

      assertEquals(
          new ListAccountsResult.Listed(
              List.of(
                  new DeclaredAccount(
                      new AccountCode("1000"),
                      new AccountName("Cash"),
                      NormalBalance.DEBIT,
                      true,
                      FIXED_CLOCK.instant()))),
          service.listAccounts());
    }
  }
}
