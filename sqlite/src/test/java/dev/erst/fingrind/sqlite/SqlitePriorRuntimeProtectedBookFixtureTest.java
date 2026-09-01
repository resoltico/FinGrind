package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Verifies that the managed runtime opens the committed protected-book compatibility fixture. */
class SqlitePriorRuntimeProtectedBookFixtureTest extends SqliteStoreLifecycleTestSupport {
  private static final String FIXTURE_RESOURCE =
      "/dev/erst/fingrind/sqlite/fixtures/sqlite3mc-2.4.0-format-57-protected-book.sqlite";
  private static final String FIXTURE_METADATA_RESOURCE =
      "/dev/erst/fingrind/sqlite/fixtures/sqlite3mc-2.4.0-format-57-protected-book.metadata.json";
  private static final String FIXTURE_ENTITY_NAME = "Acme Studio";

  @Test
  void currentManagedRuntime_opensTheFormat57BookCreatedBySqlite3mc240() throws Exception {
    Path fixturePath = materializeFixture("sqlite3mc-2.4.0-format-57-protected-book.sqlite");
    byte[] fixtureBytes = Files.readAllBytes(fixturePath);
    String fixtureMetadata = fixtureMetadata();
    String fixtureSha256 =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fixtureBytes));

    assertTrue(fixtureMetadata.contains("\"sqlite3mcVersion\" : \"2.4.0\""));
    assertTrue(
        fixtureMetadata.contains("\"bookFormatVersion\" : " + SqliteBookContract.FORMAT_VERSION));
    assertTrue(fixtureMetadata.contains("\"fixtureSha256\" : \"" + fixtureSha256 + "\""));

    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "prior-runtime fixture direct native open", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database = SqliteNativeConnections.open(fixturePath, passphrase)) {
      assertEquals(
          SqliteBookContract.FORMAT_VERSION,
          SqliteStatementQueries.querySingleInt(database, "pragma user_version"));
    }

    try (SqlitePostingFactStore bookStore = openStore(bookAccess(fixturePath))) {
      BookLifecycleInspection.Initialized inspection =
          assertInstanceOf(BookLifecycleInspection.Initialized.class, bookStore.inspectBook());

      assertTrue(inspection.compatibleWithCurrentBinary());
      assertEquals(SqliteBookContract.APPLICATION_ID, inspection.applicationId());
      assertEquals(SqliteBookContract.FORMAT_VERSION, inspection.detectedBookFormatVersion());
      assertEquals(SqliteBookContract.FORMAT_VERSION, inspection.supportedBookFormatVersion());
      assertEquals(Instant.parse("2026-08-28T00:00:00Z"), inspection.initializedAt());
      assertEquals(FIXTURE_ENTITY_NAME, inspection.bookIdentity().entityName().value());
      assertEquals(inspection.bookIdentity(), bookStore.requireInitializedBookIdentity());
    }

    assertFalse(
        new String(fixtureBytes, StandardCharsets.ISO_8859_1).contains(FIXTURE_ENTITY_NAME));
    Path wrongKeyFixture =
        materializeFixture("sqlite3mc-2.4.0-format-57-protected-book-wrong-key.sqlite");
    try (SqlitePostingFactStore wrongKeyStore =
        openStore(bookAccess(wrongKeyFixture, "wrong-prior-runtime-fixture-key"))) {
      assertProtectedBookVerificationFailure(
          assertThrows(IllegalStateException.class, wrongKeyStore::inspectBook));
    }
  }

  private Path materializeFixture(String targetFileName) throws IOException {
    Path targetPath = tempDirectory.resolve(targetFileName);
    SqliteBookFileSecurity.createNewOwnerOnlyBookFile(targetPath);
    try (InputStream fixture =
        Objects.requireNonNull(
            getClass().getResourceAsStream(FIXTURE_RESOURCE),
            "Prior-runtime protected-book fixture is missing.")) {
      Files.write(targetPath, fixture.readAllBytes());
    }
    return targetPath;
  }

  private String fixtureMetadata() throws IOException {
    try (InputStream metadata =
        Objects.requireNonNull(
            getClass().getResourceAsStream(FIXTURE_METADATA_RESOURCE),
            "Prior-runtime protected-book fixture metadata is missing.")) {
      return new String(metadata.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
