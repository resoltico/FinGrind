package dev.erst.fingrind.report.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountingBasis;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.BusinessActivityTag;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.EntityForm;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.OwnerModel;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.ReportingObligationStatus;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.StatementLineKind;
import dev.erst.fingrind.core.TaxRegistrationStatus;
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
  }

  @Test
  void displayRowKindFormatsDeclaredAndDerivedRows() {
    assertEquals("Account", PdfValueFormatter.displayRowKind(StatementLineKind.DECLARED_ACCOUNT));
    assertEquals(
        "Current period result",
        PdfValueFormatter.displayRowKind(StatementLineKind.CURRENT_PERIOD_RESULT));
    assertEquals(
        "(derived)",
        PdfValueFormatter.displayStatementLineCode(
            "current-period-result", StatementLineKind.CURRENT_PERIOD_RESULT));
    assertEquals(
        "3000",
        PdfValueFormatter.displayStatementLineCode("3000", StatementLineKind.DECLARED_ACCOUNT));
  }

  @Test
  void displayLineRoleFormatsDeclaredAndDerivedRoles() {
    assertEquals("Ordinary", PdfValueFormatter.displayLineRole(Optional.of(AccountRole.ORDINARY)));
    assertEquals("(derived)", PdfValueFormatter.displayLineRole(Optional.empty()));
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
  void displayAccountRoleFormatsEveryVariant() {
    assertEquals("Ordinary", PdfValueFormatter.displayAccountRole(AccountRole.ORDINARY));
    assertEquals("Contra", PdfValueFormatter.displayAccountRole(AccountRole.CONTRA));
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
        "Current liability",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.CURRENT_LIABILITY));
    assertEquals(
        "Non-current liability",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.NONCURRENT_LIABILITY));
    assertEquals(
        "Owner capital",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.OWNER_CAPITAL));
    assertEquals(
        "Owner drawings",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.OWNER_DRAWINGS));
    assertEquals(
        "Partner capital",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.PARTNER_CAPITAL));
    assertEquals(
        "Partner current",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.PARTNER_CURRENT));
    assertEquals(
        "Share capital",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.SHARE_CAPITAL));
    assertEquals(
        "Retained earnings",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.RETAINED_EARNINGS));
    assertEquals(
        "Accumulated surplus",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.ACCUMULATED_SURPLUS));
    assertEquals(
        "Reserve",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.RESERVE));
    assertEquals(
        "Current period result",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.CURRENT_PERIOD_RESULT));
    assertEquals(
        "Other equity",
        PdfValueFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.OTHER_EQUITY));
    assertEquals(
        "Operating revenue",
        PdfValueFormatter.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.OPERATING_REVENUE));
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
        "Finance expense",
        PdfValueFormatter.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.FINANCE_EXPENSE));
    assertEquals(
        "Tax expense",
        PdfValueFormatter.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.TAX_EXPENSE));
    assertEquals(
        "Direct",
        PdfValueFormatter.postingRole(postingFact("posting-1", "idem-1", PostingLineage.direct())));
    assertEquals(
        "Reversal",
        PdfValueFormatter.postingRole(
            new PostingFact(
                new PostingId("posting-2"),
                journalEntry(),
                PostingLineage.reversal(
                    new ReversalReference(new PostingId("posting-1")),
                    new ReversalReason("undo test posting")),
                PostingKind.STANDARD,
                new CommittedProvenance(
                    new RequestProvenance(
                        new ActorId("actor-1"),
                        ActorType.AGENT,
                        new CommandId("command-1"),
                        new IdempotencyKey("idem-2"),
                        new CausationId("cause-1"),
                        Optional.empty()),
                    Instant.parse("2026-04-07T10:15:30Z"),
                    SourceChannel.CLI))));
    assertEquals(
        "(not a reversal)",
        PdfValueFormatter.reversalTarget(
            postingFact("posting-1", "idem-1", PostingLineage.direct())));
    assertEquals(
        "posting-1",
        PdfValueFormatter.reversalTarget(
            new PostingFact(
                new PostingId("posting-2"),
                journalEntry(),
                PostingLineage.reversal(
                    new ReversalReference(new PostingId("posting-1")),
                    new ReversalReason("undo test posting")),
                PostingKind.STANDARD,
                new CommittedProvenance(
                    new RequestProvenance(
                        new ActorId("actor-1"),
                        ActorType.AGENT,
                        new CommandId("command-1"),
                        new IdempotencyKey("idem-2"),
                        new CausationId("cause-1"),
                        Optional.empty()),
                    Instant.parse("2026-04-07T10:15:30Z"),
                    SourceChannel.CLI))));
    assertEquals(
        "Other / Multi Owner",
        PdfValueFormatter.displayEntityProfile(EntityForm.OTHER, OwnerModel.MULTI_OWNER));
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
        PdfValueFormatter.displayPostingCoverage(PostingCoverage.ALL_POSTING_KINDS));
    assertEquals(
        "Non-closing postings",
        PdfValueFormatter.displayPostingCoverage(PostingCoverage.NON_CLOSING_POSTINGS));
  }

  @Test
  void displayIdentityProfileValuesFormatsEntityReportingAndActivityFacts() {
    assertEquals(
        "Company / Multi Owner",
        PdfValueFormatter.displayEntityProfile(EntityForm.COMPANY, OwnerModel.MULTI_OWNER));
    assertEquals(
        "Internal Management Only / Unspecified",
        PdfValueFormatter.displayReportingProfile(
            ReportingObligationStatus.INTERNAL_MANAGEMENT_ONLY, TaxRegistrationStatus.UNSPECIFIED));
    assertEquals(
        "translation-services, advisory",
        PdfValueFormatter.displayBusinessActivityTags(
            List.of(
                new BusinessActivityTag("translation-services"),
                new BusinessActivityTag("advisory"))));
    assertEquals("(none)", PdfValueFormatter.displayBusinessActivityTags(List.of()));
    assertEquals("Accrual", PdfValueFormatter.displayAccountingBasis(AccountingBasis.ACCRUAL));
  }

  @Test
  void displayPostingKindFormatsEveryVariant() {
    assertEquals("Standard", PdfValueFormatter.displayPostingKind(PostingKind.STANDARD));
    assertEquals("Period close", PdfValueFormatter.displayPostingKind(PostingKind.PERIOD_CLOSE));
    assertEquals(
        "Opening balance", PdfValueFormatter.displayPostingKind(PostingKind.OPENING_BALANCE));
  }

  @Test
  void optionalDateFormatsNullAndConcreteDates() {
    assertEquals("latest committed posting date", PdfValueFormatter.optionalDate(null));
    assertEquals("2026-05-07", PdfValueFormatter.optionalDate(LocalDate.parse("2026-05-07")));
  }

  @Test
  void optionalDateRangeFormatsOpenAndBoundedRanges() {
    LocalDate from = LocalDate.parse("2026-05-01");
    LocalDate to = LocalDate.parse("2026-05-31");

    assertEquals(
        "book start to latest committed posting date",
        PdfValueFormatter.optionalDateRange(null, null));
    assertEquals(
        "2026-05-01 to latest committed posting date",
        PdfValueFormatter.optionalDateRange(from, null));
    assertEquals("book start to 2026-05-31", PdfValueFormatter.optionalDateRange(null, to));
    assertEquals("2026-05-01 to 2026-05-31", PdfValueFormatter.optionalDateRange(from, to));
  }

  @Test
  void effectiveDateRangeFormatsEveryStructuralVariant() {
    LocalDate from = LocalDate.parse("2026-05-01");
    LocalDate to = LocalDate.parse("2026-05-31");

    assertEquals(
        "book start to latest committed posting date",
        PdfValueFormatter.effectiveDateRange(EffectiveDateRange.unbounded()));
    assertEquals(
        "2026-05-01 to latest committed posting date",
        PdfValueFormatter.effectiveDateRange(new EffectiveDateRange.From(from)));
    assertEquals(
        "book start to 2026-05-31",
        PdfValueFormatter.effectiveDateRange(new EffectiveDateRange.To(to)));
    assertEquals(
        "2026-05-01 to 2026-05-31",
        PdfValueFormatter.effectiveDateRange(new EffectiveDateRange.Bounded(from, to)));
  }

  @Test
  void comparativeRangeFormatsNoneAndBoundedComparatives() {
    assertEquals("(none)", PdfValueFormatter.comparativeRange(EffectiveDateRange.unbounded()));
    assertEquals(
        "book start to 2026-05-31",
        PdfValueFormatter.comparativeRange(
            new EffectiveDateRange.To(LocalDate.parse("2026-05-31"))));
    assertEquals(
        "2026-05-01 to 2026-05-31",
        PdfValueFormatter.comparativeRange(
            new EffectiveDateRange.Bounded(
                LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-31"))));
  }

  @Test
  void reversalTargetFormatsDirectAndReversalPostings() {
    PostingFact direct = postingFact("posting-1", "idem-1", PostingLineage.direct());
    PostingFact reversal =
        new PostingFact(
            new PostingId("posting-2"),
            journalEntry(),
            PostingLineage.reversal(
                new ReversalReference(new PostingId("posting-1")),
                new ReversalReason("undo test posting")),
            PostingKind.STANDARD,
            direct.provenance());

    assertEquals("(not a reversal)", PdfValueFormatter.reversalTarget(direct));
    assertEquals("posting-1", PdfValueFormatter.reversalTarget(reversal));
  }

  private static PostingFact postingFact(
      String postingId, String idempotencyKey, PostingLineage postingLineage) {
    return new PostingFact(
        new PostingId(postingId),
        journalEntry(),
        postingLineage,
        PostingKind.STANDARD,
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-1"),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-1"),
                Optional.empty()),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
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
