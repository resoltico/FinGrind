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
              where key = 'schema_fingerprint_sha256'
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
          insertJournalLineRow(database, "posting-usd", 0, "1000", "DEBIT", "USD", 1000);
          insertJournalLineRow(database, "posting-usd", 1, "2000", "CREDIT", "USD", 1000);

          assertFalse(SqliteBookIntegrityVerifier.hasFunctionalCurrencyAlignedJournal(database));
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
    Path bookPath = tempDirectory.resolve(filename);
    initializeBookOnDisk(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          corruptionAction.run(database);
          assertFalse(SqliteBookIntegrityVerifier.hasBalancedPersistedJournal(database));
        });
  }
}
