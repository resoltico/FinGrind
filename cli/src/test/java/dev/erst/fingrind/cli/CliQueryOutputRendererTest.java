package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.AccountPageCursor;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.contract.PostingPageCursor;
import dev.erst.fingrind.contract.RekeyBookResult;
import dev.erst.fingrind.contract.TrialBalanceReport;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit tests for CLI read, report, and mutation renderers. */
@NullUnmarked
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
            new BookInspection.Initialized(123, 1, 1, Instant.parse("2026-04-07T10:15:30Z")));
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

    assertTrue(missingInspection.contains("missing"));
    assertTrue(missingInspection.contains("Can initialize with open-book"));
    assertTrue(missingInspection.contains("true"));
    assertTrue(missingInspection.contains("Supported book format version"));
    assertTrue(existingInspection.contains("SQLite applicationId"));
    assertTrue(existingInspection.contains("State"));
    assertTrue(existingInspection.contains("blank-sqlite"));
    assertTrue(initializedInspection.contains("Initialized at"));
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
            new OpenBookResult.Opened(Instant.parse("2026-04-07T10:15:30Z")));
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
    assertTrue(trialBalanceCsv.contains("effectiveDateTo,accountCode"));
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
    assertTrue(rekeyBookHuman.contains("Book Rekeyed"));
    assertTrue(declaredAccountHuman.contains("Account Declared"));
    assertTrue(preflightHuman.contains("Entry Preflight Accepted"));
    assertTrue(committedHuman.contains("Entry Committed"));
  }
}
