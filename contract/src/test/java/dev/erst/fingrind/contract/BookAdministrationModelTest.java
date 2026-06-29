package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for the current book-administration model records. */
class BookAdministrationModelTest {
  @Test
  void declaredAccount_holdsItsPayload() {
    DeclaredAccount account =
        ContractFixtures.declaredAccount(
            "1000", "Cash", AccountType.ASSET, true, Instant.parse("2026-04-07T10:15:30Z"));
    assertEquals("1000", account.accountCode().value());
    assertTrue(account.cashAndCashEquivalent());
  }

  @Test
  void declareAccountCommand_rejectsNullAccountTaxonomy() {
    assertThrows(
        NullPointerException.class,
        () ->
            new DeclareAccountCommand(
                new AccountCode("1000"), new AccountName("Cash"), AccountType.ASSET, nullOf()));
  }

  @Test
  void openBookResultRejected_rejectsNullRejection() {
    assertThrows(NullPointerException.class, () -> new OpenBookResult.Rejected(nullOf()));
  }

  @Test
  void bookContainsSchema_hasValueSemantics() {
    assertEquals(
        new BookAdministrationRejection.BookContainsSchema(),
        new BookAdministrationRejection.BookContainsSchema());
  }

  @Test
  void declareAccountResultFamilies_rejectNullAccount() {
    assertThrows(NullPointerException.class, () -> new DeclareAccountResult.Declared(nullOf()));
    assertThrows(NullPointerException.class, () -> new DeclareAccountResult.Reactivated(nullOf()));
    assertThrows(NullPointerException.class, () -> new DeclareAccountResult.Renamed(nullOf()));
    assertThrows(NullPointerException.class, () -> new DeclareAccountResult.Unchanged(nullOf()));
  }

  @Test
  void listAccountsResultListed_copiesItsPayload() {
    List<DeclaredAccount> source =
        new java.util.ArrayList<>(
            List.of(
                ContractFixtures.declaredAccount(
                    "1000",
                    "Cash",
                    AccountType.ASSET,
                    true,
                    Instant.parse("2026-04-07T10:15:30Z"))));
    ListAccountsResult.Listed listed =
        new ListAccountsResult.Listed(
            ContractFixtures.accountPage(source, 50, java.util.Optional.empty()));
    source.clear();
    assertEquals(1, listed.page().accounts().size());
  }

  @Test
  void openBookCommand_rejectsNullBookIdentity() {
    assertThrows(NullPointerException.class, () -> new OpenBookCommand(nullOf()));
  }

  @Test
  void openBookCommand_acceptsNarrowDoctrineIdentity() {
    assertEquals(
        "Acme Studio", new OpenBookCommand(bookIdentity()).bookIdentity().entityName().value());
  }

  private static BookIdentity bookIdentity() {
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Acme Studio")),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"));
  }
}
