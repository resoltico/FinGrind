package dev.erst.fingrind.contract.reportmodel;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleQuery;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleReport;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleResult;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleRow;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccrualCutoffKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Contract coverage for the accrual cut-off schedule and its shared report projection. */
class AccrualCutoffScheduleReportContractTest {
  @Test
  void builderProjectsEveryDurableLifecycleFactAcrossReportAndCsvSurfaces() {
    AccrualCutoffScheduleReport report =
        new AccrualCutoffScheduleReport(
            ReportModelTestSupport.bookIdentity(),
            Optional.of(LocalDate.parse("2026-06-30")),
            List.of(prepayment(), accruedExpense()));
    ReportModel model = AccrualCutoffScheduleReportModelBuilder.INSTANCE.build(report);
    ReportCsvProjection csv = Objects.requireNonNull(model.tabularCsvProjection());

    assertEquals("accrual-cutoff-schedule", model.family());
    assertEquals(ReportModel.Orientation.LANDSCAPE, model.orientation());
    assertEquals("2026-06-30", model.context().asOf());
    assertEquals("2", model.verdicts().getFirst().value());
    assertEquals(2, model.sections().getFirst().rows().size());
    assertEquals(
        List.of(
            "prepayment-2026-q2",
            "PREPAYMENT",
            "2026-04-25",
            "prepaid-expense",
            "operating-expense",
            "EUR 1200.00",
            "EUR 400.00",
            "EUR 800.00",
            "2026-04-25 through 2026-06-30",
            "2026-05-31"),
        model.sections().getFirst().rows().getFirst().cells());
    assertEquals("Not applicable", model.sections().getFirst().rows().get(1).cells().get(8));
    assertEquals("None", model.sections().getFirst().rows().get(1).cells().get(9));
    assertEquals("accrual-cutoff-schedule", csvValue(csv, 0, "exportFamily"));
    assertEquals("prepayment-2026-q2", csvValue(csv, 0, "accrualCutoffId"));
    assertEquals("80000", csvValue(csv, 0, "remainingAmountMinorUnits"));
    assertEquals("2026-04-25", csvValue(csv, 0, "recognitionStartDate"));
    assertEquals("", csvValue(csv, 1, "recognitionStartDate"));
    assertEquals("", csvValue(csv, 1, "latestApplicationEffectiveDate"));
  }

  @Test
  void builderUsesOneExplicitNoMatchesProjectionForAnEmptySchedule() {
    ReportModel model =
        AccrualCutoffScheduleReportModelBuilder.buildModel(
            new AccrualCutoffScheduleReport(
                ReportModelTestSupport.bookIdentity(), Optional.empty(), List.of()));
    ReportCsvProjection csv = Objects.requireNonNull(model.tabularCsvProjection());

    assertNull(model.context().asOf());
    assertTrue(model.sections().getFirst().rows().isEmpty());
    assertTrue(
        model.sections().getFirst().verdicts().stream()
            .anyMatch(verdict -> verdict.value().contains("No accrual cut-offs matched")));
    assertEquals("accrual-cutoff-schedule:scope-empty", csvValue(csv, 0, "rowId"));
    assertTrue(csvValue(csv, 0, "message").contains("No accrual cut-offs matched"));
  }

