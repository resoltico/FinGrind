package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.security.Provider;
import java.security.Security;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Tests the persisted schema and money integrity checks for initialized SQLite books. */
class SqliteBookIntegrityVerifierTest extends SqlitePostingFactStoreTestSupport {
  private static final MethodHandle SHA256_HEX = verifierHelper("sha256Hex");

  @Test
  void integrityAndForeignKeyChecks_acceptHealthyInitializedBooks() {
    Path bookPath = tempDirectory.resolve("integrity-healthy.sqlite");
    initializeBookOnDisk(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          assertTrue(SqliteBookIntegrityVerifier.passesIntegrityCheck(database));
          assertTrue(SqliteBookIntegrityVerifier.passesForeignKeyCheck(database));
        });
  }

  @Test
  void integrityCheck_requiresExactlyOneOkRow() {
    Path bookPath = tempDirectory.resolve("integrity-check-row-shapes.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteStatementRedirectingDatabase noRowDatabase =
              new SqliteStatementRedirectingDatabase(
                  database,
                  sql ->
                      database.prepare(
                          SqlitePostingSql.PRAGMA_INTEGRITY_CHECK.equals(sql)
                              ? "select value from (select 'ok' as value) where 0"
                              : sql));
          assertFalse(SqliteBookIntegrityVerifier.passesIntegrityCheck(noRowDatabase));

          SqliteStatementRedirectingDatabase nonOkDatabase =
              new SqliteStatementRedirectingDatabase(
                  database,
                  sql ->
                      database.prepare(
                          SqlitePostingSql.PRAGMA_INTEGRITY_CHECK.equals(sql)
                              ? "select 'corrupt' as value"
                              : sql));
          assertFalse(SqliteBookIntegrityVerifier.passesIntegrityCheck(nonOkDatabase));

          SqliteStatementRedirectingDatabase extraRowDatabase =
              new SqliteStatementRedirectingDatabase(
                  database,
                  sql ->
                      database.prepare(
                          SqlitePostingSql.PRAGMA_INTEGRITY_CHECK.equals(sql)
                              ? "select 'ok' as value union all select 'ok'"
                              : sql));
          assertFalse(SqliteBookIntegrityVerifier.passesIntegrityCheck(extraRowDatabase));
        });
  }

  @Test
  void recordedSchemaFingerprint_requiresPresenceAndMatchingValue() {
    Path bookPath = tempDirectory.resolve("recorded-schema-fingerprint.sqlite");
    initializeBookOnDisk(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          assertTrue(SqliteBookIntegrityVerifier.hasMatchingRecordedSchemaFingerprint(database));

          database.executeStatement(
              """
              delete from book_meta
              where meta_key = 'schema_fingerprint_sha256'
              """);
          assertFalse(SqliteBookIntegrityVerifier.hasMatchingRecordedSchemaFingerprint(database));

          SqliteMutationWriter.insertBookMetaValue(
              database, SqlitePostingSql.SCHEMA_FINGERPRINT_META_KEY, "bogus");
          assertFalse(SqliteBookIntegrityVerifier.hasMatchingRecordedSchemaFingerprint(database));
        });
  }

  @Test
  void balancedPersistedJournal_rejectsMissingAndMalformedJournalShapes() {
    Path missingLinesPath = tempDirectory.resolve("persisted-journal-missing-lines.sqlite");
    initializeBookOnDisk(missingLinesPath);
    withStandaloneDatabase(
        bookAccess(missingLinesPath),
        database -> {
          insertPostingFactRow(database, "posting-without-lines", "idem-without-lines");
          assertFalse(SqliteBookIntegrityVerifier.hasBalancedPersistedJournal(database));
        });

    assertRejectedPersistedJournal(
        "persisted-journal-only-debit.sqlite",
        database -> {
          insertPostingFactRow(database, "posting-only-debit", "idem-only-debit");
          insertJournalLineRow(database, "posting-only-debit", 0, "1000", "DEBIT", "EUR", 1000);
          insertJournalLineRow(database, "posting-only-debit", 1, "2000", "DEBIT", "EUR", 1000);
        });
    assertRejectedPersistedJournal(
        "persisted-journal-only-credit.sqlite",
        database -> {
          insertPostingFactRow(database, "posting-only-credit", "idem-only-credit");
          insertJournalLineRow(database, "posting-only-credit", 0, "1000", "CREDIT", "EUR", 1000);
          insertJournalLineRow(database, "posting-only-credit", 1, "2000", "CREDIT", "EUR", 1000);
        });
    assertRejectedPersistedJournal(
        "persisted-journal-mixed-currency.sqlite",
        """
        drop trigger journal_line_validate_functional_currency_on_insert
        """,
        database -> {
          insertPostingFactRow(database, "posting-mixed-currency", "idem-mixed-currency");
          insertJournalLineRow(database, "posting-mixed-currency", 0, "1000", "DEBIT", "EUR", 1000);
          insertJournalLineRow(
              database, "posting-mixed-currency", 1, "2000", "CREDIT", "USD", 1000);
        });
    assertRejectedPersistedJournal(
        "persisted-journal-unbalanced.sqlite",
        database -> {
          insertPostingFactRow(database, "posting-unbalanced", "idem-unbalanced");
          insertJournalLineRow(database, "posting-unbalanced", 0, "1000", "DEBIT", "EUR", 1000);
          insertJournalLineRow(database, "posting-unbalanced", 1, "2000", "CREDIT", "EUR", 900);
        });
  }

  @Test
  void persistedMoneyAudit_rejectsRowsThatViolateThePinnedCurrencyRegistry() {
    Path bookPath = tempDirectory.resolve("persisted-money-invalid-currency.sqlite");
    initializeBookOnDisk(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          database.executeStatement(
              "drop trigger journal_line_validate_functional_currency_on_insert");
          insertPostingFactRow(database, "posting-invalid-currency", "idem-invalid-currency");
          insertJournalLineRow(
              database, "posting-invalid-currency", 0, "1000", "DEBIT", "ZZZ", 1000);
          insertJournalLineRow(
              database, "posting-invalid-currency", 1, "2000", "CREDIT", "ZZZ", 1000);
          assertFalse(SqliteBookIntegrityVerifier.hasValidPersistedMoney(database));
        });
  }

  @Test
  void functionalCurrencyAudit_rejectsJournalLinesOutsideTheBookFunctionalCurrency() {
    Path bookPath = tempDirectory.resolve("persisted-journal-functional-currency.sqlite");
    initializeBookOnDisk(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          assertTrue(SqliteBookIntegrityVerifier.hasFunctionalCurrencyAlignedJournal(database));

          insertPostingFactRow(database, "posting-usd", "idem-usd");
          SqliteNativeException rejection =
              assertThrows(
                  SqliteNativeException.class,
                  () ->
                      insertJournalLineRow(
                          database, "posting-usd", 0, "1000", "DEBIT", "USD", 1000));
          assertEquals("SQLITE_CONSTRAINT_TRIGGER", rejection.resultName());
          assertEquals(
              0,
              queryInt(
                  database, "select count(*) from journal_line where posting_id = 'posting-usd'"));

          database.executeStatement(
              "drop trigger journal_line_validate_functional_currency_on_insert");
          insertJournalLineRow(database, "posting-usd", 0, "1000", "DEBIT", "USD", 1000);
          insertJournalLineRow(database, "posting-usd", 1, "2000", "CREDIT", "USD", 1000);

          assertFalse(SqliteBookIntegrityVerifier.hasFunctionalCurrencyAlignedJournal(database));
        });
  }

  @Test
  void persistedPostingLifecycleAudit_rejectsDurableLifecycleViolations() {
    assertRejectedPersistedPostingLifecycle(
        "persisted-late-opening-balance.sqlite",
        """
        drop trigger posting_fact_validate_opening_balance_window_on_insert
        """,
        database -> {
          insertPostingFactRow(database, "posting-standard", "idem-standard");
          insertJournalLineRow(database, "posting-standard", 0, "1000", "DEBIT", "EUR", 1000);
          insertJournalLineRow(database, "posting-standard", 1, "2000", "CREDIT", "EUR", 1000);
          insertPostingFactRow(
              database,
              "posting-opening-balance",
              "OPENING_BALANCE",
              "2026-04-01",
              "2026-04-07T10:15:31Z",
              new PostingFactSqlLiterals(
                  "actor-opening",
                  "AGENT",
                  "command-opening",
                  "idem-opening",
                  "cause-opening",
                  "null",
                  "null",
                  "CLI",
                  "null"));
        });
    assertRejectedPersistedPostingLifecycle(
        "persisted-opening-balance-nominal.sqlite",
        """
        drop trigger journal_line_validate_opening_balance_account_type_on_insert
        """,
        database -> {
          insertAccountRow(
              database, "4000", "Sales", "REVENUE", "CREDIT", 1, "2026-04-07T10:15:30Z");
          insertPostingFactRow(
              database,
              "posting-opening-balance",
              "OPENING_BALANCE",
              "2026-04-01",
              "2026-04-07T10:15:30Z",
              new PostingFactSqlLiterals(
                  "actor-opening",
                  "AGENT",
                  "command-opening",
                  "idem-opening",
                  "cause-opening",
                  "null",
                  "null",
                  "CLI",
                  "null"));
          insertJournalLineRow(
              database, "posting-opening-balance", 0, "4000", "CREDIT", "EUR", 1000);
          insertJournalLineRow(
              database, "posting-opening-balance", 1, "1000", "DEBIT", "EUR", 1000);
        });
    assertRejectedPersistedPostingLifecycle(
        "persisted-inactive-account-line.sqlite",
        """
        drop trigger journal_line_validate_active_account_on_insert
        """,
        database -> {
          database.executeStatement("update account set active = 0 where account_code = '1000'");
          insertPostingFactRow(database, "posting-inactive", "idem-inactive");
          insertJournalLineRow(database, "posting-inactive", 0, "1000", "DEBIT", "EUR", 1000);
          insertJournalLineRow(database, "posting-inactive", 1, "2000", "CREDIT", "EUR", 1000);
        });
    assertRejectedPersistedPostingLifecycle(
        "persisted-closed-period-backfill.sqlite",
        """
        drop trigger posting_fact_validate_closed_period_on_insert
        """,
        database -> {
          insertAccountRow(
              database, "3000", "Retained Earnings", "EQUITY", "CREDIT", 1, "2026-04-07T10:15:30Z");
          insertPostingFactRow(
              database,
              "posting-period-close",
              "PERIOD_CLOSE",
              "2026-04-30",
              "2026-04-30T23:59:59Z",
              new PostingFactSqlLiterals(
                  "system:periodClose",
                  "SYSTEM",
                  "periodClose:2026-04",
                  "periodClose:2026-04",
                  "periodClose:2026-04",
                  "'periodClose:2026-04'",
                  "null",
                  "SYSTEM",
                  "null"));
          insertJournalLineRow(database, "posting-period-close", 0, "2000", "DEBIT", "EUR", 1000);
          insertJournalLineRow(database, "posting-period-close", 1, "3000", "CREDIT", "EUR", 1000);
          database.executeStatement(
              """
              insert into period_close (
                  period_close_order,
                  effective_date_from,
                  effective_date_to,
                  closing_equity_account_code,
                  closed_at
              ) values (
                  1,
                  '2026-04-01',
                  '2026-04-30',
                  '3000',
                  '2026-04-30T23:59:59Z'
              )
              """);
          database.executeStatement(
              """
              insert into period_close_posting (
                  period_close_order,
                  posting_id
              ) values (
                  1,
                  'posting-period-close'
              )
              """);
          insertPostingFactRow(
              database,
              "posting-closed-period",
              "STANDARD",
              "2026-04-15",
              "2026-05-01T10:15:31Z",
              new PostingFactSqlLiterals(
                  "actor-closed-period",
                  "AGENT",
                  "command-closed-period",
                  "idem-closed-period",
                  "cause-closed-period",
                  "null",
                  "null",
                  "CLI",
                  "null"));
        });
    assertRejectedPersistedPostingLifecycle(
        "persisted-period-close-link.sqlite",
        """
        drop trigger period_close_posting_validate_period_close_posting_on_insert
        """,
        database -> {
          insertAccountRow(
              database, "3000", "Retained Earnings", "EQUITY", "CREDIT", 1, "2026-04-07T10:15:30Z");
          insertPostingFactRow(database, "posting-standard", "idem-standard");
          insertJournalLineRow(database, "posting-standard", 0, "1000", "DEBIT", "EUR", 1000);
          insertJournalLineRow(database, "posting-standard", 1, "2000", "CREDIT", "EUR", 1000);
          database.executeStatement(
              """
              insert into period_close (
                  period_close_order,
                  effective_date_from,
                  effective_date_to,
                  closing_equity_account_code,
                  closed_at
              ) values (
                  1,
                  '2026-04-01',
                  '2026-04-30',
                  '3000',
                  '2026-04-30T23:59:59Z'
              )
              """);
          database.executeStatement(
              """
              insert into period_close_posting (
                  period_close_order,
                  posting_id
              ) values (
                  1,
                  'posting-standard'
              )
              """);
        });
    assertRejectedPersistedPostingLifecycle(
        "persisted-unlinked-period-close.sqlite",
        """
        drop trigger posting_fact_validate_period_close_provenance_on_insert
        """,
        database -> {
          insertAccountRow(
              database, "3000", "Retained Earnings", "EQUITY", "CREDIT", 1, "2026-04-07T10:15:30Z");
          insertPostingFactRow(
              database,
              "posting-period-close",
              "PERIOD_CLOSE",
              "2026-04-30",
              "2026-04-30T23:59:59Z",
              new PostingFactSqlLiterals(
                  "system:periodClose",
                  "SYSTEM",
                  "periodClose:2026-04",
                  "periodClose:2026-04",
                  "periodClose:2026-04",
                  "'periodClose:2026-04'",
                  "null",
                  "SYSTEM",
                  "null"));
          insertJournalLineRow(database, "posting-period-close", 0, "2000", "DEBIT", "EUR", 1000);
          insertJournalLineRow(database, "posting-period-close", 1, "3000", "CREDIT", "EUR", 1000);
        });
    assertRejectedPersistedPostingLifecycle(
        "persisted-invalid-period-close-target.sqlite",
        """
        drop trigger period_close_validate_closing_equity_account_on_insert
        """,
        database -> {
          database.executeStatement("update account set active = 0 where account_code = '1000'");
          database.executeStatement(
              """
              insert into period_close (
                  period_close_order,
                  effective_date_from,
                  effective_date_to,
                  closing_equity_account_code,
                  closed_at
              ) values (
                  1,
                  '2026-04-01',
                  '2026-04-30',
                  '1000',
                  '2026-04-30T23:59:59Z'
              )
              """);
        });
  }

  @Test
  void foreignKeyCheck_rejectsOrphanedPersistedJournalRows() {
    Path bookPath = tempDirectory.resolve("persisted-journal-orphan.sqlite");
    initializeBookOnDisk(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          database.executeStatement("pragma foreign_keys = off");
          insertJournalLineRow(database, "missing-posting", 0, "1000", "DEBIT", "EUR", 1000);
          database.executeStatement("pragma foreign_keys = on");
          assertFalse(SqliteBookIntegrityVerifier.passesForeignKeyCheck(database));
        });
  }

  @Test
  void liveSchemaFingerprint_rejectsCanonicalSchemaObjectCountDrift() {
    Path bookPath = tempDirectory.resolve("schema-fingerprint-drift.sqlite");
    initializeBookOnDisk(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          database.executeStatement("drop index journal_line_by_account_code");
          IllegalStateException exception =
              assertThrows(
                  IllegalStateException.class,
                  () -> SqliteBookIntegrityVerifier.liveSchemaFingerprint(database));
          assertEquals(
              "SQLite canonical schema fingerprint expected %d objects but found %d."
                  .formatted(
                      SqlitePostingSql.EXPECTED_CANONICAL_SCHEMA_OBJECT_COUNT,
                      SqlitePostingSql.EXPECTED_CANONICAL_SCHEMA_OBJECT_COUNT - 1),
              exception.getMessage());
        });
  }

  @Test
  void unexpectedSchemaObjects_areRejectedByTheIntegrityVerifier() {
    Path bookPath = tempDirectory.resolve("unexpected-schema-object.sqlite");
    initializeBookOnDisk(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          assertTrue(SqliteBookIntegrityVerifier.hasNoUnexpectedSchemaObjects(database));
          database.executeStatement("create table unexpected_table(singleton_id integer)");
          assertFalse(SqliteBookIntegrityVerifier.hasNoUnexpectedSchemaObjects(database));
        });
  }

  @Test
  @ResourceLock("java.security.providers")
  void sha256Hex_reportsUnavailableDigestAlgorithm() {
    Provider[] originalProviders = Security.getProviders();
    try {
      removeSha256Providers();
      IllegalStateException exception =
          assertThrows(IllegalStateException.class, () -> sha256Hex("schema-material"));
      assertEquals("SHA-256 is unavailable in this Java runtime.", exception.getMessage());
    } finally {
      restoreProviders(originalProviders);
    }
  }

  private static String sha256Hex(String value) {
    try {
      return (String) SHA256_HEX.invokeExact(value);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError("Failed to invoke SQLite schema fingerprint helper.", throwable);
    }
  }

  private static MethodHandle verifierHelper(String methodName) {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(SqliteBookIntegrityVerifier.class, MethodHandles.lookup());
      return lookup.findStatic(
          SqliteBookIntegrityVerifier.class,
          methodName,
          MethodType.methodType(String.class, String.class));
    } catch (IllegalAccessException | NoSuchMethodException exception) {
      throw new LinkageError("Failed to bind SQLite integrity helper: " + methodName, exception);
    }
  }

  private static void removeSha256Providers() {
    for (Provider provider : Security.getProviders()) {
      if (provider.getService("MessageDigest", "SHA-256") != null) {
        Security.removeProvider(provider.getName());
      }
    }
  }

  private static void restoreProviders(Provider[] providers) {
    for (Provider provider : Security.getProviders()) {
      Security.removeProvider(provider.getName());
    }
    for (int index = 0; index < providers.length; index++) {
      Security.insertProviderAt(providers[index], index + 1);
    }
  }

  private void assertRejectedPersistedJournal(
      String filename, SqliteDatabaseAction corruptionAction) {
    assertRejectedPersistedJournal(filename, "", corruptionAction);
  }

  private void assertRejectedPersistedJournal(
      String filename, String preCorruptionSql, SqliteDatabaseAction corruptionAction) {
    Path bookPath = tempDirectory.resolve(filename);
    initializeBookOnDisk(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          if (!preCorruptionSql.isBlank()) {
            database.executeStatement(preCorruptionSql);
          }
          corruptionAction.run(database);
          assertFalse(SqliteBookIntegrityVerifier.hasBalancedPersistedJournal(database));
        });
  }

  private void assertRejectedPersistedPostingLifecycle(
      String filename, String droppedTriggerSql, SqliteDatabaseAction corruptionAction) {
    Path bookPath = tempDirectory.resolve(filename);
    initializeBookOnDisk(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          database.executeStatement(droppedTriggerSql);
          corruptionAction.run(database);
          assertFalse(SqliteBookIntegrityVerifier.hasValidPersistedPostingLifecycle(database));
          assertEquals(
              SqliteBookState.INCOMPLETE_FINGRIND,
              SqliteBookContract.BOOK_STATE_READER.bookState(database));
        });
  }

  private static void insertPostingFactRow(
      SqliteNativeDatabase database,
      String postingId,
      String postingKind,
      String effectiveDate,
      String recordedAt,
      PostingFactSqlLiterals sqlLiterals) {
    database.executeStatement(
        """
        insert into posting_fact (
            posting_id,
            posting_kind,
            effective_date,
            recorded_at,
            actor_id,
            actor_type,
            command_id,
            idempotency_key,
            causation_id,
            correlation_id,
            reason,
            source_channel,
            prior_posting_id
        ) values (
            '%s',
            '%s',
            '%s',
            '%s',
            '%s',
            '%s',
            '%s',
            '%s',
            '%s',
            %s,
            %s,
            '%s',
            %s
        )
        """
            .formatted(
                postingId,
                postingKind,
                effectiveDate,
                recordedAt,
                sqlLiterals.actorId(),
                sqlLiterals.actorType(),
                sqlLiterals.commandId(),
                sqlLiterals.idempotencyKey(),
                sqlLiterals.causationId(),
                sqlLiterals.correlationIdSqlLiteral(),
                sqlLiterals.reasonSqlLiteral(),
                sqlLiterals.sourceChannel(),
                sqlLiterals.priorPostingIdSqlLiteral()));
  }

  private record PostingFactSqlLiterals(
      String actorId,
      String actorType,
      String commandId,
      String idempotencyKey,
      String causationId,
      String correlationIdSqlLiteral,
      String reasonSqlLiteral,
      String sourceChannel,
      String priorPostingIdSqlLiteral) {}
}
