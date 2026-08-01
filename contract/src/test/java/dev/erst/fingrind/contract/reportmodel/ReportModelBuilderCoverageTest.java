package dev.erst.fingrind.contract.reportmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.PeriodCurrencySummary;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxObligationReport;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.ReportingPeriod;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Coverage tests for bookkeeping-oriented shared report-model builders. */
class ReportModelBuilderCoverageTest {
  @Test
  void accountBalanceBuilder_coversPopulatedAndEmptyBalances() {
    var cashAccount =
        ReportModelTestSupport.declaredAccount("1000", "Cash", AccountType.ASSET, true);
    ReportModel populated =
        AccountBalanceReportModelBuilder.INSTANCE.build(
            new AccountBalanceSnapshot(
                ReportModelTestSupport.bookIdentity(),
                cashAccount,
                Optional.of(LocalDate.parse("2026-04-01")),
                Optional.of(LocalDate.parse("2026-04-30")),
                PostingCoverage.ALL_POSTING_KINDS,
                List.of(ReportModelTestSupport.balance("EUR", "15.00", "0.00"))));
    ReportModel empty =
        AccountBalanceReportModelBuilder.INSTANCE.build(
            new AccountBalanceSnapshot(
                ReportModelTestSupport.bookIdentity(),
                cashAccount,
                Optional.empty(),
                Optional.empty(),
                PostingCoverage.ALL_POSTING_KINDS,
                List.of()));

    assertEquals("account-balance", populated.family());
    assertEquals("Account Balance", populated.title());
    assertEquals(1, populated.sections().getFirst().rows().size());
    assertTrue(
        empty.sections().getFirst().verdicts().stream()
            .anyMatch(
                verdict -> "No balances matched the selected scope.".equals(verdict.value())));
  }

  @Test
  void trialBalanceBuilder_coversCurrentAndComparativeBranches() {
    var cashAccount =
        ReportModelTestSupport.declaredAccount("1000", "Cash", AccountType.ASSET, true);
    var payablesAccount =
        ReportModelTestSupport.declaredAccount("2000", "Payables", AccountType.LIABILITY, false);
    ReportModel populated =
        TrialBalanceReportModelBuilder.INSTANCE.build(
            new TrialBalanceReport(
                ReportModelTestSupport.bookIdentity(),
                Optional.of(LocalDate.parse("2026-04-30")),
                Optional.of(LocalDate.parse("2026-04-30")),
                EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
                PostingCoverage.ALL_POSTING_KINDS,
                List.of(
                    new TrialBalanceRow(
                        cashAccount, ReportModelTestSupport.balance("EUR", "15.00", "0.00"))),
                List.of(ReportModelTestSupport.balance("EUR", "15.00", "0.00")),
                true,
                List.of(
                    new TrialBalanceRow(
                        payablesAccount, ReportModelTestSupport.balance("EUR", "0.00", "5.00"))),
                List.of(ReportModelTestSupport.balance("EUR", "0.00", "5.00")),
                false));
    ReportModel empty =
        TrialBalanceReportModelBuilder.INSTANCE.build(
            new TrialBalanceReport(
                ReportModelTestSupport.bookIdentity(),
                Optional.empty(),
                Optional.empty(),
                EffectiveDateRange.unbounded(),
                PostingCoverage.ALL_POSTING_KINDS,
                List.of(),
                List.of(),
                false,
                List.of(),
                List.of(),
                false));
    ReportModel comparativeWithoutTotals =
        TrialBalanceReportModelBuilder.INSTANCE.build(
            new TrialBalanceReport(
                ReportModelTestSupport.bookIdentity(),
                Optional.of(LocalDate.parse("2026-04-30")),
                Optional.empty(),
                EffectiveDateRange.of(null, LocalDate.parse("2025-04-30")),
                PostingCoverage.ALL_POSTING_KINDS,
                List.of(
                    new TrialBalanceRow(
                        cashAccount, ReportModelTestSupport.balance("EUR", "15.00", "0.00"))),
                List.of(),
                true,
                List.of(
                    new TrialBalanceRow(
                        payablesAccount, ReportModelTestSupport.balance("EUR", "0.00", "5.00"))),
                List.of(),
                false));
    ReportModel comparativeTotalsOnly =
        TrialBalanceReportModelBuilder.INSTANCE.build(
            new TrialBalanceReport(
                ReportModelTestSupport.bookIdentity(),
                Optional.of(LocalDate.parse("2026-04-30")),
                Optional.empty(),
                EffectiveDateRange.of(null, LocalDate.parse("2025-04-30")),
                PostingCoverage.ALL_POSTING_KINDS,
                List.of(),
                List.of(),
                true,
                List.of(),
                List.of(ReportModelTestSupport.balance("EUR", "0.00", "5.00")),
                false));

    assertEquals(2, populated.sections().size());
    assertTrue(
        populated.sections().get(1).verdicts().stream()
            .anyMatch(
                verdict ->
                    "Balance state".equals(verdict.label())
                        && "Imbalanced".equals(verdict.value())));
    assertTrue(
        empty.sections().getFirst().verdicts().stream()
            .anyMatch(
                verdict ->
                    "No account balances matched the selected scope.".equals(verdict.value())));
    assertTrue(comparativeWithoutTotals.sections().get(1).totals().isEmpty());
    assertEquals(2, comparativeTotalsOnly.sections().size());
  }

