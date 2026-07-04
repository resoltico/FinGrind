package dev.erst.fingrind.contract.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.Money;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link InventoryBalanceBelowZero}. */
class InventoryBalanceBelowZeroTest {
  @Test
  void acceptsCanonicalDebitAndZeroStartingBalances() {
    InventoryBalanceBelowZero debitStartingBalance =
        assertDoesNotThrow(
            () ->
                new InventoryBalanceBelowZero(
                    new AccountCode("1400"),
                    "inventoryRelief.amount",
                    LocalDate.parse("2026-04-07"),
                    BalanceSide.DEBIT,
                    Money.parse("EUR", "10.00"),
                    Money.parse("EUR", "50.00"),
                    Money.parse("EUR", "40.00")));
    InventoryBalanceBelowZero zeroStartingBalance =
        assertDoesNotThrow(
            () ->
                new InventoryBalanceBelowZero(
                    new AccountCode("1400"),
                    "inventoryRelief.amount",
                    LocalDate.parse("2026-04-07"),
                    BalanceSide.ZERO,
                    Money.zero(CurrencyUnit.of("EUR")),
                    Money.parse("EUR", "5.00"),
                    Money.parse("EUR", "5.00")));

    assertEquals(BalanceSide.DEBIT, debitStartingBalance.currentBalanceSide());
    assertEquals(Money.zero(CurrencyUnit.of("EUR")), zeroStartingBalance.currentNetAmount());
  }

  @Test
  void rejectsNonPositiveRequestedDecrease() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new InventoryBalanceBelowZero(
                    new AccountCode("1400"),
                    "inventoryRelief.amount",
                    LocalDate.parse("2026-04-07"),
                    BalanceSide.DEBIT,
                    Money.parse("EUR", "10.00"),
                    Money.zero(CurrencyUnit.of("EUR")),
                    Money.parse("EUR", "10.00")));

    assertEquals("requestedDecreaseAmount must be positive.", error.getMessage());
  }

  @Test
  void rejectsNonPositiveResultingCreditBalance() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new InventoryBalanceBelowZero(
                    new AccountCode("1400"),
                    "inventoryRelief.amount",
                    LocalDate.parse("2026-04-07"),
                    BalanceSide.DEBIT,
                    Money.parse("EUR", "10.00"),
                    Money.parse("EUR", "5.00"),
                    Money.zero(CurrencyUnit.of("EUR"))));

    assertEquals("resultingCreditBalance must be positive.", error.getMessage());
  }

  @Test
  void rejectsZeroBalanceSideWithNonZeroCurrentBalance() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new InventoryBalanceBelowZero(
                    new AccountCode("1400"),
                    "inventoryRelief.amount",
                    LocalDate.parse("2026-04-07"),
                    BalanceSide.ZERO,
                    Money.parse("EUR", "1.00"),
                    Money.parse("EUR", "5.00"),
                    Money.parse("EUR", "4.00")));

    assertEquals(
        "currentNetAmount must be zero when currentBalanceSide is ZERO.", error.getMessage());
  }

  @Test
  void rejectsDebitOrCreditSideWithNonPositiveCurrentBalance() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new InventoryBalanceBelowZero(
                    new AccountCode("1400"),
                    "inventoryRelief.amount",
                    LocalDate.parse("2026-04-07"),
                    BalanceSide.CREDIT,
                    Money.zero(CurrencyUnit.of("EUR")),
                    Money.parse("EUR", "5.00"),
                    Money.parse("EUR", "5.00")));

    assertEquals(
        "currentNetAmount must be positive when currentBalanceSide is not ZERO.",
        error.getMessage());
  }
}
