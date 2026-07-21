package dev.erst.fingrind.contract.reportmodel;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationAccount;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationMovement;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationQuery;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationReport;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationResult;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.InventoryMovementKind;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.UnitOfMeasure;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Contract coverage for exact inventory valuation and its shared-report projection. */
class InventoryValuationReportContractTest {
  private static final UnitOfMeasure UNIT = new UnitOfMeasure("each", 0);

  @Test
  void builderKeepsExactCarryingValueSeparateFromInformationalProjection() {
    InventoryValuationReport detailedReport =
        new InventoryValuationReport(
            ReportModelTestSupport.bookIdentity(),
            Optional.of(LocalDate.parse("2026-07-10")),
            true,
            List.of(populatedAccount(), emptyAccount()));
    ReportModel detailed = InventoryValuationReportModelBuilder.INSTANCE.build(detailedReport);
    ReportModel empty =
        InventoryValuationReportModelBuilder.buildModel(
            new InventoryValuationReport(
                ReportModelTestSupport.bookIdentity(), Optional.empty(), false, List.of()));
    InventoryValuationReport noMovementDetail =
        new InventoryValuationReport(
            ReportModelTestSupport.bookIdentity(),
            Optional.empty(),
            false,
            List.of(emptyAccount()));
    InventoryValuationReport emptyMovementDetail =
        new InventoryValuationReport(
            ReportModelTestSupport.bookIdentity(), Optional.empty(), true, List.of(emptyAccount()));

    assertEquals("inventory-valuation", detailed.family());
    assertEquals(ReportModel.Orientation.LANDSCAPE, detailed.orientation());
    assertEquals(3, detailed.sections().size());
    assertEquals(
        List.of("inventory", "Inventory", "each", "10", "EUR 10.00", "EUR 100.00"),
        detailed.sections().getFirst().rows().getFirst().cells());
    assertEquals("Not applicable", detailed.sections().getFirst().rows().get(1).cells().get(4));
    assertEquals("DISPOSAL", detailed.sections().get(1).rows().getFirst().cells().get(2));
    assertTrue(detailed.sections().get(2).rows().isEmpty());
    assertTrue(
        detailed.sections().get(2).verdicts().stream()
            .anyMatch(verdict -> verdict.value().contains("No inventory movements matched")));
    assertEquals(1, empty.sections().size());
    assertTrue(
        empty.sections().getFirst().verdicts().stream()
            .anyMatch(verdict -> verdict.value().contains("No inventory accounts matched")));
    assertNull(empty.context().asOf());
    assertTrue(noMovementDetail.accounts().getFirst().movements().isEmpty());

    ReportCsvProjection detailedCsv =
        Objects.requireNonNull(detailed.tabularCsvProjection(), "detailed tabularCsvProjection");
    ReportCsvProjection noMovementCsv =
        Objects.requireNonNull(
            InventoryValuationReportModelBuilder.buildModel(noMovementDetail)
                .tabularCsvProjection(),
            "noMovement tabularCsvProjection");
    ReportCsvProjection emptyCsv =
        Objects.requireNonNull(empty.tabularCsvProjection(), "empty tabularCsvProjection");
    ReportCsvProjection emptyMovementCsv =
        Objects.requireNonNull(
            InventoryValuationReportModelBuilder.buildModel(emptyMovementDetail)
                .tabularCsvProjection(),
            "emptyMovement tabularCsvProjection");
    assertEquals(2, detailedCsv.rows().size());
    assertEquals("inventory-valuation", csvValue(detailedCsv, 0, "recordKind"));
    assertEquals("2026-07-10", csvValue(detailedCsv, 0, "effectiveDateAsOf"));
    assertEquals("10", csvValue(detailedCsv, 0, "quantityOnHand"));
    assertEquals(
        "1000", csvValue(detailedCsv, 0, "roundedMovingAverageUnitCostProjectionMinorUnits"));
    assertEquals("10000", csvValue(detailedCsv, 0, "carryingValueMinorUnits"));
    assertEquals(
        "", csvValue(noMovementCsv, 0, "roundedMovingAverageUnitCostProjectionMinorUnits"));
    assertEquals("inventory-valuation", csvValue(noMovementCsv, 0, "recordKind"));
    assertEquals("inventory-reserve", csvValue(noMovementCsv, 0, "inventoryAccountCode"));
    assertEquals("0", csvValue(noMovementCsv, 0, "quantityOnHand"));
    assertEquals("0", csvValue(noMovementCsv, 0, "carryingValueMinorUnits"));
    assertEquals("inventory-valuation", csvValue(emptyCsv, 0, "recordKind"));
    assertEquals(
        "No inventory accounts matched the selected scope.", csvValue(emptyCsv, 0, "message"));
    assertEquals("inventory-valuation", csvValue(emptyMovementCsv, 0, "recordKind"));
    assertEquals("inventory-reserve", csvValue(emptyMovementCsv, 0, "inventoryAccountCode"));
    assertEquals("", csvValue(emptyMovementCsv, 0, "movementPostingId"));
    assertEquals("", csvValue(emptyMovementCsv, 0, "message"));
  }

