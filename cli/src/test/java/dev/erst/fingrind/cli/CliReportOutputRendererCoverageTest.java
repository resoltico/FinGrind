package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerPagination;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.StatementLineKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Locks semantic report CSV tables to primary rows only. */
class CliReportOutputRendererCoverageTest extends CliFixtureSupport {
  @Test
  void renderAccountLedgerCsv_emitsOnlyTheHeaderWhenLedgerHasNoEntries() {
    String csv = CliQueryOutputRenderer.renderAccountLedgerCsv(sampleAccountLedgerReport());
    java.util.List<String> lines = csv.lines().toList();

    assertEquals(
        "family,accountCode,postingId,effectiveDate,movementCurrencyCode,debitTotalCurrencyCode,debitTotalMinorUnits,creditTotalCurrencyCode,creditTotalMinorUnits,netAmountCurrencyCode,netAmountMinorUnits,balanceSide,runningNetAmountCurrencyCode,runningNetAmountMinorUnits,runningBalanceSide",
        lines.getFirst());
    assertEquals(1, lines.size());
  }

  @Test
  void renderAccountLedgerCsv_doesNotInventABalanceRowWhenNoEntriesExist() {
    AccountLedgerReport emptyCurrencyReport =
        new AccountLedgerReport(
            bookIdentity(),
            declaredAccount("1000", "Cash", dev.erst.fingrind.core.NormalBalance.DEBIT),
            EffectiveDateRange.of(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
            allPostingKinds(),
            AccountLedgerPagination.firstPage(50),
            List.of(),
            List.of(),
            List.of());

    String csv = CliQueryOutputRenderer.renderAccountLedgerCsv(emptyCurrencyReport);
    java.util.List<String> lines = csv.lines().toList();

    assertTrue(lines.getFirst().startsWith("family,accountCode,postingId,effectiveDate"));
    assertEquals(1, lines.size());
  }

  @Test
  void renderAccountLedgerCsv_exportsOnlyTheLedgerMovementRow() {
    DeclaredAccount cashAccount =
        declaredAccount(
            "1000",
            "Cash",
            AccountType.ASSET,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T12:00:00Z"));
    PostingFact postingFact =
        new PostingFact(
            new PostingId("0ffb246b-e007-33ab-95ab-b361f43f3cd9"),
            new JournalEntry(
                LocalDate.parse("2026-04-07"),
                List.of(
                    new JournalLine(
                        new AccountCode("1000"),
                        JournalLine.EntrySide.DEBIT,
                        money("EUR", "10.00")),
                    new JournalLine(
                        new AccountCode("2000"),
                        JournalLine.EntrySide.CREDIT,
                        money("EUR", "10.00")))),
            PostingLineage.direct(),
            PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.SALE_SETTLED,
            accountingEvidenceWithApproval("ledger-approval"),
            new CommittedProvenance(
                new RequestProvenance(
                    new dev.erst.fingrind.core.CommandId("e405dba3-8ddf-3017-b9a2-081d80bbe1da"),
                    new IdempotencyKey("idem-approval-1"),
                    new dev.erst.fingrind.core.CausationId("cause-approval-1"),
                    Optional.empty()),
                Instant.parse("2026-04-07T12:30:00Z"),
                SourceChannel.CLI));
    AccountLedgerReport report =
        new AccountLedgerReport(
            bookIdentity(),
            cashAccount,
            EffectiveDateRange.of(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
            allPostingKinds(),
            AccountLedgerPagination.firstPage(50),
            List.of(CurrencyBalance.ofTotals(money("EUR", "0.00"), money("EUR", "0.00"))),
            List.of(
                new AccountLedgerEntry(
                    postingFact,
                    CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "0.00")),
                    money("EUR", "10.00"),
                    BalanceSide.DEBIT)),
            List.of(CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "0.00"))));

    String csv = CliQueryOutputRenderer.renderAccountLedgerCsv(report);
    List<String> lines = csv.lines().toList();

    assertEquals(2, lines.size());
    assertTrue(lines.getFirst().startsWith("family,accountCode,postingId,effectiveDate"));
    assertTrue(csv.contains("account-ledger,1000,0ffb246b-e007-33ab-95ab-b361f43f3cd9,2026-04-07,EUR,EUR,1000"));
    assertFalse(csv.contains("approval-ledger-approval"));
  }

  @Test
  void renderFinancialPositionCsv_exportsOnlyDeclaredStatementRows() {
    CurrencyBalance assetBalance =
        CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "0.00"));
    FinancialPositionReport report =
        new FinancialPositionReport(
            bookIdentity(),
            Optional.of(LocalDate.parse("2026-04-30")),
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.of(null, LocalDate.parse("2025-04-30")),
            allPostingKinds(),
            true,
            List.of(
                new FinancialPositionSection(
                    AccountType.ASSET,
                    List.of(
                        new FinancialPositionRow(
                            "1000",
                            "Cash",
                            AccountType.ASSET,
                            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                            StatementLineKind.DECLARED_ACCOUNT,
                            assetBalance)),
                    List.of(assetBalance)),
                new FinancialPositionSection(AccountType.LIABILITY, List.of(), List.of()),
                new FinancialPositionSection(AccountType.EQUITY, List.of(), List.of(assetBalance))),
            List.of());

    String csv = CliQueryOutputRenderer.renderFinancialPositionCsv(report);
    List<String> lines = csv.lines().toList();

    assertEquals(2, lines.size());
    assertTrue(lines.getFirst().startsWith("family,reportPeriod,sectionKind,lineCode,lineName"));
    assertTrue(csv.contains("financial-position,current,ASSET,1000,Cash,ASSET"));
    assertFalse(csv.contains("LIABILITY"));
  }

  @Test
  void renderChangesInEquityCsv_emitsOnlyTheHeaderWhenThereAreNoRows() {
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
            List.of());

    String csv = CliQueryOutputRenderer.renderChangesInEquityCsv(report);
    List<String> lines = csv.lines().toList();

    assertTrue(lines.getFirst().startsWith("family,reportPeriod,lineCode,lineName"));
    assertEquals(1, lines.size());
  }
}
