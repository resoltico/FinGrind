package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingPolicyProfile;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.BusinessActivityTag;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
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
            accountPage(List.of(cashAccount), 50, Optional.of(nextAccountCursor)));
    String accountsHumanWithoutCursor =
        CliQueryOutputRenderer.renderAccountsHuman(
            accountPage(List.of(cashAccount), 50, Optional.empty()));
    String directAccountsHuman =
        CliAccountPageOutputRenderer.renderHuman(
            accountPage(List.of(cashAccount), 50, Optional.of(nextAccountCursor)));
    String directAccountsHumanWithoutCursor =
        CliAccountPageOutputRenderer.renderHuman(
            accountPage(List.of(cashAccount), 50, Optional.empty()));
    String accountsCsv =
        CliQueryOutputRenderer.renderAccountsCsv(
            accountPage(List.of(cashAccount), 50, Optional.empty()));
    String postingHuman = CliQueryOutputRenderer.renderPostingHuman(bookIdentity(), postingFact);
    PostingPageCursor nextCursor =
        new PostingPageCursor(
            LocalDate.parse("2026-04-30"),
            Instant.parse("2026-04-07T10:15:30Z"),
            new PostingId("posting-1"));
    String postingRegisterHuman =
        CliQueryOutputRenderer.renderPostingRegisterHuman(
            postingPage(List.of(postingFact), 10, Optional.of(nextCursor)));
    String postingRegisterCsv =
        CliQueryOutputRenderer.renderPostingRegisterCsv(
            postingPage(List.of(postingFact), 10, Optional.empty()));
    assertTrue(missingInspection.contains("Missing"));
    assertTrue(missingInspection.contains("Can initialize with open-book"));
    assertTrue(missingInspection.contains("Yes"));
    assertTrue(missingInspection.contains("Supported book format version"));
    assertTrue(missingInspection.contains("Migration policy"));
    assertTrue(missingInspection.contains("Hard-break line; reject older formats"));
    assertTrue(existingInspection.contains("SQLite applicationId"));
    assertTrue(existingInspection.contains("State"));
    assertTrue(existingInspection.contains("Blank SQLite"));
    assertTrue(initializedInspection.contains("Initialized at"));
    assertTrue(initializedInspection.contains("Entity"));
    assertTrue(initializedInspection.contains("Acme Studio"));
    assertTrue(initializedInspection.contains("Policy profile"));
    assertTrue(initializedInspection.contains("Functional currency"));
    assertTrue(initializedInspection.contains("Fiscal year start"));
    assertTrue(initializedInspection.contains("Internal Management Single Entity V1"));
    assertTrue(accountsHuman.contains("Cash, reserve"));
    assertTrue(accountsHuman.contains("Current asset"));
    assertTrue(accountsHuman.contains(nextAccountCursor.wireValue()));
    assertTrue(accountsHumanWithoutCursor.contains("(none)"));
    assertTrue(directAccountsHuman.contains("Current asset"));
    assertTrue(directAccountsHuman.contains(nextAccountCursor.wireValue()));
    assertTrue(directAccountsHumanWithoutCursor.contains("(none)"));
    assertTrue(accountsCsv.contains("\"Cash, reserve\""));
    assertTrue(postingHuman.contains("Correlation id"));
    assertTrue(postingHuman.contains("posting-0"));
    assertTrue(postingHuman.contains("actor-1"));
    assertTrue(postingHuman.contains("command-1"));
    assertTrue(postingHuman.contains("idem-1"));
    assertTrue(postingHuman.contains("cause-1"));
    assertTrue(postingHuman.contains("Correction"));
    assertTrue(postingRegisterHuman.contains("Next cursor"));
    assertTrue(postingRegisterHuman.contains(nextCursor.wireValue()));
    assertTrue(postingRegisterCsv.contains("posting-1"));
  }

  @Test
  void renderBookInspectionHuman_joinsNonEmptyBusinessActivityTags() {
    BookIdentity taggedIdentity =
        new BookIdentity(
            new EntityProfile(
                new BookEntityName("Acme Studio"),
                List.of(
                    new BusinessActivityTag("translation,localization"),
                    new BusinessActivityTag("cafe services"))),
            CurrencyUnit.of("EUR"),
            FiscalYearStart.parse("01-01"),
            AccountingPolicyProfile.INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1);
    String inspection =
        CliQueryOutputRenderer.renderBookInspectionHuman(
            Path.of("office/report.sqlite"),
            new BookInspection.Initialized(
                123,
                1,
                1,
                Instant.parse("2026-04-07T10:15:30Z"),
                taggedIdentity,
                closeReadyInspection()));

    assertTrue(inspection.contains("Business activity"));
    assertTrue(inspection.contains("translation,localization, cafe services"));
    assertTrue(inspection.contains("Policy profile"));
    assertTrue(inspection.contains("Internal Management Single Entity V1"));
  }

  @Test
  void renderBookInspectionHuman_includesPolicyProfileRows() {
    BookIdentity registeredIdentity =
        new BookIdentity(
            new EntityProfile(new BookEntityName("Registered Studio"), List.of()),
            CurrencyUnit.of("EUR"),
            FiscalYearStart.parse("01-01"),
            AccountingPolicyProfile.INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1);
    String inspection =
        CliQueryOutputRenderer.renderBookInspectionHuman(
            Path.of("office/report.sqlite"),
            new BookInspection.Initialized(
                123,
                1,
                1,
                Instant.parse("2026-04-07T10:15:30Z"),
                registeredIdentity,
                closeReadyInspection()));

    assertTrue(inspection.contains("Policy profile"));
    assertTrue(inspection.contains("Internal Management Single Entity V1"));
  }

  @Test
  void renderBookInspectionHuman_showsNoCandidateAccountsWhenBlockedWithoutCandidates() {
    String inspection =
        CliQueryOutputRenderer.renderBookInspectionHuman(
            Path.of("office/report.sqlite"),
            new BookInspection.Initialized(
                123,
                1,
                1,
                Instant.parse("2026-04-07T10:15:30Z"),
                bookIdentity(),
                new BookInspection.CloseReadiness(
                    false,
                    FinancialPositionLineClassification.ACCUMULATED_RESULT,
                    null,
                    "closing-equity-account-candidate-missing",
                    "No active declared closing-equity account satisfies required classification 'ACCUMULATED_RESULT'.",
                    List.of())));

    assertTrue(inspection.contains("Candidate accounts"));
    assertTrue(inspection.contains("(none)"));
  }

  @Test
  void renderBookInspectionHuman_listsCandidateAccountsWhenBlockedWithCandidates() {
    String inspection =
        CliQueryOutputRenderer.renderBookInspectionHuman(
            Path.of("office/report.sqlite"),
            new BookInspection.Initialized(
                123,
                1,
                1,
                Instant.parse("2026-04-07T10:15:30Z"),
                bookIdentity(),
                new BookInspection.CloseReadiness(
                    false,
                    FinancialPositionLineClassification.ACCUMULATED_RESULT,
                    null,
                    "closing-equity-account-candidate-ambiguous",
                    "More than one active declared closing-equity account satisfies required classification 'ACCUMULATED_RESULT': 3200, 3210.",
                    List.of(new AccountCode("3200"), new AccountCode("3210")))));

    assertTrue(inspection.contains("Candidate accounts"));
    assertTrue(inspection.contains("3200, 3210"));
  }

  @Test
  void renderBalanceAndLedgerReportsAcrossOperatorFormats() {
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

    assertBalanceOutputSamples(
        accountBalanceHuman, accountBalanceCsv, trialBalanceHuman, trialBalanceCsv);
    assertLedgerOutputSamples(
        accountLedgerHuman,
        accountLedgerCsv,
        selfLedgerHuman,
        periodSummaryHuman,
        periodSummaryCsv);
  }

  @Test
  void renderHumanRowsAndEmptyLedgerSurfaces_coverCompactNoneBranches() {
    PostingFact postingFact = reversalPostingFact();
    DeclaredAccount cashAccount = declaredAccount("1000", "Cash, reserve", NormalBalance.DEBIT);
    CurrencyBalance balance = eurDebitBalance();
    AccountLedgerEntry ledgerEntry =
        new AccountLedgerEntry(postingFact, balance, money("EUR", "6.00"), BalanceSide.DEBIT);
    PeriodAccountActivityRow activityRow = new PeriodAccountActivityRow(cashAccount, balance);

    List<String> ledgerHumanRow =
        CliQueryOutputFormatter.accountLedgerHumanRow(cashAccount, ledgerEntry);
    assertEquals("2026-04-07", ledgerHumanRow.get(0));
    assertEquals("posting-1", ledgerHumanRow.get(2));
    assertEquals("Standard", ledgerHumanRow.get(3));
    assertEquals("Reversal", ledgerHumanRow.get(4));
    assertEquals("posting-0", ledgerHumanRow.get(5));
    assertEquals("EUR", ledgerHumanRow.get(6));
    assertEquals("10.00", ledgerHumanRow.get(7));
    assertEquals("4.00", ledgerHumanRow.get(8));
    assertEquals("6.00", ledgerHumanRow.get(9));
    assertEquals("Debit", ledgerHumanRow.get(10));
    assertEquals("2000", ledgerHumanRow.get(11));

    List<String> periodActivityHumanRow =
        CliQueryOutputFormatter.periodActivityHumanRow(activityRow);
    assertEquals("1000", periodActivityHumanRow.get(0));
    assertEquals("Cash, reserve", periodActivityHumanRow.get(1));
    assertEquals("Asset", periodActivityHumanRow.get(2));
    assertEquals("Ordinary", periodActivityHumanRow.get(3));
    assertEquals("Debit", periodActivityHumanRow.get(4));
    assertEquals("EUR", periodActivityHumanRow.get(5));
    assertEquals("10.00", periodActivityHumanRow.get(6));
    assertEquals("4.00", periodActivityHumanRow.get(7));
    assertEquals("6.00", periodActivityHumanRow.get(8));
    assertEquals("Debit", periodActivityHumanRow.get(9));

    String emptyAccountsHuman =
        CliAccountPageOutputRenderer.renderHuman(accountPage(List.of(), 50, Optional.empty()));
    String emptyPostingsHuman =
        CliPostingOutputRenderer.renderPostingRegisterHuman(
            postingPage(List.of(), 10, Optional.empty()));
    String emptyLedgerHuman =
        CliReportOutputRenderer.renderAccountLedgerHuman(sampleAccountLedgerReport());
    TrialBalanceReport comparativeWithoutReference =
        trialBalanceReport(
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(new TrialBalanceRow(cashAccount, balance)),
            List.of(new TrialBalanceRow(cashAccount, balance)));
    String comparativeTrialBalanceHuman =
        CliReportOutputRenderer.renderTrialBalanceHuman(comparativeWithoutReference);

    assertTrue(emptyAccountsHuman.contains("(none)"));
    assertTrue(emptyPostingsHuman.contains("(none)"));
    assertTrue(emptyLedgerHuman.contains("Entries"));
    assertTrue(emptyLedgerHuman.contains("(none)"));
    assertTrue(comparativeTrialBalanceHuman.contains("Comparative Trial Balance"));
    assertTrue(comparativeTrialBalanceHuman.contains("(none)"));
  }

  @Test
  void renderMutationViewsAcrossOperatorFormats() {
    DeclaredAccount cashAccount = declaredAccount("1000", "Cash, reserve", NormalBalance.DEBIT);
    DeclaredAccount childAccount =
        new DeclaredAccount(
            new AccountCode("1100"),
            new AccountName("Petty Cash"),
            AccountType.ASSET,
            AccountRole.ORDINARY,
            new AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                Optional.of(new AccountCode("1000")),
                Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                Optional.empty()),
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
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
            new RekeyBookResult.Rekeyed(Path.of("office/report.sqlite")),
            new BookAccess.PassphraseSource.KeyFile(Path.of("office/keys/rotated.key")));
    String declaredAccountHuman = CliMutationOutputRenderer.renderDeclaredAccountHuman(cashAccount);
    String childAccountHuman = CliMutationOutputRenderer.renderDeclaredAccountHuman(childAccount);
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
    assertTrue(generatedKeyHuman.contains("Book Key File Generated"));
    assertTrue(openBookHuman.contains("Book Initialized"));
    assertTrue(openBookHuman.contains("Entity"));
    assertTrue(openBookHuman.contains("Acme Studio"));
    assertTrue(openBookHuman.contains("Policy profile"));
    assertTrue(openBookHuman.contains("Functional currency"));
    assertTrue(openBookHuman.contains("Fiscal year start"));
    assertTrue(openBookHuman.contains("Internal Management Single Entity V1"));
    assertTrue(rekeyBookHuman.contains("Book Rekeyed"));
    assertTrue(rekeyBookHuman.contains("Replacement secret source"));
    assertTrue(rekeyBookHuman.contains("Replacement key file"));
    assertTrue(declaredAccountHuman.contains("Account Declared"));
    assertTrue(declaredAccountHuman.contains("Parent account"));
    assertTrue(declaredAccountHuman.contains("(none)"));
    assertTrue(childAccountHuman.contains("Parent account"));
    assertTrue(childAccountHuman.contains("1000"));
    assertTrue(preflightHuman.contains("Entry Preflight Accepted"));
    assertTrue(committedHuman.contains("Entry Committed"));
    assertTrue(committedHuman.contains("posting-committed"));
    assertTrue(committedHuman.contains("coverage-idem"));
  }

  private static void assertBalanceOutputSamples(
      String accountBalanceHuman,
      String accountBalanceCsv,
      String trialBalanceHuman,
      String trialBalanceCsv) {
    assertTrue(accountBalanceHuman.contains("Account Balance"));
    assertTrue(accountBalanceHuman.contains("Range"));
    assertTrue(
        accountBalanceCsv.contains(
            "accountCode,accountName,accountType,accountRole,normalBalance,effectiveDateFrom,effectiveDateTo,currencyCode,debitTotal,creditTotal,netAmount,balanceSide"));
    assertTrue(trialBalanceHuman.contains("Trial Balance"));
    assertTrue(trialBalanceHuman.contains("As of"));
    assertTrue(trialBalanceHuman.contains("Balanced"));
    assertTrue(
        trialBalanceCsv.contains(
            "reportBasis,recordKind,effectiveDateAsOf,balanced,accountCode,accountName,accountType,accountRole,normalBalance,active,currencyCode,debitTotal,creditTotal,netAmount,balanceSide"));
  }

  private static void assertLedgerOutputSamples(
      String accountLedgerHuman,
      String accountLedgerCsv,
      String selfLedgerHuman,
      String periodSummaryHuman,
      String periodSummaryCsv) {
    assertTrue(accountLedgerHuman.contains("Account Ledger"));
    assertTrue(accountLedgerHuman.contains("Opening balances"));
    assertTrue(accountLedgerHuman.contains("2000"));
    assertTrue(accountLedgerCsv.contains("counterpartAccounts"));
    List<String> accountLedgerCsvLines = accountLedgerCsv.lines().toList();
    int accountLedgerCsvColumnCount = csvFieldCount(accountLedgerCsvLines.getFirst());
    for (String line : accountLedgerCsvLines) {
      assertEquals(accountLedgerCsvColumnCount, csvFieldCount(line));
    }
    assertTrue(selfLedgerHuman.contains("(self)"));
    assertTrue(selfLedgerHuman.contains("(none)"));
    assertTrue(periodSummaryHuman.contains("Period Summary"));
    assertTrue(periodSummaryHuman.contains("Posting line count"));
    assertTrue(
        periodSummaryCsv.contains("recordKind,postingCount,postingLineCount,accountsTouched"));
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
            List.of(),
            List.of());
    IncomeStatementReport emptyIncomeStatement =
        new IncomeStatementReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            standardOnly(),
            List.of(),
            List.of(),
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

  private static int csvFieldCount(String row) {
    int fieldCount = 1;
    boolean insideQuotes = false;
    int index = 0;
    while (index < row.length()) {
      char character = row.charAt(index);
      if (character == '"') {
        if (insideQuotes && index + 1 < row.length() && row.charAt(index + 1) == '"') {
          index++;
        } else {
          insideQuotes = !insideQuotes;
        }
      } else if (character == ',' && !insideQuotes) {
        fieldCount++;
      }
      index++;
    }
    return fieldCount;
  }

  @Test
  void displayRowKind_labelsDeclaredAndDerivedRows() {
    assertEquals(
        "Current period result",
        CliQueryOutputFormatter.displayRowKind(StatementLineKind.CURRENT_PERIOD_RESULT));
    assertEquals(
        "Account", CliQueryOutputFormatter.displayRowKind(StatementLineKind.DECLARED_ACCOUNT));
  }

  @Test
  void statementHumanRenderers_hideSyntheticLineCodesForDerivedRows() {
    CurrencyBalance creditBalance =
        CliResponseWriterTestSupport.currencyBalance(
            "EUR", "0.00", "10.00", "10.00", BalanceSide.CREDIT);
    FinancialPositionReport financialPositionReport =
        new FinancialPositionReport(
            bookIdentity(),
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(
                new FinancialPositionSection(
                    AccountType.EQUITY,
                    List.of(
                        new FinancialPositionRow(
                            "current-period-result",
                            "Current period result",
                            AccountType.EQUITY,
                            Optional.empty(),
                            Optional.empty(),
                            StatementLineKind.CURRENT_PERIOD_RESULT,
                            creditBalance)),
                    List.of(creditBalance))),
            List.of());
    ChangesInEquityReport changesInEquityReport =
        new ChangesInEquityReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(
                new dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow(
                    "current-period-result",
                    "Current period result",
                    Optional.of(AccountType.EQUITY),
                    Optional.empty(),
                    Optional.empty(),
                    StatementLineKind.CURRENT_PERIOD_RESULT,
                    CliResponseWriterTestSupport.currencyBalance(
                        "EUR", "0.00", "0.00", "0.00", BalanceSide.ZERO),
                    creditBalance,
                    creditBalance)),
            List.of(),
            List.of(creditBalance),
            List.of(creditBalance),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    String financialPositionHuman =
        CliReportOutputRenderer.renderFinancialPositionHuman(financialPositionReport);
    String changesInEquityHuman =
        CliReportOutputRenderer.renderChangesInEquityHuman(changesInEquityReport);

    assertTrue(financialPositionHuman.contains("(derived)"));
    assertFalse(financialPositionHuman.contains("current-period-result"));
    assertTrue(changesInEquityHuman.contains("(derived)"));
    assertFalse(changesInEquityHuman.contains("current-period-result"));
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
    PostingFact directPostingFact = directPostingFact();

    assertEquals(
        "(derived)",
        CliQueryOutputFormatter.displayStatementLineCode(
            "current-period-result", StatementLineKind.CURRENT_PERIOD_RESULT));
    assertEquals(
        "3000",
        CliQueryOutputFormatter.displayStatementLineCode(
            "3000", StatementLineKind.DECLARED_ACCOUNT));
    assertEquals("Direct", CliQueryOutputFormatter.displayPostingRoleHuman(directPostingFact));
    assertEquals("Reversal", CliQueryOutputFormatter.displayPostingRoleHuman(postingFact));
    assertEquals(
        "(not a reversal)", CliQueryOutputFormatter.reversalTargetHuman(directPostingFact));
    assertEquals("posting-0", CliQueryOutputFormatter.reversalTargetHuman(postingFact));
    assertEquals(
        List.of("EUR", "10.00", "4.00", "6.00", "DEBIT"),
        CliQueryOutputFormatter.balanceCsvRow(balance));
    assertEquals(
        List.of(
            "2026-04-07",
            "2026-04-07 10:15:30 UTC",
            "posting-1",
            "Standard",
            "Reversal",
            "EUR",
            "10.00",
            "10.00",
            "1000, 2000",
            "posting-0"),
        CliQueryOutputFormatter.postingRegisterHumanRow(postingFact));
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
            "STANDARD",
            "reversal",
            "posting-0",
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
    assertEquals("Standard", CliQueryOutputFormatter.displayPostingKind(PostingKind.STANDARD));
    assertEquals(
        "Period close", CliQueryOutputFormatter.displayPostingKind(PostingKind.PERIOD_CLOSE));
    assertEquals(
        "Opening balance", CliQueryOutputFormatter.displayPostingKind(PostingKind.OPENING_BALANCE));
  }

  @Test
  void accountPageAndClassificationHelpers_coverAllTaxonomyLabelsAndPresentOptionalValues() {
    DeclaredAccount equityAccount =
        new DeclaredAccount(
            new AccountCode("3200"),
            new AccountName("Contributed capital account"),
            AccountType.EQUITY,
            AccountRole.ORDINARY,
            new AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                Optional.of(new AccountCode("3000")),
                Optional.of(FinancialPositionLineClassification.CONTRIBUTED_CAPITAL),
                Optional.empty()),
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    DeclaredAccount expenseAccount =
        new DeclaredAccount(
            new AccountCode("5000"),
            new AccountName("Cost of sales"),
            AccountType.EXPENSE,
            fixtureAccountRole(AccountType.EXPENSE, NormalBalance.DEBIT),
            new AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                Optional.of(new AccountCode("5100")),
                Optional.empty(),
                Optional.of(ProfitAndLossLineClassification.COST_OF_SALES)),
            true,
            Instant.parse("2026-04-07T10:15:30Z"));

    String human =
        CliAccountPageOutputRenderer.renderHuman(
            accountPage(List.of(equityAccount, expenseAccount), 50, Optional.empty()));
    String csv =
        CliAccountPageOutputRenderer.renderCsv(
            accountPage(List.of(equityAccount, expenseAccount), 50, Optional.empty()));

    assertEquals(
        "Current asset",
        CliQueryOutputFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.CURRENT_ASSET));
    assertEquals(
        "Non-current asset",
        CliQueryOutputFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.NONCURRENT_ASSET));
    assertEquals(
        "Current liability",
        CliQueryOutputFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.CURRENT_LIABILITY));
    assertEquals(
        "Non-current liability",
        CliQueryOutputFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.NONCURRENT_LIABILITY));
    assertEquals(
        "Contributed capital",
        CliQueryOutputFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.CONTRIBUTED_CAPITAL));
    assertEquals(
        "Distributions",
        CliQueryOutputFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.DISTRIBUTIONS));
    assertEquals(
        "Contributed capital",
        CliQueryOutputFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.CONTRIBUTED_CAPITAL));
    assertEquals(
        "Distributions",
        CliQueryOutputFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.DISTRIBUTIONS));
    assertEquals(
        "Contributed capital",
        CliQueryOutputFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.CONTRIBUTED_CAPITAL));
    assertEquals(
        "Accumulated result",
        CliQueryOutputFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.ACCUMULATED_RESULT));
    assertEquals(
        "Accumulated result",
        CliQueryOutputFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.ACCUMULATED_RESULT));
    assertEquals(
        "Reserve",
        CliQueryOutputFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.RESERVE));
    assertEquals(
        "(derived)",
        CliQueryOutputFormatter.displayFinancialPositionLineClassification(Optional.empty()));
    assertEquals(
        "Other equity",
        CliQueryOutputFormatter.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.OTHER_EQUITY));
    assertEquals(
        "Operating revenue",
        CliQueryOutputFormatter.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.OPERATING_REVENUE));
    assertEquals(
        "Other revenue",
        CliQueryOutputFormatter.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.OTHER_REVENUE));
    assertEquals(
        "Finance income",
        CliQueryOutputFormatter.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.FINANCE_INCOME));
    assertEquals(
        "Cost of sales",
        CliQueryOutputFormatter.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.COST_OF_SALES));
    assertEquals(
        "Operating expense",
        CliQueryOutputFormatter.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.OPERATING_EXPENSE));
    assertEquals(
        "Depreciation and amortization",
        CliQueryOutputFormatter.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.DEPRECIATION_AND_AMORTIZATION));
    assertEquals(
        "Finance expense",
        CliQueryOutputFormatter.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.FINANCE_EXPENSE));
    assertEquals(
        "Tax expense",
        CliQueryOutputFormatter.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.TAX_EXPENSE));

    assertTrue(human.contains("Contributed capital"));
    assertTrue(human.contains("3000"));
    assertTrue(human.contains("Cost of sales"));
    assertTrue(human.contains("5100"));
    assertTrue(csv.contains("CONTRIBUTED_CAPITAL"));
    assertTrue(csv.contains("COST_OF_SALES"));
  }

  @Test
  void renderStatementHumans_skipEmptySectionsAndKeepTotalsOnlySections() {
    CurrencyBalance debitBalance = eurDebitBalance();
    FinancialPositionReport financialPositionReport =
        new FinancialPositionReport(
            bookIdentity(),
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.of(null, LocalDate.parse("2025-04-30")),
            allPostingKinds(),
            List.of(
                new FinancialPositionSection(
                    AccountType.ASSET,
                    List.of(
                        new FinancialPositionRow(
                            "1000",
                            "Cash without Totals",
                            AccountType.ASSET,
                            Optional.of(AccountRole.ORDINARY),
                            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                            StatementLineKind.DECLARED_ACCOUNT,
                            debitBalance)),
                    List.of()),
                new FinancialPositionSection(AccountType.LIABILITY, List.of(), List.of()),
                new FinancialPositionSection(AccountType.EQUITY, List.of(), List.of(debitBalance))),
            List.of());
    IncomeStatementReport incomeStatementReport =
        new IncomeStatementReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            standardOnly(),
            List.of(
                new IncomeStatementSection(
                    AccountType.REVENUE,
                    List.of(
                        new IncomeStatementRow(
                            "4000",
                            "Revenue without Totals",
                            AccountType.REVENUE,
                            Optional.of(AccountRole.ORDINARY),
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            StatementLineKind.DECLARED_ACCOUNT,
                            debitBalance)),
                    List.of()),
                new IncomeStatementSection(AccountType.EXPENSE, List.of(), List.of()),
                new IncomeStatementSection(AccountType.EXPENSE, List.of(), List.of(debitBalance))),
            List.of(),
            List.of(),
            List.of());

    String financialPositionHuman =
        CliReportOutputRenderer.renderFinancialPositionHuman(financialPositionReport);
    String incomeStatementHuman =
        CliReportOutputRenderer.renderIncomeStatementHuman(incomeStatementReport);

    assertTrue(financialPositionHuman.contains("Cash without Totals"));
    assertTrue(financialPositionHuman.contains("Equity"));
    assertTrue(financialPositionHuman.contains("Section totals"));
    assertFalse(financialPositionHuman.contains("Liabilities"));
    assertFalse(financialPositionHuman.contains("Comparative Financial Position"));
    assertTrue(incomeStatementHuman.contains("Revenue without Totals"));
    assertTrue(incomeStatementHuman.contains("Expenses"));
    assertTrue(incomeStatementHuman.contains("Section totals"));
    assertFalse(incomeStatementHuman.contains("Comparative Income Statement"));
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
    assertEquals("CLI", CliPostingOutputRenderer.displayWireLabel("CLI"));
    assertEquals("Human", CliPostingOutputRenderer.displayWireLabel("HUMAN"));
    assertEquals("System", CliPostingOutputRenderer.displayWireLabel("SYSTEM"));
    assertEquals("Internal", CliPostingOutputRenderer.displayWireLabel("INTERNAL"));
    assertEquals("agent batch", CliPostingOutputRenderer.displayWireLabel("AGENT_BATCH"));
  }

  @Test
  void reportRenderers_andBalanceFormatter_renderComparativeBranches() {
    DeclaredAccount cashAccount = declaredAccount("1000", "Cash", NormalBalance.DEBIT);
    CurrencyBalance eurDebitBalance = eurDebitBalance();
    TrialBalanceRow currentRow = new TrialBalanceRow(cashAccount, eurDebitBalance);
    TrialBalanceRow comparativeRow =
        new TrialBalanceRow(
            cashAccount,
            CliResponseWriterTestSupport.currencyBalance(
                "EUR", "7.00", "1.00", "6.00", BalanceSide.DEBIT));
    TrialBalanceReport comparativeTrialBalance =
        trialBalanceReport(
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.of(null, LocalDate.parse("2025-04-30")),
            allPostingKinds(),
            List.of(currentRow),
            List.of(comparativeRow));
    IncomeStatementReport comparativeTotalsOnlyIncomeStatement =
        new IncomeStatementReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            standardOnly(),
            List.of(),
            List.of(),
            List.of(),
            List.of(eurDebitBalance));
    ChangesInEquityReport partialComparativeEquityReport =
        new ChangesInEquityReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            allPostingKinds(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(eurDebitBalance),
            List.of());

    String trialBalanceHuman =
        CliReportOutputRenderer.renderTrialBalanceHuman(comparativeTrialBalance);
    String trialBalanceCsv = CliReportOutputRenderer.renderTrialBalanceCsv(comparativeTrialBalance);
    String incomeStatementHuman =
        CliReportOutputRenderer.renderIncomeStatementHuman(comparativeTotalsOnlyIncomeStatement);
    String changesInEquityHuman =
        CliReportOutputRenderer.renderChangesInEquityHuman(partialComparativeEquityReport);

    assertEquals(
        "EUR 6.00 DEBIT", CliQueryOutputFormatter.displayBalance(comparativeRow.balance()));
    assertTrue(trialBalanceHuman.contains("Comparative Trial Balance"));
    assertTrue(trialBalanceCsv.contains("comparative"));
    assertTrue(incomeStatementHuman.contains("Comparative Income Statement"));
    assertTrue(incomeStatementHuman.contains("Comparative Net Income Totals"));
    assertTrue(changesInEquityHuman.contains("Comparative Changes In Equity"));
    assertTrue(changesInEquityHuman.contains("Comparative movement totals"));
  }

  @Test
  void renderChangesInEquityHuman_omitsComparativeSectionWhenComparativeDataIsAbsent() {
    ChangesInEquityReport nonComparativeReport =
        new ChangesInEquityReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            allPostingKinds(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    String rendered = CliReportOutputRenderer.renderChangesInEquityHuman(nonComparativeReport);

    assertTrue(rendered.contains("Changes In Equity"));
    assertTrue(rendered.contains("Outcome"));
    assertTrue(rendered.contains("No equity balances or movements matched the selected period."));
    assertFalse(rendered.contains("Comparative Changes In Equity"));
  }

  @Test
  void renderChangesInEquityHuman_rendersComparativeSectionWhenOnlyClosingTotalsExist() {
    CurrencyBalance comparativeClosing =
        CliResponseWriterTestSupport.currencyBalance(
            "EUR", "0.00", "8.00", "8.00", BalanceSide.CREDIT);
    ChangesInEquityReport report =
        new ChangesInEquityReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            allPostingKinds(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(comparativeClosing));

    String rendered = CliReportOutputRenderer.renderChangesInEquityHuman(report);

    assertTrue(rendered.contains("Comparative Changes In Equity"));
    assertTrue(rendered.contains("Comparative closing totals"));
  }

  @Test
  void renderChangesInEquityHuman_rendersComparativeSectionWhenOnlyOpeningTotalsExist() {
    CurrencyBalance comparativeOpening =
        CliResponseWriterTestSupport.currencyBalance(
            "EUR", "4.00", "0.00", "4.00", BalanceSide.DEBIT);
    ChangesInEquityReport report =
        new ChangesInEquityReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            allPostingKinds(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(comparativeOpening),
            List.of(),
            List.of());

    String rendered = CliReportOutputRenderer.renderChangesInEquityHuman(report);

    assertTrue(rendered.contains("Comparative Changes In Equity"));
    assertTrue(rendered.contains("Comparative opening totals"));
  }

  @Test
  void renderChangesInEquityHuman_rendersComparativeSectionWhenOnlyMovementTotalsExist() {
    CurrencyBalance comparativeMovement =
        CliResponseWriterTestSupport.currencyBalance(
            "EUR", "0.00", "5.00", "5.00", BalanceSide.CREDIT);
    ChangesInEquityReport report =
        new ChangesInEquityReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            allPostingKinds(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(comparativeMovement),
            List.of());

    String rendered = CliReportOutputRenderer.renderChangesInEquityHuman(report);

    assertTrue(rendered.contains("Comparative Changes In Equity"));
    assertTrue(rendered.contains("Comparative movement totals"));
  }

  @Test
  void renderChangesInEquityHuman_rendersComparativeRowsWithoutSyntheticTotalsBlock() {
    CurrencyBalance zeroBalance =
        CliResponseWriterTestSupport.currencyBalance(
            "EUR", "0.00", "0.00", "0.00", BalanceSide.ZERO);
    ChangesInEquityReport report =
        new ChangesInEquityReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            allPostingKinds(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(
                new dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow(
                    "equity-rollforward",
                    "Equity rollforward",
                    Optional.of(AccountType.EQUITY),
                    Optional.empty(),
                    Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
                    StatementLineKind.DECLARED_ACCOUNT,
                    zeroBalance,
                    zeroBalance,
                    zeroBalance)),
            List.of(),
            List.of(),
            List.of());

    String rendered = CliReportOutputRenderer.renderChangesInEquityHuman(report);

    assertTrue(rendered.contains("Comparative Changes In Equity"));
    assertTrue(rendered.contains("Equity rollforward"));
    assertFalse(rendered.contains("Comparative opening totals"));
    assertFalse(rendered.contains("Comparative movement totals"));
    assertFalse(rendered.contains("Comparative closing totals"));
  }

  @Test
  void reportSurfacePolicy_detectsTrialBalanceAndCurrentEquityComparatives() {
    DeclaredAccount cashAccount = declaredAccount("1000", "Cash", NormalBalance.DEBIT);
    TrialBalanceReport nonComparativeTrialBalance =
        trialBalanceReport(
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(new TrialBalanceRow(cashAccount, eurDebitBalance())),
            List.of());
    ChangesInEquityReport nonCurrentEquityReport =
        new ChangesInEquityReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    ChangesInEquityReport openingOnlyEquityReport =
        new ChangesInEquityReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(),
            List.of(
                CliResponseWriterTestSupport.currencyBalance(
                    "EUR", "4.00", "0.00", "4.00", BalanceSide.DEBIT)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    ChangesInEquityReport movementOnlyEquityReport =
        new ChangesInEquityReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(),
            List.of(),
            List.of(
                CliResponseWriterTestSupport.currencyBalance(
                    "EUR", "0.00", "3.00", "3.00", BalanceSide.CREDIT)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    ChangesInEquityReport closingOnlyEquityReport =
        new ChangesInEquityReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(),
            List.of(),
            List.of(),
            List.of(
                CliResponseWriterTestSupport.currencyBalance(
                    "EUR", "0.00", "8.00", "8.00", BalanceSide.CREDIT)),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    assertFalse(CliReportSurfacePolicy.hasComparative(nonComparativeTrialBalance));
    assertTrue(
        CliReportSurfacePolicy.hasComparative(
            trialBalanceReport(
                nonComparativeTrialBalance.bookIdentity(),
                Optional.of(LocalDate.parse("2026-04-30")),
                EffectiveDateRange.unbounded(),
                allPostingKinds(),
                nonComparativeTrialBalance.rows(),
                List.of(new TrialBalanceRow(cashAccount, eurDebitBalance())))));
    assertFalse(CliReportSurfacePolicy.hasCurrent(nonCurrentEquityReport));
    assertTrue(CliReportSurfacePolicy.hasCurrent(openingOnlyEquityReport));
    assertTrue(CliReportSurfacePolicy.hasCurrent(movementOnlyEquityReport));
    assertTrue(CliReportSurfacePolicy.hasCurrent(closingOnlyEquityReport));
  }

  private static PostingFact directPostingFact() {
    PostingFact reversalPosting = reversalPostingFact();
    return new PostingFact(
        new PostingId("posting-direct-1"),
        reversalPosting.journalEntry(),
        PostingLineage.direct(),
        reversalPosting.postingKind(),
        reversalPosting.evidence(),
        reversalPosting.provenance());
  }
}
