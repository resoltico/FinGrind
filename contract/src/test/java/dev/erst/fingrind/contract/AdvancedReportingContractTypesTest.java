package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.reporting.CashFlowLine;
import dev.erst.fingrind.contract.reporting.CashFlowQuery;
import dev.erst.fingrind.contract.reporting.CashFlowReport;
import dev.erst.fingrind.contract.reporting.CashFlowResult;
import dev.erst.fingrind.contract.reporting.ComprehensiveIncomeQuery;
import dev.erst.fingrind.contract.reporting.ComprehensiveIncomeReport;
import dev.erst.fingrind.contract.reporting.ComprehensiveIncomeResult;
import dev.erst.fingrind.contract.reporting.ComprehensiveIncomeRow;
import dev.erst.fingrind.contract.reporting.DisclosureNote;
import dev.erst.fingrind.contract.reporting.DisclosurePack;
import dev.erst.fingrind.contract.reporting.DisclosurePackQuery;
import dev.erst.fingrind.contract.reporting.DisclosurePackResult;
import dev.erst.fingrind.core.CashFlowActivity;
import dev.erst.fingrind.core.DisclosureNoteKind;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.OtherComprehensiveIncomeClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.SourceDocument;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentNumber;
import dev.erst.fingrind.core.SourceDocumentType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for advanced reporting public contract value types. */
class AdvancedReportingContractTypesTest {
  @Test
  void reportingQueriesReportsAndResultsPreserveCanonicalState() {
    ReportingPeriod reportingPeriod =
        new ReportingPeriod(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"));
    CashFlowQuery cashFlowQuery = new CashFlowQuery(reportingPeriod);
    ComprehensiveIncomeQuery comprehensiveIncomeQuery =
        new ComprehensiveIncomeQuery(reportingPeriod);
    DisclosurePackQuery disclosurePackQuery = new DisclosurePackQuery(reportingPeriod);
    List<CashFlowLine> mutableCashFlowLines =
        new ArrayList<>(
            List.of(
                new CashFlowLine(
                    CashFlowActivity.OPERATING,
                    "operating-cash",
                    "Operating Cash",
                    Money.parse("EUR", "15.00"))));
    List<ComprehensiveIncomeRow> mutableProfitOrLossRows =
        new ArrayList<>(
            List.of(
                new ComprehensiveIncomeRow(
                    "operating-profit",
                    "Operating Profit",
                    Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE),
                    Optional.empty(),
                    Money.parse("EUR", "30.00"))));
    List<ComprehensiveIncomeRow> mutableOciRows =
        new ArrayList<>(
            List.of(
                new ComprehensiveIncomeRow(
                    "fx-translation",
                    "FX Translation",
                    Optional.empty(),
                    Optional.of(
                        OtherComprehensiveIncomeClassification.FOREIGN_CURRENCY_TRANSLATION),
                    Money.parse("EUR", "4.00"))));
    List<DisclosureNote> mutableNotes =
        new ArrayList<>(
            List.of(
                new DisclosureNote(
                    DisclosureNoteKind.ACCOUNTING_POLICIES,
                    "Accounting Policies",
                    List.of("Revenue is recognized when control passes."),
                    List.of(sourceDocument("policy-doc")))));

    CashFlowReport cashFlowReport =
        new CashFlowReport(
            ContractFixtures.bookIdentity(),
            cashFlowQuery.reportingPeriod(),
            mutableCashFlowLines,
            Money.parse("EUR", "15.00"));
    ComprehensiveIncomeReport comprehensiveIncomeReport =
        new ComprehensiveIncomeReport(
            ContractFixtures.bookIdentity(),
            comprehensiveIncomeQuery.reportingPeriod(),
            mutableProfitOrLossRows,
            mutableOciRows,
            Money.parse("EUR", "30.00"),
            Money.parse("EUR", "4.00"),
            Money.parse("EUR", "34.00"));
    DisclosurePack disclosurePack =
        new DisclosurePack(
            ContractFixtures.bookIdentity(), disclosurePackQuery.reportingPeriod(), mutableNotes);
    CashFlowResult.Computed computedCashFlow = new CashFlowResult.Computed(cashFlowReport);
    CashFlowResult.Rejected rejectedCashFlow =
        new CashFlowResult.Rejected(new BookQueryRejection.BookNotInitialized());
    ComprehensiveIncomeResult.Computed computedComprehensiveIncome =
        new ComprehensiveIncomeResult.Computed(comprehensiveIncomeReport);
    ComprehensiveIncomeResult.Rejected rejectedComprehensiveIncome =
        new ComprehensiveIncomeResult.Rejected(new BookQueryRejection.BookNotInitialized());
    DisclosurePackResult.Computed computedDisclosurePack =
        new DisclosurePackResult.Computed(disclosurePack);
    DisclosurePackResult.Rejected rejectedDisclosurePack =
        new DisclosurePackResult.Rejected(new BookQueryRejection.BookNotInitialized());

    mutableCashFlowLines.clear();
    mutableProfitOrLossRows.clear();
    mutableOciRows.clear();
    mutableNotes.clear();

    assertEquals(reportingPeriod, cashFlowQuery.reportingPeriod());
    assertEquals(1, cashFlowReport.lines().size());
    assertEquals(reportingPeriod, comprehensiveIncomeQuery.reportingPeriod());
    assertEquals(1, comprehensiveIncomeReport.profitOrLossRows().size());
    assertEquals(1, comprehensiveIncomeReport.otherComprehensiveIncomeRows().size());
    assertEquals(reportingPeriod, disclosurePackQuery.reportingPeriod());
    assertEquals(1, disclosurePack.notes().size());
    assertEquals(cashFlowReport, computedCashFlow.report());
    assertInstanceOf(BookQueryRejection.BookNotInitialized.class, rejectedCashFlow.rejection());
    assertEquals(comprehensiveIncomeReport, computedComprehensiveIncome.report());
    assertInstanceOf(
        BookQueryRejection.BookNotInitialized.class, rejectedComprehensiveIncome.rejection());
    assertEquals(disclosurePack, computedDisclosurePack.pack());
    assertInstanceOf(
        BookQueryRejection.BookNotInitialized.class, rejectedDisclosurePack.rejection());
  }

