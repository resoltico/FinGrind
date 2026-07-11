package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.CashFlowRow;
import dev.erst.fingrind.contract.bookkeeping.CashFlowSection;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementReport;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementResult;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowSectionKind;
import dev.erst.fingrind.core.ComparativeSelection;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct contract-model coverage for public cash-flow statement types. */
class CashFlowStatementContractTypesTest {
  @Test
  void cashFlowStatementTypes_preserveCanonicalPayloads() {
    CashFlowStatementQuery query =
        new CashFlowStatementQuery(
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            ComparativeSelection.range(
                EffectiveDateRange.of(
                    LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30"))));

    CashFlowRow cashRow =
        new CashFlowRow(
            "1000",
            "Cash",
            AccountType.ASSET,
            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
            Optional.empty(),
            StatementLineKind.DECLARED_ACCOUNT,
            balance("EUR", "15.00", "0.00"));
    CashFlowRow revenueRow =
        new CashFlowRow(
            "4000",
            "Revenue",
            AccountType.REVENUE,
            Optional.empty(),
            Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE),
            StatementLineKind.DECLARED_ACCOUNT,
            balance("EUR", "0.00", "15.00"));
    CashFlowRow liabilityRow =
        new CashFlowRow(
            "2000",
            "Payables",
            AccountType.LIABILITY,
            Optional.of(FinancialPositionLineClassification.CURRENT_LIABILITY),
            Optional.empty(),
            StatementLineKind.DECLARED_ACCOUNT,
            balance("EUR", "0.00", "5.00"));

    List<CashFlowRow> operatingRows = new ArrayList<>(List.of(cashRow, revenueRow));
    List<CurrencyBalance> operatingTotals =
        new ArrayList<>(List.of(balance("EUR", "15.00", "15.00")));
    CashFlowSection operatingSection =
        new CashFlowSection(CashFlowSectionKind.OPERATING, operatingRows, operatingTotals);
    operatingRows.clear();
    operatingTotals.clear();

    List<CashFlowSection> sections = new ArrayList<>(List.of(operatingSection));
    List<CurrencyBalance> openingCashTotals =
        new ArrayList<>(List.of(balance("EUR", "10.00", "0.00")));
    List<CurrencyBalance> movementTotals =
        new ArrayList<>(List.of(balance("EUR", "15.00", "15.00")));
    List<CurrencyBalance> closingCashTotals =
        new ArrayList<>(List.of(balance("EUR", "25.00", "15.00")));
    List<CashFlowSection> comparativeSections = new ArrayList<>(List.of(operatingSection));
    List<CurrencyBalance> comparativeOpeningCashTotals =
        new ArrayList<>(List.of(balance("EUR", "8.00", "0.00")));
    List<CurrencyBalance> comparativeMovementTotals =
        new ArrayList<>(List.of(balance("EUR", "12.00", "12.00")));
    List<CurrencyBalance> comparativeClosingCashTotals =
        new ArrayList<>(List.of(balance("EUR", "20.00", "12.00")));

    CashFlowStatementReport report =
        new CashFlowStatementReport(
            ContractFixtures.bookIdentity(),
            query.effectiveDateFrom(),
            query.effectiveDateTo(),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.NON_CLOSING_POSTINGS,
            openingCashTotals,
            sections,
            movementTotals,
            closingCashTotals,
            comparativeOpeningCashTotals,
            comparativeSections,
            comparativeMovementTotals,
            comparativeClosingCashTotals);
    sections.clear();
    openingCashTotals.clear();
    movementTotals.clear();
    closingCashTotals.clear();
    comparativeSections.clear();
    comparativeOpeningCashTotals.clear();
    comparativeMovementTotals.clear();
    comparativeClosingCashTotals.clear();

    CashFlowStatementResult.Reported reported = new CashFlowStatementResult.Reported(report);
    BookQueryRejection.BookNotInitialized rejection = new BookQueryRejection.BookNotInitialized();
    CashFlowStatementResult.Rejected rejected = new CashFlowStatementResult.Rejected(rejection);

    assertEquals(LocalDate.parse("2026-04-01"), query.effectiveDateFrom());
    assertEquals(LocalDate.parse("2026-04-30"), query.effectiveDateTo());
    assertEquals(
        ComparativeSelection.range(
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30"))),
        query.comparativeSelection());
    assertEquals(AccountType.LIABILITY, liabilityRow.lineType());
    assertEquals(2, operatingSection.rows().size());
    assertEquals(1, operatingSection.totals().size());
    assertEquals(CashFlowSectionKind.OPERATING, report.sections().getFirst().sectionKind());
    assertEquals(1, report.openingCashTotals().size());
    assertEquals(1, report.movementTotals().size());
    assertEquals(1, report.closingCashTotals().size());
    assertEquals(1, report.comparativeSections().size());
    assertSame(report, reported.report());
    assertSame(report, reported.reported());
    assertNull(reported.rejection());
    assertSame(rejection, rejected.rejection());
    assertEquals("reported", reported.fold(ignored -> "reported", ignored -> "rejected"));
    assertEquals("rejected", rejected.fold(ignored -> "reported", ignored -> "rejected"));
  }

  @Test
  void cashFlowRows_enforceClassificationDoctrine() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CashFlowRow(
                "1000",
                "Cash",
                AccountType.ASSET,
                Optional.empty(),
                Optional.empty(),
                StatementLineKind.DECLARED_ACCOUNT,
                balance("EUR", "1.00", "0.00")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CashFlowRow(
                "1000",
                "Cash",
                AccountType.ASSET,
                Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE),
                StatementLineKind.DECLARED_ACCOUNT,
                balance("EUR", "1.00", "0.00")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CashFlowRow(
                "5000",
                "Expense",
                AccountType.EXPENSE,
                Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE),
                StatementLineKind.DECLARED_ACCOUNT,
                balance("EUR", "1.00", "0.00")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CashFlowRow(
                "5000",
                "Expense",
                AccountType.EXPENSE,
                Optional.empty(),
                Optional.empty(),
                StatementLineKind.DECLARED_ACCOUNT,
                balance("EUR", "1.00", "0.00")));
  }

  @Test
  void cashFlowStatementTypes_rejectInvalidInputs() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CashFlowStatementQuery(
                LocalDate.parse("2026-04-30"),
                LocalDate.parse("2026-04-01"),
                ComparativeSelection.none()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CashFlowStatementQuery(
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-30"),
                ComparativeSelection.range(
                    EffectiveDateRange.of(null, LocalDate.parse("2025-04-30")))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CashFlowStatementReport(
                ContractFixtures.bookIdentity(),
                LocalDate.parse("2026-04-30"),
                LocalDate.parse("2026-04-01"),
                EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
                PostingCoverage.ALL_POSTING_KINDS,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
    assertThrows(
        NullPointerException.class, () -> new CashFlowSection(nullOf(), List.of(), List.of()));
    assertThrows(NullPointerException.class, () -> new CashFlowStatementResult.Reported(nullOf()));
    assertThrows(NullPointerException.class, () -> new CashFlowStatementResult.Rejected(nullOf()));
    assertTrue(CashFlowSectionKind.wireValues().contains("OPERATING"));
  }

  private static CurrencyBalance balance(
      String currencyCode, String debitAmount, String creditAmount) {
    return CurrencyBalance.ofTotals(
        Money.parse(currencyCode, debitAmount), Money.parse(currencyCode, creditAmount));
  }
}
