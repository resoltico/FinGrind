package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestFingerprint;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Coverage for SQLite applied-tax attachment read paths and failure handling. */
class SqliteAppliedTaxStoreCoverageTest extends SqlitePostingFactStoreTestSupport {
  private static final int SQLITE_API_PREPARE_V2 = 14;
  private static final int SQLITE_API_STEP = 19;
  private static final int SQLITE_API_FINALIZE = 20;
  private static final MethodHandle LOAD_APPLIED_TAX = loadAppliedTaxHandle();
  private static final String DUPLICATE_APPLIED_TAX_ROW_SQL =
      """
      select
          tax_registration_id,
          tax_code,
          tax_code_name,
          rate_parts_per_million_of_whole,
          inclusion_mode,
          application_kind,
          currency_code,
          taxable_amount_minor,
          tax_amount_minor,
          gross_amount_minor,
          tax_account_code
      from posting_applied_tax
      where posting_id = ?1
      union all
      select
          tax_registration_id,
          tax_code,
          tax_code_name,
          rate_parts_per_million_of_whole,
          inclusion_mode,
          application_kind,
          currency_code,
          taxable_amount_minor,
          tax_amount_minor,
          gross_amount_minor,
          tax_account_code
      from posting_applied_tax
      where posting_id = ?1
      """;

