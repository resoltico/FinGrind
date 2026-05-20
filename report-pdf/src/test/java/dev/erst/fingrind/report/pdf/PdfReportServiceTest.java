package dev.erst.fingrind.report.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.PeriodCurrencySummary;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingBasis;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.EntityForm;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.JournalLine.EntrySide;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
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
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** Tests for {@link PdfReportService}. */
class PdfReportServiceTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-04-19T10:15:30Z"), ZoneOffset.UTC);
  private static final PdfReportService PDF_REPORT_SERVICE =
      new PdfReportService("FinGrind", "0.42.0", CLOCK);
  private static final BookIdentity BOOK_IDENTITY =
      new BookIdentity(
          new EntityProfile(
              new BookEntityName("Acme Studio"),
              EntityForm.COMPANY,
              OwnerModel.MULTI_OWNER,
              ReportingObligationStatus.INTERNAL_MANAGEMENT_ONLY,
              List.of()),
          CurrencyUnit.of("EUR"),
          FiscalYearStart.parse("01-01"),
          AccountingBasis.ACCRUAL);
  private static final DeclaredAccount CASH_ACCOUNT =
      declaredAccount("1000", "Cash on Hand and Bank Balances", NormalBalance.DEBIT, true);
  private static final DeclaredAccount REVENUE_ACCOUNT =
      declaredAccount(
          "2000", "Subscription Revenue from Enterprise Customers", NormalBalance.CREDIT, true);

  @Test
  void renderAccountBalanceAndTrialBalanceIncludeMetadataAndExpectedText() throws IOException {
    byte[] accountBalancePdf =
        PDF_REPORT_SERVICE.renderAccountBalance(
            new AccountBalanceSnapshot(
                BOOK_IDENTITY,
                CASH_ACCOUNT,
                Optional.of(LocalDate.parse("2026-04-01")),
                Optional.of(LocalDate.parse("2026-04-30")),
                PostingCoverage.ALL_POSTING_KINDS,
                List.of(
                    balance("EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT),
                    balance("USD", "500.00", "100.00", "400.00", BalanceSide.DEBIT))));
    byte[] trialBalancePdf =
        PDF_REPORT_SERVICE.renderTrialBalance(
            new TrialBalanceReport(
                BOOK_IDENTITY,
                Optional.of(LocalDate.parse("2026-04-30")),
                EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
                PostingCoverage.ALL_POSTING_KINDS,
                List.of(
                    new TrialBalanceRow(
                        CASH_ACCOUNT,
                        balance("EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT)),
                    new TrialBalanceRow(
                        REVENUE_ACCOUNT,
                        balance("EUR", "10.00", "1250.00", "1240.00", BalanceSide.CREDIT))),
                List.of(
                    new TrialBalanceRow(
                        CASH_ACCOUNT,
                        balance("EUR", "1000.00", "10.00", "990.00", BalanceSide.DEBIT)))));
    assertPdfMetadata(accountBalancePdf, "Account Balance", true);
    assertPdfMetadata(trialBalancePdf, "Trial Balance", false);
    assertTrue(extractedText(accountBalancePdf).contains("Cash on Hand and Bank Balances"));
    assertTrue(extractedText(accountBalancePdf).contains("Per-Currency Balances"));
    assertTrue(extractedText(trialBalancePdf).contains("Trial Balance"));
    String trialBalanceText = extractedText(trialBalancePdf);
    assertTrue(trialBalanceText.contains("Acme Studio"));
    assertTrue(trialBalanceText.contains("Company / Multi Owner"));
    assertTrue(trialBalanceText.contains("Internal Management Only"));
    assertFalse(trialBalanceText.contains("Internal Management Only / Unspecified"));
    assertTrue(trialBalanceText.contains("Accrual"));
    assertTrue(trialBalanceText.contains("All posting kinds"));
    assertTrue(trialBalanceText.contains("2025-04-01 to 2025-04-30"));
    assertTrue(trialBalanceText.contains("Comparative Trial Balance"));
    assertTrue(trialBalanceText.contains("Asset"));
    assertTrue(trialBalanceText.contains("Ordinary"));
    assertTrue(trialBalanceText.contains("Yes"));
    assertTrue(trialBalanceText.contains("Subscription Revenue from"));
    assertTrue(trialBalanceText.contains("Enterprise Customers"));
  }

  @Test
  void renderAccountLedgerAndPeriodSummaryPaginateLongTables() throws IOException {
    AccountLedgerReport accountLedgerReport =
        new AccountLedgerReport(
            BOOK_IDENTITY,
            CASH_ACCOUNT,
            new EffectiveDateRange.Bounded(
                LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(balance("EUR", "0.00", "0.00", "0.00", BalanceSide.ZERO)),
            ledgerEntries(72),
            List.of(balance("EUR", "3600.00", "0.00", "3600.00", BalanceSide.DEBIT)));
    PeriodSummaryReport periodSummaryReport =
        new PeriodSummaryReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            PostingCoverage.ALL_POSTING_KINDS,
            72,
            144,
            72,
            List.of(
                new PeriodCurrencySummary(
                    balance("EUR", "3600.00", "3600.00", "0.00", BalanceSide.ZERO))),
            accountActivityRows(72));
    byte[] accountLedgerPdf = PDF_REPORT_SERVICE.renderAccountLedger(accountLedgerReport);
    byte[] periodSummaryPdf = PDF_REPORT_SERVICE.renderPeriodSummary(periodSummaryReport);
    assertPdfPageCountAtLeast(accountLedgerPdf, 2);
    assertPdfPageCountAtLeast(periodSummaryPdf, 2);
    String accountLedgerText = extractedText(accountLedgerPdf);
    String periodSummaryText = extractedText(periodSummaryPdf);
    assertTrue(accountLedgerText.contains("Acme Studio"));
    assertTrue(accountLedgerText.contains("All posting kinds"));
    assertTrue(accountLedgerText.contains("Classification"));
    assertTrue(accountLedgerText.contains("Role and polarity"));
    assertTrue(accountLedgerText.contains("Effective date range"));
    assertTrue(accountLedgerText.contains("Ledger Entries"));
    assertFalse(accountLedgerText.contains("Opening Balances"));
    assertTrue(accountLedgerText.contains("Closing Balances"));
    assertTrue(accountLedgerText.contains("1 / "));
    assertTrue(periodSummaryText.contains("Acme Studio"));
    assertTrue(periodSummaryText.contains("All posting kinds"));
    assertTrue(periodSummaryText.contains("Account Activity"));
    assertTrue(periodSummaryText.contains("Accounts touched"));
    assertTrue(periodSummaryText.contains("Yes"));
    assertTrue(periodSummaryText.contains("1 / "));
  }

  @Test
  void renderAccountLedgerIncludesMeaningfulOpeningBalancesAndReversalSemantics()
      throws IOException {
    PostingFact reversalPosting =
        new PostingFact(
            new PostingId("019e26ff-0000-7000-8000-000000000001"),
            new JournalEntry(
                LocalDate.parse("2026-04-02"),
                List.of(
                    new JournalLine(
                        CASH_ACCOUNT.accountCode(), EntrySide.CREDIT, money("EUR", "100.00")),
                    new JournalLine(
                        REVENUE_ACCOUNT.accountCode(), EntrySide.DEBIT, money("EUR", "100.00")))),
            PostingLineage.reversal(
                new ReversalReference(new PostingId("019e26ff-0000-7000-8000-000000000000")),
                new ReversalReason("duplicate-charge")),
            PostingKind.STANDARD,
            new CommittedProvenance(
                new RequestProvenance(
                    new ActorId("office-worker"),
                    ActorType.HUMAN,
                    new CommandId("command-reversal"),
                    new IdempotencyKey("idem-reversal"),
                    new CausationId("cause-reversal"),
                    Optional.of(new CorrelationId("corr-reversal"))),
                Instant.parse("2026-04-19T10:15:45Z"),
                SourceChannel.CLI));
    AccountLedgerReport accountLedgerReport =
        new AccountLedgerReport(
            BOOK_IDENTITY,
            CASH_ACCOUNT,
            new EffectiveDateRange.Bounded(
                LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(balance("EUR", "250.00", "0.00", "250.00", BalanceSide.DEBIT)),
            List.of(
                new AccountLedgerEntry(
                    reversalPosting,
                    balance("EUR", "0.00", "100.00", "100.00", BalanceSide.CREDIT),
                    money("EUR", "150.00"),
                    BalanceSide.DEBIT)),
            List.of(balance("EUR", "250.00", "100.00", "150.00", BalanceSide.DEBIT)));

    String accountLedgerText =
        extractedText(PDF_REPORT_SERVICE.renderAccountLedger(accountLedgerReport));

    assertTrue(accountLedgerText.contains("Opening Balances"));
    assertTrue(accountLedgerText.contains("250.00"));
    assertTrue(accountLedgerText.contains("Entry"));
    assertTrue(accountLedgerText.contains("Counterpart"));
    assertTrue(accountLedgerText.contains("accounts"));
    assertTrue(accountLedgerText.contains("019e26ff-0000-7000"));
    assertTrue(accountLedgerText.contains("000000000001"));
    assertTrue(accountLedgerText.contains("000000000000"));
    assertFalse(accountLedgerText.contains("..."));
  }

  @Test
  void renderAccountLedgerTreatsCreditOnlyOpeningBalancesAsMeaningful() throws IOException {
    AccountLedgerReport accountLedgerReport =
        new AccountLedgerReport(
            BOOK_IDENTITY,
            CASH_ACCOUNT,
            new EffectiveDateRange.Bounded(
                LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(balance("EUR", "0.00", "25.00", "25.00", BalanceSide.CREDIT)),
            List.of(),
            List.of(balance("EUR", "0.00", "25.00", "25.00", BalanceSide.CREDIT)));

    String accountLedgerText =
        extractedText(PDF_REPORT_SERVICE.renderAccountLedger(accountLedgerReport));

    assertTrue(accountLedgerText.contains("Opening Balances"));
    assertTrue(accountLedgerText.contains("25.00"));
  }

  @Test
  void renderStatementsHideSyntheticDerivedLineCodesInPdfOutput() throws IOException {
    CurrencyBalance creditBalance = balance("EUR", "0.00", "10.00", "10.00", BalanceSide.CREDIT);
    FinancialPositionReport financialPositionReport =
        new FinancialPositionReport(
            BOOK_IDENTITY,
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.unbounded(),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(
                new FinancialPositionSection(
                    AccountType.EQUITY,
                    List.of(
                        new FinancialPositionRow(
                            "current-period-result",
                            "Current period result",
                            AccountType.EQUITY,
                            Optional.empty(),
                            FinancialPositionLineClassification.CURRENT_PERIOD_RESULT,
                            StatementLineKind.CURRENT_PERIOD_RESULT,
                            creditBalance)),
                    List.of(creditBalance))),
            List.of());
    ChangesInEquityReport changesInEquityReport =
        new ChangesInEquityReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(
                new ChangesInEquityRow(
                    "current-period-result",
                    "Current period result",
                    Optional.of(AccountType.EQUITY),
                    Optional.empty(),
                    FinancialPositionLineClassification.CURRENT_PERIOD_RESULT,
                    StatementLineKind.CURRENT_PERIOD_RESULT,
                    balance("EUR", "0.00", "0.00", "0.00", BalanceSide.ZERO),
                    creditBalance,
                    creditBalance)),
            List.of(),
            List.of(creditBalance),
            List.of(creditBalance),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    String financialPositionText =
        extractedText(PDF_REPORT_SERVICE.renderFinancialPosition(financialPositionReport));
    String changesInEquityText =
        extractedText(PDF_REPORT_SERVICE.renderChangesInEquity(changesInEquityReport));

    assertTrue(financialPositionText.contains("(derived)"));
    assertFalse(financialPositionText.contains("current-period-result"));
    assertTrue(changesInEquityText.contains("(derived)"));
    assertFalse(changesInEquityText.contains("current-period-result"));
  }

  @Test
  void renderAccountLedgerPublishesSelfCounterpartWhenPostingTouchesOnlyTheSelectedAccount()
      throws IOException {
    PostingFact selfPosting =
        new PostingFact(
            new PostingId("019e26ff-0000-7000-8000-000000000009"),
            new JournalEntry(
                LocalDate.parse("2026-04-03"),
                List.of(
                    new JournalLine(
                        CASH_ACCOUNT.accountCode(), EntrySide.DEBIT, money("EUR", "10.00")),
                    new JournalLine(
                        CASH_ACCOUNT.accountCode(), EntrySide.CREDIT, money("EUR", "10.00")))),
            PostingLineage.direct(),
            PostingKind.STANDARD,
            new CommittedProvenance(
                new RequestProvenance(
                    new ActorId("office-worker"),
                    ActorType.HUMAN,
                    new CommandId("command-self"),
                    new IdempotencyKey("idem-self"),
                    new CausationId("cause-self"),
                    Optional.empty()),
                Instant.parse("2026-04-19T10:15:45Z"),
                SourceChannel.CLI));
    AccountLedgerReport accountLedgerReport =
        new AccountLedgerReport(
            BOOK_IDENTITY,
            CASH_ACCOUNT,
            new EffectiveDateRange.Bounded(
                LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(),
            List.of(
                new AccountLedgerEntry(
                    selfPosting,
                    balance("EUR", "10.00", "10.00", "0.00", BalanceSide.ZERO),
                    money("EUR", "0.00"),
                    BalanceSide.ZERO)),
            List.of(balance("EUR", "10.00", "10.00", "0.00", BalanceSide.ZERO)));

    String accountLedgerText =
        extractedText(PDF_REPORT_SERVICE.renderAccountLedger(accountLedgerReport));

    assertTrue(accountLedgerText.contains("(self)"));
  }

  @Test
  void renderStatementsIncludeStatementSpecificTablesAndMetadata() throws IOException {
    FinancialPositionReport financialPositionReport =
        new FinancialPositionReport(
            BOOK_IDENTITY,
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(
                new FinancialPositionSection(
                    AccountType.ASSET,
                    List.of(
                        financialPositionRow(
                            "1000",
                            "Cash and Cash Equivalents",
                            AccountType.ASSET,
                            AccountRole.ORDINARY,
                            FinancialPositionLineClassification.CURRENT_ASSET,
                            balance("EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT))),
                    List.of(balance("EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT)))),
            List.of(
                new FinancialPositionSection(
                    AccountType.ASSET,
                    List.of(
                        financialPositionRow(
                            "1000",
                            "Prior Cash and Cash Equivalents",
                            AccountType.ASSET,
                            AccountRole.ORDINARY,
                            FinancialPositionLineClassification.CURRENT_ASSET,
                            balance("EUR", "1000.00", "10.00", "990.00", BalanceSide.DEBIT))),
                    List.of(balance("EUR", "1000.00", "10.00", "990.00", BalanceSide.DEBIT)))));
    IncomeStatementReport incomeStatementReport =
        new IncomeStatementReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(
                new IncomeStatementSection(
                    AccountType.REVENUE,
                    List.of(
                        incomeStatementRow(
                            "4000",
                            "Subscription Revenue",
                            AccountType.REVENUE,
                            AccountRole.ORDINARY,
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            balance("EUR", "0.00", "2500.00", "2500.00", BalanceSide.CREDIT))),
                    List.of(balance("EUR", "0.00", "2500.00", "2500.00", BalanceSide.CREDIT)))),
            List.of(balance("EUR", "0.00", "2500.00", "2500.00", BalanceSide.CREDIT)),
            List.of(
                new IncomeStatementSection(
                    AccountType.REVENUE,
                    List.of(
                        incomeStatementRow(
                            "4000",
                            "Prior Subscription Revenue",
                            AccountType.REVENUE,
                            AccountRole.ORDINARY,
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            balance("EUR", "0.00", "1750.00", "1750.00", BalanceSide.CREDIT))),
                    List.of(balance("EUR", "0.00", "1750.00", "1750.00", BalanceSide.CREDIT)))),
            List.of(balance("EUR", "0.00", "1750.00", "1750.00", BalanceSide.CREDIT)));
    ChangesInEquityReport changesInEquityReport =
        new ChangesInEquityReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(
                changesInEquityRow(
                    "3000",
                    "Owner Capital",
                    AccountRole.ORDINARY,
                    FinancialPositionLineClassification.OWNER_CAPITAL,
                    balance("EUR", "0.00", "1000.00", "1000.00", BalanceSide.CREDIT),
                    balance("EUR", "0.00", "250.00", "250.00", BalanceSide.CREDIT),
                    balance("EUR", "0.00", "1250.00", "1250.00", BalanceSide.CREDIT))),
            List.of(balance("EUR", "0.00", "1000.00", "1000.00", BalanceSide.CREDIT)),
            List.of(balance("EUR", "0.00", "250.00", "250.00", BalanceSide.CREDIT)),
            List.of(balance("EUR", "0.00", "1250.00", "1250.00", BalanceSide.CREDIT)),
            List.of(
                changesInEquityRow(
                    "3000",
                    "Prior Owner Capital",
                    AccountRole.ORDINARY,
                    FinancialPositionLineClassification.OWNER_CAPITAL,
                    balance("EUR", "0.00", "800.00", "800.00", BalanceSide.CREDIT),
                    balance("EUR", "0.00", "200.00", "200.00", BalanceSide.CREDIT),
                    balance("EUR", "0.00", "1000.00", "1000.00", BalanceSide.CREDIT))),
            List.of(balance("EUR", "0.00", "800.00", "800.00", BalanceSide.CREDIT)),
            List.of(balance("EUR", "0.00", "200.00", "200.00", BalanceSide.CREDIT)),
            List.of(balance("EUR", "0.00", "1000.00", "1000.00", BalanceSide.CREDIT)));

    byte[] financialPositionPdf =
        PDF_REPORT_SERVICE.renderFinancialPosition(financialPositionReport);
    byte[] incomeStatementPdf = PDF_REPORT_SERVICE.renderIncomeStatement(incomeStatementReport);
    byte[] changesInEquityPdf = PDF_REPORT_SERVICE.renderChangesInEquity(changesInEquityReport);

    assertPdfMetadata(financialPositionPdf, "Financial Position", false);
    assertPdfMetadata(incomeStatementPdf, "Income Statement", false);
    assertPdfMetadata(changesInEquityPdf, "Changes In Equity", false);
    String financialPositionText = extractedText(financialPositionPdf);
    String incomeStatementText = extractedText(incomeStatementPdf);
    String changesInEquityText = extractedText(changesInEquityPdf);
    assertTrue(financialPositionText.contains("Cash and Cash Equivalents"));
    assertTrue(financialPositionText.contains("Financial Position"));
    assertTrue(financialPositionText.contains("Acme Studio"));
    assertTrue(financialPositionText.contains("All posting kinds"));
    assertTrue(financialPositionText.contains("Ordinary"));
    assertTrue(financialPositionText.contains("Comparative Assets"));
    assertTrue(financialPositionText.contains("Prior Cash and Cash"));
    assertTrue(financialPositionText.contains("Equivalents"));
    assertTrue(incomeStatementText.contains("Subscription Revenue"));
    assertTrue(incomeStatementText.contains("Income Statement"));
    assertTrue(incomeStatementText.contains("Non-closing postings"));
    assertTrue(incomeStatementText.contains("Ordinary"));
    assertTrue(incomeStatementText.contains("Comparative Revenue"));
    assertTrue(incomeStatementText.contains("Comparative Net Income Totals"));
    assertTrue(incomeStatementText.contains("Prior Subscription Revenue"));
    assertTrue(changesInEquityText.contains("Owner Capital"));
    assertTrue(changesInEquityText.contains("Changes In Equity"));
    assertTrue(changesInEquityText.contains("Acme Studio"));
    assertTrue(changesInEquityText.contains("Ordinary"));
    assertTrue(changesInEquityText.contains("Comparative Changes In Equity"));
    assertTrue(changesInEquityText.contains("Comparative Equity Totals"));
    assertTrue(changesInEquityText.contains("Prior Owner Capital"));
  }

  @Test
  void renderComparativeBranchesWhenComparativesAreOmittedOrPartiallyPresent() throws IOException {
    TrialBalanceReport trialBalanceWithoutComparatives =
        new TrialBalanceReport(
            BOOK_IDENTITY,
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(
                new TrialBalanceRow(
                    CASH_ACCOUNT,
                    balance("EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT))),
            List.of());
    IncomeStatementReport incomeStatementWithoutComparativeTotals =
        new IncomeStatementReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(
                new IncomeStatementSection(
                    AccountType.REVENUE,
                    List.of(
                        incomeStatementRow(
                            "4000",
                            "Subscription Revenue",
                            AccountType.REVENUE,
                            AccountRole.ORDINARY,
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            balance("EUR", "0.00", "2500.00", "2500.00", BalanceSide.CREDIT))),
                    List.of(balance("EUR", "0.00", "2500.00", "2500.00", BalanceSide.CREDIT)))),
            List.of(balance("EUR", "0.00", "2500.00", "2500.00", BalanceSide.CREDIT)),
            List.of(),
            List.of());

    String trialBalanceText =
        extractedText(PDF_REPORT_SERVICE.renderTrialBalance(trialBalanceWithoutComparatives));
    String incomeStatementText =
        extractedText(
            PDF_REPORT_SERVICE.renderIncomeStatement(incomeStatementWithoutComparativeTotals));

    assertFalse(trialBalanceText.contains("Comparative Trial Balance"));
    assertFalse(incomeStatementText.contains("Comparative Net Income Totals"));

    ChangesInEquityReport noComparativeEquity =
        new ChangesInEquityReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(
                changesInEquityRow(
                    "3000",
                    "Owner Capital",
                    AccountRole.ORDINARY,
                    FinancialPositionLineClassification.OWNER_CAPITAL,
                    balance("EUR", "0.00", "1000.00", "1000.00", BalanceSide.CREDIT),
                    balance("EUR", "0.00", "250.00", "250.00", BalanceSide.CREDIT),
                    balance("EUR", "0.00", "1250.00", "1250.00", BalanceSide.CREDIT))),
            List.of(balance("EUR", "0.00", "1000.00", "1000.00", BalanceSide.CREDIT)),
            List.of(balance("EUR", "0.00", "250.00", "250.00", BalanceSide.CREDIT)),
            List.of(balance("EUR", "0.00", "1250.00", "1250.00", BalanceSide.CREDIT)),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    ChangesInEquityReport movementOnlyComparativeEquity =
        new ChangesInEquityReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            noComparativeEquity.rows(),
            noComparativeEquity.openingTotals(),
            noComparativeEquity.movementTotals(),
            noComparativeEquity.closingTotals(),
            List.of(),
            List.of(),
            List.of(balance("EUR", "0.00", "200.00", "200.00", BalanceSide.CREDIT)),
            List.of());
    ChangesInEquityReport closingOnlyComparativeEquity =
        new ChangesInEquityReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            noComparativeEquity.rows(),
            noComparativeEquity.openingTotals(),
            noComparativeEquity.movementTotals(),
            noComparativeEquity.closingTotals(),
            List.of(),
            List.of(),
            List.of(),
            List.of(balance("EUR", "0.00", "1000.00", "1000.00", BalanceSide.CREDIT)));

    String noComparativeEquityText =
        extractedText(PDF_REPORT_SERVICE.renderChangesInEquity(noComparativeEquity));
    String movementOnlyComparativeEquityText =
        extractedText(PDF_REPORT_SERVICE.renderChangesInEquity(movementOnlyComparativeEquity));
    String closingOnlyComparativeEquityText =
        extractedText(PDF_REPORT_SERVICE.renderChangesInEquity(closingOnlyComparativeEquity));

    assertFalse(noComparativeEquityText.contains("Comparative Changes In Equity"));
    assertFalse(noComparativeEquityText.contains("Comparative Equity Totals"));
    assertTrue(movementOnlyComparativeEquityText.contains("Comparative Equity Totals"));
    assertTrue(closingOnlyComparativeEquityText.contains("Comparative Equity Totals"));
  }

  @Test
  void renderStatementsSkipEmptySectionsAndAllowRowsWithoutTotals() throws IOException {
    FinancialPositionReport financialPositionReport =
        new FinancialPositionReport(
            BOOK_IDENTITY,
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(
                new FinancialPositionSection(AccountType.LIABILITY, List.of(), List.of()),
                new FinancialPositionSection(
                    AccountType.ASSET,
                    List.of(
                        financialPositionRow(
                            "1000",
                            "Cash without Totals",
                            AccountType.ASSET,
                            AccountRole.ORDINARY,
                            FinancialPositionLineClassification.CURRENT_ASSET,
                            balance("EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT))),
                    List.of()),
                new FinancialPositionSection(
                    AccountType.EQUITY,
                    List.of(),
                    List.of(balance("EUR", "0.00", "1250.00", "1250.00", BalanceSide.CREDIT)))),
            List.of(new FinancialPositionSection(AccountType.EQUITY, List.of(), List.of())));
    IncomeStatementReport incomeStatementReport =
        new IncomeStatementReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(
                new IncomeStatementSection(AccountType.EXPENSE, List.of(), List.of()),
                new IncomeStatementSection(
                    AccountType.REVENUE,
                    List.of(
                        incomeStatementRow(
                            "4000",
                            "Revenue without Totals",
                            AccountType.REVENUE,
                            AccountRole.ORDINARY,
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            balance("EUR", "0.00", "2500.00", "2500.00", BalanceSide.CREDIT))),
                    List.of()),
                new IncomeStatementSection(
                    AccountType.EXPENSE,
                    List.of(),
                    List.of(balance("EUR", "900.00", "0.00", "900.00", BalanceSide.DEBIT)))),
            List.of(balance("EUR", "0.00", "2500.00", "2500.00", BalanceSide.CREDIT)),
            List.of(new IncomeStatementSection(AccountType.EXPENSE, List.of(), List.of())),
            List.of());

    String financialPositionText =
        extractedText(PDF_REPORT_SERVICE.renderFinancialPosition(financialPositionReport));
    String incomeStatementText =
        extractedText(PDF_REPORT_SERVICE.renderIncomeStatement(incomeStatementReport));

    assertTrue(financialPositionText.contains("Cash without Totals"));
    assertFalse(financialPositionText.contains("Liabilities"));
    assertFalse(financialPositionText.contains("Assets Totals"));
    assertTrue(financialPositionText.contains("Equity Totals"));
    assertFalse(financialPositionText.contains("Comparative Financial Position"));

    assertTrue(incomeStatementText.contains("Revenue without Totals"));
    assertTrue(incomeStatementText.contains("Expenses Totals"));
    assertFalse(incomeStatementText.contains("Revenue Totals"));
    assertFalse(incomeStatementText.contains("Comparative Income Statement"));
  }

  @Test
  @org.jspecify.annotations.NullUnmarked
  void constructorAndRenderMethodsRejectNullInputs() {
    assertThrows(NullPointerException.class, () -> new PdfReportService(null, "0.42.0", CLOCK));
    assertThrows(NullPointerException.class, () -> new PdfReportService("FinGrind", null, CLOCK));
    assertThrows(
        NullPointerException.class, () -> new PdfReportService("FinGrind", "0.42.0", null));
    assertThrows(NullPointerException.class, () -> PDF_REPORT_SERVICE.renderAccountBalance(null));
    assertThrows(NullPointerException.class, () -> PDF_REPORT_SERVICE.renderTrialBalance(null));
    assertThrows(NullPointerException.class, () -> PDF_REPORT_SERVICE.renderAccountLedger(null));
    assertThrows(NullPointerException.class, () -> PDF_REPORT_SERVICE.renderPeriodSummary(null));
    assertThrows(
        NullPointerException.class, () -> PDF_REPORT_SERVICE.renderFinancialPosition(null));
    assertThrows(NullPointerException.class, () -> PDF_REPORT_SERVICE.renderIncomeStatement(null));
    assertThrows(NullPointerException.class, () -> PDF_REPORT_SERVICE.renderChangesInEquity(null));
  }

  private static void assertPdfMetadata(byte[] pdfBytes, String title, boolean portrait)
      throws IOException {
    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
      PDDocumentInformation information = document.getDocumentInformation();
      PDRectangle mediaBox = document.getPage(0).getMediaBox();
      assertEquals(title, information.getTitle());
      assertEquals("FinGrind 0.42.0", information.getCreator());
      assertEquals(title, information.getSubject());
      assertEquals(portrait, mediaBox.getHeight() > mediaBox.getWidth());
    }
  }

  private static void assertPdfPageCountAtLeast(byte[] pdfBytes, int minimumPages)
      throws IOException {
    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
      assertTrue(document.getNumberOfPages() >= minimumPages);
    }
  }

  private static String extractedText(byte[] pdfBytes) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
      return new PDFTextStripper().getText(document);
    }
  }

  private static List<AccountLedgerEntry> ledgerEntries(int count) {
    List<AccountLedgerEntry> entries = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      String amount = Integer.toString((index + 1) * 50);
      entries.add(
          new AccountLedgerEntry(
              postingFact(index, amount),
              balance("EUR", amount, "0.00", amount, BalanceSide.DEBIT),
              money("EUR", Integer.toString((index + 1) * 50)),
              BalanceSide.DEBIT));
    }
    return entries;
  }

  private static List<PeriodAccountActivityRow> accountActivityRows(int count) {
    List<PeriodAccountActivityRow> rows = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      rows.add(
          new PeriodAccountActivityRow(
              declaredAccount(
                  "%04d".formatted(3000 + index),
                  "Operating Expense Category " + index + " with a deliberately long display name",
                  NormalBalance.DEBIT,
                  true),
              balance(
                  "EUR",
                  Integer.toString(index + 1),
                  "0.00",
                  Integer.toString(index + 1),
                  BalanceSide.DEBIT)));
    }
    return rows;
  }

  private static PostingFact postingFact(int index, String amount) {
    LocalDate effectiveDate = LocalDate.parse("2026-04-01").plusDays(index % 28);
    PostingId postingId = new PostingId("posting-%03d".formatted(index));
    return new PostingFact(
        postingId,
        new JournalEntry(
            effectiveDate,
            List.of(
                new JournalLine(
                    CASH_ACCOUNT.accountCode(), JournalLine.EntrySide.DEBIT, money("EUR", amount)),
                new JournalLine(
                    REVENUE_ACCOUNT.accountCode(),
                    JournalLine.EntrySide.CREDIT,
                    money("EUR", amount)))),
        index % 5 == 0
            ? PostingLineage.reversal(
                new ReversalReference(new PostingId("prior-%03d".formatted(index))),
                new ReversalReason("Automated reversal %03d".formatted(index)))
            : PostingLineage.direct(),
        PostingKind.STANDARD,
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("office-worker"),
                ActorType.HUMAN,
                new CommandId("command-%03d".formatted(index)),
                new IdempotencyKey("idem-%03d".formatted(index)),
                new CausationId("cause-%03d".formatted(index)),
                Optional.of(new CorrelationId("corr-%03d".formatted(index)))),
            Instant.parse("2026-04-19T10:15:30Z").plusSeconds(index),
            SourceChannel.CLI));
  }

  private static DeclaredAccount declaredAccount(
      String code, String name, NormalBalance normalBalance, boolean active) {
    AccountType accountType =
        normalBalance == NormalBalance.DEBIT ? AccountType.ASSET : AccountType.REVENUE;
    return new DeclaredAccount(
        new AccountCode(code),
        new AccountName(name),
        accountType,
        AccountRole.ORDINARY,
        accountTaxonomy(accountType),
        active,
        Instant.parse("2026-04-01T08:00:00Z"));
  }

  private static FinancialPositionRow financialPositionRow(
      String lineCode,
      String lineName,
      AccountType accountType,
      AccountRole accountRole,
      FinancialPositionLineClassification lineClassification,
      CurrencyBalance balance) {
    return new FinancialPositionRow(
        lineCode,
        lineName,
        accountType,
        Optional.of(accountRole),
        lineClassification,
        StatementLineKind.DECLARED_ACCOUNT,
        balance);
  }

  private static IncomeStatementRow incomeStatementRow(
      String lineCode,
      String lineName,
      AccountType accountType,
      AccountRole accountRole,
      ProfitAndLossLineClassification lineClassification,
      CurrencyBalance movement) {
    return new IncomeStatementRow(
        lineCode,
        lineName,
        accountType,
        Optional.of(accountRole),
        lineClassification,
        StatementLineKind.DECLARED_ACCOUNT,
        movement);
  }

  private static ChangesInEquityRow changesInEquityRow(
      String lineCode,
      String lineName,
      AccountRole accountRole,
      FinancialPositionLineClassification lineClassification,
      CurrencyBalance openingBalance,
      CurrencyBalance movement,
      CurrencyBalance closingBalance) {
    return new ChangesInEquityRow(
        lineCode,
        lineName,
        Optional.of(AccountType.EQUITY),
        Optional.of(accountRole),
        lineClassification,
        StatementLineKind.DECLARED_ACCOUNT,
        openingBalance,
        movement,
        closingBalance);
  }

  private static AccountTaxonomy accountTaxonomy(AccountType accountType) {
    return switch (accountType) {
      case ASSET ->
          new AccountTaxonomy(
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
              Optional.empty());
      case LIABILITY ->
          new AccountTaxonomy(
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_LIABILITY),
              Optional.empty());
      case EQUITY ->
          new AccountTaxonomy(
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.OWNER_CAPITAL),
              Optional.empty());
      case REVENUE ->
          new AccountTaxonomy(
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE));
      case EXPENSE ->
          new AccountTaxonomy(
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE));
    };
  }

  private static CurrencyBalance balance(
      String currencyCode,
      String debitTotal,
      String creditTotal,
      String netAmount,
      BalanceSide balanceSide) {
    CurrencyBalance balance =
        CurrencyBalance.ofTotals(money(currencyCode, debitTotal), money(currencyCode, creditTotal));
    if (!balance.netAmount().equals(money(currencyCode, netAmount))
        || balance.balanceSide() != balanceSide) {
      throw new IllegalArgumentException("Test fixture balance does not match derived totals.");
    }
    return balance;
  }

  private static Money money(String currencyCode, String amount) {
    return Money.parse(currencyCode, amount);
  }
}
