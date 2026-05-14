package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for CLI read, report, and mutation renderers. */
class CliQueryOutputRendererTest extends FinGrindCliTestSupport {
  @Test
  void renderInspectionAccountsAndPostingViewsInHumanAndCsvForms() {
    PostingFact postingFact = reversalPostingFact();
    DeclaredAccount cashAccount = declaredAccount("1000", "Cash, reserve", NormalBalance.DEBIT);
    String missingInspection =
        CliQueryOutputRenderer.renderBookInspectionHuman(
            Path.of("office/report.sqlite"), new BookInspection.Missing(1));
    String existingInspection =
        CliQueryOutputRenderer.renderBookInspectionHuman(
            Path.of("office/report.sqlite"),
            new BookInspection.Existing(BookInspection.Status.BLANK_SQLITE, 123, 0, 1));
    String initializedInspection =
        CliQueryOutputRenderer.renderBookInspectionHuman(
            Path.of("office/report.sqlite"),
            initializedBookInspection(123, 1, 1, Instant.parse("2026-04-07T10:15:30Z")));
    AccountPageCursor nextAccountCursor = AccountPageCursor.fromAccount(cashAccount);
    String accountsHuman =
        CliQueryOutputRenderer.renderAccountsHuman(
            new AccountPage(List.of(cashAccount), 50, Optional.of(nextAccountCursor)));
    String accountsHumanWithoutCursor =
        CliQueryOutputRenderer.renderAccountsHuman(
            new AccountPage(List.of(cashAccount), 50, Optional.empty()));
    String directAccountsHuman =
        CliAccountPageOutputRenderer.renderHuman(
            new AccountPage(List.of(cashAccount), 50, Optional.of(nextAccountCursor)));
    String directAccountsHumanWithoutCursor =
        CliAccountPageOutputRenderer.renderHuman(
            new AccountPage(List.of(cashAccount), 50, Optional.empty()));
    String accountsCsv =
        CliQueryOutputRenderer.renderAccountsCsv(
            new AccountPage(List.of(cashAccount), 50, Optional.empty()));
    String postingHuman = CliQueryOutputRenderer.renderPostingHuman(postingFact);
    PostingPageCursor nextCursor =
        new PostingPageCursor(
            LocalDate.parse("2026-04-30"),
            Instant.parse("2026-04-07T10:15:30Z"),
            new PostingId("posting-1"));
    String postingRegisterHuman =
        CliQueryOutputRenderer.renderPostingRegisterHuman(
            new PostingPage(List.of(postingFact), 10, Optional.of(nextCursor)));
    String postingRegisterCsv =
        CliQueryOutputRenderer.renderPostingRegisterCsv(
            new PostingPage(List.of(postingFact), 10, Optional.empty()));
    assertTrue(missingInspection.contains("Missing"));
    assertTrue(missingInspection.contains("Can initialize with open-book"));
    assertTrue(missingInspection.contains("Yes"));
    assertTrue(missingInspection.contains("Supported book format version"));
    assertTrue(existingInspection.contains("SQLite applicationId"));
    assertTrue(existingInspection.contains("State"));
    assertTrue(existingInspection.contains("Blank SQLite"));
    assertTrue(initializedInspection.contains("Initialized at"));
    assertTrue(initializedInspection.contains("Entity name"));
    assertTrue(initializedInspection.contains("Acme Studio"));
    assertTrue(initializedInspection.contains("Functional currency"));
    assertTrue(initializedInspection.contains("Fiscal year start"));
    assertTrue(accountsHuman.contains("Cash, reserve"));
    assertTrue(accountsHuman.contains(nextAccountCursor.wireValue()));
    assertTrue(accountsHumanWithoutCursor.contains("(none)"));
    assertTrue(directAccountsHuman.contains(nextAccountCursor.wireValue()));
    assertTrue(directAccountsHumanWithoutCursor.contains("(none)"));
    assertTrue(accountsCsv.contains("\"Cash, reserve\""));
    assertTrue(postingHuman.contains("Correlation id"));
    assertTrue(postingHuman.contains("posting-0"));
    assertTrue(postingHuman.contains("Correction"));
    assertTrue(postingRegisterHuman.contains("Next cursor"));
    assertTrue(postingRegisterHuman.contains(nextCursor.wireValue()));
    assertTrue(postingRegisterCsv.contains("posting-1"));
  }

