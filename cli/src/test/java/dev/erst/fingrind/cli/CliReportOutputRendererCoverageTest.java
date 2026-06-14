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
import dev.erst.fingrind.core.AccountRole;
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
    String csv = CliReportOutputRenderer.renderAccountLedgerCsv(sampleAccountLedgerReport());
    java.util.List<String> lines = csv.lines().toList();

    assertEquals(3, lines.size());
    assertTrue(
        lines.getFirst().startsWith("exportFamily,rowId,parentRowId,relationKind,recordKind"));
    assertEquals(CliCsvExportFamilies.POSTING_RELATIONSHIPS, csvValue(lines, 1, "exportFamily"));
    assertEquals("ledger-summary", csvValue(lines, 1, "relationKind"));
    assertEquals("summary", csvValue(lines, 1, "recordKind"));
    assertEquals("1000", csvValue(lines, 1, "accountCode"));
    assertEquals("Cash", csvValue(lines, 1, "accountName"));
    assertEquals("2026-04-01", csvValue(lines, 1, "effectiveDateFrom"));
    assertEquals("2026-04-30", csvValue(lines, 1, "effectiveDateTo"));
    assertEquals("EUR", csvValue(lines, 1, "currencyCode"));
    assertEquals("10.00", csvValue(lines, 1, "closingDebitTotal"));
    assertEquals("10.00", csvValue(lines, 1, "closingNetAmount"));
    assertEquals(CliCsvExportFamilies.POSTING_RELATIONSHIPS, csvValue(lines, 2, "exportFamily"));
    assertEquals("scope-empty", csvValue(lines, 2, "relationKind"));
    assertEquals(CliCsvEmptyKinds.SCOPE_EMPTY, csvValue(lines, 2, "recordKind"));
    assertEquals("No ledger entries matched the selected scope.", csvValue(lines, 2, "message"));
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

    String csv = CliReportOutputRenderer.renderAccountLedgerCsv(emptyCurrencyReport);
    java.util.List<String> lines = csv.lines().toList();

    assertEquals(3, lines.size());
    assertTrue(lines.get(1).contains(",EUR,0.00,0.00,0.00,ZERO,0.00,0.00,0.00,ZERO,"));
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
            dev.erst.fingrind.core.PostingOriginKind.CASH_REVENUE,
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

    String csv = CliReportOutputRenderer.renderAccountLedgerCsv(report);
    List<String> lines = csv.lines().toList();

    assertTrue(
        lines.stream()
            .skip(1)
            .anyMatch(
                line ->
                    CliCsvExportFamilies.POSTING_RELATIONSHIPS.equals(
                            csvValue(line, lines.getFirst(), "exportFamily"))
                        && "approval".equals(csvValue(line, lines.getFirst(), "relationKind"))
                        && "approval-ledger-approval"
                            .equals(csvValue(line, lines.getFirst(), "approvalId"))
                        && "APPROVED"
                            .equals(csvValue(line, lines.getFirst(), "approvalDecision"))));
    assertTrue(
        lines.stream()
            .skip(1)
            .anyMatch(
                line ->
                    "counterpart-account".equals(csvValue(line, lines.getFirst(), "relationKind"))
                        && "1000".equals(csvValue(line, lines.getFirst(), "accountCode"))));
    assertTrue(
        lines.stream()
            .skip(1)
            .anyMatch(
                line ->
                    "source-document".equals(csvValue(line, lines.getFirst(), "relationKind"))
                        && "1000".equals(csvValue(line, lines.getFirst(), "accountCode"))));
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
                            Optional.of(AccountRole.ORDINARY),
                            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                            StatementLineKind.DECLARED_ACCOUNT,
                            assetBalance)),
                    List.of(assetBalance)),
                new FinancialPositionSection(AccountType.LIABILITY, List.of(), List.of()),
                new FinancialPositionSection(AccountType.EQUITY, List.of(), List.of(assetBalance))),
            List.of());

    String csv = CliReportOutputRenderer.renderFinancialPositionCsv(report);
    List<String> lines = csv.lines().toList();

    assertTrue(
        lines.stream()
            .skip(1)
            .anyMatch(
                line ->
                    "current".equals(csvValue(line, lines.getFirst(), "reportBasis"))
                        && "line".equals(csvValue(line, lines.getFirst(), "relationKind"))
                        && "row".equals(csvValue(line, lines.getFirst(), "recordKind"))
                        && "ASSET".equals(csvValue(line, lines.getFirst(), "accountType"))
                        && "1000".equals(csvValue(line, lines.getFirst(), "lineCode"))
                        && "Cash".equals(csvValue(line, lines.getFirst(), "lineName"))));
    assertTrue(
        lines.stream()
            .skip(1)
            .anyMatch(
                line ->
                    "section-empty".equals(csvValue(line, lines.getFirst(), "relationKind"))
                        && "LIABILITY".equals(csvValue(line, lines.getFirst(), "accountType"))
                        && "No liability lines matched the selected scope."
                            .equals(csvValue(line, lines.getFirst(), "message"))));
    assertTrue(
        lines.stream()
            .anyMatch(
                line ->
                    "current".equals(csvValue(line, lines.getFirst(), "reportBasis"))
                        && "section-total".equals(csvValue(line, lines.getFirst(), "relationKind"))
                        && "ASSET".equals(csvValue(line, lines.getFirst(), "accountType"))
                        && "asset-total".equals(csvValue(line, lines.getFirst(), "lineCode"))));
    assertTrue(
        lines.stream()
            .anyMatch(
                line ->
                    "current".equals(csvValue(line, lines.getFirst(), "reportBasis"))
                        && "section-total".equals(csvValue(line, lines.getFirst(), "relationKind"))
                        && "EQUITY".equals(csvValue(line, lines.getFirst(), "accountType"))
                        && "equity-total".equals(csvValue(line, lines.getFirst(), "lineCode"))));
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

    String csv = CliReportOutputRenderer.renderChangesInEquityCsv(report);
    List<String> lines = csv.lines().toList();

    assertTrue(
        lines.stream()
            .skip(1)
            .anyMatch(
                line ->
                    CliCsvExportFamilies.STATEMENT.equals(
                            csvValue(line, lines.getFirst(), "exportFamily"))
                        && "report-empty".equals(csvValue(line, lines.getFirst(), "relationKind"))
                        && "current".equals(csvValue(line, lines.getFirst(), "reportBasis"))
                        && CliCsvEmptyKinds.REPORT_EMPTY.equals(
                            csvValue(line, lines.getFirst(), "recordKind"))
                        && "2026-04-01"
                            .equals(csvValue(line, lines.getFirst(), "effectiveDateFrom"))
                        && "2026-04-30".equals(csvValue(line, lines.getFirst(), "effectiveDateTo"))
                        && "EUR".equals(csvValue(line, lines.getFirst(), "currencyCode"))
                        && "No equity lines matched the selected scope."
                            .equals(csvValue(line, lines.getFirst(), "message"))));
    assertTrue(
        lines.stream()
            .skip(1)
            .anyMatch(
                line ->
                    CliCsvExportFamilies.STATEMENT.equals(
                            csvValue(line, lines.getFirst(), "exportFamily"))
                        && "report-empty".equals(csvValue(line, lines.getFirst(), "relationKind"))
                        && "comparative".equals(csvValue(line, lines.getFirst(), "reportBasis"))
                        && CliCsvEmptyKinds.REPORT_EMPTY.equals(
                            csvValue(line, lines.getFirst(), "recordKind"))
                        && "2025-04-01"
                            .equals(csvValue(line, lines.getFirst(), "effectiveDateFrom"))
                        && "2025-04-30".equals(csvValue(line, lines.getFirst(), "effectiveDateTo"))
                        && "EUR".equals(csvValue(line, lines.getFirst(), "currencyCode"))
                        && "No equity lines matched the selected scope."
                            .equals(csvValue(line, lines.getFirst(), "message"))));
    int columnCount = csvFieldCount(lines.getFirst());
    for (String line : lines) {
      assertEquals(columnCount, csvFieldCount(line));
    }
  }

  private static int csvFieldCount(String line) {
    return CliCsvFormat.csvFieldCount(line);
  }

  private static String csvValue(List<String> lines, int rowIndex, String headerName) {
    return csvValue(lines.get(rowIndex), lines.getFirst(), headerName);
  }

  private static String csvValue(String line, String headerLine, String headerName) {
    List<String> headers = CliCsvFormat.parseRow(headerLine);
    List<String> row = CliCsvFormat.parseRow(line);
    return row.get(headers.indexOf(headerName));
  }
}
