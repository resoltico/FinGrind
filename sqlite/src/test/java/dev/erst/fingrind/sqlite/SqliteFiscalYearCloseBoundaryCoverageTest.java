package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.FiscalYearCloseService;
import dev.erst.fingrind.executor.InterimResultSweepService;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.CloseTargetAccountCandidateMissing;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearClosePlanner;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepOutcome;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Focused branch coverage for fiscal-year-close and transaction-scoped SQLite mutation seams. */
class SqliteFiscalYearCloseBoundaryCoverageTest extends SqlitePostingFactStoreTestSupport {
  private static final ReportingPeriod FISCAL_YEAR =
      new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"));
  private static final Clock CLOSED_CLOCK =
      Clock.fixed(Instant.parse("2026-12-31T23:59:59Z"), ZoneOffset.UTC);

  @Test
  void fiscalYearClose_rejectsMissingBookBeforeOpeningAnySQLiteFile() {
    Path bookPath = tempDirectory.resolve("fiscal-year-close-missing.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath));
        SqliteReportingPeriodCloseSession closeSession =
            SqliteCapabilitySessions.reportingPeriodClose(postingFactStore)) {
      assertEquals(
          new FiscalYearCloseOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          closeService(closeSession, "unused-1", "unused-2").fiscalYearClose(FISCAL_YEAR));
      assertTrue(java.nio.file.Files.notExists(bookPath));
    }
  }

