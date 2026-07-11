package dev.erst.fingrind.executor.bookkeeping.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import dev.erst.fingrind.core.InventoryMovementKind;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.UnitOfMeasure;
import dev.erst.fingrind.executor.bookkeeping.InventoryValuationCriteria;
import dev.erst.fingrind.executor.bookkeeping.InventoryValuationMovementRecord;
import dev.erst.fingrind.executor.bookkeeping.InventoryValuationView;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Locks the exact pool replay boundary behind the inventory-valuation projection. */
class InventoryValuationCalculatorTest {
  private static final CurrencyUnit EUR = CurrencyUnit.of("EUR");
  private static final AccountCode INVENTORY = new AccountCode("inventory");

  @Test
  void calculation_replaysExactPoolThroughAsOfAndNeverMultipliesRoundedProjection() {
    List<InventoryValuationMovementRecord> movements =
        List.of(
            movement("purchase", "2026-01-10", 1, InventoryMovementKind.ACQUISITION, 10, 10_000),
            movement("sale", "2026-01-10", 2, InventoryMovementKind.DISPOSAL, -4, -4_000),
            movement("write-down", "2026-02-01", 3, InventoryMovementKind.WRITE_DOWN, 0, -1_000));

    InventoryValuationView asOfJanuary =
        InventoryValuationCalculator.calculate(
                tradingBookIdentity(),
                List.of(inventoryAccount()),
                movements.stream()
                    .filter(
                        movement ->
                            !movement.effectiveDate().isAfter(LocalDate.parse("2026-01-31")))
                    .toList(),
                new InventoryValuationCriteria(Optional.of(LocalDate.parse("2026-01-31")), true))
            .getFirst();
    InventoryValuationView current =
        InventoryValuationCalculator.calculate(
                tradingBookIdentity(),
                List.of(inventoryAccount()),
                movements,
                new InventoryValuationCriteria(Optional.empty(), false))
            .getFirst();

    assertEquals(Quantity.ofScaledUnits(0, 6), asOfJanuary.pool().quantityOnHand());
    assertEquals(Money.ofMinorUnits(EUR, 6_000), asOfJanuary.pool().costPool());
    assertEquals(
        Money.ofMinorUnits(EUR, 1_000), asOfJanuary.roundedMovingAverageUnitCostProjection());
    assertEquals(2, asOfJanuary.movements().size());

    assertEquals(Quantity.ofScaledUnits(0, 6), current.pool().quantityOnHand());
    assertEquals(Money.ofMinorUnits(EUR, 5_000), current.pool().costPool());
    Money roundedProjection =
        Objects.requireNonNull(
            current.roundedMovingAverageUnitCostProjection(), "rounded projection");
    assertEquals(Money.ofMinorUnits(EUR, 833), roundedProjection);
    assertTrue(current.movements().isEmpty());
    assertEquals(
        4_998, roundedProjection.minorUnits() * current.pool().quantityOnHand().scaledUnits());
    assertEquals(5_000, current.pool().costPool().minorUnits());
  }

  @Test
  void calculation_rejectsLedgerFactsOutsideTheDeclaredInventoryCatalog() {
    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                InventoryValuationCalculator.calculate(
                    tradingBookIdentity(),
                    List.of(inventoryAccount()),
                    List.of(
                        new InventoryValuationMovementRecord(
                            new AccountCode("other-inventory"),
                            LocalDate.parse("2026-01-10"),
                            1,
                            InventoryMovementKind.ACQUISITION,
                            1,
                            100,
                            new PostingId("unknown-inventory"))),
                    new InventoryValuationCriteria(Optional.empty(), false)));

    assertEquals(
        "Inventory movement references an account that is not an inventory account.",
        failure.getMessage());
  }

  @Test
  void calculation_preservesTheZeroPoolProjectionBoundary() {
    InventoryValuationView valuation =
        InventoryValuationCalculator.calculate(
                tradingBookIdentity(),
                List.of(inventoryAccount()),
                List.of(),
                new InventoryValuationCriteria(Optional.empty(), true))
            .getFirst();

    assertEquals(Quantity.ofScaledUnits(0, 0), valuation.pool().quantityOnHand());
    assertEquals(Money.ofMinorUnits(EUR, 0), valuation.pool().costPool());
    assertEquals(null, valuation.roundedMovingAverageUnitCostProjection());
    assertEquals(List.of(), valuation.movements());
  }

  @Test
  void calculation_rejectsDuplicateInventoryAccountsAndNegativeDurableReplay() {
    IllegalStateException duplicateFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                InventoryValuationCalculator.calculate(
                    tradingBookIdentity(),
                    List.of(inventoryAccount(), inventoryAccount(), nonInventoryAccount()),
                    List.of(),
                    new InventoryValuationCriteria(Optional.empty(), false)));
    IllegalStateException negativePoolFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                InventoryValuationCalculator.calculate(
                    tradingBookIdentity(),
                    List.of(inventoryAccount(), nonInventoryAccount()),
                    List.of(
                        movement(
                            "overdraw", "2026-01-10", 1, InventoryMovementKind.DISPOSAL, -1, -100)),
                    new InventoryValuationCriteria(Optional.empty(), false)));
    IllegalStateException negativeCostPoolFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                InventoryValuationCalculator.calculate(
                    tradingBookIdentity(),
                    List.of(inventoryAccount(), nonInventoryAccount()),
                    List.of(
                        movement(
                            "negative-cost",
                            "2026-01-10",
                            1,
                            InventoryMovementKind.ACQUISITION,
                            1,
                            -100)),
                    new InventoryValuationCriteria(Optional.empty(), false)));

    assertEquals(
        "Inventory account catalog must not contain duplicates.", duplicateFailure.getMessage());
    assertEquals(
        "Durable inventory replay produced a negative on-hand pool.",
        negativePoolFailure.getMessage());
    assertEquals(
        "Durable inventory replay produced a negative on-hand pool.",
        negativeCostPoolFailure.getMessage());
  }

  private static InventoryValuationMovementRecord movement(
      String postingId,
      String effectiveDate,
      long accountSequence,
      InventoryMovementKind kind,
      long quantityDelta,
      long costDeltaMinor) {
    return new InventoryValuationMovementRecord(
        INVENTORY,
        LocalDate.parse(effectiveDate),
        accountSequence,
        kind,
        quantityDelta,
        costDeltaMinor,
        new PostingId(postingId));
  }

  private static RegisteredAccount inventoryAccount() {
    return new RegisteredAccount(
        INVENTORY,
        new AccountName("Inventory"),
        AccountType.ASSET,
        new AccountTaxonomy(
            AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.of(FinancialPositionLineClassification.INVENTORY),
            Optional.empty(),
            Optional.of(CashFlowAssetClassification.NON_CASH)),
        new UnitOfMeasure("unit", 0),
        true,
        Instant.parse("2026-01-01T00:00:00Z"));
  }

  private static RegisteredAccount nonInventoryAccount() {
    return new RegisteredAccount(
        new AccountCode("cash"),
        new AccountName("Cash"),
        AccountType.ASSET,
        new AccountTaxonomy(
            AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
            Optional.empty(),
            Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)),
        true,
        Instant.parse("2026-01-01T00:00:00Z"));
  }

  private static BookIdentity tradingBookIdentity() {
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Acme Trading")),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING,
        EUR,
        FiscalYearStart.parse("01-01"));
  }
}
