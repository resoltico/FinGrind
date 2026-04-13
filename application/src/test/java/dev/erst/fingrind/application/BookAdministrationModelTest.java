package dev.erst.fingrind.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for Phase 2 book-administration model records. */
class BookAdministrationModelTest {
  @Test
  void declaredAccount_holdsItsPayload() {
    DeclaredAccount account =
        new DeclaredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));

    assertEquals("1000", account.accountCode().value());
  }

  @Test
  void declareAccountCommand_rejectsNullNormalBalance() {
    assertThrows(
        NullPointerException.class,
        () -> new DeclareAccountCommand(new AccountCode("1000"), new AccountName("Cash"), null));
  }

  @Test
  void openBookResultRejected_rejectsNullRejection() {
    assertThrows(NullPointerException.class, () -> new OpenBookResult.Rejected(null));
  }

  @Test
  void bookContainsSchema_hasValueSemantics() {
    assertEquals(
        new BookAdministrationRejection.BookContainsSchema(),
        new BookAdministrationRejection.BookContainsSchema());
  }

  @Test
  void declareAccountResultDeclared_rejectsNullAccount() {
    assertThrows(NullPointerException.class, () -> new DeclareAccountResult.Declared(null));
  }

  @Test
  void listAccountsResultListed_copiesItsPayload() {
    List<DeclaredAccount> source =
        new java.util.ArrayList<>(
            List.of(
                new DeclaredAccount(
                    new AccountCode("1000"),
                    new AccountName("Cash"),
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T10:15:30Z"))));

    ListAccountsResult.Listed listed = new ListAccountsResult.Listed(source);
    source.clear();

    assertEquals(1, listed.accounts().size());
  }

  @Test
  void normalBalanceConflict_rejectsNullRequestedBalance() {
    assertThrows(
        NullPointerException.class,
        () ->
            new BookAdministrationRejection.NormalBalanceConflict(
                new AccountCode("1000"), NormalBalance.DEBIT, null));
  }
}
