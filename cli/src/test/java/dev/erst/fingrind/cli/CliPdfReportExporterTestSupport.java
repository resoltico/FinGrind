package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerPagination;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.PeriodCurrencySummary;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.ArtifactPublicationRetainedStageException;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Shared report fixtures and filesystem doubles for PDF exporter tests. */
final class CliPdfReportExporterTestSupport {
  static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-19T10:15:30Z"), ZoneOffset.UTC);
  static final DeclaredAccount CASH_ACCOUNT =
      CliIoFixtureSupport.declaredAccount(
          "1000",
          "Cash",
          dev.erst.fingrind.core.AccountType.ASSET,
          NormalBalance.DEBIT,
          true,
          Instant.parse("2026-04-07T12:00:00Z"));
  static final DeclaredAccount REVENUE_ACCOUNT =
      CliIoFixtureSupport.declaredAccount(
          "2000",
          "Revenue",
          dev.erst.fingrind.core.AccountType.REVENUE,
          NormalBalance.CREDIT,
          true,
          Instant.parse("2026-04-07T12:00:00Z"));

  private CliPdfReportExporterTestSupport() {}

  static CliPdfReportExporter exporterWithoutNativeDirectoryForce() {
    return new CliPdfReportExporter(
        new PdfReportService("FinGrind", "0.57.0", CLOCK),
        new RealFileOperationsWithoutDirectoryForce());
  }

  static CliPdfReportExporter exporterWith(RecordingFileOperations fileOperations) {
    return new CliPdfReportExporter(
        new PdfReportService("FinGrind", "0.57.0", CLOCK), fileOperations, ignored -> {});
  }

  static Path privatePdfOutputDirectory(Path temporaryDirectory, String name) throws IOException {
    Path outputDirectory = temporaryDirectory.toRealPath().resolve(name);
    Files.createDirectories(outputDirectory);
    CliTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(outputDirectory);
    return outputDirectory;
  }

  static void assertPdfFile(Path path) throws IOException {
    assertTrue(Files.exists(path));
    assertEquals("%PDF-", new String(Files.readAllBytes(path), 0, 5, StandardCharsets.ISO_8859_1));
  }

