package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.fx.QuotedExchangeRate;
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

/** Coverage for SQLite foreign-exchange attachment read paths and failure handling. */
class SqliteForeignExchangeStoreCoverageTest extends SqlitePostingFactStoreTestSupport {
  private static final int SQLITE_API_PREPARE_V2 = 14;
  private static final int SQLITE_API_STEP = 19;
  private static final int SQLITE_API_FINALIZE = 20;
  private static final MethodHandle LOAD_FOREIGN_EXCHANGE = loadForeignExchangeHandle();
  private static final String DUPLICATE_FOREIGN_EXCHANGE_ROW_SQL =
      """
      select
          transaction_currency_code,
          transaction_amount_minor,
          functional_currency_code,
          functional_amount_minor,
          quoted_transaction_amount_minor,
          quoted_functional_amount_minor,
          quoted_on,
          quote_source,
          treatment_kind
      from posting_foreign_exchange
      where posting_id = ?1
      union all
      select
          transaction_currency_code,
          transaction_amount_minor,
          functional_currency_code,
          functional_amount_minor,
          quoted_transaction_amount_minor,
          quoted_functional_amount_minor,
          quoted_on,
          quote_source,
          treatment_kind
      from posting_foreign_exchange
      where posting_id = ?1
      """;

  @Test
  void postingReader_rejectsDuplicateForeignExchangeRows() {
    Path bookPath = tempDirectory.resolve("posting-reader-foreign-exchange-duplicates.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          initializePostingStorage(database);
          insertForeignExchangePosting(database, "sale");

          try (SqliteStatementRedirectingDatabase redirectedDatabase =
              redirectedDatabase(
                  database,
                  (activeDatabase, sql) ->
                      activeDatabase.prepare(
                          SqlitePostingSql.LOAD_POSTING_FOREIGN_EXCHANGE.equals(sql)
                              ? DUPLICATE_FOREIGN_EXCHANGE_ROW_SQL
                              : sql))) {
            IllegalStateException failure =
                assertThrows(
                    IllegalStateException.class,
                    () ->
                        invokeLoadForeignExchange(
                            new SqlitePostingReader(),
                            redirectedDatabase,
                            new PostingId("099c15e8-f223-31cf-a21c-382e45f9e9cb")));
            assertEquals(
                "SQLite posting foreign-exchange query returned more than one row for posting 099c15e8-f223-31cf-a21c-382e45f9e9cb.",
                failure.getMessage());
          }
        });
  }

  @Test
  void postingReader_propagatesPrepareFailureWhenLoadingForeignExchange() {
    SqliteNativeException failure =
        assertThrows(
            SqliteNativeException.class,
            () ->
                invokeLoadForeignExchange(
                    new SqlitePostingReader(),
                    new SqliteStoreFixtureSupport.ThrowingSqliteNativeDatabase(),
                    new PostingId("1a9c6fac-0d3b-3993-a3d7-0afe0c10b123")));
    assertTrue(
        NullTestSupport.messageOf(failure).contains("prepare a SQLite statement"),
        failure.getMessage());
  }

  @Test
  void postingReader_closesStatementAfterForeignExchangeStepFailure() {
    try (Arena arena = Arena.ofConfined();
        SqliteNativeDatabase database = stepFailingForeignExchangeDatabase(arena)) {
      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  invokeLoadForeignExchange(
                      new SqlitePostingReader(),
                      database,
                      new PostingId("fabb2aac-3f31-38f5-af1d-e7bc3dfdc9e2")));
      assertEquals("Failed to step a SQLite statement.", failure.getMessage());
      assertEquals("step boom", NullTestSupport.messageOf(NullTestSupport.causeOf(failure)));
    }
  }

  @Test
  void postingReader_suppressesFinalizeFailureAfterForeignExchangeDuplicate() {
    Path bookPath = tempDirectory.resolve("posting-reader-foreign-exchange-finalize.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          initializePostingStorage(database);
          insertForeignExchangePosting(database, "sale");

          try (SqliteStatementRedirectingDatabase redirectedDatabase =
              redirectedDatabase(
                  finalizeFailingDatabase(database),
                  (activeDatabase, sql) ->
                      activeDatabase.prepare(
                          SqlitePostingSql.LOAD_POSTING_FOREIGN_EXCHANGE.equals(sql)
                              ? DUPLICATE_FOREIGN_EXCHANGE_ROW_SQL
                              : sql))) {
            IllegalStateException failure =
                assertThrows(
                    IllegalStateException.class,
                    () ->
                        invokeLoadForeignExchange(
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

  private static void insertForeignExchangePosting(
      SqliteNativeDatabase database, String postingIdText) {
    BookkeepingEntry.SaleSettled entry = saleEntryWithForeignExchange();
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

  private static BookkeepingEntry.SaleSettled saleEntryWithForeignExchange() {
    return new BookkeepingEntry.SaleSettled(
        LocalDate.parse("2026-04-07"),
        new AccountCode("1000"),
        new AccountCode("4000"),
        new MonetaryAmount("EUR", "9200"),
        null,
        null,
        new ForeignExchangeDetails(
            new MonetaryAmount("USD", "10000"),
            new MonetaryAmount("EUR", "9200"),
            new QuotedExchangeRate(
                new MonetaryAmount("USD", "10000"),
                new MonetaryAmount("EUR", "9200"),
                LocalDate.parse("2026-04-06"),
                "ecb-spot"),
            ForeignExchangeTreatmentKind.SPOT_TRANSACTION),
        null,
        null);
  }

  private static CommittedProvenance committedProvenance(String token) {
    return new CommittedProvenance(
        new RequestProvenance(
            TestCommandIds.fromLabel("command-" + token),
            new IdempotencyKey("idem-" + token),
            new CausationId("cause-" + token),
            Optional.of(new CorrelationId("corr-" + token))),
        Instant.parse("2026-04-07T10:20:30Z"),
        SourceChannel.CLI);
  }

  private static SqliteNativeDatabase stepFailingForeignExchangeDatabase(Arena arena) {
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

  private static @Nullable ForeignExchangeDetails invokeLoadForeignExchange(
      SqlitePostingReader postingReader, SqliteNativeDatabase activeDatabase, PostingId postingId) {
    try {
      return (@Nullable ForeignExchangeDetails)
          LOAD_FOREIGN_EXCHANGE.invoke(postingReader, activeDatabase, postingId);
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new AssertionError(
          "Failed to invoke SQLite posting foreign-exchange loader.", throwable);
    }
  }

  private static MethodHandle loadForeignExchangeHandle() {
    try {
      return MethodHandles.privateLookupIn(SqlitePostingReader.class, MethodHandles.lookup())
          .findVirtual(
              SqlitePostingReader.class,
              "loadForeignExchange",
              MethodType.methodType(
                  ForeignExchangeDetails.class, SqliteNativeDatabase.class, PostingId.class));
    } catch (ReflectiveOperationException exception) {
      throw new LinkageError(
          "Failed to resolve SQLite posting foreign-exchange loader.", exception);
    }
  }

  /** Redirects one prepared-statement request to a test-controlled SQL variant. */
  @FunctionalInterface
  private interface StatementRedirector {
    SqliteNativeStatement prepare(SqliteNativeDatabase database, String sql);
  }
}