  @Test
  void postingReader_rejectsDuplicateAppliedTaxRows() {
    Path bookPath = tempDirectory.resolve("posting-reader-applied-tax-duplicates.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          initializePostingStorage(database);
          insertAppliedTaxPosting(database, "sale");

          try (SqliteStatementRedirectingDatabase redirectedDatabase =
              redirectedDatabase(
                  database,
                  (activeDatabase, sql) ->
                      activeDatabase.prepare(
                          SqliteTaxSql.LOAD_POSTING_APPLIED_TAX.equals(sql)
                              ? DUPLICATE_APPLIED_TAX_ROW_SQL
                              : sql))) {
            IllegalStateException failure =
                assertThrows(
                    IllegalStateException.class,
                    () ->
                        invokeLoadAppliedTax(
                            new SqlitePostingReader(),
                            redirectedDatabase,
                            new PostingId("099c15e8-f223-31cf-a21c-382e45f9e9cb")));
            assertEquals(
                "SQLite posting applied-tax query returned more than one row for posting 099c15e8-f223-31cf-a21c-382e45f9e9cb.",
                failure.getMessage());
          }
        });
  }

  @Test
  void postingReader_propagatesPrepareFailureWhenLoadingAppliedTax() {
    SqliteNativeException failure =
        assertThrows(
            SqliteNativeException.class,
            () ->
                invokeLoadAppliedTax(
                    new SqlitePostingReader(),
                    new SqliteStoreFixtureSupport.ThrowingSqliteNativeDatabase(),
                    new PostingId("1a9c6fac-0d3b-3993-a3d7-0afe0c10b123")));
    assertTrue(
        NullTestSupport.messageOf(failure).contains("prepare a SQLite statement"),
        failure.getMessage());
  }

  @Test
  void postingReader_closesStatementAfterAppliedTaxStepFailure() {
    try (Arena arena = Arena.ofConfined();
        SqliteNativeDatabase database = stepFailingAppliedTaxDatabase(arena)) {
      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  invokeLoadAppliedTax(
                      new SqlitePostingReader(),
                      database,
                      new PostingId("fabb2aac-3f31-38f5-af1d-e7bc3dfdc9e2")));
      assertEquals("Failed to step a SQLite statement.", failure.getMessage());
      assertEquals("step boom", NullTestSupport.messageOf(NullTestSupport.causeOf(failure)));
    }
  }

  @Test
  void postingReader_suppressesFinalizeFailureAfterAppliedTaxDuplicate() {
    Path bookPath = tempDirectory.resolve("posting-reader-applied-tax-finalize.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          initializePostingStorage(database);
          insertAppliedTaxPosting(database, "sale");

          try (SqliteStatementRedirectingDatabase redirectedDatabase =
              redirectedDatabase(
                  finalizeFailingDatabase(database),
                  (activeDatabase, sql) ->
                      activeDatabase.prepare(
                          SqliteTaxSql.LOAD_POSTING_APPLIED_TAX.equals(sql)
                              ? DUPLICATE_APPLIED_TAX_ROW_SQL
                              : sql))) {
            IllegalStateException failure =
                assertThrows(
                    IllegalStateException.class,
                    () ->
                        invokeLoadAppliedTax(
                            new SqlitePostingReader(),
                            redirectedDatabase,
                            new PostingId("099c15e8-f223-31cf-a21c-382e45f9e9cb")));
            assertTrue(
                NullTestSupport.messageOf(failure).contains("returned more than one row"),
                failure.getMessage());
            assertEquals(1, failure.getSuppressed().length);
            assertEquals(
                "Failed to finalize a SQLite statement.", failure.getSuppressed()[0].getMessage());
            assertEquals(
                "finalize boom",
                NullTestSupport.messageOf(NullTestSupport.causeOf(failure.getSuppressed()[0])));
          }
        });
  }

  private static void initializePostingStorage(SqliteNativeDatabase database) {
    SqliteBookSchemaBootstrap.initializeBook(database);
    SqliteStoreFixtureSupport.insertCanonicalInitializedBookMetadata(database);
  }

  private static void insertAppliedTaxPosting(SqliteNativeDatabase database, String postingIdText) {
    AppliedTax appliedTax =
        new AppliedTax(
            new TaxRegistrationId("vat-lv"),
            new TaxCode("vat-21"),
            new TaxCodeName("VAT Standard Sale"),
            new TaxRate(210_000),
            TaxInclusionMode.EXCLUSIVE,
            TaxApplicationKind.OUTPUT_SALE,
            new MonetaryAmount("EUR", "1000"),
            new MonetaryAmount("EUR", "210"),
            new MonetaryAmount("EUR", "1210"),
            new AccountCode("2200"));
    BookkeepingEntry.SaleSettled entry =
        new BookkeepingEntry.SaleSettled(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1000"),
            new AccountCode("4000"),
            new MonetaryAmount("EUR", "1000"),
            null,
            null,
            null,
            new TaxSelection(appliedTax.taxRegistrationId(), appliedTax.taxCode()),
            appliedTax);
    CommittedPosting posting =
        new CommittedPosting(
            new PostingId(
                java.util
                    .UUID
                    .nameUUIDFromBytes(
                        ("fingrind-test-postingid:" + postingIdText)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .toString()),
            entry.journalEntry(),
            PostingLineageModel.direct(),
            entry.postingKind(),
            entry.postingOriginKind(),
            SqlitePostingFactFixtureSupport.accountingEvidence(postingIdText),
            committedProvenance(postingIdText),
            entry,
            null);
    RequestFingerprint requestFingerprint =
        new RequestFingerprint(RequestFingerprint.CURRENT_VERSION, "0".repeat(64));

    SqliteMutationWriter.insertPostingFact(database, posting, requestFingerprint);
    SqliteMutationWriter.insertJournalLines(database, posting, SqliteCommitFaultHook.NONE);
  }

  private static CommittedProvenance committedProvenance(String token) {
    return new CommittedProvenance(
        new RequestProvenance(
            SqliteTestCommandIds.fromLabel("command-" + token),
            new IdempotencyKey("idem-" + token),
            new CausationId("cause-" + token),
            Optional.of(new CorrelationId("corr-" + token))),
        Instant.parse("2026-04-07T10:20:30Z"),
        SourceChannel.CLI);
  }

  private static SqliteNativeDatabase stepFailingAppliedTaxDatabase(Arena arena) {
    Object[] sqliteApiArguments = SqliteNativeBridgeTestSupport.defaultSqliteApiArguments();
    sqliteApiArguments[SQLITE_API_PREPARE_V2] =
        SqliteNativeBridgeTestSupport.constantMethodHandle(
            0,
            MemorySegment.class,
            MemorySegment.class,
            int.class,
            MemorySegment.class,
            MemorySegment.class);
    sqliteApiArguments[SQLITE_API_STEP] =
        SqliteNativeBridgeTestSupport.throwingMethodHandle(
            new IllegalStateException("step boom"), int.class, MemorySegment.class);
    sqliteApiArguments[SQLITE_API_FINALIZE] =
        SqliteNativeBridgeTestSupport.constantMethodHandle(0, MemorySegment.class);
    return new SqliteNativeDatabase(
        arena.allocate(1), SqliteNativeBridgeTestSupport.buildSqliteApi(sqliteApiArguments)) {
      @Override
      public void close() {}
    };
  }

  private static SqliteNativeDatabase finalizeFailingDatabase(SqliteNativeDatabase database) {
    SqliteNativeApi sqliteApi = database.sqliteApi();
    return new SqliteNativeDatabase(
        database.handle(),
        new SqliteNativeApi(
            sqliteApi.libraryArena(),
            sqliteApi.sqlite3OpenV2(),
            sqliteApi.sqlite3CloseV2(),
            sqliteApi.sqlite3Key(),
            sqliteApi.sqlite3Rekey(),
            sqliteApi.sqlite3Shutdown(),
            sqliteApi.sqlite3BusyTimeout(),
            sqliteApi.sqlite3ExtendedResultCodes(),
            sqliteApi.sqlite3mcConfig(),
            sqliteApi.sqlite3mcConfigCipher(),
            sqliteApi.sqlite3mcCipherName(),
            sqliteApi.sqlite3FileControl(),
            sqliteApi.sqlite3Exec(),
            sqliteApi.sqlite3Free(),
            sqliteApi.sqlite3PrepareV2(),
            sqliteApi.sqlite3BindNull(),
            sqliteApi.sqlite3BindInt(),
            sqliteApi.sqlite3BindInt64(),
            sqliteApi.sqlite3BindText(),
            sqliteApi.sqlite3Step(),
            SqliteNativeBridgeTestSupport.throwingMethodHandle(
                new IllegalStateException("finalize boom"), int.class, MemorySegment.class),
            sqliteApi.sqlite3ColumnText(),
            sqliteApi.sqlite3ColumnBytes(),
            sqliteApi.sqlite3ColumnInt(),
            sqliteApi.sqlite3ColumnInt64(),
            sqliteApi.sqlite3Errmsg(),
            sqliteApi.sqlite3Errstr(),
            sqliteApi.sqlite3ExtendedErrcode(),
            sqliteApi.loadedVersion(),
            sqliteApi.loadedSqlite3mcVersion(),
            sqliteApi.loadedSourceId(),
            sqliteApi.runtimeProvenance(),
            sqliteApi.loadedLibraryPath(),
            sqliteApi.sqlite3BackupInit(),
            sqliteApi.sqlite3BackupStep(),
            sqliteApi.sqlite3BackupFinish()));
  }

  private static SqliteStatementRedirectingDatabase redirectedDatabase(
      SqliteNativeDatabase database, StatementRedirector redirector) {
    return new SqliteStatementRedirectingDatabase(
        database, sql -> redirector.prepare(database, sql));
  }

  private static @Nullable AppliedTax invokeLoadAppliedTax(
      SqlitePostingReader postingReader, SqliteNativeDatabase activeDatabase, PostingId postingId) {
    try {
      return (@Nullable AppliedTax)
          LOAD_APPLIED_TAX.invoke(postingReader, activeDatabase, postingId);
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new AssertionError("Failed to invoke SQLite posting applied-tax loader.", throwable);
    }
  }

  private static MethodHandle loadAppliedTaxHandle() {
    try {
      return MethodHandles.privateLookupIn(SqlitePostingReader.class, MethodHandles.lookup())
          .findVirtual(
              SqlitePostingReader.class,
              "loadAppliedTax",
              MethodType.methodType(AppliedTax.class, SqliteNativeDatabase.class, PostingId.class));
    } catch (ReflectiveOperationException exception) {
      throw new LinkageError("Failed to resolve SQLite posting applied-tax loader.", exception);
    }
  }

  /** Redirects one prepared-statement request to a test-controlled SQL variant. */
  @FunctionalInterface
  private interface StatementRedirector {
    SqliteNativeStatement prepare(SqliteNativeDatabase database, String sql);
  }
}
