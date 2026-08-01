package dev.erst.fingrind.contract.reportmodel;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.StatementLineKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Coverage tests for shared report-model value objects, displays, and support helpers. */
class ReportModelSupportCoverageTest {
  @Test
  void sharedReportTypes_validateAndProjectContextRows() {
    ReportColumn labelColumn = ReportModelSupport.leftColumn("label", "Label");
    ReportColumn valueColumn = ReportModelSupport.rightColumn("value", "Value");
    ReportRow row = ReportModelSupport.row("row-1", "Cash", "EUR 10.00");
    ReportTotals totals =
        ReportModelSupport.totals(
            "totals", "Totals", List.of(labelColumn, valueColumn), List.of(row));
    ReportSection section =
        ReportModelSupport.section(
            "section",
            "Section",
            List.of(new ReportVerdict("Outcome", "Present")),
            List.of(labelColumn, valueColumn),
            List.of(row),
            List.of(totals));
    ReportContext context =
        ReportModelSupport.context(
            ReportModelTestSupport.bookIdentity(),
            PostingCoverage.NON_CLOSING_POSTINGS,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            List.of(new ReportVerdict("Supplement", "Yes")));
    ReportContext taxContext =
        ReportModelSupport.taxContext(
            ReportModelTestSupport.bookIdentity(),
            "vat-lv",
            "Latvia VAT",
            "LV",
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            LocalDate.parse("2026-05-20"));
    ReportContext minimalContext =
        ReportModelSupport.context(
            ReportModelTestSupport.bookIdentity(),
            null,
            null,
            null,
            null,
            EffectiveDateRange.unbounded(),
            List.of());
    ReportModel model =
        new ReportModel(
            "trial-balance",
            "Trial Balance",
            ReportModel.Orientation.LANDSCAPE,
            context,
            List.of(new ReportVerdict("Status", "Ready")),
            List.of(section));
    ReportSection totalsFreeSection =
        ReportStatementModelSupport.accountTypeStatementSection(
            "current",
            "",
            AccountType.ASSET,
            List.of(
                ReportStatementModelSupport.statementSectionRow(
                    "1000",
                    "EUR",
                    "Cash",
                    StatementLineKind.DECLARED_ACCOUNT,
                    "Current asset",
                    Money.parse("EUR", "15.00"),
                    BalanceSide.DEBIT)),
            List.of());

    assertEquals("left", labelColumn.alignment().wireValue());
    assertEquals("right", valueColumn.alignment().wireValue());
    assertEquals("landscape", model.orientation().wireValue());
    assertEquals(13, context.rows().size());
    assertEquals(6, minimalContext.rows().size());
    assertTrue(totalsFreeSection.totals().isEmpty());
    assertTrue(ReportStatementModelSupport.hasRenderableContent(List.of("row"), List.of()));
    assertTrue(ReportStatementModelSupport.hasRenderableContent(List.of(), List.of("total")));
    assertFalse(ReportStatementModelSupport.hasRenderableContent(List.of(), List.of()));
    assertTrue(
        taxContext.rows().stream()
            .anyMatch(
                rowValue ->
                    "Due date".equals(rowValue.label()) && "2026-05-20".equals(rowValue.value())));
    assertDoesNotThrow(
        () ->
            ReportModelSupport.requireCellWidth(
                List.of(), List.of(new ReportRow("ignored", List.of("too", "wide"))), "rows"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ReportSection(
                "bad",
                "Bad",
                List.of(),
                List.of(labelColumn),
                List.of(new ReportRow("bad-row", List.of("one", "two"))),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ReportTotals(
                "bad",
                "Bad",
                List.of(labelColumn),
                List.of(new ReportRow("bad-row", List.of("one", "two")))));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ReportColumn(" ", "Title", ReportColumn.Alignment.LEFT));
    assertThrows(IllegalArgumentException.class, () -> new ReportVerdict(" ", "value"));
  }

  @Test
  void displayHelpers_coverCanonicalMappings() {
    DeclaredAccount cashAccount =
        ReportModelTestSupport.declaredAccount("1000", "Cash", AccountType.ASSET, true);

    assertEquals("EUR 12.34", ReportModelDisplay.displayMoney(Money.parse("EUR", "12.34")));
    assertEquals(
        "EUR 12.34",
        ReportModelDisplay.displayAmount(MonetaryAmount.of(Money.parse("EUR", "12.34"))));
    assertEquals("Yes", ReportModelDisplay.displayBoolean(true));
    assertEquals("No", ReportModelDisplay.displayBoolean(false));
    assertEquals("Debit", ReportModelDisplay.displayBalanceSide(BalanceSide.DEBIT));
    assertEquals("Credit", ReportModelDisplay.displayBalanceSide(BalanceSide.CREDIT));
    assertEquals("Zero", ReportModelDisplay.displayBalanceSide(BalanceSide.ZERO));
    assertEquals("Balanced", ReportModelDisplay.displayBalanceState(true));
    assertEquals("Imbalanced", ReportModelDisplay.displayBalanceState(false));
    assertEquals(
        "All posting kinds",
        ReportModelDisplay.displayPostingCoverage(PostingCoverage.ALL_POSTING_KINDS));
    assertEquals(
        "Non-close postings",
        ReportModelDisplay.displayPostingCoverage(PostingCoverage.NON_CLOSING_POSTINGS));
    assertEquals("Cash [1000]", ReportModelDisplay.accountLabel(cashAccount));
    assertEquals("Asset", ReportModelDisplay.displayLineType(AccountType.ASSET));
    assertEquals("Liability", ReportModelDisplay.displayLineType(AccountType.LIABILITY));
    assertEquals("Equity", ReportModelDisplay.displayLineType(AccountType.EQUITY));
    assertEquals("Revenue", ReportModelDisplay.displayLineType(AccountType.REVENUE));
    assertEquals("Expense", ReportModelDisplay.displayLineType(AccountType.EXPENSE));
    assertEquals(
        "Calculated line",
        ReportModelDisplay.displayStatementLineCode(
            "current-result", StatementLineKind.CURRENT_PERIOD_RESULT));
    assertEquals(
        "Current period result",
        ReportModelDisplay.displayStatementLineKind(StatementLineKind.CURRENT_PERIOD_RESULT));
    assertEquals(
        "Account", ReportModelDisplay.displayStatementLineKind(StatementLineKind.DECLARED_ACCOUNT));
    assertEquals("Debit", ReportModelDisplay.displayNormalBalance(cashAccount.normalBalance()));
    assertEquals(
        "Direct journal",
        ReportModelDisplay.displayPostingOriginKind(PostingOriginKind.DIRECT_JOURNAL));
    assertEquals(
        "Fiscal-year close",
        ReportModelDisplay.displayPostingOriginKind(PostingOriginKind.FISCAL_YEAR_CLOSE));
    for (PostingOriginKind postingOriginKind : PostingOriginKind.values()) {
      assertFalse(ReportModelDisplay.displayPostingOriginKind(postingOriginKind).isBlank());
    }
  }

  @Test
  void classificationHelpers_coverCanonicalMappings() {
    assertEquals(
        "Assets", ReportModelClassificationDisplay.displayAccountTypeSection(AccountType.ASSET));
    assertEquals(
        "Liabilities",
        ReportModelClassificationDisplay.displayAccountTypeSection(AccountType.LIABILITY));
    assertEquals(
        "Equity", ReportModelClassificationDisplay.displayAccountTypeSection(AccountType.EQUITY));
    assertEquals(
        "Revenue", ReportModelClassificationDisplay.displayAccountTypeSection(AccountType.REVENUE));
    assertEquals(
        "Expenses",
        ReportModelClassificationDisplay.displayAccountTypeSection(AccountType.EXPENSE));
    assertEquals(
        "Calculated line",
        ReportModelClassificationDisplay.displayFinancialPositionClassification(Optional.empty()));
    assertEquals(
        "Asset (Current asset)",
        ReportModelClassificationDisplay.displayCashFlowClassification(
            AccountType.ASSET,
            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
            Optional.empty()));
    assertEquals(
        "Revenue (Operating revenue)",
        ReportModelClassificationDisplay.displayCashFlowClassification(
            AccountType.REVENUE,
            Optional.empty(),
            Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE)));
    assertEquals(
        "Equity (Calculated line)",
        ReportModelClassificationDisplay.displayCashFlowClassification(
            AccountType.EQUITY, Optional.empty(), Optional.empty()));
    assertEquals(
        "Operating",
        ReportModelClassificationDisplay.displayCashFlowSection(
            dev.erst.fingrind.core.CashFlowSectionKind.OPERATING));
    assertEquals(
        "Investing",
        ReportModelClassificationDisplay.displayCashFlowSection(
            dev.erst.fingrind.core.CashFlowSectionKind.INVESTING));
    assertEquals(
        "Financing",
        ReportModelClassificationDisplay.displayCashFlowSection(
            dev.erst.fingrind.core.CashFlowSectionKind.FINANCING));
    for (FinancialPositionLineClassification classification :
        FinancialPositionLineClassification.values()) {
      assertFalse(
          ReportModelClassificationDisplay.displayFinancialPositionClassification(classification)
              .isBlank());
    }
    for (ProfitAndLossLineClassification classification :
        ProfitAndLossLineClassification.values()) {
      assertFalse(
          ReportModelClassificationDisplay.displayProfitAndLossClassification(classification)
              .isBlank());
    }
  }

