package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.InventoryValuationAccount;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationMovement;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationReport;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.InventoryMovementKind;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.UnitOfMeasure;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Sample exact-pool inventory valuation used by output-projection tests. */
final class ReportCrossFormatInventoryFixture {
  private ReportCrossFormatInventoryFixture() {}

  static InventoryValuationReport sampleInventoryValuationReport(boolean includesMovements) {
    List<InventoryValuationMovement> movements =
        includesMovements
            ? List.of(
                new InventoryValuationMovement(
                    new PostingId("98d7a34c-0b61-3c00-98a8-8d3ec4dc4a98"),
                    LocalDate.parse("2026-04-01"),
                    1L,
                    InventoryMovementKind.ACQUISITION,
                    10L,
                    10_000L),
                new InventoryValuationMovement(
                    new PostingId("fd6c691c-eca4-35ba-9672-d0aa49305ff0"),
                    LocalDate.parse("2026-04-02"),
                    2L,
                    InventoryMovementKind.DISPOSAL,
                    -4L,
                    -4_000L),
                new InventoryValuationMovement(
                    new PostingId("5794731f-c7c3-3061-9e24-058328a76d97"),
                    LocalDate.parse("2026-04-03"),
                    3L,
                    InventoryMovementKind.WRITE_DOWN,
                    0L,
                    -1_000L))
            : List.of();
    return new InventoryValuationReport(
        CliFixtureSupport.bookIdentity(),
        Optional.of(LocalDate.parse("2026-04-30")),
        includesMovements,
        List.of(
            new InventoryValuationAccount(
                new AccountCode("inventory"),
                new AccountName("Inventory"),
                new UnitOfMeasure("each", 0),
                Quantity.ofScaledUnits(0, 6L),
                new MonetaryAmount("EUR", "5000"),
                new MonetaryAmount("EUR", "833"),
                movements),
            new InventoryValuationAccount(
                new AccountCode("inventory-reserve"),
                new AccountName("Inventory reserve"),
                new UnitOfMeasure("each", 0),
                Quantity.zero(0),
                new MonetaryAmount("EUR", "0"),
                null,
                List.of())));
  }
}
