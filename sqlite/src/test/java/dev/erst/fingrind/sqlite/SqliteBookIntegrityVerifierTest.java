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
  void balancedPersistedJournal_rejectsMissingAndUnbalancedJournalLines() {
    Path missingLinesPath = tempDirectory.resolve("persisted-journal-missing-lines.sqlite");
    initializeBookOnDisk(missingLinesPath);
    withStandaloneDatabase(
        bookAccess(missingLinesPath),
        database -> {
          insertPostingFactRow(database, "posting-without-lines", "idem-without-lines");
          assertFalse(SqliteBookIntegrityVerifier.hasBalancedPersistedJournal(database));
        });

    Path unbalancedPath = tempDirectory.resolve("persisted-journal-unbalanced.sqlite");
    initializeBookOnDisk(unbalancedPath);
    withStandaloneDatabase(
        bookAccess(unbalancedPath),
        database -> {
          insertPostingFactRow(database, "posting-unbalanced", "idem-unbalanced");
          insertJournalLineRow(database, "posting-unbalanced", 0, "1000", "DEBIT", "EUR", 1000);
          insertJournalLineRow(database, "posting-unbalanced", 1, "2000", "CREDIT", "EUR", 900);
          assertFalse(SqliteBookIntegrityVerifier.hasBalancedPersistedJournal(database));
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
              "SQLite canonical schema fingerprint expected 8 objects but found 7.",
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
}
