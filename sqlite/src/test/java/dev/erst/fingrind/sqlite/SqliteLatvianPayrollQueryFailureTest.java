package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.executor.spi.LatvianPayrollLookupStore;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Verifies payroll-query result-shape and native-failure translation at each SQLite boundary. */
class SqliteLatvianPayrollQueryFailureTest extends SqlitePostingFactStoreTestSupport {
  private static final LatvianPayrollRunId RUN_ID =
      new LatvianPayrollRunId("payroll-run-2026-06-employee-001");
  private static final LatvianPayrollEmployeeReference EMPLOYEE =
      new LatvianPayrollEmployeeReference("employee-001");
  private static final LatvianPayrollMonth PAYROLL_MONTH = LatvianPayrollMonth.parse("2026-06");
  private static final PostingId RUN_POSTING_ID = new PostingId("payroll-run-posting");
  private static final PostingId SETTLEMENT_POSTING_ID = new PostingId("payroll-net-wage-posting");

  @Test
  void transactionValidationPayrollQueries_translateEveryNativeFailure() throws Exception {
    Path bookPath = tempDirectory.resolve("payroll-validation-stale.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath))) {
      SqliteTransactionValidationBook validationBook =
          new SqliteTransactionValidationBook(staleDatabaseHandle(bookPath), store.postingReader());

      assertPayrollQueryFailure(
          "Failed to query SQLite Latvian payroll run.",
          () -> validationBook.findLatvianPayrollRun(RUN_ID));
      assertPayrollQueryFailure(
          "Failed to query SQLite Latvian payroll run.",
          () -> validationBook.findLatvianPayrollRunByOriginPosting(RUN_POSTING_ID));
      assertPayrollQueryFailure(
          "Failed to query SQLite Latvian payroll run.",
          () -> validationBook.findActiveLatvianPayrollRun(EMPLOYEE, PAYROLL_MONTH));
      assertPayrollQueryFailure(
          "Failed to query SQLite Latvian payroll runs.", validationBook::latvianPayrollRuns);
      assertPayrollQueryFailure(
          "Failed to query SQLite Latvian payroll settlement.",
          () ->
              validationBook.findActiveLatvianPayrollSettlement(
                  RUN_ID, LatvianPayrollSettlementKind.NET_WAGES));
      assertPayrollQueryFailure(
          "Failed to query SQLite Latvian payroll settlement.",
          () -> validationBook.findLatvianPayrollSettlementByPosting(SETTLEMENT_POSTING_ID));
      assertPayrollQueryFailure(
          "Failed to query SQLite Latvian payroll settlements.",
          validationBook::latvianPayrollSettlements);
    }
  }

