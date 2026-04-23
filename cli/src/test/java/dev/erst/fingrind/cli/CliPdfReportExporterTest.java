package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.CurrencyBalance;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.EffectiveDateRange;
import dev.erst.fingrind.contract.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.PeriodCurrencySummary;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingLineage;
import dev.erst.fingrind.contract.TrialBalanceReport;
import dev.erst.fingrind.contract.TrialBalanceRow;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.report.pdf.PdfReportService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link CliPdfReportExporter}. */
class CliPdfReportExporterTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-04-19T10:15:30Z"), ZoneOffset.UTC);
  private static final Path BOOK_PATH =
      Path.of("/tmp/Rīga büro/2026 Q2 close/Ops & Sales [April] #1.sqlite");
  private static final DeclaredAccount CASH_ACCOUNT =
      new DeclaredAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          NormalBalance.DEBIT,
          true,
          Instant.parse("2026-04-07T12:00:00Z"));
  private static final DeclaredAccount REVENUE_ACCOUNT =
      new DeclaredAccount(
          new AccountCode("2000"),
          new AccountName("Revenue"),
          NormalBalance.CREDIT,
          true,
          Instant.parse("2026-04-07T12:00:00Z"));

  @TempDir Path tempDirectory;

  @Test
  void exportMethodsWritePdfArtifacts() throws java.io.IOException {
    CliPdfReportExporter exporter =
        new CliPdfReportExporter(new PdfReportService("FinGrind", "0.24.0", CLOCK));

    Path accountBalancePdf = tempDirectory.resolve("balance.pdf");
    Path trialBalancePdf = tempDirectory.resolve("trial.pdf");
    Path accountLedgerPdf = tempDirectory.resolve("ledger.pdf");
    Path periodSummaryPdf = tempDirectory.resolve("summary.pdf");

    exporter.exportAccountBalance(accountBalancePdf, BOOK_PATH, accountBalanceSnapshot());
    exporter.exportTrialBalance(trialBalancePdf, BOOK_PATH, trialBalanceReport());
    exporter.exportAccountLedger(accountLedgerPdf, BOOK_PATH, accountLedgerReport());
    exporter.exportPeriodSummary(periodSummaryPdf, BOOK_PATH, periodSummaryReport());

    assertPdfFile(accountBalancePdf);
    assertPdfFile(trialBalancePdf);
    assertPdfFile(accountLedgerPdf);
    assertPdfFile(periodSummaryPdf);
  }

  @Test
  void exportWrapsFilesystemFailuresInCliPdfExportException() throws java.io.IOException {
    CliPdfReportExporter exporter =
        new CliPdfReportExporter(new PdfReportService("FinGrind", "0.24.0", CLOCK));
    Path blockedParent = tempDirectory.resolve("not-a-directory");
    Files.writeString(blockedParent, "nope", StandardCharsets.UTF_8);
    Path outputPath = blockedParent.resolve("trial-balance.pdf");

    CliPdfExportException exception =
        assertThrows(
            CliPdfExportException.class,
            () -> exporter.exportTrialBalance(outputPath, BOOK_PATH, trialBalanceReport()));

    assertEquals(outputPath.toAbsolutePath().normalize(), exception.outputPath());
  }

  @Test
  void exportFallsBackToNonAtomicMoveWhenAtomicMoveIsUnsupported() {
    RecordingFileOperations fileOperations = new RecordingFileOperations();
    CliPdfReportExporter exporter =
        new CliPdfReportExporter(new PdfReportService("FinGrind", "0.24.0", CLOCK), fileOperations);

    exporter.exportTrialBalance(Path.of("trial-balance.pdf"), BOOK_PATH, trialBalanceReport());

    assertTrue(fileOperations.atomicMoveAttempted);
    assertTrue(fileOperations.regularMovePerformed);
  }

  @Test
  void exportStillFailsCleanlyWhenCleanupDeleteAlsoFails() {
    RecordingFileOperations fileOperations = new RecordingFileOperations();
    fileOperations.failDuringMove = true;
    fileOperations.failDuringDelete = true;
    CliPdfReportExporter exporter =
        new CliPdfReportExporter(new PdfReportService("FinGrind", "0.24.0", CLOCK), fileOperations);

    CliPdfExportException exception =
        assertThrows(
            CliPdfExportException.class,
            () ->
                exporter.exportTrialBalance(
                    Path.of("trial-balance.pdf"), BOOK_PATH, trialBalanceReport()));

    assertEquals(Path.of("trial-balance.pdf").toAbsolutePath().normalize(), exception.outputPath());
    assertTrue(fileOperations.deleteAttempted);
  }

  @Test
  void parentDirectoryFallsBackToCurrentWorkingDirectoryWhenPathHasNoParent() {
    assertEquals(
        Path.of(".").toAbsolutePath().normalize(),
        CliPdfReportExporter.parentDirectory(Path.of("trial-balance.pdf")));
  }

  @Test
  void deleteIfPresentRemovesExistingTemporaryFiles() throws IOException {
    CliPdfReportExporter exporter =
        new CliPdfReportExporter(new PdfReportService("FinGrind", "0.24.0", CLOCK));
    Path temporaryFile = Files.createTempFile(tempDirectory, "delete-me", ".tmp");

    exporter.deleteIfPresent(temporaryFile);

    assertTrue(Files.notExists(temporaryFile));
  }

  @Test
  void deleteIfPresentSuppressesCleanupFailures() {
    RecordingFileOperations fileOperations = new RecordingFileOperations();
    fileOperations.failDuringDelete = true;
    CliPdfReportExporter exporter =
        new CliPdfReportExporter(new PdfReportService("FinGrind", "0.24.0", CLOCK), fileOperations);

    exporter.deleteIfPresent(Path.of("temporary.pdf"));

    assertTrue(fileOperations.deleteAttempted);
  }

  private static void assertPdfFile(Path path) throws java.io.IOException {
    assertTrue(Files.exists(path));
    assertEquals("%PDF-", new String(Files.readAllBytes(path), 0, 5, StandardCharsets.ISO_8859_1));
  }

  private static AccountBalanceSnapshot accountBalanceSnapshot() {
    return new AccountBalanceSnapshot(
        CASH_ACCOUNT,
        Optional.of(LocalDate.parse("2026-04-01")),
        Optional.of(LocalDate.parse("2026-04-30")),
        List.of(balance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)));
  }

  private static TrialBalanceReport trialBalanceReport() {
    return new TrialBalanceReport(
        Optional.of(LocalDate.parse("2026-04-30")),
        List.of(
            new TrialBalanceRow(
                CASH_ACCOUNT, balance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT))));
  }

  private static AccountLedgerReport accountLedgerReport() {
    return new AccountLedgerReport(
        CASH_ACCOUNT,
        new EffectiveDateRange.Bounded(
            LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
        List.of(balance("EUR", "0.00", "0.00", "0.00", BalanceSide.ZERO)),
        List.of(
            new dev.erst.fingrind.contract.AccountLedgerEntry(
                postingFact(),
                balance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT),
                money("EUR", "10.00"),
                BalanceSide.DEBIT)),
        List.of(balance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)));
  }

  private static PeriodSummaryReport periodSummaryReport() {
    return new PeriodSummaryReport(
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        1,
        2,
        2,
        List.of(
            new PeriodCurrencySummary(balance("EUR", "10.00", "10.00", "0.00", BalanceSide.ZERO))),
        List.of(
            new PeriodAccountActivityRow(
                REVENUE_ACCOUNT, balance("EUR", "0.00", "10.00", "10.00", BalanceSide.CREDIT))));
  }

  private static PostingFact postingFact() {
    return new PostingFact(
        new PostingId("posting-1"),
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    CASH_ACCOUNT.accountCode(), JournalLine.EntrySide.DEBIT, money("EUR", "10.00")),
                new JournalLine(
                    REVENUE_ACCOUNT.accountCode(),
                    JournalLine.EntrySide.CREDIT,
                    money("EUR", "10.00")))),
        PostingLineage.direct(),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("operator"),
                ActorType.HUMAN,
                new CommandId("command-1"),
                new IdempotencyKey("idem-1"),
                new CausationId("cause-1"),
                Optional.empty()),
            Instant.parse("2026-04-19T10:15:30Z"),
            SourceChannel.CLI));
  }

  private static CurrencyBalance balance(
      String currencyCode,
      String debitTotal,
      String creditTotal,
      String netAmount,
      BalanceSide balanceSide) {
    return new CurrencyBalance(
        money(currencyCode, debitTotal),
        money(currencyCode, creditTotal),
        money(currencyCode, netAmount),
        balanceSide);
  }

  private static Money money(String currencyCode, String amount) {
    return new Money(new CurrencyCode(currencyCode), new BigDecimal(amount));
  }

  /** Minimal fake filesystem adapter for focused PDF export tests. */
  private static final class RecordingFileOperations
      implements CliPdfReportExporter.FileOperations {
    private boolean atomicMoveAttempted;
    private boolean regularMovePerformed;
    private boolean failDuringMove;
    private boolean failDuringDelete;
    private boolean deleteAttempted;

    @Override
    public void createDirectories(Path directory) {}

    @Override
    public Path createTempFile(Path directory, String prefix, String suffix) {
      return directory.resolve(prefix + "temporary" + suffix);
    }

    @Override
    public void write(Path path, byte[] bytes, StandardOpenOption... options) {}

    @Override
    public Path move(Path source, Path target, StandardCopyOption... options) throws IOException {
      regularMovePerformed = true;
      if (failDuringMove) {
        throw new IOException("move failed");
      }
      return target;
    }

    @Override
    public boolean deleteIfExists(Path path) throws IOException {
      deleteAttempted = true;
      if (failDuringDelete) {
        throw new IOException("delete failed");
      }
      return true;
    }

    @Override
    public Path moveAtomically(Path source, Path target, StandardCopyOption... options)
        throws IOException {
      atomicMoveAttempted = true;
      throw new java.nio.file.AtomicMoveNotSupportedException(
          source.toString(), target.toString(), "atomic move not supported");
    }
  }
}
