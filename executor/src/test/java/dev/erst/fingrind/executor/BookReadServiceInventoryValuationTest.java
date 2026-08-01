package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.BookReadServiceTestSupport.readService;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationQuery;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationReport;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.InventoryMovementKind;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.UnitOfMeasure;
import dev.erst.fingrind.executor.bookkeeping.InventoryValuationMovementRecord;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Exercises exact inventory-ledger valuation through the published read service. */
class BookReadServiceInventoryValuationTest {
  private static final CurrencyUnit EUR = CurrencyUnit.of("EUR");
  private static final LocalDate MOVEMENT_DATE = LocalDate.parse("2026-06-01");

  @Test
  void inventoryValuation_rejectsAnUninitializedBook() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      assertEquals(
          new InventoryValuationResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          readService(bookSession)
              .inventoryValuation(new InventoryValuationQuery(Optional.empty(), false)));
    }
  }

  @Test
  void inventoryValuation_projectsExactPoolAndInformationalUnitCostFromTheLedger() {
    RegisteredAccount inventory = inventoryAccount("1500", "Goods Inventory");
    RegisteredAccount emptyInventory = inventoryAccount("1510", "Reserve Inventory");
    InventoryValuationMovementRecord acquisition =
        new InventoryValuationMovementRecord(
            inventory.accountCode(),
            MOVEMENT_DATE,
            1,
            InventoryMovementKind.ACQUISITION,
            3,
            1_000,
            new PostingId("98d7a34c-0b61-3c00-98a8-8d3ec4dc4a98"));
    StatementBookStore bookStore =
        new StatementBookStore(List.of(inventory, emptyInventory), List.of(), List.of(acquisition));

    InventoryValuationReport report =
        assertInstanceOf(
                InventoryValuationResult.Reported.class,
                new BookReadService(bookStore, bookStore)
                    .inventoryValuation(
                        new InventoryValuationQuery(Optional.of(MOVEMENT_DATE), true)))
            .report();

    assertEquals(bookIdentity(), report.bookIdentity());
    assertEquals(Optional.of(MOVEMENT_DATE), report.effectiveDateAsOf());
    assertTrue(report.includesMovements());
    assertEquals(2, report.accounts().size());
    assertEquals(inventory.accountCode(), report.accounts().getFirst().inventoryAccountCode());
    assertEquals(Quantity.ofScaledUnits(0, 3), report.accounts().getFirst().quantityOnHand());
    assertEquals(
        Money.ofMinorUnits(EUR, 1_000), report.accounts().getFirst().carryingValue().toMoney());
    assertEquals(
        Money.ofMinorUnits(EUR, 333),
        Objects.requireNonNull(
                report.accounts().getFirst().roundedMovingAverageUnitCostProjection(),
                "positive inventory projection")
            .toMoney());
    assertEquals(1, report.accounts().getFirst().movements().size());
    assertEquals(0, report.accounts().get(1).quantityOnHand().scaledUnits());
    assertEquals(Money.ofMinorUnits(EUR, 0), report.accounts().get(1).carryingValue().toMoney());
    assertEquals(null, report.accounts().get(1).roundedMovingAverageUnitCostProjection());

    InventoryValuationReport summary =
        assertInstanceOf(
                InventoryValuationResult.Reported.class,
                new BookReadService(bookStore, bookStore)
                    .inventoryValuation(new InventoryValuationQuery(Optional.empty(), false)))
            .report();
    assertFalse(summary.includesMovements());
    assertEquals(List.of(), summary.accounts().getFirst().movements());
  }

  private static RegisteredAccount inventoryAccount(String accountCode, String accountName) {
    return new RegisteredAccount(
        new AccountCode(accountCode),
        new AccountName(accountName),
        AccountType.ASSET,
        new AccountTaxonomy(
            AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.empty(),
            Optional.of(FinancialPositionLineClassification.INVENTORY),
            Optional.empty(),
            Optional.of(CashFlowAssetClassification.NON_CASH)),
        new UnitOfMeasure("unit", 0),
        true,
        Instant.parse("2026-01-01T00:00:00Z"));
  }
}
