package dev.erst.fingrind.report.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CashFlowSectionKind;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.core.StatementLineKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Focused branch coverage tests for {@link PdfValueFormatter}. */
class PdfValueFormatterTest {
  @Test
  void displayMoneyUsesCanonicalCurrencyScale() {
    assertEquals("12.50", PdfValueFormatter.displayMoney(Money.parse("EUR", "12.50")));
    assertEquals("42.00", PdfValueFormatter.displayMoney(Money.parse("EUR", "42.00")));
    assertEquals("100", PdfValueFormatter.displayMoney(Money.parse("JPY", "100")));
    assertEquals("1.250", PdfValueFormatter.displayMoney(Money.parse("BHD", "1.25")));
  }

  @Test
  void displayBalanceSideFormatsEveryVariant() {
    assertEquals("Debit", PdfValueFormatter.displayBalanceSide(BalanceSide.DEBIT));
    assertEquals("Credit", PdfValueFormatter.displayBalanceSide(BalanceSide.CREDIT));
    assertEquals("Balanced", PdfValueFormatter.displayBalanceSide(BalanceSide.ZERO));
  }

  @Test
  void displayAccountTypeSectionFormatsEveryVariant() {
    assertEquals(
        "Assets",
        PdfValueFormatter.displayAccountTypeSection(dev.erst.fingrind.core.AccountType.ASSET));
    assertEquals(
        "Liabilities",
        PdfValueFormatter.displayAccountTypeSection(dev.erst.fingrind.core.AccountType.LIABILITY));
    assertEquals(
        "Equity",
        PdfValueFormatter.displayAccountTypeSection(dev.erst.fingrind.core.AccountType.EQUITY));
    assertEquals(
        "Revenue",
        PdfValueFormatter.displayAccountTypeSection(dev.erst.fingrind.core.AccountType.REVENUE));
    assertEquals(
        "Expenses",
        PdfValueFormatter.displayAccountTypeSection(dev.erst.fingrind.core.AccountType.EXPENSE));
    assertEquals(
        "Operating", PdfValueFormatter.displayCashFlowSection(CashFlowSectionKind.OPERATING));
    assertEquals(
        "Investing", PdfValueFormatter.displayCashFlowSection(CashFlowSectionKind.INVESTING));
    assertEquals(
        "Financing", PdfValueFormatter.displayCashFlowSection(CashFlowSectionKind.FINANCING));
  }

  @Test
  void displayRowKindFormatsDeclaredAndDerivedRows() {
    assertEquals("Account", PdfValueFormatter.displayRowKind(StatementLineKind.DECLARED_ACCOUNT));
    assertEquals(
        "Current period result",
        PdfValueFormatter.displayRowKind(StatementLineKind.CURRENT_PERIOD_RESULT));
    assertEquals(
        "Calculated line",
        PdfValueFormatter.displayStatementLineCode(
            "current-period-result", StatementLineKind.CURRENT_PERIOD_RESULT));
    assertEquals(
        "3000",
        PdfValueFormatter.displayStatementLineCode("3000", StatementLineKind.DECLARED_ACCOUNT));
  }

  @Test
  void displayAccountTypeFormatsEveryVariant() {
    assertEquals(
        "Asset", PdfValueFormatter.displayAccountType(dev.erst.fingrind.core.AccountType.ASSET));
    assertEquals(
        "Liability",
        PdfValueFormatter.displayAccountType(dev.erst.fingrind.core.AccountType.LIABILITY));
    assertEquals(
        "Equity", PdfValueFormatter.displayAccountType(dev.erst.fingrind.core.AccountType.EQUITY));
    assertEquals(
        "Revenue",
        PdfValueFormatter.displayAccountType(dev.erst.fingrind.core.AccountType.REVENUE));
    assertEquals(
        "Expense",
        PdfValueFormatter.displayAccountType(dev.erst.fingrind.core.AccountType.EXPENSE));
  }