  @Test
  void valuationValueObjectsRejectIncoherentExactFacts() {
    assertThrows(
        IllegalArgumentException.class,
        () -> account(Quantity.zero(0), money("1"), nullOf(MonetaryAmount.class), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> account(Quantity.zero(0), money("0"), money("1"), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            account(
                Quantity.ofScaledUnits(0, 1), money("1"), nullOf(MonetaryAmount.class), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> account(Quantity.ofScaledUnits(0, 1), money("1"), usd("1"), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> account(Quantity.ofScaledUnits(1, 1), money("1"), money("1"), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> movement(0L, InventoryMovementKind.ACQUISITION, 1L, 1L));
    assertThrows(
        IllegalArgumentException.class,
        () -> movement(1L, InventoryMovementKind.ACQUISITION, 0L, 0L));
    assertDoesNotThrow(() -> movement(1L, InventoryMovementKind.CAPITALIZATION, 0L, 1L));
    assertDoesNotThrow(
        () -> new InventoryValuationQuery(Optional.of(LocalDate.parse("2026-07-10")), true));
    assertThrows(NullPointerException.class, () -> new InventoryValuationQuery(nullOf(), false));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new InventoryValuationReport(
                ReportModelTestSupport.bookIdentity(),
                Optional.empty(),
                false,
                List.of(populatedAccount())));
  }

  @Test
  void valueObjectsCopyCollectionsAndResultsExposeBothOutcomes() {
    InventoryValuationAccount account = populatedAccount();
    InventoryValuationReport report =
        new InventoryValuationReport(
            ReportModelTestSupport.bookIdentity(), Optional.empty(), true, List.of(account));
    InventoryValuationResult.Reported reported = new InventoryValuationResult.Reported(report);
    BookQueryRejection rejection = new BookQueryRejection.BookNotInitialized();
    InventoryValuationResult.Rejected rejected = new InventoryValuationResult.Rejected(rejection);

    assertThrows(UnsupportedOperationException.class, () -> account.movements().add(movement()));
    assertThrows(UnsupportedOperationException.class, () -> report.accounts().add(emptyAccount()));
    assertSame(report, reported.reported());
    assertNull(reported.rejection());
    assertNull(rejected.reported());
    assertSame(rejection, rejected.rejection());
    assertEquals("reported", reported.fold(value -> "reported", value -> "rejected"));
    assertEquals("rejected", rejected.fold(value -> "reported", value -> "rejected"));
    assertThrows(
        NullPointerException.class,
        () -> new InventoryValuationResult.Reported(nullOf(InventoryValuationReport.class)));
    assertThrows(
        NullPointerException.class,
        () -> new InventoryValuationResult.Rejected(nullOf(BookQueryRejection.class)));
  }

  private static InventoryValuationAccount populatedAccount() {
    return account(
        Quantity.ofScaledUnits(0, 10), money("10000"), money("1000"), List.of(movement()));
  }

  private static InventoryValuationAccount emptyAccount() {
    return account(
        "inventory-reserve",
        "Inventory reserve",
        Quantity.zero(0),
        money("0"),
        nullOf(MonetaryAmount.class),
        List.of());
  }

  private static InventoryValuationAccount account(
      Quantity quantity,
      MonetaryAmount carryingValue,
      @Nullable MonetaryAmount roundedProjection,
      List<InventoryValuationMovement> movements) {
    return account("inventory", "Inventory", quantity, carryingValue, roundedProjection, movements);
  }

  private static InventoryValuationAccount account(
      String accountCode,
      String accountName,
      Quantity quantity,
      MonetaryAmount carryingValue,
      @Nullable MonetaryAmount roundedProjection,
      List<InventoryValuationMovement> movements) {
    return new InventoryValuationAccount(
        new AccountCode(accountCode),
        new AccountName(accountName),
        UNIT,
        quantity,
        carryingValue,
        roundedProjection,
        movements);
  }

  private static InventoryValuationMovement movement() {
    return movement(1L, InventoryMovementKind.DISPOSAL, -4L, -4000L);
  }

  private static InventoryValuationMovement movement(
      long accountSequence, InventoryMovementKind kind, long quantityDelta, long costDelta) {
    return new InventoryValuationMovement(
        new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
        LocalDate.parse("2026-07-10"),
        accountSequence,
        kind,
        quantityDelta,
        costDelta);
  }

  private static MonetaryAmount money(String minorUnits) {
    return new MonetaryAmount("EUR", minorUnits);
  }

  private static MonetaryAmount usd(String minorUnits) {
    return new MonetaryAmount("USD", minorUnits);
  }

  private static String csvValue(ReportCsvProjection projection, int rowIndex, String header) {
    return projection.rows().get(rowIndex).get(projection.headers().indexOf(header));
  }
}