  @Test
  void defaultValidationQueries_admitAnInitializedBook() {
    Path bookPath = tempDirectory.resolve("payroll-validation-default-gate.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          insertCanonicalInitializedBookMetadata(database);

          assertTrue(
              new SqliteTransactionValidationQueries(database, new SqlitePostingReader())
                  .allowsInitializedWorkflow());
        });
  }

  @Test
  void statementQueries_rejectDuplicateRowsInsteadOfSelectingAnArbitraryPayrollFact() {
    Path bookPath = tempDirectory.resolve("payroll-query-result-shapes.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          try (SqliteStatementRedirectingDatabase duplicateRunDatabase =
                  redirectedDatabase(
                      database, SqliteLatvianPayrollSql.FIND_RUN_BY_ID, duplicateRunRowsSql());
              SqliteStatementRedirectingDatabase duplicateSettlementDatabase =
                  redirectedDatabase(
                      database,
                      SqliteLatvianPayrollSql.FIND_ACTIVE_SETTLEMENT,
                      duplicateSettlementRowsSql())) {
            IllegalStateException duplicateRun =
                assertThrows(
                    IllegalStateException.class,
                    () ->
                        SqliteLatvianPayrollStatementQueries.findRun(duplicateRunDatabase, RUN_ID));
            assertEquals(
                "SQLite Latvian payroll lookup returned more than one row.",
                duplicateRun.getMessage());

            IllegalStateException duplicateSettlement =
                assertThrows(
                    IllegalStateException.class,
                    () ->
                        SqliteLatvianPayrollStatementQueries.findActiveSettlement(
                            duplicateSettlementDatabase,
                            RUN_ID,
                            LatvianPayrollSettlementKind.NET_WAGES));
            assertEquals(
                "SQLite Latvian payroll settlement lookup returned more than one row.",
                duplicateSettlement.getMessage());
          }
        });
  }

  @Test
  void statementQueries_rejectEachUnsupportedRetainedWithholdingProfileFact() throws Exception {
    Path bookPath = tempDirectory.resolve("payroll-query-unsupported-profile.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          try (SqliteStatementRedirectingDatabase unsupportedProfileDatabase =
              redirectedDatabase(
                  database,
                  SqliteLatvianPayrollSql.FIND_RUN_BY_ID,
                  unsupportedWithholdingProfileRunRowSql())) {
            assertUnsupportedWithholdingProfile(unsupportedProfileDatabase);
          }
          try (SqliteStatementRedirectingDatabase unsupportedDependantCountDatabase =
              redirectedDatabase(
                  database, SqliteLatvianPayrollSql.FIND_RUN_BY_ID, runRowSql(1, 1))) {
            assertUnsupportedWithholdingProfile(unsupportedDependantCountDatabase);
          }
        });
  }

  @Test
  void originatingEntryMapper_rejectsMissingDurablePayrollFacts() {
    Path bookPath = tempDirectory.resolve("payroll-originating-entry-facts.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          try (SqliteStatementRedirectingDatabase noFactsDatabase =
              redirectedDatabase(
                  database, SqliteLatvianPayrollSql.FIND_RUN_BY_ORIGIN_POSTING_ID, noRowsSql())) {
            IllegalStateException missingRun =
                assertThrows(
                    IllegalStateException.class,
                    () ->
                        SqliteLatvianPayrollOriginatingEntryMapper.originatingEntry(
                            noFactsDatabase,
                            RUN_POSTING_ID,
                            PostingOriginKind.LATVIAN_MONTHLY_PAYROLL));
            assertEquals(
                "Latvian payroll posting payroll-run-posting has no durable payroll-run facts.",
                missingRun.getMessage());
          }

          try (SqliteStatementRedirectingDatabase noSettlementDatabase =
              redirectedDatabase(
                  database,
                  SqliteLatvianPayrollSql.FIND_SETTLEMENT_BY_ORIGIN_POSTING_ID,
                  noRowsSql())) {
            IllegalStateException missingSettlement =
                assertThrows(
                    IllegalStateException.class,
                    () ->
                        SqliteLatvianPayrollOriginatingEntryMapper.originatingEntry(
                            noSettlementDatabase,
                            SETTLEMENT_POSTING_ID,
                            PostingOriginKind.LATVIAN_PAYROLL_STATE_REMITTANCE));
            assertEquals(
                "Latvian payroll settlement posting payroll-net-wage-posting has no durable settlement facts.",
                missingSettlement.getMessage());
          }

          try (SqliteStatementRedirectingDatabase missingRunForSettlementDatabase =
              new SqliteStatementRedirectingDatabase(
                  database,
                  sql ->
                      database.prepare(
                          switch (sql) {
                            case SqliteLatvianPayrollSql.FIND_SETTLEMENT_BY_ORIGIN_POSTING_ID ->
                                settlementRowByOriginSql();
                            case SqliteLatvianPayrollSql.FIND_RUN_BY_ID -> noRowsSql();
                            default -> sql;
                          }))) {
            IllegalStateException missingRunForSettlement =
                assertThrows(
                    IllegalStateException.class,
                    () ->
                        SqliteLatvianPayrollOriginatingEntryMapper.originatingEntry(
                            missingRunForSettlementDatabase,
                            SETTLEMENT_POSTING_ID,
                            PostingOriginKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT));
            assertEquals(
                "Latvian payroll settlement posting payroll-net-wage-posting has no durable payroll-run facts.",
                missingRunForSettlement.getMessage());
          }
        });
  }

  @Test
  void storePayrollQueryOperations_translateNativeFailuresAfterSessionInitialization()
      throws Exception {
    Path bookPath = tempDirectory.resolve("payroll-store-query-stale.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath))) {
      openBookWithNoDeclaredAccounts(store);
      LatvianPayrollLookupStore payrollLookup = readPayrollCapability(store);

      try (StoreDatabaseSwap ignored = swapStoreDatabase(store, staleDatabaseHandle(bookPath))) {
        assertPayrollQueryFailure(
            "Failed to query SQLite Latvian payroll run.",
            () -> payrollLookup.findLatvianPayrollRun(RUN_ID));
      }
    }
  }

  private static SqliteReadLatvianPayrollCapabilityView readPayrollCapability(
      SqlitePostingFactStore store) {
    return new SqliteReadLatvianPayrollCapabilityView() {
      @Override
      public SqliteThreadOwner storeThreadOwner() {
        return store.storeThreadOwner();
      }

      @Override
      public SqliteStoreReadOperations storeReadOperations() {
        return store.storeReadOperations();
      }
    };
  }

  private static SqliteStatementRedirectingDatabase redirectedDatabase(
      SqliteNativeDatabase database, String redirectedSql, String replacementSql) {
    return new SqliteStatementRedirectingDatabase(
        database, sql -> database.prepare(redirectedSql.equals(sql) ? replacementSql : sql));
  }

  private static void assertPayrollQueryFailure(
      String expectedMessage, org.junit.jupiter.api.function.Executable query) {
    IllegalStateException exception = assertThrows(IllegalStateException.class, query);
    assertTrue(NullTestSupport.messageOf(exception).contains(expectedMessage));
  }

  private static void assertUnsupportedWithholdingProfile(
      SqliteStatementRedirectingDatabase unsupportedProfileDatabase) {
    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteLatvianPayrollStatementQueries.findRun(unsupportedProfileDatabase, RUN_ID));
    assertEquals(
        "Stored Latvian payroll run has unsupported withholding-profile facts.",
        failure.getMessage());
  }

  private static String noRowsSql() {
    return "select 'unreachable' where ?1 is not null and 0";
  }

  private static String duplicateRunRowsSql() {
    return runRowSql() + " union all " + runRowSql();
  }

  private static String unsupportedWithholdingProfileRunRowSql() {
    return runRowSql(0, 0);
  }

  private static String runRowSql() {
    return runRowSql(1, 0);
  }

  private static String runRowSql(long taxBookHeldAtEmployer, long dependantCount) {
    return """
        select
            'payroll-run-2026-06-employee-001',
            'employee-001',
            '2026-06',
            %d,
            %d,
            '2026-06-30',
            'payroll-wage-expense',
            'payroll-employer-social-expense',
            'payroll-net-wages-payable',
            'payroll-employee-social-payable',
            'payroll-employer-social-payable',
            'payroll-personal-income-tax-payable',
            'EUR',
            200000,
            21000,
            47180,
            55000,
            31620,
            147380,
            'payroll-run-posting',
            null
        where ?1 is not null
        """
        .formatted(taxBookHeldAtEmployer, dependantCount);
  }

  private static String duplicateSettlementRowsSql() {
    return settlementRowSql() + " union all " + settlementRowSql();
  }

  private static String settlementRowSql() {
    return """
        select
            'NET_WAGES',
            'payroll-run-2026-06-employee-001',
            'payroll-net-wage-posting',
            '2026-07-01',
            'payroll-cash',
            null
        where ?1 is not null
          and ?2 is not null
        """;
  }

  private static String settlementRowByOriginSql() {
    return """
        select
            'NET_WAGES',
            'payroll-run-2026-06-employee-001',
            'payroll-net-wage-posting',
            '2026-07-01',
            'payroll-cash',
            null
        where ?1 is not null
        """;
  }
}