  @Test
  void classificationAndPostingHelpers_coverAllRemainingDisplayVariants() {
    assertEquals(
        "Current asset",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.CURRENT_ASSET));
    assertEquals(
        "Non-current asset",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.NONCURRENT_ASSET));
    assertEquals(
        "Inventory",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.INVENTORY));
    assertEquals(
        "Trade receivable",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.TRADE_RECEIVABLE));
    assertEquals(
        "Current liability",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.CURRENT_LIABILITY));
    assertEquals(
        "Non-current liability",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.NONCURRENT_LIABILITY));
    assertEquals(
        "Trade payable",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.TRADE_PAYABLE));
    assertEquals(
        "Contributed capital",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.EQUITY_CONTRIBUTION));
    assertEquals(
        "Distributions",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.EQUITY_WITHDRAWAL));
    assertEquals(
        "Accumulated result",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.RESULT_HOLDING));
    assertEquals(
        "Retained accumulated",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.RETAINED_ACCUMULATED));
    assertEquals(
        "Reserve",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.RESERVE));
    assertEquals(
        "Calculated line",
        PdfValueFormatter.displayFinancialPositionLineClassification(Optional.empty()));
    assertEquals(
        "Current asset",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET)));
    assertEquals(
        "Other equity",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.OTHER_EQUITY));
    assertEquals(
        "Operating revenue",
        PdfValueFormatter.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.OPERATING_REVENUE));
    assertEquals(
        "Sales discount allowance",
        PdfValueFormatter.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.SALES_DISCOUNT_ALLOWANCE));
    assertEquals(
        "Other revenue",
        PdfValueFormatter.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.OTHER_REVENUE));
    assertEquals(
        "Finance income",
        PdfValueFormatter.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.FINANCE_INCOME));
    assertEquals(
        "Cost of sales",
        PdfValueFormatter.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.COST_OF_SALES));
    assertEquals(
        "Operating expense",
        PdfValueFormatter.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.OPERATING_EXPENSE));
    assertEquals(
        "Depreciation and amortization",
        PdfValueFormatter.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.DEPRECIATION_AND_AMORTIZATION));
    assertEquals(
        "Settlement fee",
        PdfValueFormatter.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.SETTLEMENT_FEE));
    assertEquals(
        "Bad debt write-off",
        PdfValueFormatter.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.BAD_DEBT_WRITE_OFF));
    assertEquals(
        "Finance expense",
        PdfValueFormatter.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.FINANCE_EXPENSE));
    assertEquals(
        "Other expense",
        PdfValueFormatter.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.OTHER_EXPENSE));
    assertEquals(
        "Direct",
        PdfPostingValueFormatter.postingRole(
            postingFact(
                "019e26ff-0000-7000-8000-000000000001", "idem-1", PostingLineage.direct())));
    assertEquals(
        "Reversal",
        PdfPostingValueFormatter.postingRole(
            new PostingFact(
                new PostingId("019e26ff-0000-7000-8000-000000000002"),
                journalEntry(),
                PostingLineage.reversal(
                    new ReversalReference(new PostingId("019e26ff-0000-7000-8000-000000000001")),
                    new ReversalReason("undo test posting")),
                PostingKind.STANDARD,
                dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
                evidence("idem-2"),
                new CommittedProvenance(
                    new RequestProvenance(
                        new CommandId("019e26ff-0000-7002-8000-000000000001"),
                        new IdempotencyKey("idem-2"),
                        new CausationId("019e26ff-0000-7003-8000-000000000001"),
                        Optional.empty()),
                    Instant.parse("2026-04-07T10:15:30Z"),
                    SourceChannel.CLI))));
    assertEquals(
        "(not a reversal)",
        PdfPostingValueFormatter.reversalTarget(
            postingFact(
                "019e26ff-0000-7000-8000-000000000001", "idem-1", PostingLineage.direct())));
    assertEquals(
        "019e26ff-0000-7000-8000-000000000001",
        PdfPostingValueFormatter.reversalTarget(
            new PostingFact(
                new PostingId("019e26ff-0000-7000-8000-000000000002"),
                journalEntry(),
                PostingLineage.reversal(
                    new ReversalReference(new PostingId("019e26ff-0000-7000-8000-000000000001")),
                    new ReversalReason("undo test posting")),
                PostingKind.STANDARD,
                dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
                evidence("idem-2"),
                new CommittedProvenance(
                    new RequestProvenance(
                        new CommandId("019e26ff-0000-7002-8000-000000000001"),
                        new IdempotencyKey("idem-2"),
                        new CausationId("019e26ff-0000-7003-8000-000000000001"),
                        Optional.empty()),
                    Instant.parse("2026-04-07T10:15:30Z"),
                    SourceChannel.CLI))));
  }

  @Test
  void displayNormalBalanceFormatsEveryVariant() {
    assertEquals(
        "Debit",
        PdfValueFormatter.displayNormalBalance(dev.erst.fingrind.core.NormalBalance.DEBIT));
    assertEquals(
        "Credit",
        PdfValueFormatter.displayNormalBalance(dev.erst.fingrind.core.NormalBalance.CREDIT));
  }

  @Test
  void displayBooleanFormatsBothVariants() {
    assertEquals("Yes", PdfValueFormatter.displayBoolean(true));
    assertEquals("No", PdfValueFormatter.displayBoolean(false));
  }

  @Test
  void displayPostingCoverageFormatsEveryVariant() {
    assertEquals(
        "All posting kinds",
        PdfPostingValueFormatter.displayPostingCoverage(PostingCoverage.ALL_POSTING_KINDS));
    assertEquals(
        "Non-close postings",
        PdfPostingValueFormatter.displayPostingCoverage(PostingCoverage.NON_CLOSING_POSTINGS));
  }

  @Test
  void displayPostingKindFormatsEveryVariant() {
    assertEquals("Standard", PdfPostingValueFormatter.displayPostingKind(PostingKind.STANDARD));
    assertEquals(
        "Interim result sweep",
        PdfPostingValueFormatter.displayPostingKind(PostingKind.INTERIM_RESULT_SWEEP));
    assertEquals(
        "Fiscal-year close",
        PdfPostingValueFormatter.displayPostingKind(PostingKind.FISCAL_YEAR_CLOSE));
    assertEquals(
        "Opening accounting position",
        PdfPostingValueFormatter.displayPostingKind(PostingKind.OPENING_BALANCE));
  }

  @Test
  void optionalDateFormatsNullAndConcreteDates() {
    assertEquals("current book horizon", PdfTemporalValueFormatter.optionalDate(null));
    assertEquals(
        "2026-05-07", PdfTemporalValueFormatter.optionalDate(LocalDate.parse("2026-05-07")));
  }

  @Test
  void optionalDateRangeFormatsOpenAndBoundedRanges() {
    LocalDate from = LocalDate.parse("2026-05-01");
    LocalDate to = LocalDate.parse("2026-05-31");

    assertEquals(
        "book start to current book horizon",
        PdfTemporalValueFormatter.optionalDateRange(null, null));
    assertEquals(
        "2026-05-01 to current book horizon",
        PdfTemporalValueFormatter.optionalDateRange(from, null));
    assertEquals("book start to 2026-05-31", PdfTemporalValueFormatter.optionalDateRange(null, to));
    assertEquals("2026-05-01 to 2026-05-31", PdfTemporalValueFormatter.optionalDateRange(from, to));
  }

  @Test
  void effectiveDateRangeFormatsEveryStructuralVariant() {
    LocalDate from = LocalDate.parse("2026-05-01");
    LocalDate to = LocalDate.parse("2026-05-31");

    assertEquals(
        "book start to current book horizon",
        PdfTemporalValueFormatter.effectiveDateRange(EffectiveDateRange.unbounded()));
    assertEquals(
        "2026-05-01 to current book horizon",
        PdfTemporalValueFormatter.effectiveDateRange(new EffectiveDateRange.From(from)));
    assertEquals(
        "book start to 2026-05-31",
        PdfTemporalValueFormatter.effectiveDateRange(new EffectiveDateRange.To(to)));
    assertEquals(
        "2026-05-01 to 2026-05-31",
        PdfTemporalValueFormatter.effectiveDateRange(new EffectiveDateRange.Bounded(from, to)));
  }

  @Test
  void comparativeRangeFormatsNoneAndBoundedComparatives() {
    assertEquals(
        "(none)", PdfTemporalValueFormatter.comparativeRange(EffectiveDateRange.unbounded()));
    assertEquals(
        "book start to 2026-05-31",
        PdfTemporalValueFormatter.comparativeRange(
            new EffectiveDateRange.To(LocalDate.parse("2026-05-31"))));
    assertEquals(
        "2026-05-01 to 2026-05-31",
        PdfTemporalValueFormatter.comparativeRange(
            new EffectiveDateRange.Bounded(
                LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-31"))));
  }

  @Test
  void reversalTargetFormatsDirectAndReversalPostings() {
    PostingFact direct =
        postingFact("019e26ff-0000-7000-8000-000000000001", "idem-1", PostingLineage.direct());
    PostingFact reversal =
        new PostingFact(
            new PostingId("019e26ff-0000-7000-8000-000000000002"),
            journalEntry(),
            PostingLineage.reversal(
                new ReversalReference(new PostingId("019e26ff-0000-7000-8000-000000000001")),
                new ReversalReason("undo test posting")),
            PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
            evidence("idem-1"),
            direct.provenance());

    assertEquals("(not a reversal)", PdfPostingValueFormatter.reversalTarget(direct));
    assertEquals(
        "019e26ff-0000-7000-8000-000000000001", PdfPostingValueFormatter.reversalTarget(reversal));
  }

  private static PostingFact postingFact(
      String postingId, String idempotencyKey, PostingLineage postingLineage) {
    return new PostingFact(
        new PostingId(postingId),
        journalEntry(),
        postingLineage,
        PostingKind.STANDARD,
        dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
        evidence(idempotencyKey),
        new CommittedProvenance(
            new RequestProvenance(
                new CommandId("019e26ff-0000-7002-8000-000000000001"),
                new IdempotencyKey(idempotencyKey),
                new CausationId("019e26ff-0000-7003-8000-000000000001"),
                Optional.empty()),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }

  private static AccountingEvidence evidence(String token) {
    return new AccountingEvidence(
        List.of(
            new SourceDocumentReference(
                new SourceDocumentId("document-" + token),
                new SourceDocumentType("cash-receipt"),
                LocalDate.parse("2026-04-07"))),
        List.of());
  }

  private static JournalEntry journalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            new JournalLine(new AccountCode("1000"), JournalLine.EntrySide.DEBIT, money("10.00")),
            new JournalLine(
                new AccountCode("2000"), JournalLine.EntrySide.CREDIT, money("10.00"))));
  }

  private static Money money(String amount) {
    return Money.parse("EUR", amount);
  }
}