  @Test
  void narrativeHelpers_coverDirectReversalAndRangeBranches() {
    DeclaredAccount cashAccount =
        ReportModelTestSupport.declaredAccount("1000", "Cash", AccountType.ASSET, true);
    var selfPostingFact =
        ReportModelTestSupport.postingFact(
            "posting-self",
            PostingOriginKind.DIRECT_JOURNAL,
            PostingLineage.direct(),
            ReportModelTestSupport.journalLine("1000", JournalLine.EntrySide.DEBIT, "10.00"),
            ReportModelTestSupport.journalLine("1000", JournalLine.EntrySide.CREDIT, "10.00"));
    var counterpartPostingFact =
        ReportModelTestSupport.postingFact(
            "posting-counterparts",
            PostingOriginKind.DIRECT_JOURNAL,
            PostingLineage.direct(),
            ReportModelTestSupport.journalLine("1000", JournalLine.EntrySide.DEBIT, "20.00"),
            ReportModelTestSupport.journalLine("2000", JournalLine.EntrySide.CREDIT, "5.00"),
            ReportModelTestSupport.journalLine("3000", JournalLine.EntrySide.CREDIT, "15.00"));
    var reversalPostingFact =
        ReportModelTestSupport.postingFact(
            "posting-reversal",
            PostingOriginKind.REVERSAL,
            PostingLineage.reversal(
                new ReversalReference(new PostingId("e888fd00-a501-341d-9a6b-8d9059757d1b")),
                new ReversalReason("operator reversal")),
            ReportModelTestSupport.journalLine("1000", JournalLine.EntrySide.DEBIT, "10.00"),
            ReportModelTestSupport.journalLine("2000", JournalLine.EntrySide.CREDIT, "10.00"));
    AccountLedgerEntry ledgerEntry =
        ReportModelTestSupport.accountLedgerEntry(
            counterpartPostingFact,
            ReportModelTestSupport.balance("EUR", "20.00", "0.00"),
            "20.00",
            BalanceSide.DEBIT);

    assertEquals("(self)", ReportModelNarrative.counterpartAccounts(cashAccount, selfPostingFact));
    assertEquals(
        "2000, 3000",
        ReportModelNarrative.counterpartAccounts(cashAccount, counterpartPostingFact));
    assertEquals(
        "Direct journal / Direct posting",
        ReportModelNarrative.accountLedgerEntrySummary(selfPostingFact));
    assertEquals(
        "Reversal / Reversal posting of e888fd00-a501-341d-9a6b-8d9059757d1b",
        ReportModelNarrative.accountLedgerEntrySummary(reversalPostingFact));
    assertEquals("EUR 20.00 Debit", ReportModelNarrative.runningBalance(ledgerEntry));
    assertFalse(
        ReportModelNarrative.hasMeaningfulBalances(
            List.of(ReportModelTestSupport.balance("EUR", "0.00", "0.00"))));
    assertTrue(
        ReportModelNarrative.hasMeaningfulBalances(
            List.of(
                ReportModelTestSupport.balance("EUR", "0.00", "0.00"),
                ReportModelTestSupport.balance("EUR", "10.00", "0.00"))));
    assertTrue(
        ReportModelNarrative.hasMeaningfulBalances(
            List.of(ReportModelTestSupport.balance("EUR", "0.00", "5.00"))));
    assertEquals("Zero across all currencies.", ReportModelNarrative.joinedBalancesText(List.of()));
    assertTrue(
        ReportModelNarrative.joinedBalancesText(
                List.of(ReportModelTestSupport.balance("EUR", "10.00", "0.00")))
            .contains("EUR 10.00 debit"));
    assertTrue(
        ReportModelNarrative.joinedBalancesText(
                List.of(
                    ReportModelTestSupport.balance("EUR", "10.00", "0.00"),
                    ReportModelTestSupport.balance("USD", "0.00", "8.00")))
            .contains(", "));
    assertEquals(
        "No ledger entries matched the selected scope.",
        ReportModelNarrative.noMatches("ledger entries"));
    assertEquals(
        "book start to latest effective date in the selected book",
        ReportModelNarrative.dateRange(null, null));
    assertEquals(
        "2026-04-01 to 2026-04-30",
        ReportModelNarrative.dateRange(
            LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")));
    assertEquals(
        "book start to current book horizon",
        ReportModelNarrative.comparativeRange(EffectiveDateRange.unbounded()));
    assertEquals(
        "book start to 2025-04-30",
        ReportModelNarrative.comparativeRange(
            EffectiveDateRange.of(null, LocalDate.parse("2025-04-30"))));
  }
}
