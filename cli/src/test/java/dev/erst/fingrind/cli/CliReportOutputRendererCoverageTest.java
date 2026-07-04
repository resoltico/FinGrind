package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
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

/** Locks the empty-ledger CSV summary contract to the new rectangular row model. */
class CliReportOutputRendererCoverageTest extends CliFixtureSupport {
  @Test
  void renderAccountLedgerCsv_emitsRectangularSummaryRowsWhenLedgerHasNoEntries() {
    String csv = CliQueryOutputRenderer.renderAccountLedgerCsv(sampleAccountLedgerReport());
    java.util.List<String> lines = csv.lines().toList();

    assertTrue(lines.getFirst().startsWith("exportFamily,rowId,parentRowId,relationKind"));
    assertTrue(csv.contains("ledger-summary:1000:EUR"));
    assertTrue(csv.contains("No ledger entries matched the selected scope."));
    int columnCount = csvFieldCount(lines.getFirst());
    for (String line : lines) {
      assertEquals(columnCount, csvFieldCount(line));
    }
  }

  @Test
  void renderAccountLedgerCsv_fallsBackToBookCurrencyWhenNoBalanceBucketsCarryCurrency() {
    AccountLedgerReport emptyCurrencyReport =
        new AccountLedgerReport(
            bookIdentity(),
            declaredAccount("1000", "Cash", dev.erst.fingrind.core.NormalBalance.DEBIT),
            EffectiveDateRange.of(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
            allPostingKinds(),
            List.of(),
            List.of(),
            List.of());

    String csv = CliQueryOutputRenderer.renderAccountLedgerCsv(emptyCurrencyReport);
    java.util.List<String> lines = csv.lines().toList();

    assertTrue(lines.getFirst().startsWith("exportFamily,rowId,parentRowId,relationKind"));
    assertEquals("EUR", CliCsvFormat.parseRow(lines.get(1)).get(12));
  }

  @Test
  void renderAccountLedgerCsv_emitsApprovalChildRowsWithoutPackedCells() {
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
            new PostingId("posting-approval-1"),
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
                    new dev.erst.fingrind.core.ActorId("actor-approval-1"),
                    dev.erst.fingrind.core.ActorType.PERSON,
                    new dev.erst.fingrind.core.CommandId("command-approval-1"),
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

    assertTrue(csv.contains("ledger-counterpart:posting-approval-1:2000"));
    assertTrue(csv.contains("ledger-source-document:posting-approval-1:document-ledger-approval"));
    assertTrue(csv.contains("ledger-approval:posting-approval-1:approval-ledger-approval"));
    int columnCount = csvFieldCount(lines.getFirst());
    for (String line : lines) {
      assertEquals(columnCount, csvFieldCount(line));
    }
  }

  @Test
  void renderFinancialPositionCsv_emitsExplicitEmptySectionRows() {
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

    assertTrue(lines.getFirst().startsWith("exportFamily,rowId,parentRowId,relationKind"));
    assertTrue(csv.contains("financial-position-row:current:1000"));
    assertTrue(csv.contains("financial-position-section-empty:current:LIABILITY"));
    assertTrue(csv.contains("financial-position-section-total:current:ASSET:EUR"));
    assertTrue(csv.contains("financial-position-section-total:current:EQUITY:EUR"));
    int columnCount = csvFieldCount(lines.getFirst());
    for (String line : lines) {
      assertEquals(columnCount, csvFieldCount(line));
    }
  }

  @Test
  void renderChangesInEquityCsv_emitsEmptyRowsThroughTheMessageColumnOnly() {
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

    assertTrue(lines.getFirst().startsWith("exportFamily,rowId,parentRowId,relationKind"));
    assertTrue(csv.contains("changes-in-equity-report-empty:current:2026-04-01:2026-04-30"));
    assertTrue(csv.contains("changes-in-equity-report-empty:comparative:2025-04-01:2025-04-30"));
    assertTrue(csv.contains("No equity lines matched the selected scope."));
    int columnCount = csvFieldCount(lines.getFirst());
    for (String line : lines) {
      assertEquals(columnCount, csvFieldCount(line));
    }
  }

  private static int csvFieldCount(String line) {
    return CliCsvFormat.csvFieldCount(line);
  }
}
