package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingPolicyProfile;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.BusinessActivityTag;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityForm;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.OwnerModel;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for Phase 2 book-administration model records. */
class BookAdministrationModelTest {
  @Test
  void declaredAccount_holdsItsPayload() {
    DeclaredAccount account =
        ContractFixtures.declaredAccount(
            "1000",
            "Cash",
            AccountType.ASSET,
            AccountRole.ORDINARY,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    assertEquals("1000", account.accountCode().value());
  }

  @Test
  void declareAccountCommand_rejectsNullAccountRole() {
    assertThrows(
        NullPointerException.class,
        () ->
            new DeclareAccountCommand(
                new AccountCode("1000"),
                new AccountName("Cash"),
                AccountType.ASSET,
                nullOf(),
                ContractFixtures.accountTaxonomy(AccountType.ASSET)));
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
  void declareAccountResultDeclared_rejectsNullAccount() {
    assertThrows(NullPointerException.class, () -> new DeclareAccountResult.Declared(nullOf()));
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
                    AccountRole.ORDINARY,
                    true,
                    Instant.parse("2026-04-07T10:15:30Z"))));
    ListAccountsResult.Listed listed =
        new ListAccountsResult.Listed(
            ContractFixtures.accountPage(source, 50, java.util.Optional.empty()));
    source.clear();
    assertEquals(1, listed.page().accounts().size());
  }

  @Test
  void accountRoleConflict_rejectsNullRequestedRole() {
    assertThrows(
        NullPointerException.class,
        () ->
            new BookAdministrationRejection.AccountRoleConflict(
                new AccountCode("1000"), AccountRole.ORDINARY, nullOf()));
  }

  @Test
  void openBookCommand_rejectsNullBookIdentity() {
    assertThrows(NullPointerException.class, () -> new OpenBookCommand(nullOf()));
  }

  @Test
  void openBookCommand_rejectsMissingBusinessActivityTags() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new OpenBookCommand(bookIdentity(OwnerModel.MULTI_OWNER, List.of())));
  }

  private static BookIdentity bookIdentity(
      OwnerModel ownerModel, List<BusinessActivityTag> businessActivityTags) {
    return new BookIdentity(
        new EntityProfile(
            new BookEntityName("Acme Studio"),
            EntityForm.COMPANY,
            ownerModel,
            businessActivityTags),
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"),
        AccountingPolicyProfile.INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1);
  }
}