  static AccountBalanceSnapshot accountBalanceSnapshot() {
    return new AccountBalanceSnapshot(
        CliIoFixtureSupport.bookIdentity(),
        CASH_ACCOUNT,
        Optional.of(LocalDate.parse("2026-04-01")),
        Optional.of(LocalDate.parse("2026-04-30")),
        CliIoFixtureSupport.allPostingKinds(),
        List.of(balance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)));
  }

  static TrialBalanceReport trialBalanceReport() {
    return CliIoFixtureSupport.trialBalanceReport(
        CliIoFixtureSupport.bookIdentity(),
        Optional.of(LocalDate.parse("2026-04-30")),
        EffectiveDateRange.of(null, LocalDate.parse("2025-04-30")),
        CliIoFixtureSupport.allPostingKinds(),
        List.of(
            new TrialBalanceRow(
                CASH_ACCOUNT, balance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT))),
        List.of());
  }

  static AccountLedgerReport accountLedgerReport() {
    return new AccountLedgerReport(
        CliIoFixtureSupport.bookIdentity(),
        CASH_ACCOUNT,
        new EffectiveDateRange.Bounded(
            LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
        CliIoFixtureSupport.allPostingKinds(),
        AccountLedgerPagination.firstPage(50),
        List.of(balance("EUR", "0.00", "0.00", "0.00", BalanceSide.ZERO)),
        List.of(
            new dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry(
                postingFact(),
                balance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT),
                money("EUR", "10.00"),
                BalanceSide.DEBIT,
                null)),
        List.of(balance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)));
  }

  static PeriodSummaryReport periodSummaryReport() {
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
        new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
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
        dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
        CliFixtureSupport.accountingEvidence("idem-1"),
        new CommittedProvenance(
            new RequestProvenance(
                new CommandId("20aea0ba-3b2e-3428-af5b-f9ee3094522c"),
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

  /** Minimal filesystem double for focused publication tests. */
  static final class RecordingFileOperations implements CliPdfReportExporter.FileOperations {
    final FailurePlan failures = new FailurePlan();
    final Observations observations = new Observations();

    @Override
    public Path createAndWriteStage(Path directory, String prefix, String suffix, byte[] bytes)
        throws IOException {
      if (failures.errorBeforeStageCreation != null) {
        throw failures.errorBeforeStageCreation;
      }
      if (failures.failureBeforeStageCreation != null) {
        throw failures.failureBeforeStageCreation;
      }
      Path stagedPath = directory.resolve(prefix + "recorded-stage" + suffix);
      observations.stagedPath = stagedPath;
      observations.stageBytes = bytes.clone();
      if (failures.errorAfterStageCreation != null) {
        retainStageAfterFatalFailure(stagedPath, failures.errorAfterStageCreation);
        throw failures.errorAfterStageCreation;
      }
      if (failures.failureAfterStageCreation != null) {
        throw new ArtifactPublicationRetainedStageException(
            new ArtifactPublicationRetention(stagedPath), failures.failureAfterStageCreation);
      }
      observations.stageCreatedAndWritten = true;
      return stagedPath;
    }

    @Override
    public void createLink(Path finalPath, Path stagedPath) throws IOException {
      observations.linkAttempted = true;
      if (failures.errorDuringLink != null) {
        throw failures.errorDuringLink;
      }
      if (failures.failDuringLinkWithExistingTarget) {
        throw new FileAlreadyExistsException(finalPath.toString());
      }
      if (failures.failDuringLink) {
        throw new IOException("link failed");
      }
      observations.linkCreated = true;
    }

    @Override
    public void forceDirectory(Path directory) throws IOException {
      observations.directoryForceCount++;
      if (observations.directoryForceCount == failures.errorOnDirectoryForceAttempt
          && failures.errorDuringDirectoryForce != null) {
        throw failures.errorDuringDirectoryForce;
      }
      if (observations.directoryForceCount == failures.throwSecurityOnDirectoryForceAttempt) {
        throw new SecurityException("directory force rejected");
      }
      if (observations.directoryForceCount == failures.failOnDirectoryForceAttempt) {
        throw new IOException("directory force failed");
      }
    }

    Path stagedPath() {
      return java.util.Objects.requireNonNull(observations.stagedPath, "stagedPath");
    }

    byte[] stageBytes() {
      return java.util.Objects.requireNonNull(observations.stageBytes, "stageBytes").clone();
    }

    private static void retainStageAfterFatalFailure(Path stagedPath, Error primaryFailure) {
      primaryFailure.addSuppressed(
          new ArtifactPublicationRetainedStageException(
              new ArtifactPublicationRetention(stagedPath),
              new IOException("Fatal staged PDF write retained the exact private stage.")));
    }

    /** Configures failure modes that are independent of the operations observed by a test. */
    static final class FailurePlan {
      @Nullable IOException failureBeforeStageCreation;
      @Nullable IOException failureAfterStageCreation;
      boolean failDuringLinkWithExistingTarget;
      boolean failDuringLink;
      int errorOnDirectoryForceAttempt;
      int failOnDirectoryForceAttempt;
      int throwSecurityOnDirectoryForceAttempt;
      @Nullable Error errorBeforeStageCreation;
      @Nullable Error errorAfterStageCreation;
      @Nullable Error errorDuringLink;
      @Nullable Error errorDuringDirectoryForce;
    }

    /** Captures the observable publication facts asserted by focused tests. */
    static final class Observations {
      boolean stageCreatedAndWritten;
      boolean linkAttempted;
      boolean linkCreated;
      int directoryForceCount;
      @Nullable Path stagedPath;
      byte @Nullable [] stageBytes;
    }
  }

  /**
   * Uses real staged writes and no-clobber links while isolating tests from native directory force.
   */
  private static final class RealFileOperationsWithoutDirectoryForce
      implements CliPdfReportExporter.FileOperations {
    private final CliPdfReportExporter.DefaultFileOperations delegate =
        new CliPdfReportExporter.DefaultFileOperations();

    @Override
    public Path createAndWriteStage(Path directory, String prefix, String suffix, byte[] bytes)
        throws IOException {
      return delegate.createAndWriteStage(directory, prefix, suffix, bytes);
    }

    @Override
    public void createLink(Path finalPath, Path stagedPath) throws IOException {
      delegate.createLink(finalPath, stagedPath);
    }

    @Override
    public void forceDirectory(Path directory) {
      // Native directory durability is isolated in its transport tests.
    }
  }
}
