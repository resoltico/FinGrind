package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.BookCipher;
import dev.erst.fingrind.contract.protocol.ProtectedBookFormatContract;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.executor.PostingCommitResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Compatibility and restore coverage for committed protected-book fixtures. */
@NullUnmarked
class SqliteProtectedBookCompatibilityFixtureTest extends SqlitePostingFactStoreTestSupport {
  private static final String CURRENT_DEFAULT_FIXTURE_RESOURCE =
      "/dev/erst/fingrind/sqlite/fixtures/current-default-protected-book.sqlite";
  private static final String CURRENT_DEFAULT_FIXTURE_METADATA_RESOURCE =
      "/dev/erst/fingrind/sqlite/fixtures/current-default-protected-book.metadata.json";
  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

  @Test
  void currentDefaultProtectedBookFixture_metadataAndPersistedFormatMatchCanonicalContract()
      throws Exception {
    ProtectedBookFormatContract expectedFormat = ProtocolCatalog.protectedBookFormat();
    assertEquals(expectedFormat, fixtureMetadataFormat());

    Path fixtureCopy = copyFixture("current-default-protected-book-format.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(fixtureCopy))) {
      assertTrue(postingFactStore.isInitialized());
      assertEquals(
          expectedFormat,
          SqliteProtectedBookFormatIntrospection.openedBookFormat(
              requireStoreDatabase(postingFactStore)));
    }
  }

  @Test
  void newlyCreatedProtectedBook_usesCanonicalProtectedBookFormat() throws Exception {
    Path newBookPath = tempDirectory.resolve("new-protected-book-format.sqlite");

    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromUtf8Bytes(
                "test-protected-book-format",
                "book-format-fixture".getBytes(StandardCharsets.UTF_8));
        SqliteNativeDatabase database = SqliteNativeConnections.open(newBookPath, passphrase)) {
      assertEquals(
          ProtocolCatalog.protectedBookFormat(),
          SqliteProtectedBookFormatIntrospection.openedBookFormat(database));
    }
  }

  @Test
  void currentDefaultProtectedBookFixture_reopensAndRejectsWrongKey() throws Exception {
    Path fixtureCopy = copyFixture("current-default-protected-book.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(fixtureCopy))) {
      assertTrue(postingFactStore.isInitialized());
      assertEquals(2, listAccounts(postingFactStore).size());
      assertTrue(postingFactStore.findAccount(new AccountCode("1000")).isPresent());
      assertTrue(postingFactStore.findAccount(new AccountCode("2000")).isPresent());
      assertTrue(
          postingFactStore.findExistingPosting(new IdempotencyKey("fixture-idem-1")).isPresent());
    }

    try (SqlitePostingFactStore wrongKeyStore =
        new SqlitePostingFactStore(bookAccess(fixtureCopy, "wrong-fixture-key"))) {
      IllegalStateException exception =
          org.junit.jupiter.api.Assertions.assertThrows(
              IllegalStateException.class, wrongKeyStore::isInitialized);
      assertInvalidPlaintextBookFailure(exception);
    }
  }

  @Test
  void closedBookBackupCopy_restoresFixtureStateAndPreservesEncryption() throws Exception {
    Path workingBook = copyFixture("working-protected-book.sqlite");
    Path backupBook = tempDirectory.resolve("working-protected-book.backup.sqlite");
    Files.copy(workingBook, backupBook, StandardCopyOption.REPLACE_EXISTING);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(workingBook))) {
      assertEquals(
          new PostingCommitResult.Committed(
              postingFact(
                  "fixture-posting-2",
                  "fixture-idem-2",
                  java.util.Optional.empty(),
                  java.util.Optional.empty())),
          postingFactStore.commit(
              postingFact(
                  "fixture-posting-2",
                  "fixture-idem-2",
                  java.util.Optional.empty(),
                  java.util.Optional.empty())));
      assertTrue(
          postingFactStore.findExistingPosting(new IdempotencyKey("fixture-idem-2")).isPresent());
    }

    Files.copy(backupBook, workingBook, StandardCopyOption.REPLACE_EXISTING);

    try (SqlitePostingFactStore restoredStore =
        new SqlitePostingFactStore(bookAccess(workingBook))) {
      assertTrue(restoredStore.isInitialized());
      assertTrue(
          restoredStore.findExistingPosting(new IdempotencyKey("fixture-idem-1")).isPresent());
      assertFalse(
          restoredStore.findExistingPosting(new IdempotencyKey("fixture-idem-2")).isPresent());
      assertTrue(restoredStore.findAccount(new AccountCode("1000")).isPresent());
      assertTrue(restoredStore.findAccount(new AccountCode("2000")).isPresent());
    }

    try (SqlitePostingFactStore wrongKeyStore =
        new SqlitePostingFactStore(bookAccess(workingBook, "wrong-fixture-key"))) {
      IllegalStateException exception =
          org.junit.jupiter.api.Assertions.assertThrows(
              IllegalStateException.class, wrongKeyStore::isInitialized);
      assertInvalidPlaintextBookFailure(exception);
    }
  }

  private Path copyFixture(String targetFileName) throws IOException {
    Path targetPath = tempDirectory.resolve(targetFileName);
    try (InputStream resourceStream =
        SqliteProtectedBookCompatibilityFixtureTest.class.getResourceAsStream(
            CURRENT_DEFAULT_FIXTURE_RESOURCE)) {
      if (resourceStream == null) {
        throw new IOException(
            "Missing committed protected-book fixture: " + CURRENT_DEFAULT_FIXTURE_RESOURCE);
      }
      Files.copy(resourceStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
      return targetPath;
    }
  }

  private static ProtectedBookFormatContract fixtureMetadataFormat() throws IOException {
    try (InputStream resourceStream =
        SqliteProtectedBookCompatibilityFixtureTest.class.getResourceAsStream(
            CURRENT_DEFAULT_FIXTURE_METADATA_RESOURCE)) {
      if (resourceStream == null) {
        throw new IOException(
            "Missing committed protected-book fixture metadata: "
                + CURRENT_DEFAULT_FIXTURE_METADATA_RESOURCE);
      }
      JsonNode document =
          Objects.requireNonNull(JSON_MAPPER.readTree(resourceStream), "fixture metadata");
      JsonNode formatNode = document.path("protectedBookFormat");
      return new ProtectedBookFormatContract(
          BookCipher.fromWireValue(formatNode.path("cipher").textValue()),
          formatNode.path("legacyMode").booleanValue(),
          formatNode.path("pageSize").intValue(),
          formatNode.path("reservedBytes").intValue(),
          formatNode.path("legacyPageSize").intValue(),
          formatNode.path("kdfIter").intValue(),
          formatNode.path("plaintextHeaderSize").intValue());
    }
  }
}