  @Test
  void reportingValueTypesRejectInvalidInputs() {
    IllegalArgumentException blankCashFlowLineCode =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CashFlowLine(
                    CashFlowActivity.OPERATING,
                    "  ",
                    "Operating Cash",
                    Money.parse("EUR", "1.00")));
    IllegalArgumentException missingComprehensiveClassification =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ComprehensiveIncomeRow(
                    "bad-row",
                    "Bad Row",
                    Optional.empty(),
                    Optional.empty(),
                    Money.parse("EUR", "1.00")));
    IllegalArgumentException blankComprehensiveLineName =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ComprehensiveIncomeRow(
                    "good-code",
                    "  ",
                    Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE),
                    Optional.empty(),
                    Money.parse("EUR", "1.00")));
    IllegalArgumentException blankDisclosureParagraph =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new DisclosureNote(
                    DisclosureNoteKind.OTHER,
                    "Other",
                    List.of(" "),
                    List.of(sourceDocument("other-doc"))));

    assertEquals("lineCode must not be blank.", blankCashFlowLineCode.getMessage());
    assertEquals(
        "Comprehensive-income row must declare either profit/loss or OCI classification.",
        missingComprehensiveClassification.getMessage());
    assertEquals("lineName must not be blank.", blankComprehensiveLineName.getMessage());
    assertEquals("paragraph must not be blank.", blankDisclosureParagraph.getMessage());
  }

  private static SourceDocument sourceDocument(String id) {
    return new SourceDocument(
        new SourceDocumentId(id),
        SourceDocumentType.OTHER,
        LocalDate.parse("2026-04-01"),
        new SourceDocumentNumber("DOC-" + id),
        Optional.of("Support document"));
  }
}
