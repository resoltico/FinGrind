package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
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
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingCoverage;
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
            accountPage(List.of(cashAccount), 50, Optional.of(nextAccountCursor)), false);
    String directAccountsTextWithoutCursor =
        CliAccountPageOutputRenderer.renderText(
            accountPage(List.of(cashAccount), 50, Optional.empty()), false);
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
    assertTrue(
        accountsCsv.contains("accounts,account:1000,,account,accounts,1000,\"Cash, reserve\""));
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
    assertTrue(
        postingRegisterCsv.contains(
            "postings,posting:posting-1,postings,2026-04-07,2026-04-07T10:15:30Z,posting-1"));
  }

  @Test
  void renderBookInspectionText_reportsDoctrineAndEntityFacts() {
    BookIdentity doctrinalIdentity =
        new BookIdentity(
            new EntityProfile(new BookEntityName("Acme Studio")),
            dev.erst.fingrind.core.BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
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
                doctrinalIdentity,
                readyCloseReadiness()));

    assertTrue(inspection.contains("Accounting kernel"));
    assertTrue(inspection.contains("Internal management bookkeeping"));
    assertTrue(inspection.contains("Accounting posture"));
    assertTrue(inspection.contains("Non-statutory internal management"));
    assertTrue(inspection.contains("Functional currency"));
  }

  @Test
  void renderPostingText_showsCallerAuthoredEntryFactsWhenAvailable() {
    String postingText =
        CliQueryOutputRenderer.renderPostingText(bookIdentity(), salePostingFact());

    assertTrue(postingText.contains("Entry facts"));
    assertTrue(postingText.contains("Cash account"));
    assertTrue(postingText.contains("service-revenue"));
    assertTrue(postingText.contains("10.00"));
  }

  @Test
  void renderBookInspectionText_omitsRetiredPolicyProfileRows() {
    BookIdentity registeredIdentity =
        new BookIdentity(
            new EntityProfile(new BookEntityName("Registered Studio")),
            dev.erst.fingrind.core.BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
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
                readyCloseReadiness()));

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
                new BookInspection.CloseReadiness(
                    blockedCloseTarget(
                        FinancialPositionLineClassification.RESULT_HOLDING,
                        "close-target-account-candidate-missing",
                        "No active declared result-holding account satisfies required classification 'RESULT_HOLDING'.",
                        List.of()),
                    readyCloseTarget(
                        FinancialPositionLineClassification.RETAINED_ACCUMULATED,
                        new AccountCode("3300")))));

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
                new BookInspection.CloseReadiness(
                    blockedCloseTarget(
                        FinancialPositionLineClassification.RESULT_HOLDING,
                        "close-target-account-candidate-ambiguous",
                        "More than one active declared result-holding account satisfies required classification 'RESULT_HOLDING': 3200, 3210.",
                        List.of(new AccountCode("3200"), new AccountCode("3210"))),
                    readyCloseTarget(
                        FinancialPositionLineClassification.RETAINED_ACCUMULATED,
                        new AccountCode("3300")))));

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

    String rendered = CliQueryOutputRenderer.renderAccountBalanceText(emptySnapshot);

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
    assertEquals("Reversal", ledgerTextRow.get(1));
    assertEquals("10.00", ledgerTextRow.get(2));
    assertEquals("4.00", ledgerTextRow.get(3));
    assertEquals("6.00 Debit", ledgerTextRow.get(4));
    assertEquals("2000", ledgerTextRow.get(5));
    assertEquals("posting-1", ledgerTextRow.get(6));

    String emptyAccountsText =
        CliAccountPageOutputRenderer.renderText(
            accountPage(List.of(), 50, Optional.empty()), false);
    String emptyPostingsText =
        CliPostingOutputRenderer.renderPostingRegisterText(
            postingPage(List.of(), 10, Optional.empty()), false);
    String emptyLedgerText =
        CliQueryOutputRenderer.renderAccountLedgerText(sampleAccountLedgerReport());
    TrialBalanceReport comparativeWithoutReference =
        trialBalanceReport(
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(new TrialBalanceRow(cashAccount, balance)),
            List.of(new TrialBalanceRow(cashAccount, balance)));
    String comparativeTrialBalanceText =
        CliQueryOutputRenderer.renderTrialBalanceText(comparativeWithoutReference);

    assertTrue(emptyAccountsText.contains("Outcome"));
    assertTrue(emptyAccountsText.contains("No accounts matched the selected scope."));
    assertFalse(emptyAccountsText.contains("Returned accounts"));
    assertTrue(emptyPostingsText.contains("Outcome"));
    assertTrue(emptyPostingsText.contains("No postings matched the selected scope."));
    assertFalse(emptyPostingsText.contains("Returned postings"));
    assertTrue(emptyLedgerText.contains("Entries"));
    assertTrue(emptyLedgerText.contains("No ledger entries matched the selected scope."));
    assertTrue(comparativeTrialBalanceText.contains("Comparative Trial Balance"));
    assertTrue(comparativeTrialBalanceText.contains("(none)"));
  }

  @Test
  void renderPostingRegisterText_rendersNextCursorForEmptyPostingScope() {
    PostingPageCursor nextCursor =
        new PostingPageCursor(
            LocalDate.parse("2026-04-30"),
            Instant.parse("2026-04-07T10:15:30Z"),
            new PostingId("posting-1"));

    String emptyPostingsText =
        CliPostingOutputRenderer.renderPostingRegisterText(
            postingPage(List.of(), 10, Optional.of(nextCursor)), false);

    assertTrue(emptyPostingsText.contains("Outcome"));
    assertTrue(emptyPostingsText.contains("No postings matched the selected scope."));
    assertTrue(emptyPostingsText.contains(nextCursor.wireValue()));
    assertFalse(emptyPostingsText.contains("Returned postings"));
  }

  @Test
  void renderMutationViewsAcrossOperatorFormats() {
    DeclaredAccount cashAccount = declaredAccount("1000", "Cash, reserve", NormalBalance.DEBIT);
    DeclaredAccount childAccount =
        new DeclaredAccount(
            new AccountCode("1100"),
            new AccountName("Petty Cash"),
            AccountType.ASSET,
            new AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                Optional.of(new AccountCode("1000")),
                Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                Optional.empty(),
                Optional.of(
                    dev.erst.fingrind.core.CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)),
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    String generatedKeyText =
        CliBookAccessOutputRenderer.renderGeneratedBookKeyFileText(
            new dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile(
                Path.of("office/keys/book.key"), "base64url-no-padding", 256, "0600"),
            List.of());
    String openBookText =
        CliBookAccessOutputRenderer.renderOpenBookText(
            Path.of("office/report.sqlite"),
            List.of(),
            openedBookResult(Instant.parse("2026-04-07T10:15:30Z")));
    String rekeyBookText =
        CliBookAccessOutputRenderer.renderRekeyBookText(
            new RekeyBookResult.Rekeyed(Path.of("office/report.sqlite")),
            new BookAccess.PassphraseSource.KeyFile(Path.of("office/keys/rotated.key")));
    String declaredAccountText =
        CliMutationOutputRenderer.renderAccountDeclarationText("declared", cashAccount);
    String childAccountText =
        CliMutationOutputRenderer.renderAccountDeclarationText("renamed", childAccount);
    String preflightText =
        CliMutationOutputRenderer.renderPreflightAcceptedText(
            CliPostEntryResultFixtures.preflightAccepted(
                new IdempotencyKey("coverage-idem"), LocalDate.parse("2026-04-07")));
    String committedText =
        CliMutationOutputRenderer.renderCommittedText(
            CliPostEntryResultFixtures.committed(
                new PostingId("posting-committed"),
                new IdempotencyKey("coverage-idem"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z"),
                false));
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
    assertTrue(declaredAccountText.contains("Outcome"));
    assertTrue(declaredAccountText.contains("declared"));
    assertTrue(declaredAccountText.contains("Parent account"));
    assertTrue(declaredAccountText.contains("(none)"));
    assertTrue(childAccountText.contains("Account Renamed"));
    assertTrue(childAccountText.contains("Parent account"));
    assertTrue(childAccountText.contains("1000"));
    assertTrue(preflightText.contains("Entry Preflight Passed"));
    assertTrue(preflightText.contains("Commit status"));
    assertTrue(preflightText.contains("Not committed"));
    assertTrue(preflightText.contains("Event class"));
    assertFalse(preflightText.contains("Contained typed events"));
    assertTrue(preflightText.contains("Journal lines"));
    assertTrue(preflightText.contains("2100"));
    assertTrue(preflightText.contains("12.10"));
    assertTrue(committedText.contains("Entry Committed"));
    assertTrue(committedText.contains("posting-committed"));
    assertTrue(committedText.contains("coverage-idem"));
    assertTrue(committedText.contains("Event class"));
    assertFalse(committedText.contains("Contained typed events"));
    assertTrue(committedText.contains("Journal lines"));
    assertTrue(committedText.contains("4000"));
  }

  @Test
  void renderMutationAndReportSupportHelpers_coverRemainingOutcomeAndPluralizationBranches() {
    DeclaredAccount account = declaredAccount("1000", "Cash", NormalBalance.DEBIT);

    String reactivatedText =
        CliMutationOutputRenderer.renderAccountDeclarationText("reactivated", account);
    String unchangedText =
        CliMutationOutputRenderer.renderAccountDeclarationText("unchanged", account);
    String fallbackText =
        CliMutationOutputRenderer.renderAccountDeclarationText("updated", account);

    assertTrue(reactivatedText.contains("Account Reactivated"));
    assertTrue(unchangedText.contains("Account Unchanged"));
    assertTrue(fallbackText.contains("Account Updated"));
    assertEquals(
        "(none)", CliReportRenderSupport.comparativeReferenceLine(EffectiveDateRange.unbounded()));
    assertEquals(
        CliQueryScopeText.dateRange(null, LocalDate.parse("2026-03-31")),
        CliReportRenderSupport.comparativeReferenceLine(
            EffectiveDateRange.to(LocalDate.parse("2026-03-31"))));
    assertEquals(
        CliQueryScopeText.noMatchesLabel("activity lines"),
        CliReportRenderSupport.emptySectionLinesMessage("Activities"));
    assertEquals(
        CliQueryScopeText.noMatchesLabel("asset lines"),
        CliReportRenderSupport.emptySectionLinesMessage("Assets"));
    assertEquals(
        CliQueryScopeText.noMatchesLabel("income lines"),
        CliReportRenderSupport.emptySectionLinesMessage("Income"));
  }

  private static void assertBalanceOutputSamples(
      String accountBalanceText,
      String accountBalanceCsv,
      String trialBalanceText,
      String trialBalanceCsv) {
    assertTrue(accountBalanceText.contains("Account Balance"));
    assertTrue(accountBalanceText.contains("Effective date range"));
    assertTrue(accountBalanceCsv.contains("exportFamily,rowId,parentRowId,relationKind"));
    assertTrue(accountBalanceCsv.contains("account-balance:1000:EUR"));
    assertTrue(trialBalanceText.contains("Trial Balance"));
    assertTrue(trialBalanceText.contains("As of"));
    assertTrue(trialBalanceText.contains("Balance state"));
    assertTrue(trialBalanceText.contains("Imbalanced"));
    assertTrue(trialBalanceCsv.contains("exportFamily,rowId,parentRowId,relationKind"));
    assertTrue(trialBalanceCsv.contains("trial-balance-row:current:1000"));
  }

  private static void assertLedgerOutputSamples(
      String accountLedgerText,
      String accountLedgerCsv,
      String selfLedgerText,
      String periodSummaryText,
      String periodSummaryCsv) {
    assertTrue(accountLedgerText.contains("Account Ledger"));
    assertTrue(accountLedgerText.contains("Opening Balances"));
    assertTrue(accountLedgerText.contains("2000"));
    assertTrue(accountLedgerCsv.contains("exportFamily,rowId,parentRowId,relationKind"));
    assertTrue(accountLedgerCsv.contains("ledger-entry:posting-1"));
    assertTrue(accountLedgerCsv.contains("ledger-counterpart:posting-1:2000"));
    List<String> accountLedgerCsvLines = accountLedgerCsv.lines().toList();
    int accountLedgerCsvColumnCount = csvFieldCount(accountLedgerCsvLines.getFirst());
    for (String line : accountLedgerCsvLines) {
      assertEquals(accountLedgerCsvColumnCount, csvFieldCount(line));
    }
    assertTrue(selfLedgerText.contains("(self)"));
    assertFalse(selfLedgerText.contains("(none)"));
    assertTrue(periodSummaryText.contains("Period Summary"));
    assertTrue(periodSummaryText.contains("Posting line count"));
    assertTrue(periodSummaryCsv.contains("exportFamily,rowId,parentRowId,relationKind"));
    assertTrue(periodSummaryCsv.contains("period-summary:account:2000:net"));
  }

  private static int csvFieldCount(String row) {
    return CliCsvFormat.csvFieldCount(row);
  }

  @Test
  void formatterHelpers_coverCsvRowsAndRemainingAccountLabels() {
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
        "Calculated line",
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
    assertEquals(
        "Header", CliQueryLabelFormatAccess.displayAccountNodeKindLabel(AccountNodeKind.HEADER));
    assertEquals(
        "Postable",
        CliQueryLabelFormatAccess.displayAccountNodeKindLabel(AccountNodeKind.POSTABLE));
    assertEquals(
        "cash-receipt document-idem-1 on 2026-04-07",
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
        List.of("2026-04-07", "Reversal", "Reversal", "10.00", "10.00", "1000, 2000", "posting-1"),
        CliQueryRowFormatAccess.postingRegisterTextRow(postingFact));
    assertEquals(
        List.of(
            "2900",
            "Sales returns",
            "REVENUE",
            "CREDIT",
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
            "2900", "Sales returns", "REVENUE", "CREDIT", "EUR", "10.00", "4.00", "6.00", "DEBIT"),
        CliQueryRowFormatAccess.periodActivityCsvRow(
            new PeriodAccountActivityRow(contraRevenueAccount, balance)));
    assertEquals(
        "All posting kinds",
        CliQueryLabelFormatAccess.displayPostingCoverage(PostingCoverage.ALL_POSTING_KINDS));
    assertEquals(
        "Non-closing postings",
        CliQueryLabelFormatAccess.displayPostingCoverage(PostingCoverage.NON_CLOSING_POSTINGS));
    assertEquals("Standard", CliQueryLabelFormatAccess.displayPostingKind(PostingKind.STANDARD));
    assertEquals(
        "Interim result sweep",
        CliQueryLabelFormatAccess.displayPostingKind(PostingKind.INTERIM_RESULT_SWEEP));
    assertEquals(
        "Fiscal-year close",
        CliQueryLabelFormatAccess.displayPostingKind(PostingKind.FISCAL_YEAR_CLOSE));
    assertEquals(
        "Opening accounting position",
        CliQueryLabelFormatAccess.displayPostingKind(PostingKind.OPENING_BALANCE));
    assertEquals(
        "Direct journal",
        CliQueryLabelFormatAccess.displayPostingOriginKind(PostingOriginKind.DIRECT_JOURNAL));
    assertEquals(
        "Settled sale",
        CliQueryLabelFormatAccess.displayPostingOriginKind(PostingOriginKind.SALE_SETTLED));
    assertEquals(
        "Settled expense",
        CliQueryLabelFormatAccess.displayPostingOriginKind(PostingOriginKind.EXPENSE_SETTLED));
    assertEquals(
        "Owner contribution",
        CliQueryLabelFormatAccess.displayPostingOriginKind(PostingOriginKind.OWNER_CONTRIBUTION));
    assertEquals(
        "Owner withdrawal",
        CliQueryLabelFormatAccess.displayPostingOriginKind(PostingOriginKind.OWNER_WITHDRAWAL));
    assertEquals(
        "Opening position",
        CliQueryLabelFormatAccess.displayPostingOriginKind(PostingOriginKind.OPENING_POSITION));
    assertEquals(
        "Reversal", CliQueryLabelFormatAccess.displayPostingOriginKind(PostingOriginKind.REVERSAL));
    assertEquals(
        "Interim result sweep",
        CliQueryLabelFormatAccess.displayPostingOriginKind(PostingOriginKind.INTERIM_RESULT_SWEEP));
    assertEquals(
        "Fiscal-year close",
        CliQueryLabelFormatAccess.displayPostingOriginKind(PostingOriginKind.FISCAL_YEAR_CLOSE));
  }

  @Test
  void accountPageAndClassificationHelpers_coverAllTaxonomyLabelsAndPresentOptionalValues() {
    DeclaredAccount equityAccount =
        new DeclaredAccount(
            new AccountCode("3200"),
            new AccountName("Contributed capital account"),
            AccountType.EQUITY,
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
            new AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                Optional.of(new AccountCode("5100")),
                Optional.empty(),
                Optional.of(ProfitAndLossLineClassification.COST_OF_SALES)),
            true,
            Instant.parse("2026-04-07T10:15:30Z"));

    String text =
        CliAccountPageOutputRenderer.renderText(
            accountPage(List.of(equityAccount, expenseAccount), 50, Optional.empty()), false);
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
        "Calculated line",
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
  void renderAccountPage_rendersInventoryUnitOfMeasureInTextAndCsv() {
    DeclaredAccount inventoryAccount = inventoryDeclaredAccount("1400", "Inventory", "kg", 3);

    String text =
        CliAccountPageOutputRenderer.renderText(
            accountPage(List.of(inventoryAccount), 50, Optional.empty()), false);
    String csv =
        CliAccountPageOutputRenderer.renderCsv(
            accountPage(List.of(inventoryAccount), 50, Optional.empty()));

    assertTrue(text.contains("kg (scale 3)"));
    assertTrue(csv.contains("kg"));
    assertTrue(csv.contains(",3,"));
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