  @Test
  void fiscalYearClose_directMutationPathRejectsMissingBookBeforeOpeningAnySQLiteFile() {
    Path bookPath = tempDirectory.resolve("fiscal-year-close-direct-missing.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      assertEquals(
          new FiscalYearCloseOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          directFiscalYearClose(postingFactStore, FISCAL_YEAR, "unused-direct"));
      assertTrue(java.nio.file.Files.notExists(bookPath));
    }
  }

  @Test
  void fiscalYearClose_rejectsBlankSQLiteBooksAfterOpeningTheHandle() {
    Path bookPath = tempDirectory.resolve("fiscal-year-close-blank.sqlite");
    createEmptySqliteFile(bookPath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath));
        SqliteReportingPeriodCloseSession closeSession =
            SqliteCapabilitySessions.reportingPeriodClose(postingFactStore)) {
      assertEquals(
          new FiscalYearCloseOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          closeService(closeSession, "unused-1", "unused-2").fiscalYearClose(FISCAL_YEAR));
    }
  }

  @Test
  void fiscalYearClose_directMutationPathRejectsBlankSQLiteBooksAfterOpeningTheHandle() {
    Path bookPath = tempDirectory.resolve("fiscal-year-close-direct-blank.sqlite");
    createEmptySqliteFile(bookPath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      assertEquals(
          new FiscalYearCloseOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          directFiscalYearClose(postingFactStore, FISCAL_YEAR, "unused-direct"));
    }
  }

  @Test
  void fiscalYearClose_rejectsMissingCapitalTarget() {
    Path bookPath = tempDirectory.resolve("fiscal-year-close-missing-capital.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath));
        SqliteReportingPeriodCloseSession closeSession =
            SqliteCapabilitySessions.reportingPeriodClose(postingFactStore)) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      declareCloseAccount(
          postingFactStore,
          "3200",
          "Result Holding",
          FinancialPositionLineClassification.RESULT_HOLDING);
      declareCloseAccount(
          postingFactStore,
          "3300",
          "Retained Accumulated",
          FinancialPositionLineClassification.RETAINED_ACCUMULATED);

      FiscalYearCloseOutcome.Rejected rejected =
          assertInstanceOf(
              FiscalYearCloseOutcome.Rejected.class,
              closeService(closeSession, "unused-1", "unused-2").fiscalYearClose(FISCAL_YEAR));
      assertEquals(
          new CloseTargetAccountCandidateMissing(
              FinancialPositionLineClassification.EQUITY_CONTRIBUTION, List.of()),
          rejected.rejection());
    }
  }

  @Test
  void fiscalYearClose_rejectsMissingResultHoldingTarget() {
    Path bookPath = tempDirectory.resolve("fiscal-year-close-missing-result-holding.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath));
        SqliteReportingPeriodCloseSession closeSession =
            SqliteCapabilitySessions.reportingPeriodClose(postingFactStore)) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      declareCloseAccount(
          postingFactStore,
          "3000",
          "Capital",
          FinancialPositionLineClassification.EQUITY_CONTRIBUTION);
      declareCloseAccount(
          postingFactStore,
          "3300",
          "Retained Accumulated",
          FinancialPositionLineClassification.RETAINED_ACCUMULATED);

      FiscalYearCloseOutcome.Rejected rejected =
          assertInstanceOf(
              FiscalYearCloseOutcome.Rejected.class,
              closeService(closeSession, "unused-1", "unused-2").fiscalYearClose(FISCAL_YEAR));
      assertEquals(
          new CloseTargetAccountCandidateMissing(
              FinancialPositionLineClassification.RESULT_HOLDING, List.of()),
          rejected.rejection());
    }
  }

  @Test
  void fiscalYearClose_rejectsMissingRetainedAccumulatedTarget() {
    Path bookPath = tempDirectory.resolve("fiscal-year-close-missing-retained.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath));
        SqliteReportingPeriodCloseSession closeSession =
            SqliteCapabilitySessions.reportingPeriodClose(postingFactStore)) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      declareCloseAccount(
          postingFactStore,
          "3000",
          "Capital",
          FinancialPositionLineClassification.EQUITY_CONTRIBUTION);
      declareCloseAccount(
          postingFactStore,
          "3200",
          "Result Holding",
          FinancialPositionLineClassification.RESULT_HOLDING);

      FiscalYearCloseOutcome.Rejected rejected =
          assertInstanceOf(
              FiscalYearCloseOutcome.Rejected.class,
              closeService(closeSession, "unused-1", "unused-2").fiscalYearClose(FISCAL_YEAR));
      assertEquals(
          new CloseTargetAccountCandidateMissing(
              FinancialPositionLineClassification.RETAINED_ACCUMULATED, List.of()),
          rejected.rejection());
    }
  }

  @Test
  void fiscalYearClose_rejectsInvalidFiscalYearBoundary() {
    Path bookPath = tempDirectory.resolve("fiscal-year-close-invalid-boundary.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath));
        SqliteReportingPeriodCloseSession closeSession =
            SqliteCapabilitySessions.reportingPeriodClose(postingFactStore)) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      declareAllCloseTargets(postingFactStore);

      assertEquals(
          new FiscalYearCloseOutcome.Rejected(
              new BookkeepingAdministrationRejection.FiscalYearCloseMustStartAt(
                  LocalDate.parse("2026-01-01"))),
          closeService(closeSession, "unused-1", "unused-2")
              .fiscalYearClose(
                  new ReportingPeriod(
                      LocalDate.parse("2026-01-02"), LocalDate.parse("2026-12-31"))));
    }
  }

  @Test
  void fiscalYearClose_rejectsYearsThatEndBeforeTheImmutableBookStart() {
    Path bookPath = tempDirectory.resolve("fiscal-year-close-precedes-horizon.sqlite");
    ReportingPeriod earlierFiscalYear =
        new ReportingPeriod(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31"));
    ReportingPeriod firstQuarter2026 =
        new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-03-31"));
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath));
        SqliteReportingPeriodCloseSession closeSession =
            SqliteCapabilitySessions.reportingPeriodClose(postingFactStore)) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      declareAllCloseTargets(postingFactStore);

      assertInstanceOf(
          InterimResultSweepOutcome.Transferred.class,
          new InterimResultSweepService(
                  closeSession, closeSession, new SequencePostingIdGenerator(), CLOSED_CLOCK)
              .interimResultSweep(firstQuarter2026));

      FiscalYearCloseOutcome.Rejected rejected =
          assertInstanceOf(
              FiscalYearCloseOutcome.Rejected.class,
              closeService(closeSession, "unused-close-1", "unused-close-2")
                  .fiscalYearClose(earlierFiscalYear));

      assertEquals(
          new BookkeepingAdministrationRejection.FiscalYearCloseMustStartAt(
              LocalDate.parse("2026-01-01")),
          rejected.rejection());
      assertEquals(0, countRows(requireStoreDatabase(postingFactStore), "fiscal_year_close"));
    }
  }

  @Test
  void fiscalYearClose_rejectsYearsThatPrecedeTheTransferredThroughHorizon() {
    Path bookPath = tempDirectory.resolve("fiscal-year-close-precedes-transferred-through.sqlite");
    var baseline = bookIdentity();
    var bookOpenedIn2025 =
        new dev.erst.fingrind.core.BookIdentity(
            baseline.entityProfile(),
            baseline.bookDoctrine(),
            baseline.functionalCurrency(),
            baseline.fiscalYearStart(),
            LocalDate.parse("2025-01-01"));
    ReportingPeriod fiscalYear2025 =
        new ReportingPeriod(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31"));
    ReportingPeriod firstQuarter2026 =
        new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-03-31"));
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath));
        SqliteReportingPeriodCloseSession closeSession =
            SqliteCapabilitySessions.reportingPeriodClose(postingFactStore)) {
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"), bookOpenedIn2025, List.of());
      declareMinimalNumericAccounts(postingFactStore);
      declareAllCloseTargets(postingFactStore);

      assertInstanceOf(
          InterimResultSweepOutcome.Transferred.class,
          new InterimResultSweepService(
                  closeSession, closeSession, new SequencePostingIdGenerator(), CLOSED_CLOCK)
              .interimResultSweep(firstQuarter2026));

      FiscalYearCloseOutcome.Rejected rejected =
          assertInstanceOf(
              FiscalYearCloseOutcome.Rejected.class,
              closeService(closeSession, "unused-close-1", "unused-close-2")
                  .fiscalYearClose(fiscalYear2025));

      assertEquals(
          new BookkeepingAdministrationRejection.FiscalYearClosePrecedesTransferredThroughHorizon(
              LocalDate.parse("2025-12-31"), LocalDate.parse("2026-03-31")),
          rejected.rejection());
      assertEquals(0, countRows(requireStoreDatabase(postingFactStore), "fiscal_year_close"));
    }
  }

  @Test
  void fiscalYearClose_replaysExistingClosedYearWithoutPersistingAnotherCloseRow() {
    Path bookPath = tempDirectory.resolve("fiscal-year-close-replay.sqlite");
    ReportingPeriod fiscalYear2026 =
        new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"));
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath));
        SqliteReportingPeriodCloseSession closeSession =
            SqliteCapabilitySessions.reportingPeriodClose(postingFactStore)) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      declareAllCloseTargets(postingFactStore);

      FiscalYearCloseOutcome.Closed firstClose =
          assertInstanceOf(
              FiscalYearCloseOutcome.Closed.class,
              closeService(closeSession).fiscalYearClose(fiscalYear2026));
      FiscalYearCloseOutcome.Closed replay =
          assertInstanceOf(
              FiscalYearCloseOutcome.Closed.class,
              closeService(closeSession, "unused-replay-1", "unused-replay-2")
                  .fiscalYearClose(fiscalYear2026));

      assertFalse(firstClose.idempotentReplay());
      assertTrue(replay.idempotentReplay());
      assertEquals(firstClose.closedFiscalYear(), replay.closedFiscalYear());
      assertEquals(1, countRows(requireStoreDatabase(postingFactStore), "fiscal_year_close"));
      assertEquals(
          1,
          countRowsWhereTextEquals(
              requireStoreDatabase(postingFactStore),
              "audit_event",
              "event_kind",
              "FISCAL_YEAR_CLOSED"));
    }
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void fiscalYearClose_wrapsSQLiteFailuresAfterTheTransactionBegins() {
    Path bookPath = tempDirectory.resolve("fiscal-year-close-sqlite-failure.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath));
        SqliteReportingPeriodCloseSession closeSession =
            SqliteCapabilitySessions.reportingPeriodClose(postingFactStore)) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      declareAllCloseTargets(postingFactStore);
      SqliteNativeDatabase realDatabase = requireStoreDatabase(postingFactStore);
      setStoreDatabase(
          postingFactStore,
          new SqliteStatementRedirectingDatabase(
              realDatabase,
              sql -> {
                if (SqlitePostingSql.LOAD_ALL_ACCOUNTS.equals(sql)) {
                  throw new SqliteNativeException(
                      SqliteNativeResultCode.code("ERROR"), "load-accounts-boom");
                }
                return realDatabase.prepare(sql);
              }));

      try {
        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    closeService(closeSession, "unused-1", "unused-2")
                        .fiscalYearClose(FISCAL_YEAR));
        assertTrue(
            NullTestSupport.messageOf(failure).contains("Failed to close one SQLite fiscal year."));
      } finally {
        setStoreDatabase(postingFactStore, realDatabase);
      }
    }
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void transactionValidationBook_wrapsPostingLookupFailures() {
    Path bookPath = tempDirectory.resolve("validation-book-posting-lookup-failure.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      SqliteNativeDatabase realDatabase = requireStoreDatabase(postingFactStore);
      SqliteTransactionValidationBook validationBook =
          new SqliteTransactionValidationBook(
              new SqliteStatementRedirectingDatabase(
                  realDatabase,
                  sql -> {
                    if (SqlitePostingSql.FIND_POSTING_BY_ID.equals(sql)) {
                      throw new SqliteNativeException(
                          SqliteNativeResultCode.code("ERROR"), "find-posting-boom");
                    }
                    return realDatabase.prepare(sql);
                  }),
              postingFactStore.storeContext().postingReader());

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () -> validationBook.findPosting(new PostingId("missing-posting")));
      assertTrue(NullTestSupport.messageOf(failure).contains("Failed to query SQLite book."));
    }
  }

  @Test
  void commit_replaysMatchingStoredRequestsAcrossTheStoreMutationPath() {
    Path bookPath = tempDirectory.resolve("store-mutation-replay.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      var postingFact = postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty());

      assertEquals(
          new PostingCommitResult.Committed(postingFact, false),
          postingFactStore.commit(postingDraft(postingFact), postingFact::postingId));
      assertEquals(
          new PostingCommitResult.Committed(postingFact, true),
          postingFactStore.commit(
              postingDraft(postingFact), () -> new PostingId("ignored-posting")));
    }
  }

  private static FiscalYearCloseService closeService(
      SqliteReportingPeriodCloseSession closeSession, String... postingIds) {
    return new FiscalYearCloseService(
        closeSession, closeSession, new SequencePostingIdGenerator(postingIds), CLOSED_CLOCK);
  }

  private static FiscalYearCloseOutcome directFiscalYearClose(
      SqlitePostingFactStore postingFactStore,
      ReportingPeriod reportingPeriod,
      String... postingIds) {
    return postingFactStore
        .storeMutationOperations()
        .fiscalYearClose(
            reportingPeriod,
            bookIdentity(),
            directClosePlanner(),
            LocalDate.now(CLOSED_CLOCK),
            CLOSED_CLOCK.instant(),
            new SequencePostingIdGenerator(postingIds));
  }

  private static FiscalYearClosePlanner directClosePlanner() {
    try {
      Class<?> closePostingPolicyClass =
          Class.forName("dev.erst.fingrind.executor.bookkeeping.policy.ClosePostingPolicy");
      Object policy =
          Proxy.newProxyInstance(
              java.util.Objects.requireNonNull(
                  Thread.currentThread().getContextClassLoader(), "context class loader"),
              new Class<?>[] {closePostingPolicyClass},
              (proxy, method, arguments) -> {
                return switch (method.getName()) {
                  case "closesAccountType" -> {
                    AccountType accountType = (AccountType) arguments[0];
                    yield accountType == AccountType.REVENUE || accountType == AccountType.EXPENSE;
                  }
                  case "resultHoldingLineClassification" ->
                      FinancialPositionLineClassification.RESULT_HOLDING;
                  case "toString" -> "DirectClosePolicy";
                  case "hashCode" -> System.identityHashCode(proxy);
                  case "equals" ->
                      arguments[0] != null
                          && Proxy.isProxyClass(arguments[0].getClass())
                          && java.util.Objects.equals(
                              Proxy.getInvocationHandler(arguments[0]),
                              Proxy.getInvocationHandler(proxy));
                  default ->
                      throw new UnsupportedOperationException(
                          "Unsupported ClosePostingPolicy method: " + method.getName());
                };
              });
      return FiscalYearClosePlanner.class
          .getConstructor(closePostingPolicyClass)
          .newInstance(policy);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(
          "Failed to create a direct fiscal-year-close planner for branch coverage.", exception);
    }
  }

  private static void declareAllCloseTargets(SqlitePostingFactStore postingFactStore) {
    declareCloseAccount(
        postingFactStore,
        "3000",
        "Capital",
        FinancialPositionLineClassification.EQUITY_CONTRIBUTION);
    declareCloseAccount(
        postingFactStore,
        "3200",
        "Result Holding",
        FinancialPositionLineClassification.RESULT_HOLDING);
    declareCloseAccount(
        postingFactStore,
        "3300",
        "Retained Accumulated",
        FinancialPositionLineClassification.RETAINED_ACCUMULATED);
  }

  private static void declareCloseAccount(
      SqlitePostingFactStore postingFactStore,
      String accountCode,
      String accountName,
      FinancialPositionLineClassification classification) {
    assertEquals(
        new AccountDeclarationOutcome.Declared(
            new RegisteredAccount(
                new AccountCode(accountCode),
                new AccountName(accountName),
                AccountType.EQUITY,
                financialPositionTaxonomy(classification),
                true,
                Instant.parse("2026-12-31T23:59:59Z"))),
        postingFactStore.declareAccount(
            new dev.erst.fingrind.executor.bookkeeping.AccountDeclaration(
                new AccountCode(accountCode),
                new AccountName(accountName),
                AccountType.EQUITY,
                financialPositionTaxonomy(classification)),
            Instant.parse("2026-12-31T23:59:59Z")));
  }

  /** Deterministic posting-id source for fiscal-year-close coverage paths. */
  private static final class SequencePostingIdGenerator
      implements dev.erst.fingrind.executor.spi.PostingIdGenerator {
    private final String[] postingIds;
    private int nextIndex;

    private SequencePostingIdGenerator(String... postingIds) {
      this.postingIds = postingIds;
    }

    @Override
    public PostingId nextPostingId() {
      if (nextIndex >= postingIds.length) {
        throw new IllegalStateException("SequencePostingIdGenerator ran out of posting ids.");
      }
      int postingIndex = nextIndex;
      nextIndex++;
      return new PostingId(postingIds[postingIndex]);
    }
  }
}
