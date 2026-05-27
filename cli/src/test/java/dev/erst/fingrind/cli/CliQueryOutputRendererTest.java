package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
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
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
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
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for CLI read, report, and mutation renderers. */
class CliQueryOutputRendererTest extends FinGrindCliTestSupport {
  @Test
  void renderInspectionAccountsAndPostingViewsInTextAndCsvForms() {
    PostingFact postingFact = reversalPostingFact();
    DeclaredAccount cashAccount = declaredAccount("1000", "Cash, reserve", NormalBalance.DEBIT);
    String missingInspection =
        CliQueryOutputRenderer.renderBookInspectionText(
            Path.of("office/report.sqlite"), new BookInspection.Missing(1));
    String existingInspection =
        CliQueryOutputRenderer.renderBookInspectionText(
            Path.of("office/report.sqlite"),
            new BookInspection.Existing(BookInspection.Status.BLANK_SQLITE, 123, 0, 1));
    String initializedInspection =
        CliQueryOutputRenderer.renderBookInspectionText(
            Path.of("office/report.sqlite"),
            initializedBookInspection(123, 1, 1, Instant.parse("2026-04-07T10:15:30Z")));
    AccountPageCursor nextAccountCursor = AccountPageCursor.fromAccount(cashAccount);
    String accountsText =
        CliQueryOutputRenderer.renderAccountsText(
            accountPage(List.of(cashAccount), 50, Optional.of(nextAccountCursor)));
    String accountsTextWithoutCursor =
        CliQueryOutputRenderer.renderAccountsText(
            accountPage(List.of(cashAccount), 50, Optional.empty()));
    String directAccountsText =
        CliAccountPageOutputRenderer.renderText(
            accountPage(List.of(cashAccount), 50, Optional.of(nextAccountCursor)));
    String directAccountsTextWithoutCursor =
        CliAccountPageOutputRenderer.renderText(
            accountPage(List.of(cashAccount), 50, Optional.empty()));
    String accountsCsv =
        CliQueryOutputRenderer.renderAccountsCsv(
            accountPage(List.of(cashAccount), 50, Optional.empty()));
    String postingText = CliQueryOutputRenderer.renderPostingText(bookIdentity(), postingFact);
    PostingPageCursor nextCursor =
        new PostingPageCursor(
            LocalDate.parse("2026-04-30"),
            Instant.parse("2026-04-07T10:15:30Z"),
            new PostingId("posting-1"));
    String postingRegisterText =
        CliQueryOutputRenderer.renderPostingRegisterText(
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
    assertTrue(initializedInspection.contains("Functional currency"));
    assertTrue(initializedInspection.contains("Fiscal year start"));
    assertTrue(accountsText.contains("Cash, reserve"));
    assertTrue(accountsText.contains("Current asset"));
    assertTrue(accountsText.contains(nextAccountCursor.wireValue()));
    assertTrue(accountsTextWithoutCursor.contains("(none)"));
    assertTrue(directAccountsText.contains("Current asset"));
    assertTrue(directAccountsText.contains(nextAccountCursor.wireValue()));
    assertTrue(directAccountsTextWithoutCursor.contains("(none)"));
    assertTrue(accountsCsv.contains("\"Cash, reserve\""));
    assertTrue(postingText.contains("Correlation id"));
    assertTrue(postingText.contains("posting-0"));
    assertTrue(postingText.contains("actor-1"));
    assertTrue(postingText.contains("command-1"));
    assertTrue(postingText.contains("idem-1"));
    assertTrue(postingText.contains("cause-1"));
    assertTrue(postingText.contains("Correction"));
    assertTrue(postingRegisterText.contains("Next cursor"));
    assertTrue(postingRegisterText.contains(nextCursor.wireValue()));
    assertTrue(postingRegisterCsv.contains("posting-1"));
  }

  @Test
  void renderBookInspectionText_joinsNonEmptyBusinessActivityTags() {
    BookIdentity taggedIdentity =
        new BookIdentity(
            new EntityProfile(
                new BookEntityName("Acme Studio"),
                List.of(
                    new BusinessActivityTag("translation,localization"),
                    new BusinessActivityTag("cafe services"))),
            CurrencyUnit.of("EUR"),
            FiscalYearStart.parse("01-01"));
    String inspection =
        CliQueryOutputRenderer.renderBookInspectionText(
            Path.of("office/report.sqlite"),
            new BookInspection.Initialized(
                123,
                1,
                1,
                Instant.parse("2026-04-07T10:15:30Z"),
                taggedIdentity,
                resultTransferReadyInspection()));

    assertTrue(inspection.contains("Business activity"));
    assertTrue(inspection.contains("translation,localization, cafe services"));
    assertTrue(inspection.contains("Functional currency"));
  }

  @Test
  void renderBookInspectionText_omitsRetiredPolicyProfileRows() {
    BookIdentity registeredIdentity =
        new BookIdentity(
            new EntityProfile(new BookEntityName("Registered Studio"), List.of()),
            CurrencyUnit.of("EUR"),
            FiscalYearStart.parse("01-01"));
    String inspection =
        CliQueryOutputRenderer.renderBookInspectionText(
            Path.of("office/report.sqlite"),
            new BookInspection.Initialized(
                123,
                1,
                1,
                Instant.parse("2026-04-07T10:15:30Z"),
                registeredIdentity,
                resultTransferReadyInspection()));

    assertFalse(inspection.contains("Policy profile"));
    assertFalse(inspection.contains("Internal Management Single Entity V1"));
  }

  @Test
  void renderBookInspectionText_showsNoCandidateAccountsWhenBlockedWithoutCandidates() {
    String inspection =
        CliQueryOutputRenderer.renderBookInspectionText(
            Path.of("office/report.sqlite"),
            new BookInspection.Initialized(
                123,
                1,
                1,
                Instant.parse("2026-04-07T10:15:30Z"),
                bookIdentity(),
                new BookInspection.ResultTransferReadiness(
                    false,
                    FinancialPositionLineClassification.RESULT_HOLDING,
                    null,
                    "result-holding-account-candidate-missing",
                    "No active declared result-holding account satisfies required classification 'RESULT_HOLDING'.",
                    List.of())));

    assertTrue(inspection.contains("Candidate accounts"));
    assertTrue(inspection.contains("(none)"));
  }

  @Test
  void renderBookInspectionText_listsCandidateAccountsWhenBlockedWithCandidates() {
    String inspection =
        CliQueryOutputRenderer.renderBookInspectionText(
            Path.of("office/report.sqlite"),
            new BookInspection.Initialized(
                123,
                1,
                1,
                Instant.parse("2026-04-07T10:15:30Z"),
                bookIdentity(),
                new BookInspection.ResultTransferReadiness(
                    false,
                    FinancialPositionLineClassification.RESULT_HOLDING,
                    null,
                    "result-holding-account-candidate-ambiguous",
                    "More than one active declared result-holding account satisfies required classification 'RESULT_HOLDING': 3200, 3210.",
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
    String accountBalanceText = CliQueryOutputRenderer.renderAccountBalanceText(balanceSnapshot);
    String accountBalanceCsv = CliQueryOutputRenderer.renderAccountBalanceCsv(balanceSnapshot);
    String trialBalanceText = CliQueryOutputRenderer.renderTrialBalanceText(trialBalanceReport);
    String trialBalanceCsv = CliQueryOutputRenderer.renderTrialBalanceCsv(trialBalanceReport);
    String accountLedgerText = CliQueryOutputRenderer.renderAccountLedgerText(accountLedgerReport);
    String accountLedgerCsv = CliQueryOutputRenderer.renderAccountLedgerCsv(accountLedgerReport);
    String selfLedgerText = CliQueryOutputRenderer.renderAccountLedgerText(selfLedgerReport);
    String periodSummaryText = CliQueryOutputRenderer.renderPeriodSummaryText(periodSummaryReport);
    String periodSummaryCsv = CliQueryOutputRenderer.renderPeriodSummaryCsv(periodSummaryReport);

    assertBalanceOutputSamples(
        accountBalanceText, accountBalanceCsv, trialBalanceText, trialBalanceCsv);
    assertLedgerOutputSamples(
        accountLedgerText, accountLedgerCsv, selfLedgerText, periodSummaryText, periodSummaryCsv);
  }

  @Test
  void renderAccountBalanceText_rendersNoMatchesLabelWhenNoBalanceBucketsExist() {
    DeclaredAccount cashAccount = declaredAccount("1000", "Cash, reserve", NormalBalance.DEBIT);
    AccountBalanceSnapshot emptySnapshot =
        new AccountBalanceSnapshot(
            bookIdentity(),
            cashAccount,
            Optional.of(LocalDate.parse("2026-04-01")),
            Optional.of(LocalDate.parse("2026-04-30")),
            allPostingKinds(),
            List.of());

    String rendered = CliAccountBalanceOutputRenderer.renderText(emptySnapshot);

    assertTrue(rendered.contains("Account Balance"));
    assertTrue(rendered.contains("No balances matched the selected scope."));
  }

  @Test
  void renderTextRowsAndEmptyLedgerSurfaces_coverCompactNoneBranches() {
    PostingFact postingFact = reversalPostingFact();
    DeclaredAccount cashAccount = declaredAccount("1000", "Cash, reserve", NormalBalance.DEBIT);
    CurrencyBalance balance = eurDebitBalance();
    AccountLedgerEntry ledgerEntry =
        new AccountLedgerEntry(postingFact, balance, money("EUR", "6.00"), BalanceSide.DEBIT);

    List<String> ledgerTextRow =
        CliQueryRowFormatAccess.accountLedgerTextRow(cashAccount, ledgerEntry);
    assertEquals("2026-04-07", ledgerTextRow.get(0));
    assertEquals("Correction", ledgerTextRow.get(1));
    assertEquals("10.00", ledgerTextRow.get(2));
    assertEquals("4.00", ledgerTextRow.get(3));
    assertEquals("6.00 Debit", ledgerTextRow.get(4));
    assertEquals("2000", ledgerTextRow.get(5));
    assertEquals("posting-1", ledgerTextRow.get(6));

    String emptyAccountsText =
        CliAccountPageOutputRenderer.renderText(accountPage(List.of(), 50, Optional.empty()));
    String emptyPostingsText =
        CliPostingOutputRenderer.renderPostingRegisterText(
            postingPage(List.of(), 10, Optional.empty()));
    String emptyLedgerText =
        CliReportOutputRenderer.renderAccountLedgerText(sampleAccountLedgerReport());
    TrialBalanceReport comparativeWithoutReference =
        trialBalanceReport(
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(new TrialBalanceRow(cashAccount, balance)),
            List.of(new TrialBalanceRow(cashAccount, balance)));
    String comparativeTrialBalanceText =
        CliReportOutputRenderer.renderTrialBalanceText(comparativeWithoutReference);

    assertTrue(emptyAccountsText.contains("No accounts matched the selected scope."));
    assertTrue(emptyPostingsText.contains("No postings matched the selected scope."));
    assertTrue(emptyLedgerText.contains("Entries"));
    assertTrue(emptyLedgerText.contains("No ledger entries matched the selected scope."));
    assertTrue(comparativeTrialBalanceText.contains("Comparative Trial Balance"));
    assertTrue(comparativeTrialBalanceText.contains("(none)"));
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
    String generatedKeyText =
        CliMutationOutputRenderer.renderGeneratedBookKeyFileText(
            new dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile(
                Path.of("office/keys/book.key"), "base64url-no-padding", 256, "0600"));
    String openBookText =
        CliMutationOutputRenderer.renderOpenBookText(
            Path.of("office/report.sqlite"),
            openedBookResult(Instant.parse("2026-04-07T10:15:30Z")));
    String rekeyBookText =
        CliMutationOutputRenderer.renderRekeyBookText(
            new RekeyBookResult.Rekeyed(Path.of("office/report.sqlite")),
            new BookAccess.PassphraseSource.KeyFile(Path.of("office/keys/rotated.key")));
    String declaredAccountText = CliMutationOutputRenderer.renderDeclaredAccountText(cashAccount);
    String childAccountText = CliMutationOutputRenderer.renderDeclaredAccountText(childAccount);
    String preflightText =
        CliMutationOutputRenderer.renderPreflightAcceptedText(
            new PostEntryResult.PreflightAccepted(
                new IdempotencyKey("coverage-idem"), LocalDate.parse("2026-04-07")));
    String committedText =
        CliMutationOutputRenderer.renderCommittedText(
            new PostEntryResult.Committed(
                new PostingId("posting-committed"),
                new IdempotencyKey("coverage-idem"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z")));
    assertTrue(generatedKeyText.contains("Book Key File Generated"));
    assertTrue(openBookText.contains("Book Initialized"));
    assertTrue(openBookText.contains("Entity"));
    assertTrue(openBookText.contains("Acme Studio"));
    assertTrue(openBookText.contains("Functional currency"));
    assertTrue(openBookText.contains("Fiscal year start"));
    assertTrue(rekeyBookText.contains("Book Rekeyed"));
    assertTrue(rekeyBookText.contains("Replacement secret source"));
    assertTrue(rekeyBookText.contains("Replacement key file"));
    assertTrue(declaredAccountText.contains("Account Declared"));
    assertTrue(declaredAccountText.contains("Parent account"));
    assertTrue(declaredAccountText.contains("(none)"));
    assertTrue(childAccountText.contains("Parent account"));
    assertTrue(childAccountText.contains("1000"));
    assertTrue(preflightText.contains("Entry Preflight Accepted"));
    assertTrue(committedText.contains("Entry Committed"));
    assertTrue(committedText.contains("posting-committed"));
    assertTrue(committedText.contains("coverage-idem"));
  }

  private static void assertBalanceOutputSamples(
      String accountBalanceText,
      String accountBalanceCsv,
      String trialBalanceText,
      String trialBalanceCsv) {
    assertTrue(accountBalanceText.contains("Account Balance"));
    assertTrue(accountBalanceText.contains("Range"));
    assertTrue(
        accountBalanceCsv.contains(
            "recordKind,accountCode,accountName,accountType,accountRole,normalBalance,effectiveDateFrom,effectiveDateTo,currencyCode,debitTotal,creditTotal,netAmount,balanceSide,message"));
    assertTrue(trialBalanceText.contains("Trial Balance"));
    assertTrue(trialBalanceText.contains("As of"));
    assertTrue(trialBalanceText.contains("Balance state"));
    assertTrue(trialBalanceText.contains("Imbalanced"));
    assertTrue(
        trialBalanceCsv.contains(
            "reportBasis,recordKind,effectiveDateAsOf,balanced,accountCode,accountName,accountType,accountRole,normalBalance,active,currencyCode,debitTotal,creditTotal,netAmount,balanceSide,message"));
  }

  private static void assertLedgerOutputSamples(
      String accountLedgerText,
      String accountLedgerCsv,
      String selfLedgerText,
      String periodSummaryText,
      String periodSummaryCsv) {
    assertTrue(accountLedgerText.contains("Account Ledger"));
    assertTrue(accountLedgerText.contains("Opening balances"));
    assertTrue(accountLedgerText.contains("2000"));
    assertTrue(
        accountLedgerCsv.contains(
            "recordKind,accountCode,accountName,accountType,accountRole,normalBalance,active,effectiveDateFrom,effectiveDateTo,currencyCode,openingDebitTotal,openingCreditTotal,openingNetAmount,openingBalanceSide,closingDebitTotal,closingCreditTotal,closingNetAmount,closingBalanceSide,effectiveDate,recordedAt,postingId,postingKind,postingOriginKind,reversalState,reversalTarget,debitAmount,creditAmount,runningNetAmount,runningBalanceSide,counterpartAccountCode,sourceDocumentId,sourceDocumentType,approvalId,approvalDecision,message"));
    assertTrue(accountLedgerCsv.contains("entry,1000,\"Cash, reserve\",ASSET,ORDINARY,DEBIT,true"));
    assertTrue(
        accountLedgerCsv.contains(
            "counterpart-account,1000,\"Cash, reserve\",ASSET,ORDINARY,DEBIT,true"));
    List<String> accountLedgerCsvLines = accountLedgerCsv.lines().toList();
    int accountLedgerCsvColumnCount = csvFieldCount(accountLedgerCsvLines.getFirst());
    for (String line : accountLedgerCsvLines) {
      assertEquals(accountLedgerCsvColumnCount, csvFieldCount(line));
    }
    assertTrue(selfLedgerText.contains("(self)"));
    assertTrue(selfLedgerText.contains("(none)"));
    assertTrue(periodSummaryText.contains("Period Summary"));
    assertTrue(periodSummaryText.contains("Posting line count"));
    assertTrue(
        periodSummaryCsv.contains(
            "recordKind,subjectKind,subjectCode,subjectName,metricName,metricValue,currencyCode,metricUnit,message"));
  }

  @Test
  void renderStatementAndFormatterHelpers_coverAllAccountTypeAndEmptySectionBranches() {
    assertEquals(
        "Assets", CliQueryLabelFormatAccess.displayAccountTypeSectionLabel(AccountType.ASSET));
    assertEquals(
        "Liabilities",
        CliQueryLabelFormatAccess.displayAccountTypeSectionLabel(AccountType.LIABILITY));
    assertEquals(
        "Equity", CliQueryLabelFormatAccess.displayAccountTypeSectionLabel(AccountType.EQUITY));
    assertEquals(
        "Revenue", CliQueryLabelFormatAccess.displayAccountTypeSectionLabel(AccountType.REVENUE));
    assertEquals(
        "Expenses", CliQueryLabelFormatAccess.displayAccountTypeSectionLabel(AccountType.EXPENSE));
    assertEquals("Asset", CliQueryLabelFormatAccess.displayLineTypeLabel(AccountType.ASSET));
    assertEquals(
        "Liability", CliQueryLabelFormatAccess.displayLineTypeLabel(AccountType.LIABILITY));
    assertEquals("Equity", CliQueryLabelFormatAccess.displayLineTypeLabel(AccountType.EQUITY));
    assertEquals("Revenue", CliQueryLabelFormatAccess.displayLineTypeLabel(AccountType.REVENUE));
    assertEquals("Expense", CliQueryLabelFormatAccess.displayLineTypeLabel(AccountType.EXPENSE));

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

    String financialPositionText =
        CliReportOutputRenderer.renderFinancialPositionText(emptyFinancialPosition);
    String incomeStatementText =
        CliReportOutputRenderer.renderIncomeStatementText(emptyIncomeStatement);
    String changesInEquityText =
        CliReportOutputRenderer.renderChangesInEquityText(changesInEquityReport);

    assertTrue(financialPositionText.contains("Financial Position"));
    assertTrue(
        financialPositionText.contains("No financial position lines matched the selected scope."));
    assertTrue(incomeStatementText.contains("Income Statement"));
    assertTrue(
        incomeStatementText.contains("No income statement lines matched the selected scope."));
    assertTrue(changesInEquityText.contains("Changes In Equity"));
    assertTrue(changesInEquityText.contains("Closing totals"));
  }

  private static int csvFieldCount(String row) {
    return CliCsvFormat.csvFieldCount(row);
  }

  @Test
  void displayRowKind_labelsDeclaredAndDerivedRows() {
    assertEquals(
        "Current period result",
        CliQueryLabelFormatAccess.displayRowKind(StatementLineKind.CURRENT_PERIOD_RESULT));
    assertEquals(
        "Account", CliQueryLabelFormatAccess.displayRowKind(StatementLineKind.DECLARED_ACCOUNT));
  }

  @Test
  void statementTextRenderers_hideSyntheticLineCodesForDerivedRows() {
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

    String financialPositionText =
        CliReportOutputRenderer.renderFinancialPositionText(financialPositionReport);
    String changesInEquityText =
        CliReportOutputRenderer.renderChangesInEquityText(changesInEquityReport);

    assertTrue(financialPositionText.contains("(derived)"));
    assertFalse(financialPositionText.contains("current-period-result"));
    assertTrue(changesInEquityText.contains("(derived)"));
    assertFalse(changesInEquityText.contains("current-period-result"));
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
        CliQueryLabelFormatAccess.displayStatementLineCode(
            "current-period-result", StatementLineKind.CURRENT_PERIOD_RESULT));
    assertEquals(
        "3000",
        CliQueryLabelFormatAccess.displayStatementLineCode(
            "3000", StatementLineKind.DECLARED_ACCOUNT));
    assertEquals("Direct", CliQueryLabelFormatAccess.displayPostingRoleText(directPostingFact));
    assertEquals("Reversal", CliQueryLabelFormatAccess.displayPostingRoleText(postingFact));
    assertEquals(
        "(not a reversal)", CliQueryLabelFormatAccess.reversalTargetText(directPostingFact));
    assertEquals("posting-0", CliQueryLabelFormatAccess.reversalTargetText(postingFact));
    assertEquals("(derived)", CliQueryLabelFormatAccess.displayLineRole(Optional.empty()));
    assertEquals(
        "Contra", CliQueryLabelFormatAccess.displayLineRole(Optional.of(AccountRole.CONTRA)));
    assertEquals(
        "Header", CliQueryLabelFormatAccess.displayAccountNodeKindLabel(AccountNodeKind.HEADER));
    assertEquals(
        "Postable",
        CliQueryLabelFormatAccess.displayAccountNodeKindLabel(AccountNodeKind.POSTABLE));
    assertEquals(
        "cash-receipt document-idem-1 on 2026-04-07 at vault://fixtures/document-idem-1",
        CliQueryRowFormatAccess.postingSourceDocumentsText(postingFact));
    assertEquals(
        "document-idem-1", CliQueryRowFormatAccess.postingSourceDocumentIdsText(postingFact));
    assertEquals("(none)", CliQueryRowFormatAccess.postingApprovalsText(postingFact));
    assertEquals(
        "manager-signoff approval-idem-1 by PERSON approver-approval-idem-1 APPROVED",
        CliQueryRowFormatAccess.postingApprovalsText(
            CliResponseWriterTestSupport.postingFactWithApproval()));
    assertEquals(
        List.of("EUR", "10.00", "4.00", "6.00", "DEBIT"),
        CliQueryRowFormatAccess.balanceCsvRow(balance));
    assertEquals(
        List.of(
            "2026-04-07", "Correction", "Reversal", "10.00", "10.00", "1000, 2000", "posting-1"),
        CliQueryRowFormatAccess.postingRegisterTextRow(postingFact));
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
        CliQueryRowFormatAccess.trialBalanceCsvRow(
            new TrialBalanceRow(contraRevenueAccount, balance)));
    assertEquals(
        "1000, 2000",
        CliQueryRowFormatAccess.counterpartAccounts(contraRevenueAccount, postingFact));
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
        CliQueryRowFormatAccess.periodActivityCsvRow(
            new PeriodAccountActivityRow(contraRevenueAccount, balance)));
    assertEquals("Contra", CliQueryLabelFormatAccess.displayAccountRoleLabel(AccountRole.CONTRA));
    assertEquals("Standard", CliQueryLabelFormatAccess.displayPostingKind(PostingKind.STANDARD));
    assertEquals(
        "Period result transfer",
        CliQueryLabelFormatAccess.displayPostingKind(PostingKind.PERIOD_RESULT_TRANSFER));
    assertEquals(
        "Opening balance",
        CliQueryLabelFormatAccess.displayPostingKind(PostingKind.OPENING_BALANCE));
    assertEquals(
        "Cash revenue",
        CliQueryLabelFormatAccess.displayPostingOriginKind(PostingOriginKind.CASH_REVENUE));
    assertEquals(
        "Cash expense",
        CliQueryLabelFormatAccess.displayPostingOriginKind(PostingOriginKind.CASH_EXPENSE));
    assertEquals(
        "Equity contribution",
        CliQueryLabelFormatAccess.displayPostingOriginKind(PostingOriginKind.EQUITY_CONTRIBUTION));
    assertEquals(
        "Equity withdrawal",
        CliQueryLabelFormatAccess.displayPostingOriginKind(PostingOriginKind.EQUITY_WITHDRAWAL));
    assertEquals(
        "Opening balance",
        CliQueryLabelFormatAccess.displayPostingOriginKind(
            PostingOriginKind.OPENING_BALANCE_ADJUSTMENT));
    assertEquals(
        "Correction",
        CliQueryLabelFormatAccess.displayPostingOriginKind(
            PostingOriginKind.CORRECTION_ADJUSTMENT));
    assertEquals(
        "Reversal adjustment",
        CliQueryLabelFormatAccess.displayPostingOriginKind(PostingOriginKind.REVERSAL_ADJUSTMENT));
    assertEquals(
        "Result transfer",
        CliQueryLabelFormatAccess.displayPostingOriginKind(
            PostingOriginKind.PERIOD_RESULT_TRANSFER));
  }

  @Test
  void renderChangesInEquityCsv_fillsMissingCurrencyTotalsWithZeroBalances() {
    CurrencyBalance openingBalance =
        CurrencyBalance.ofTotals(money("EUR", "0.00"), money("EUR", "0.00"));
    CurrencyBalance movementBalance =
        CurrencyBalance.ofTotals(money("EUR", "0.00"), money("EUR", "10.00"));
    CurrencyBalance closingBalance =
        CurrencyBalance.ofTotals(money("EUR", "0.00"), money("EUR", "10.00"));
    ChangesInEquityReport report =
        new ChangesInEquityReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(
                new ChangesInEquityRow(
                    "3200",
                    "Retained Earnings",
                    Optional.of(AccountType.EQUITY),
                    Optional.of(AccountRole.ORDINARY),
                    Optional.of(FinancialPositionLineClassification.RESULT_HOLDING),
                    StatementLineKind.DECLARED_ACCOUNT,
                    openingBalance,
                    movementBalance,
                    closingBalance)),
            List.of(),
            List.of(movementBalance),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    String rendered = CliReportOutputRenderer.renderChangesInEquityCsv(report);

    assertTrue(
        rendered.contains(
            "current,report-total,2026-04-01,2026-04-30,report-total,Report total,,,REPORT_TOTAL,EUR"));
    assertTrue(
        rendered.contains(",EUR,0.00,0.00,0.00,ZERO,0.00,10.00,10.00,CREDIT,0.00,0.00,0.00,ZERO"));
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
                Optional.of(FinancialPositionLineClassification.EQUITY_CONTRIBUTION),
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

    String text =
        CliAccountPageOutputRenderer.renderText(
            accountPage(List.of(equityAccount, expenseAccount), 50, Optional.empty()));
    String csv =
        CliAccountPageOutputRenderer.renderCsv(
            accountPage(List.of(equityAccount, expenseAccount), 50, Optional.empty()));

    assertEquals(
        "Current asset",
        CliQueryLabelFormatAccess.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.CURRENT_ASSET));
    assertEquals(
        "Non-current asset",
        CliQueryLabelFormatAccess.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.NONCURRENT_ASSET));
    assertEquals(
        "Current liability",
        CliQueryLabelFormatAccess.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.CURRENT_LIABILITY));
    assertEquals(
        "Non-current liability",
        CliQueryLabelFormatAccess.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.NONCURRENT_LIABILITY));
    assertEquals(
        "Contributed capital",
        CliQueryLabelFormatAccess.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.EQUITY_CONTRIBUTION));
    assertEquals(
        "Distributions",
        CliQueryLabelFormatAccess.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.EQUITY_WITHDRAWAL));
    assertEquals(
        "Contributed capital",
        CliQueryLabelFormatAccess.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.EQUITY_CONTRIBUTION));
    assertEquals(
        "Distributions",
        CliQueryLabelFormatAccess.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.EQUITY_WITHDRAWAL));
    assertEquals(
        "Contributed capital",
        CliQueryLabelFormatAccess.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.EQUITY_CONTRIBUTION));
    assertEquals(
        "Accumulated result",
        CliQueryLabelFormatAccess.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.RESULT_HOLDING));
    assertEquals(
        "Accumulated result",
        CliQueryLabelFormatAccess.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.RESULT_HOLDING));
    assertEquals(
        "Reserve",
        CliQueryLabelFormatAccess.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.RESERVE));
    assertEquals(
        "(derived)",
        CliQueryLabelFormatAccess.displayFinancialPositionLineClassification(Optional.empty()));
    assertEquals(
        "Other equity",
        CliQueryLabelFormatAccess.displayFinancialPositionLineClassification(
            FinancialPositionLineClassification.OTHER_EQUITY));
    assertEquals(
        "Operating revenue",
        CliQueryLabelFormatAccess.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.OPERATING_REVENUE));
    assertEquals(
        "Other revenue",
        CliQueryLabelFormatAccess.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.OTHER_REVENUE));
    assertEquals(
        "Finance income",
        CliQueryLabelFormatAccess.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.FINANCE_INCOME));
    assertEquals(
        "Cost of sales",
        CliQueryLabelFormatAccess.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.COST_OF_SALES));
    assertEquals(
        "Operating expense",
        CliQueryLabelFormatAccess.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.OPERATING_EXPENSE));
    assertEquals(
        "Depreciation and amortization",
        CliQueryLabelFormatAccess.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.DEPRECIATION_AND_AMORTIZATION));
    assertEquals(
        "Finance expense",
        CliQueryLabelFormatAccess.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.FINANCE_EXPENSE));
    assertEquals(
        "Other expense",
        CliQueryLabelFormatAccess.displayProfitAndLossLineClassification(
            ProfitAndLossLineClassification.OTHER_EXPENSE));

    assertTrue(text.contains("Contributed capital"));
    assertTrue(text.contains("3000"));
    assertTrue(text.contains("Cost of sales"));
    assertTrue(text.contains("5100"));
    assertTrue(csv.contains("EQUITY_CONTRIBUTION"));
    assertTrue(csv.contains("COST_OF_SALES"));
  }

  @Test
  void renderStatementTexts_skipEmptySectionsAndKeepTotalsOnlySections() {
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

    String financialPositionText =
        CliReportOutputRenderer.renderFinancialPositionText(financialPositionReport);
    String incomeStatementText =
        CliReportOutputRenderer.renderIncomeStatementText(incomeStatementReport);

    assertTrue(financialPositionText.contains("Cash without Totals"));
    assertTrue(financialPositionText.contains("Equity"));
    assertTrue(financialPositionText.contains("Section totals"));
    assertTrue(financialPositionText.contains("Empty sections"));
    assertTrue(financialPositionText.contains("Liabilities"));
    assertFalse(financialPositionText.contains("Comparative Financial Position"));
    assertTrue(incomeStatementText.contains("Revenue without Totals"));
    assertTrue(incomeStatementText.contains("Expenses"));
    assertTrue(incomeStatementText.contains("Section totals"));
    assertTrue(incomeStatementText.contains("Empty sections"));
    assertFalse(incomeStatementText.contains("Comparative Income Statement"));
  }

  @Test
  void renderBookInspectionText_coversEveryStatusLabel() {
    assertTrue(
        CliQueryOutputRenderer.renderBookInspectionText(
                Path.of("office/report.sqlite"),
                new BookInspection.Existing(BookInspection.Status.FOREIGN_SQLITE, 123, 0, 1))
            .contains("Foreign SQLite"));
    assertTrue(
        CliQueryOutputRenderer.renderBookInspectionText(
                Path.of("office/report.sqlite"),
                new BookInspection.Existing(
                    BookInspection.Status.UNSUPPORTED_FORMAT_VERSION, 123, 99, 4))
            .contains("Unsupported format version"));
    assertTrue(
        CliQueryOutputRenderer.renderBookInspectionText(
                Path.of("office/report.sqlite"),
                new BookInspection.Existing(BookInspection.Status.INCOMPLETE_FINGRIND, 123, 2, 4))
            .contains("Incomplete FinGrind"));
  }

  @Test
  void postingWireLabels_coverSystemInternalAndFallbackValues() {
    assertEquals("CLI", CliPostingOutputRenderer.displayWireLabel("CLI"));
    assertEquals("Person", CliPostingOutputRenderer.displayWireLabel("PERSON"));
    assertEquals("System", CliPostingOutputRenderer.displayWireLabel("SYSTEM"));
    assertEquals("Internal", CliPostingOutputRenderer.displayWireLabel("INTERNAL"));
    assertEquals("agent batch", CliPostingOutputRenderer.displayWireLabel("AGENT_BATCH"));
  }

  private static PostingFact directPostingFact() {
    PostingFact reversalPosting = reversalPostingFact();
    return new PostingFact(
        new PostingId("posting-direct-1"),
        reversalPosting.journalEntry(),
        PostingLineage.direct(),
        reversalPosting.postingKind(),
        reversalPosting.postingOriginKind(),
        reversalPosting.evidence(),
        reversalPosting.provenance());
  }
}