  @Test
  void scheduleValueObjectsProtectExactLifecycleArithmeticAndOutcomeSemantics() {
    AccrualCutoffScheduleReport report =
        new AccrualCutoffScheduleReport(
            ReportModelTestSupport.bookIdentity(), Optional.empty(), List.of(prepayment()));
    AccrualCutoffScheduleResult.Reported reported =
        new AccrualCutoffScheduleResult.Reported(report);
    BookQueryRejection rejection = new BookQueryRejection.BookNotInitialized();
    AccrualCutoffScheduleResult.Rejected rejected =
        new AccrualCutoffScheduleResult.Rejected(rejection);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            row(
                AccrualCutoffKind.PREPAYMENT,
                money("120000"),
                money("40000"),
                money("79999"),
                Optional.of(LocalDate.parse("2026-04-25")),
                Optional.of(LocalDate.parse("2026-06-30"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            row(
                AccrualCutoffKind.PREPAYMENT,
                new MonetaryAmount("USD", "120000"),
                money("40000"),
                money("80000"),
                Optional.of(LocalDate.parse("2026-04-25")),
                Optional.of(LocalDate.parse("2026-06-30"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            row(
                AccrualCutoffKind.PREPAYMENT,
                money("120000"),
                money("40000"),
                new MonetaryAmount("USD", "80000"),
                Optional.of(LocalDate.parse("2026-04-25")),
                Optional.of(LocalDate.parse("2026-06-30"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            row(
                AccrualCutoffKind.ACCRUED_EXPENSE,
                money("120000"),
                money("40000"),
                money("80000"),
                Optional.of(LocalDate.parse("2026-04-25")),
                Optional.of(LocalDate.parse("2026-06-30"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            row(
                AccrualCutoffKind.DEFERRED_REVENUE,
                money("120000"),
                money("40000"),
                money("80000"),
                Optional.empty(),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            row(
                AccrualCutoffKind.PREPAYMENT,
                money("120000"),
                money("40000"),
                money("80000"),
                Optional.of(LocalDate.parse("2026-04-25")),
                Optional.empty()));
    assertEquals(
        Optional.of(LocalDate.parse("2026-06-30")),
        new AccrualCutoffScheduleQuery(Optional.of(LocalDate.parse("2026-06-30")))
            .effectiveDateAsOf());
    assertThrows(NullPointerException.class, () -> new AccrualCutoffScheduleQuery(nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new AccrualCutoffScheduleReport(nullOf(), Optional.empty(), List.of(prepayment())));
    assertThrows(
        NullPointerException.class, () -> new AccrualCutoffScheduleResult.Reported(nullOf()));
    assertThrows(
        NullPointerException.class, () -> new AccrualCutoffScheduleResult.Rejected(nullOf()));
    assertSame(report, reported.reported());
    assertNull(reported.rejection());
    assertNull(rejected.reported());
    assertSame(rejection, rejected.rejection());
    assertEquals("reported", reported.fold(value -> "reported", value -> "rejected"));
    assertEquals("rejected", rejected.fold(value -> "reported", value -> "rejected"));
  }

  private static AccrualCutoffScheduleRow prepayment() {
    return row(
        AccrualCutoffKind.PREPAYMENT,
        money("120000"),
        money("40000"),
        money("80000"),
        Optional.of(LocalDate.parse("2026-04-25")),
        Optional.of(LocalDate.parse("2026-06-30")));
  }

  private static AccrualCutoffScheduleRow accruedExpense() {
    return new AccrualCutoffScheduleRow(
        new AccrualCutoffId("accrued-expense-2026-04"),
        AccrualCutoffKind.ACCRUED_EXPENSE,
        LocalDate.parse("2026-04-25"),
        new AccountCode("accrued-expense"),
        new AccountCode("operating-expense"),
        money("120000"),
        money("40000"),
        money("80000"),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  private static AccrualCutoffScheduleRow row(
      AccrualCutoffKind kind,
      MonetaryAmount originalAmount,
      MonetaryAmount appliedAmount,
      MonetaryAmount remainingAmount,
      Optional<LocalDate> recognitionStartDate,
      Optional<LocalDate> recognitionEndDate) {
    return new AccrualCutoffScheduleRow(
        new AccrualCutoffId("prepayment-2026-q2"),
        kind,
        LocalDate.parse("2026-04-25"),
        new AccountCode("prepaid-expense"),
        new AccountCode("operating-expense"),
        originalAmount,
        appliedAmount,
        remainingAmount,
        recognitionStartDate,
        recognitionEndDate,
        Optional.of(LocalDate.parse("2026-05-31")));
  }

  private static MonetaryAmount money(String minorUnits) {
    return new MonetaryAmount("EUR", minorUnits);
  }

  private static String csvValue(ReportCsvProjection projection, int rowIndex, String header) {
    return projection.rows().get(rowIndex).get(projection.headers().indexOf(header));
  }
}
