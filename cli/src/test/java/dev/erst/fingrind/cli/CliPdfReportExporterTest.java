package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.PeriodCurrencySummary;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.report.pdf.PdfReportService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link CliPdfReportExporter}. */
class CliPdfReportExporterTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-04-19T10:15:30Z"), ZoneOffset.UTC);
  private static final DeclaredAccount CASH_ACCOUNT =
      CliIoFixtureSupport.declaredAccount(
          "1000",
          "Cash",
          dev.erst.fingrind.core.AccountType.ASSET,
          NormalBalance.DEBIT,
          true,
          Instant.parse("2026-04-07T12:00:00Z"));
  private static final DeclaredAccount REVENUE_ACCOUNT =
      CliIoFixtureSupport.declaredAccount(
          "2000",
          "Revenue",
          dev.erst.fingrind.core.AccountType.REVENUE,
          NormalBalance.CREDIT,
          true,
          Instant.parse("2026-04-07T12:00:00Z"));

  @TempDir Path tempDirectory;

  @Test
  void exportMethodsWritePdfArtifacts() throws java.io.IOException {
    CliPdfReportExporter exporter =
        new CliPdfReportExporter(new PdfReportService("FinGrind", "0.43.0", CLOCK));

    Path accountBalancePdf = tempDirectory.resolve("balance.pdf");
    Path trialBalancePdf = tempDirectory.resolve("trial.pdf");
    Path accountLedgerPdf = tempDirectory.resolve("ledger.pdf");
    Path periodSummaryPdf = tempDirectory.resolve("summary.pdf");

    exporter.exportAccountBalance(accountBalancePdf, accountBalanceSnapshot());
    exporter.exportTrialBalance(trialBalancePdf, trialBalanceReport());
    exporter.exportAccountLedger(accountLedgerPdf, accountLedgerReport());
    exporter.exportPeriodSummary(periodSummaryPdf, periodSummaryReport());

    assertPdfFile(accountBalancePdf);
    assertPdfFile(trialBalancePdf);
    assertPdfFile(accountLedgerPdf);
    assertPdfFile(periodSummaryPdf);
  }

  @Test
  void exportNormalizesPublishedPdfPermissionsOnPosixFileSystems() throws IOException {
    FileStore fileStore = Files.getFileStore(tempDirectory);
    Assumptions.assumeTrue(
        fileStore.supportsFileAttributeView("posix"),
        "requires one POSIX file store to assert mounted-volume PDF permissions");

    CliPdfReportExporter exporter =
        new CliPdfReportExporter(new PdfReportService("FinGrind", "0.43.0", CLOCK));
    Path trialBalancePdf = tempDirectory.resolve("trial-balance.pdf");

    exporter.exportTrialBalance(trialBalancePdf, trialBalanceReport());

    assertPdfFile(trialBalancePdf);
    assertEquals(
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.OTHERS_READ),
        Files.getPosixFilePermissions(trialBalancePdf));
  }

  @Test
  void exportIgnoresPermissionNormalizationOnNonPosixFileSystems() throws IOException {
    CliPdfReportExporter exporter =
        new CliPdfReportExporter(new PdfReportService("FinGrind", "0.43.0", CLOCK));
    Path archivePath = tempDirectory.resolve("reports.zip");

    try (FileSystem zipFileSystem =
        FileSystems.newFileSystem(
            java.net.URI.create("jar:" + archivePath.toUri()), Map.of("create", "true"))) {
      Path trialBalancePdf = zipFileSystem.getPath("/reports/trial-balance.pdf");

      exporter.exportTrialBalance(trialBalancePdf, trialBalanceReport());

      assertPdfFile(trialBalancePdf);
    }
  }

  @Test
  void defaultFileOperationsDelegatePermissionNormalizationWhenSupported() throws IOException {
    AtomicReference<Path> observedPath = new AtomicReference<>();
    CliPdfReportExporter.DefaultFileOperations fileOperations =
        new CliPdfReportExporter.DefaultFileOperations(observedPath::set);
    Path trialBalancePdf = tempDirectory.resolve("trial-balance.pdf");

    fileOperations.normalizePublishedPdfPermissions(trialBalancePdf);

    assertEquals(trialBalancePdf, observedPath.get());
  }

  @Test
  void defaultFileOperationsFallbackToPortablePermissionNormalizationWhenPosixIsUnsupported()
      throws IOException {
    AtomicReference<Path> observedPath = new AtomicReference<>();
    CliPdfReportExporter.DefaultFileOperations fileOperations =
        new CliPdfReportExporter.DefaultFileOperations(
            path -> {
              throw new UnsupportedOperationException("posix unsupported");
            },
            observedPath::set);
    Path trialBalancePdf = tempDirectory.resolve("trial-balance.pdf");

    fileOperations.normalizePublishedPdfPermissions(trialBalancePdf);

    assertEquals(trialBalancePdf, observedPath.get());
  }

  @Test
  void defaultFileOperationsApplyPortableHostReadablePermissionsOnDefaultFileSystems()
      throws IOException {
    CliPdfReportExporter.DefaultFileOperations fileOperations =
        new CliPdfReportExporter.DefaultFileOperations(
            path -> {
              throw new UnsupportedOperationException("posix unsupported");
            });
    Path trialBalancePdf = tempDirectory.resolve("trial-balance.pdf");
    Files.writeString(trialBalancePdf, "%PDF-", StandardCharsets.ISO_8859_1);

    fileOperations.normalizePublishedPdfPermissions(trialBalancePdf);

    assertTrue(Files.isReadable(trialBalancePdf));
    assertTrue(trialBalancePdf.toFile().canRead());
    assertTrue(trialBalancePdf.toFile().canWrite());
  }

  @Test
  void defaultFileOperationsRejectPortablePermissionNormalizationForMissingArtifact() {
    CliPdfReportExporter.DefaultFileOperations fileOperations =
        new CliPdfReportExporter.DefaultFileOperations(
            path -> {
              throw new UnsupportedOperationException("posix unsupported");
            });
    Path missingPdf = tempDirectory.resolve("missing/trial-balance.pdf");

    IOException exception =
        assertThrows(
            IOException.class, () -> fileOperations.normalizePublishedPdfPermissions(missingPdf));

    String message = exception.getMessage();
    assertNotNull(message);
    assertTrue(message.contains("host-readable"));
  }

  @Test
  void defaultFileOperationsPropagateIoFailuresFromDirectPermissionNormalization() {
    IOException failure = new IOException("permission normalization failed");
    CliPdfReportExporter.DefaultFileOperations fileOperations =
        new CliPdfReportExporter.DefaultFileOperations(
            path -> {
              throw failure;
            });

    IOException exception =
        assertThrows(
            IOException.class,
            () ->
                fileOperations.normalizePublishedPdfPermissions(
                    tempDirectory.resolve("trial-balance.pdf")));

    assertSame(failure, exception);
  }

  @Test
  void defaultFileOperationsPropagateIoFailuresFromPortablePermissionNormalization() {
    IOException failure = new IOException("portable permission normalization failed");
    CliPdfReportExporter.DefaultFileOperations fileOperations =
        new CliPdfReportExporter.DefaultFileOperations(
            path -> {
              throw new UnsupportedOperationException("posix unsupported");
            },
            path -> {
              throw failure;
            });

    IOException exception =
        assertThrows(
            IOException.class,
            () ->
                fileOperations.normalizePublishedPdfPermissions(
                    tempDirectory.resolve("trial-balance.pdf")));

    assertSame(failure, exception);
  }

  @Test
  void exportWrapsFilesystemFailuresInCliPdfExportException() throws java.io.IOException {
    CliPdfReportExporter exporter =
        new CliPdfReportExporter(new PdfReportService("FinGrind", "0.43.0", CLOCK));
    Path blockedParent = tempDirectory.resolve("not-a-directory");
    Files.writeString(blockedParent, "nope", StandardCharsets.UTF_8);
    Path outputPath = blockedParent.resolve("trial-balance.pdf");

    CliPdfExportException exception =
        assertThrows(
            CliPdfExportException.class,
            () -> exporter.exportTrialBalance(outputPath, trialBalanceReport()));

    assertEquals(outputPath.toAbsolutePath().normalize(), exception.outputPath());
  }

  @Test
  void exportFallsBackToNonAtomicMoveWhenAtomicMoveIsUnsupported() {
    RecordingFileOperations fileOperations = new RecordingFileOperations();
    CliPdfReportExporter exporter =
        new CliPdfReportExporter(new PdfReportService("FinGrind", "0.43.0", CLOCK), fileOperations);

    exporter.exportTrialBalance(Path.of("trial-balance.pdf"), trialBalanceReport());

    assertTrue(fileOperations.atomicMoveAttempted);
    assertTrue(fileOperations.regularMovePerformed);
  }

  @Test
  void exportStillFailsCleanlyWhenCleanupDeleteAlsoFails() {
    RecordingFileOperations fileOperations = new RecordingFileOperations();
    fileOperations.failDuringMove = true;
    fileOperations.failDuringDelete = true;
    CliPdfReportExporter exporter =
        new CliPdfReportExporter(new PdfReportService("FinGrind", "0.43.0", CLOCK), fileOperations);

    CliPdfExportException exception =
        assertThrows(
            CliPdfExportException.class,
            () -> exporter.exportTrialBalance(Path.of("trial-balance.pdf"), trialBalanceReport()));

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
        new CliPdfReportExporter(new PdfReportService("FinGrind", "0.43.0", CLOCK));
    Path temporaryFile = Files.createTempFile(tempDirectory, "delete-me", ".tmp");

    exporter.deleteIfPresent(temporaryFile);

    assertTrue(Files.notExists(temporaryFile));
  }

  @Test
  void deleteIfPresentSuppressesCleanupFailures() {
    RecordingFileOperations fileOperations = new RecordingFileOperations();
    fileOperations.failDuringDelete = true;
    CliPdfReportExporter exporter =
        new CliPdfReportExporter(new PdfReportService("FinGrind", "0.43.0", CLOCK), fileOperations);

    exporter.deleteIfPresent(Path.of("temporary.pdf"));

    assertTrue(fileOperations.deleteAttempted);
  }

  private static void assertPdfFile(Path path) throws java.io.IOException {
    assertTrue(Files.exists(path));
    assertEquals("%PDF-", new String(Files.readAllBytes(path), 0, 5, StandardCharsets.ISO_8859_1));
  }

  private static AccountBalanceSnapshot accountBalanceSnapshot() {
    return new AccountBalanceSnapshot(
        CliIoFixtureSupport.bookIdentity(),
        CASH_ACCOUNT,
        Optional.of(LocalDate.parse("2026-04-01")),
        Optional.of(LocalDate.parse("2026-04-30")),
        CliIoFixtureSupport.allPostingKinds(),
        List.of(balance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)));
  }

  private static TrialBalanceReport trialBalanceReport() {
    return new TrialBalanceReport(
        CliIoFixtureSupport.bookIdentity(),
        Optional.of(LocalDate.parse("2026-04-30")),
        EffectiveDateRange.of(null, LocalDate.parse("2025-04-30")),
        CliIoFixtureSupport.allPostingKinds(),
        List.of(
            new TrialBalanceRow(
                CASH_ACCOUNT, balance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT))),
        List.of());
  }

  private static AccountLedgerReport accountLedgerReport() {
    return new AccountLedgerReport(
        CliIoFixtureSupport.bookIdentity(),
        CASH_ACCOUNT,
        new EffectiveDateRange.Bounded(
            LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
        CliIoFixtureSupport.allPostingKinds(),
        List.of(balance("EUR", "0.00", "0.00", "0.00", BalanceSide.ZERO)),
        List.of(
            new dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry(
                postingFact(),
                balance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT),
                money("EUR", "10.00"),
                BalanceSide.DEBIT)),
        List.of(balance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)));
  }

  private static PeriodSummaryReport periodSummaryReport() {
    return new PeriodSummaryReport(
        CliIoFixtureSupport.bookIdentity(),
        LocalDate.parse("2026-04-01"),
        LocalDate.parse("2026-04-30"),
        CliIoFixtureSupport.allPostingKinds(),
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
        PostingKind.STANDARD,
        CliFixtureSupport.accountingEvidence("idem-1"),
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
    public void normalizePublishedPdfPermissions(Path path) {
      // Recording test doubles do not mutate filesystem permissions.
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