  @Test
  void renderBalancesReportsAndMutationViewsAcrossOperatorFormats() {
    PostingFact postingFact = reversalPostingFact();
    PostingFact selfPostingFact = selfPostingFact();
    DeclaredAccount cashAccount = declaredAccount("1000", "Cash, reserve", NormalBalance.DEBIT);
    DeclaredAccount revenueAccount = declaredAccount("2000", "Revenue", NormalBalance.CREDIT);
    CurrencyBalance eurDebitBalance = eurDebitBalance();
    AccountBalanceSnapshot balanceSnapshot = accountBalanceSnapshot(cashAccount, eurDebitBalance);
    TrialBalanceReport trialBalanceReport = trialBalanceReport(cashAccount, eurDebitBalance);
    AccountLedgerReport accountLedgerReport =
        accountLedgerReport(cashAccount, postingFact, eurDebitBalance);
    AccountLedgerReport selfLedgerReport = selfLedgerReport(cashAccount, selfPostingFact);
    PeriodSummaryReport periodSummaryReport = periodSummaryReport(revenueAccount, eurDebitBalance);
    String accountBalanceHuman = CliQueryOutputRenderer.renderAccountBalanceHuman(balanceSnapshot);
    String accountBalanceCsv = CliQueryOutputRenderer.renderAccountBalanceCsv(balanceSnapshot);
    String trialBalanceHuman = CliQueryOutputRenderer.renderTrialBalanceHuman(trialBalanceReport);
    String trialBalanceCsv = CliQueryOutputRenderer.renderTrialBalanceCsv(trialBalanceReport);
    String accountLedgerHuman =
        CliQueryOutputRenderer.renderAccountLedgerHuman(accountLedgerReport);
    String accountLedgerCsv = CliQueryOutputRenderer.renderAccountLedgerCsv(accountLedgerReport);
    String selfLedgerHuman = CliQueryOutputRenderer.renderAccountLedgerHuman(selfLedgerReport);
    String periodSummaryHuman =
        CliQueryOutputRenderer.renderPeriodSummaryHuman(periodSummaryReport);
    String periodSummaryCsv = CliQueryOutputRenderer.renderPeriodSummaryCsv(periodSummaryReport);
    String generatedKeyHuman =
        CliMutationOutputRenderer.renderGeneratedBookKeyFileHuman(
            new SqliteBookKeyFileGenerator.GeneratedKeyFile(
                Path.of("office/keys/book.key"), "base64url-no-padding", 256, "0600"));
    String openBookHuman =
        CliMutationOutputRenderer.renderOpenBookHuman(
            Path.of("office/report.sqlite"),
            openedBookResult(Instant.parse("2026-04-07T10:15:30Z")));
    String rekeyBookHuman =
        CliMutationOutputRenderer.renderRekeyBookHuman(
            new RekeyBookResult.Rekeyed(Path.of("office/report.sqlite")));
    String declaredAccountHuman = CliMutationOutputRenderer.renderDeclaredAccountHuman(cashAccount);
    String preflightHuman =
        CliMutationOutputRenderer.renderPreflightAcceptedHuman(
            new PostEntryResult.PreflightAccepted(
                new IdempotencyKey("coverage-idem"), LocalDate.parse("2026-04-07")));
    String committedHuman =
        CliMutationOutputRenderer.renderCommittedHuman(
            new PostEntryResult.Committed(
                new PostingId("posting-committed"),
                new IdempotencyKey("coverage-idem"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z")));
    assertTrue(accountBalanceHuman.contains("Account Balance"));
    assertTrue(accountBalanceHuman.contains("Range"));
    assertTrue(accountBalanceCsv.contains("effectiveDateFrom,effectiveDateTo"));
    assertTrue(trialBalanceHuman.contains("Trial Balance"));
    assertTrue(trialBalanceHuman.contains("Effective date to"));
    assertTrue(
        trialBalanceCsv.contains("entityName,functionalCurrency,fiscalYearStart,effectiveDateTo"));
    assertTrue(accountLedgerHuman.contains("Account Ledger"));
    assertTrue(accountLedgerHuman.contains("Opening balances"));
    assertTrue(accountLedgerHuman.contains("2000"));
    assertTrue(accountLedgerCsv.contains("counterpartAccounts"));
    assertTrue(selfLedgerHuman.contains("(self)"));
    assertTrue(selfLedgerHuman.contains("(none)"));
    assertTrue(periodSummaryHuman.contains("Period Summary"));
    assertTrue(periodSummaryHuman.contains("Posting line count"));
    assertTrue(periodSummaryCsv.contains("effectiveDateFrom,effectiveDateTo,postingCount"));
    assertTrue(generatedKeyHuman.contains("Book Key File Generated"));
    assertTrue(openBookHuman.contains("Book Initialized"));
    assertTrue(openBookHuman.contains("Entity name"));
    assertTrue(openBookHuman.contains("Acme Studio"));
    assertTrue(openBookHuman.contains("Functional currency"));
    assertTrue(openBookHuman.contains("Fiscal year start"));
    assertTrue(rekeyBookHuman.contains("Book Rekeyed"));
    assertTrue(declaredAccountHuman.contains("Account Declared"));
    assertTrue(preflightHuman.contains("Entry Preflight Accepted"));
    assertTrue(committedHuman.contains("Entry Committed"));
  }

  @Test
  void renderStatementAndFormatterHelpers_coverAllAccountTypeAndEmptySectionBranches() {
    assertEquals(
        "Assets", CliQueryOutputFormatter.displayAccountTypeSectionLabel(AccountType.ASSET));
    assertEquals(
        "Liabilities",
        CliQueryOutputFormatter.displayAccountTypeSectionLabel(AccountType.LIABILITY));
    assertEquals(
        "Equity", CliQueryOutputFormatter.displayAccountTypeSectionLabel(AccountType.EQUITY));
    assertEquals(
        "Revenue", CliQueryOutputFormatter.displayAccountTypeSectionLabel(AccountType.REVENUE));
    assertEquals(
        "Expenses", CliQueryOutputFormatter.displayAccountTypeSectionLabel(AccountType.EXPENSE));
    assertEquals("Asset", CliQueryOutputFormatter.displayLineTypeLabel(AccountType.ASSET));
    assertEquals("Liability", CliQueryOutputFormatter.displayLineTypeLabel(AccountType.LIABILITY));
    assertEquals("Equity", CliQueryOutputFormatter.displayLineTypeLabel(AccountType.EQUITY));
    assertEquals("Revenue", CliQueryOutputFormatter.displayLineTypeLabel(AccountType.REVENUE));
    assertEquals("Expense", CliQueryOutputFormatter.displayLineTypeLabel(AccountType.EXPENSE));

    FinancialPositionReport emptyFinancialPosition =
        new FinancialPositionReport(
            bookIdentity(),
            Optional.empty(),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of());
    IncomeStatementReport emptyIncomeStatement =
        new IncomeStatementReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            standardOnly(),
            List.of(),
            List.of());
    ChangesInEquityReport changesInEquityReport = sampleChangesInEquityReport();

    String financialPositionHuman =
        CliReportOutputRenderer.renderFinancialPositionHuman(emptyFinancialPosition);
    String incomeStatementHuman =
        CliReportOutputRenderer.renderIncomeStatementHuman(emptyIncomeStatement);
    String changesInEquityHuman =
        CliReportOutputRenderer.renderChangesInEquityHuman(changesInEquityReport);

    assertTrue(financialPositionHuman.contains("Financial Position"));
    assertTrue(financialPositionHuman.contains("(none)"));
    assertTrue(incomeStatementHuman.contains("Income Statement"));
    assertTrue(incomeStatementHuman.contains("(none)"));
    assertTrue(changesInEquityHuman.contains("Changes In Equity"));
    assertTrue(changesInEquityHuman.contains("Balanced"));
  }

  @Test
  void displayRowKind_labelsSyntheticAndDeclaredRows() {
    assertEquals("Synthetic", CliQueryOutputFormatter.displayRowKind(true));
    assertEquals("Account", CliQueryOutputFormatter.displayRowKind(false));
  }

  @Test
  void formatterHelpers_coverCsvRowsAndRemainingRoleLabels() {
    DeclaredAccount contraRevenueAccount =
        declaredAccount(
            "2900",
            "Sales returns",
            AccountType.REVENUE,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    CurrencyBalance balance = eurDebitBalance();
    PostingFact postingFact = reversalPostingFact();

    assertEquals(
        List.of("EUR", "10.00", "4.00", "6.00", "DEBIT"),
        CliQueryOutputFormatter.balanceCsvRow(balance));
    assertEquals(
        List.of(
            "2900",
            "Sales returns",
            "REVENUE",
            "CONTRA",
            "DEBIT",
            "true",
            "EUR",
            "10.00",
            "4.00",
            "6.00",
            "DEBIT"),
        CliQueryOutputFormatter.trialBalanceCsvRow(
            new TrialBalanceRow(contraRevenueAccount, balance)));
    assertEquals(
        List.of(
            "2026-04-07",
            "2026-04-07T10:15:30Z",
            "posting-1",
            "EUR",
            "10.00",
            "4.00",
            "6.00",
            "DEBIT",
            "1000, 2000"),
        CliQueryOutputFormatter.accountLedgerCsvRow(
            contraRevenueAccount,
            accountLedgerReport(contraRevenueAccount, postingFact, balance).entries().getFirst()));
    assertEquals(
        List.of(
            "2900",
            "Sales returns",
            "REVENUE",
            "CONTRA",
            "DEBIT",
            "EUR",
            "10.00",
            "4.00",
            "6.00",
            "DEBIT"),
        CliQueryOutputFormatter.periodActivityCsvRow(
            new PeriodAccountActivityRow(contraRevenueAccount, balance)));
    assertEquals("Contra", CliQueryOutputFormatter.displayAccountRoleLabel(AccountRole.CONTRA));
    assertEquals(
        "Retained earnings",
        CliQueryOutputFormatter.displayAccountRoleLabel(AccountRole.RETAINED_EARNINGS));
  }

  @Test
  void renderBookInspectionHuman_coversEveryStatusLabel() {
    assertTrue(
        CliQueryOutputRenderer.renderBookInspectionHuman(
                Path.of("office/report.sqlite"),
                new BookInspection.Existing(BookInspection.Status.FOREIGN_SQLITE, 123, 0, 1))
            .contains("Foreign SQLite"));
    assertTrue(
        CliQueryOutputRenderer.renderBookInspectionHuman(
                Path.of("office/report.sqlite"),
                new BookInspection.Existing(
                    BookInspection.Status.UNSUPPORTED_FORMAT_VERSION, 123, 99, 4))
            .contains("Unsupported format version"));
    assertTrue(
        CliQueryOutputRenderer.renderBookInspectionHuman(
                Path.of("office/report.sqlite"),
                new BookInspection.Existing(BookInspection.Status.INCOMPLETE_FINGRIND, 123, 2, 4))
            .contains("Incomplete FinGrind"));
  }

  @Test
  void postingWireLabels_coverSystemInternalAndFallbackValues() {
    assertEquals("System", CliPostingOutputRenderer.displayWireLabel("SYSTEM"));
    assertEquals("Internal", CliPostingOutputRenderer.displayWireLabel("INTERNAL"));
    assertEquals("agent batch", CliPostingOutputRenderer.displayWireLabel("AGENT_BATCH"));
  }
}
