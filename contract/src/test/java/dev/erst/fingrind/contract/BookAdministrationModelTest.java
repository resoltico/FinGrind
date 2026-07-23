package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.UnitOfMeasure;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
  void declareAccountCommand_enforcesInventoryUnitDoctrine() {
    UnitOfMeasure unitOfMeasure = new UnitOfMeasure("pcs", 0);
    DeclareAccountCommand cashAccount =
        ContractFixtures.declareAccountCommand("1000", "Cash", AccountType.ASSET);
    DeclareAccountCommand inventoryAccount =
        new DeclareAccountCommand(
            new AccountCode("1400"),
            new AccountName("Inventory"),
            AccountType.ASSET,
            inventoryTaxonomy(),
            unitOfMeasure);

    assertEquals(null, cashAccount.unitOfMeasure());
    assertEquals(unitOfMeasure, inventoryAccount.unitOfMeasure());
    assertEquals(
        "Inventory account declarations require one unitOfMeasure.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new DeclareAccountCommand(
                        new AccountCode("1400"),
                        new AccountName("Inventory"),
                        AccountType.ASSET,
                        inventoryTaxonomy(),
                        null))
            .getMessage());
    assertEquals(
        "Only inventory account declarations may carry one unitOfMeasure.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new DeclareAccountCommand(
                        new AccountCode("1000"),
                        new AccountName("Cash"),
                        AccountType.ASSET,
                        ContractFixtures.accountTaxonomy(AccountType.ASSET),
                        unitOfMeasure))
            .getMessage());
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
    assertThrows(
        NullPointerException.class,
        () -> new DeclareAccountResult.Declared(nullOf(), attestationCommit()));
    assertThrows(
        NullPointerException.class,
        () -> new DeclareAccountResult.Reactivated(nullOf(), attestationCommit()));
    assertThrows(
        NullPointerException.class,
        () -> new DeclareAccountResult.Renamed(nullOf(), attestationCommit()));
    assertThrows(
        NullPointerException.class, () -> new DeclareAccountResult.Unchanged(nullOf(), null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DeclareAccountResult.Unchanged(
                ContractFixtures.declaredAccount(
                    "1000", "Cash", AccountType.ASSET, true, Instant.parse("2026-04-07T10:15:30Z")),
                attestationCommit()));
  }

  @Test
  void declaredAccount_enforcesInventoryUnitDoctrine() {
    UnitOfMeasure unitOfMeasure = new UnitOfMeasure("pcs", 0);
    DeclaredAccount inventoryAccount =
        new DeclaredAccount(
            new AccountCode("1400"),
            new AccountName("Inventory"),
            AccountType.ASSET,
            inventoryTaxonomy(),
            unitOfMeasure,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));

    assertEquals(unitOfMeasure, inventoryAccount.unitOfMeasure());
    assertEquals(
        "Inventory account snapshots require one unitOfMeasure.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new DeclaredAccount(
                        new AccountCode("1400"),
                        new AccountName("Inventory"),
                        AccountType.ASSET,
                        inventoryTaxonomy(),
                        null,
                        true,
                        Instant.parse("2026-04-07T10:15:30Z")))
            .getMessage());
    assertEquals(
        "Only inventory account snapshots may carry one unitOfMeasure.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new DeclaredAccount(
                        new AccountCode("1000"),
                        new AccountName("Cash"),
                        AccountType.ASSET,
                        ContractFixtures.accountTaxonomy(AccountType.ASSET),
                        unitOfMeasure,
                        true,
                        Instant.parse("2026-04-07T10:15:30Z")))
            .getMessage());
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
            new ListAccountsQuery(50, Optional.empty()),
            ContractFixtures.accountPage(source, 50, java.util.Optional.empty()));
    source.clear();
    assertEquals(1, listed.page().accounts().size());
  }

  @Test
  void openBookCommand_rejectsNullBookIdentity() {
    assertThrows(NullPointerException.class, () -> new OpenBookCommand(nullOf(), List.of()));
  }

  @Test
  void openBookCommand_acceptsNarrowDoctrineIdentity() {
    assertEquals(
        "Acme Studio",
        new OpenBookCommand(bookIdentity(), ContractFixtures.testFounders())
            .bookIdentity()
            .entityName()
            .value());
  }

  private static dev.erst.fingrind.contract.bookkeeping.AttestationCommit attestationCommit() {
    return new dev.erst.fingrind.contract.bookkeeping.AttestationCommit(
        java.math.BigInteger.ONE, "a".repeat(64));
  }

  private static BookIdentity bookIdentity() {
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Acme Studio")),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"),
        java.time.LocalDate.parse("2026-01-01"));
  }

  private static AccountTaxonomy inventoryTaxonomy() {
    return new AccountTaxonomy(
        AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(FinancialPositionLineClassification.INVENTORY),
        Optional.empty(),
        Optional.of(CashFlowAssetClassification.NON_CASH));
  }
}