  @Test
  void accountLedgerBuilder_coversNarrativeBranches() {
    var cashAccount =
        ReportModelTestSupport.declaredAccount("1000", "Cash", AccountType.ASSET, true);
    var directPosting =
        ReportModelTestSupport.postingFact(
            "posting-direct",
            PostingOriginKind.DIRECT_JOURNAL,
            PostingLineage.direct(),
            ReportModelTestSupport.journalLine("1000", JournalLine.EntrySide.DEBIT, "15.00"),
            ReportModelTestSupport.journalLine("2000", JournalLine.EntrySide.CREDIT, "15.00"));
    var reversalPosting =
        ReportModelTestSupport.postingFact(
            "posting-reversal",
            PostingOriginKind.REVERSAL,
            PostingLineage.reversal(
                new dev.erst.fingrind.core.ReversalReference(
                    new dev.erst.fingrind.core.PostingId("e888fd00-a501-341d-9a6b-8d9059757d1b")),
                new dev.erst.fingrind.core.ReversalReason("operator reversal")),
            ReportModelTestSupport.journalLine("1000", JournalLine.EntrySide.DEBIT, "10.00"),
            ReportModelTestSupport.journalLine("3000", JournalLine.EntrySide.CREDIT, "10.00"));
    ReportModel populated =
        AccountLedgerReportModelBuilder.INSTANCE.build(
            new AccountLedgerReport(
                ReportModelTestSupport.bookIdentity(),
                cashAccount,
                EffectiveDateRange.of(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
                PostingCoverage.NON_CLOSING_POSTINGS,
                new dev.erst.fingrind.contract.bookkeeping.AccountLedgerPagination(
                    50,
                    java.util.Optional.empty(),
                    java.util.Optional.of(
                        new dev.erst.fingrind.contract.bookkeeping.AccountLedgerPageCursor(
                            LocalDate.parse("2026-04-09"),
                            java.time.Instant.parse("2026-04-09T10:15:30Z"),
                            new dev.erst.fingrind.core.PostingId(
                                "d335bf0a-b735-3860-ba2e-fcb74daf48d5")))),
                List.of(ReportModelTestSupport.balance("EUR", "2.00", "0.00")),
                List.of(
                    new dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry(
                        directPosting,
                        ReportModelTestSupport.balance("EUR", "15.00", "0.00"),
                        dev.erst.fingrind.core.Money.parse("EUR", "17.00"),
                        dev.erst.fingrind.core.BalanceSide.DEBIT,
                        new dev.erst.fingrind.contract.bookkeeping.AttestationCommit(
                            BigInteger.valueOf(42),
                            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")),
                    ReportModelTestSupport.accountLedgerEntry(
                        reversalPosting,
                        ReportModelTestSupport.balance("EUR", "10.00", "0.00"),
                        "27.00",
                        dev.erst.fingrind.core.BalanceSide.DEBIT)),
                List.of(ReportModelTestSupport.balance("EUR", "27.00", "0.00"))));
    ReportModel empty =
        AccountLedgerReportModelBuilder.INSTANCE.build(
            new AccountLedgerReport(
                ReportModelTestSupport.bookIdentity(),
                cashAccount,
                EffectiveDateRange.unbounded(),
                PostingCoverage.ALL_POSTING_KINDS,
                new dev.erst.fingrind.contract.bookkeeping.AccountLedgerPagination(
                    50, java.util.Optional.empty(), java.util.Optional.empty()),
                List.of(ReportModelTestSupport.balance("EUR", "0.00", "0.00")),
                List.of(),
                List.of(ReportModelTestSupport.balance("EUR", "0.00", "0.00"))));

    assertEquals(2, populated.sections().getFirst().rows().size());
    assertEquals(1, populated.sections().size());
    assertEquals("Attestation order", populated.sections().getFirst().columns().getLast().title());
    assertEquals("42", populated.sections().getFirst().rows().getFirst().cells().getLast());
    assertFalse(
        populated.sections().stream()
            .flatMap(section -> section.rows().stream())
            .flatMap(row -> row.cells().stream())
            .anyMatch("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"::equals));
    assertTrue(
        populated.verdicts().stream()
            .anyMatch(verdict -> "Opening Balances".equals(verdict.label())));
    assertTrue(
        populated.verdicts().stream()
            .anyMatch(
                verdict ->
                    "Next cursor".equals(verdict.label()) && !"(none)".equals(verdict.value())));
    assertTrue(
        empty.verdicts().stream()
            .anyMatch(
                verdict ->
                    "No ledger entries matched the selected scope.".equals(verdict.value())));
    assertFalse(
        empty.verdicts().stream().anyMatch(verdict -> "Opening Balances".equals(verdict.label())));
    assertEquals(1, empty.sections().size());
  }

  @Test
  void periodSummaryBuilder_coversPopulatedAndEmptySections() {
    var cashAccount =
        ReportModelTestSupport.declaredAccount("1000", "Cash", AccountType.ASSET, true);
    ReportModel populated =
        PeriodSummaryReportModelBuilder.INSTANCE.build(
            new PeriodSummaryReport(
                ReportModelTestSupport.bookIdentity(),
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-30"),
                PostingCoverage.ALL_POSTING_KINDS,
                2,
                4,
                1,
                List.of(
                    new PeriodCurrencySummary(
                        ReportModelTestSupport.balance("EUR", "15.00", "0.00"))),
                List.of(
                    new PeriodAccountActivityRow(
                        cashAccount, ReportModelTestSupport.balance("EUR", "15.00", "0.00")))));
    ReportModel empty =
        PeriodSummaryReportModelBuilder.INSTANCE.build(
            new PeriodSummaryReport(
                ReportModelTestSupport.bookIdentity(),
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-30"),
                PostingCoverage.ALL_POSTING_KINDS,
                0,
                0,
                0,
                List.of(),
                List.of()));

    assertEquals(2, populated.sections().size());
    assertEquals("1", populated.verdicts().get(4).value());
    assertTrue(
        empty.sections().getFirst().verdicts().stream()
            .anyMatch(
                verdict ->
                    "No currency totals matched the selected scope.".equals(verdict.value())));
    assertTrue(
        empty.sections().get(1).verdicts().stream()
            .anyMatch(
                verdict ->
                    "No account activity matched the selected scope.".equals(verdict.value())));
  }

  @Test
  void taxObligationBuilder_coversCodeSummariesAndEmptyOutcome() {
    ReportModel populated =
        TaxObligationReportModelBuilder.INSTANCE.build(
            new TaxObligationReport(
                ReportModelTestSupport.bookIdentity(),
                ReportModelTestSupport.taxRegistration(),
                new ReportingPeriod(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
                LocalDate.parse("2026-05-20"),
                List.of(
                    ReportModelTestSupport.taxCodeSummary(
                        "vat-standard-sale",
                        "VAT Standard Sale",
                        TaxApplicationKind.OUTPUT_SALE,
                        2,
                        "15000",
                        "3150",
                        "18150")),
                new dev.erst.fingrind.contract.bookkeeping.MonetaryAmount("EUR", "3150"),
                new dev.erst.fingrind.contract.bookkeeping.MonetaryAmount("EUR", "1050"),
                new dev.erst.fingrind.contract.bookkeeping.MonetaryAmount("EUR", "0"),
                new dev.erst.fingrind.contract.bookkeeping.MonetaryAmount("EUR", "2100"),
                new dev.erst.fingrind.contract.bookkeeping.MonetaryAmount("EUR", "0")));
    ReportModel empty =
        TaxObligationReportModelBuilder.INSTANCE.build(
            new TaxObligationReport(
                ReportModelTestSupport.bookIdentity(),
                ReportModelTestSupport.taxRegistration(),
                new ReportingPeriod(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
                LocalDate.parse("2026-05-20"),
                List.of(),
                new dev.erst.fingrind.contract.bookkeeping.MonetaryAmount("EUR", "0"),
                new dev.erst.fingrind.contract.bookkeeping.MonetaryAmount("EUR", "0"),
                new dev.erst.fingrind.contract.bookkeeping.MonetaryAmount("EUR", "0"),
                new dev.erst.fingrind.contract.bookkeeping.MonetaryAmount("EUR", "0"),
                new dev.erst.fingrind.contract.bookkeeping.MonetaryAmount("EUR", "0")));

    assertEquals("tax-obligation", populated.family());
    assertTrue(
        populated.sections().getFirst().totals().stream()
            .anyMatch(totals -> "netPosition".equals(totals.key())));
    assertTrue(
        empty.sections().getFirst().verdicts().stream()
            .anyMatch(
                verdict ->
                    "No tax obligation code summaries matched the selected scope."
                        .equals(verdict.value())));
  }
}
