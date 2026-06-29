package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.BookCipher;
import dev.erst.fingrind.contract.protocol.ProtectedBookFormatContract;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Compatibility and restore coverage for committed protected-book fixtures. */
class SqliteProtectedBookCompatibilityFixtureTest extends SqlitePostingFactStoreTestSupport {
  private static final String CURRENT_DEFAULT_FIXTURE_RESOURCE =
      "/dev/erst/fingrind/sqlite/fixtures/current-default-protected-book.sqlite";
  private static final String CURRENT_DEFAULT_FIXTURE_METADATA_RESOURCE =
      "/dev/erst/fingrind/sqlite/fixtures/current-default-protected-book.metadata.json";
  private static final String UNSUPPORTED_FORMAT_FIXTURE_RESOURCE =
      "/dev/erst/fingrind/sqlite/fixtures/unsupported-format-protected-book.sqlite";
  private static final String FOREIGN_SQLITE_FIXTURE_RESOURCE =
      "/dev/erst/fingrind/sqlite/fixtures/foreign-sqlite.sqlite";
  private static final String CORRUPTED_FIXTURE_RESOURCE =
      "/dev/erst/fingrind/sqlite/fixtures/corrupted-protected-book.sqlite";
  private static final String TRUNCATED_FIXTURE_RESOURCE =
      "/dev/erst/fingrind/sqlite/fixtures/truncated-protected-book.sqlite";
  private static final String ROLLBACK_ARTIFACT_FIXTURE_RESOURCE =
      "/dev/erst/fingrind/sqlite/fixtures/current-default-protected-book.sqlite.rekey-rollback-fixture.sqlite";
  private static final String JOURNAL_FIXTURE_RESOURCE =
      "/dev/erst/fingrind/sqlite/fixtures/current-default-protected-book.sqlite-journal";
  private static final String WAL_FIXTURE_RESOURCE =
      "/dev/erst/fingrind/sqlite/fixtures/current-default-protected-book.sqlite-wal";
  private static final String SHM_FIXTURE_RESOURCE =
      "/dev/erst/fingrind/sqlite/fixtures/current-default-protected-book.sqlite-shm";
  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

  @Test
  void currentDefaultProtectedBookFixture_metadataAndPersistedFormatMatchCanonicalContract()
      throws Exception {
    ProtectedBookFormatContract expectedFormat = ProtocolCatalog.runtime().protectedBookFormat();
    assertEquals(expectedFormat, fixtureMetadataFormat());
    Path fixtureCopy = copyFixture("current-default-protected-book-format.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(fixtureCopy))) {
      assertTrue(postingFactStore.inspectBook().initialized());
      assertEquals(SqliteBookContract.FORMAT_VERSION, fixtureMetadataBookFormatVersion());
      assertEquals(
          fixtureMetadataSchemaFingerprint(),
          SqliteBookIntegrityVerifier.liveSchemaFingerprint(
              requireStoreDatabase(postingFactStore)));
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
          ProtocolCatalog.runtime().protectedBookFormat(),
          SqliteProtectedBookFormatIntrospection.openedBookFormat(database));
    }
  }

  @Test
  void currentDefaultProtectedBookFixture_reopensAndRejectsWrongKey() throws Exception {
    Path fixtureCopy = copyFixture("current-default-protected-book.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(fixtureCopy))) {
      assertTrue(postingFactStore.inspectBook().initialized());
      assertEquals(2, listAccounts(postingFactStore).size());
      assertTrue(postingFactStore.findAccount(new AccountCode("1000")).isPresent());
      assertTrue(postingFactStore.findAccount(new AccountCode("2000")).isPresent());
      assertTrue(
          postingFactStore.findExistingPosting(new IdempotencyKey("fixture-idem-1")).isPresent());
    }
    try (SqlitePostingFactStore wrongKeyStore =
        openStore(bookAccess(fixtureCopy, "wrong-fixture-key"))) {
      IllegalStateException exception =
          org.junit.jupiter.api.Assertions.assertThrows(
              IllegalStateException.class, wrongKeyStore::inspectBook);
      assertProtectedBookVerificationFailure(exception);
    }
  }

  @Test
  void closedBookBackupCopy_restoresFixtureStateAndPreservesEncryption() throws Exception {
    Path workingBook = copyFixture("working-protected-book.sqlite");
    Path backupBook = tempDirectory.resolve("working-protected-book.backup.sqlite");
    Files.copy(workingBook, backupBook, StandardCopyOption.REPLACE_EXISTING);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(workingBook))) {
      assertEquals(
          new PostingCommitResult.Committed(
              postingFact(
                  "fixture-posting-2",
                  "fixture-idem-2",
                  java.util.Optional.empty(),
                  java.util.Optional.empty()),
              false),
          commitPosting(
              postingFactStore,
              postingFact(
                  "fixture-posting-2",
                  "fixture-idem-2",
                  java.util.Optional.empty(),
                  java.util.Optional.empty())));
      assertTrue(
          postingFactStore.findExistingPosting(new IdempotencyKey("fixture-idem-2")).isPresent());
    }
    Files.copy(backupBook, workingBook, StandardCopyOption.REPLACE_EXISTING);
    try (SqlitePostingFactStore restoredStore = openStore(bookAccess(workingBook))) {
      assertTrue(restoredStore.inspectBook().initialized());
      assertTrue(
          restoredStore.findExistingPosting(new IdempotencyKey("fixture-idem-1")).isPresent());
      assertFalse(
          restoredStore.findExistingPosting(new IdempotencyKey("fixture-idem-2")).isPresent());
      assertTrue(restoredStore.findAccount(new AccountCode("1000")).isPresent());
      assertTrue(restoredStore.findAccount(new AccountCode("2000")).isPresent());
    }
    try (SqlitePostingFactStore wrongKeyStore =
        openStore(bookAccess(workingBook, "wrong-fixture-key"))) {
      IllegalStateException exception =
          org.junit.jupiter.api.Assertions.assertThrows(
              IllegalStateException.class, wrongKeyStore::inspectBook);
      assertProtectedBookVerificationFailure(exception);
    }
  }

  @Test
  void unsupportedFormatFixture_reportsOneUnsupportedFormatInspection() throws Exception {
    Path fixtureCopy =
        copyFixture(
            UNSUPPORTED_FORMAT_FIXTURE_RESOURCE, "unsupported-format-protected-book.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(fixtureCopy))) {
      assertEquals(
          new BookLifecycleInspection.Existing(
              BookLifecycleInspection.Status.UNSUPPORTED_FORMAT_VERSION,
              SqliteBookContract.APPLICATION_ID,
              SqliteBookContract.FORMAT_VERSION + 1,
              SqliteBookContract.FORMAT_VERSION),
          postingFactStore.inspectBook());
    }
  }

  @Test
  void foreignSqliteFixture_reportsOneForeignSqliteInspection() throws Exception {
    Path fixtureCopy = copyFixture(FOREIGN_SQLITE_FIXTURE_RESOURCE, "foreign-sqlite.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(fixtureCopy))) {
      assertEquals(
          new BookLifecycleInspection.Existing(
              BookLifecycleInspection.Status.FOREIGN_SQLITE,
              0,
              0,
              SqliteBookContract.FORMAT_VERSION),
          postingFactStore.inspectBook());
    }
  }

  @Test
  void corruptedAndTruncatedFixtures_failProtectedBookVerification() throws Exception {
    assertProtectedBookVerificationFailure(
        copyFixture(CORRUPTED_FIXTURE_RESOURCE, "corrupted-protected-book.sqlite"));
    assertProtectedBookVerificationFailure(
        copyFixture(TRUNCATED_FIXTURE_RESOURCE, "truncated-protected-book.sqlite"));
  }

  @Test
  void rollbackAndSidecarFixtures_coverCommittedRecoveryAndBlockingArtifactFamilies()
      throws Exception {
    Path workingBook =
        copyFixture(CURRENT_DEFAULT_FIXTURE_RESOURCE, "current-default-protected-book.sqlite");
    Path rollbackArtifact =
        copyFixture(
            ROLLBACK_ARTIFACT_FIXTURE_RESOURCE,
            "current-default-protected-book.sqlite.rekey-rollback-fixture.sqlite");
    Path journalSidecar =
        copyFixture(JOURNAL_FIXTURE_RESOURCE, "current-default-protected-book.sqlite-journal");
    Path walSidecar =
        copyFixture(WAL_FIXTURE_RESOURCE, "current-default-protected-book.sqlite-wal");
    Path shmSidecar =
        copyFixture(SHM_FIXTURE_RESOURCE, "current-default-protected-book.sqlite-shm");

    assertTrue(SqliteRekeyRollbackFile.isRollbackArtifactForBook(workingBook, rollbackArtifact));
    assertEquals(
        List.of(journalSidecar, shmSidecar, walSidecar, rollbackArtifact),
        SqliteBookMaintenanceFiles.blockingArtifactsForBook(workingBook));
  }

  private Path copyFixture(String targetFileName) throws IOException {
    return copyFixture(CURRENT_DEFAULT_FIXTURE_RESOURCE, targetFileName);
  }

  private Path copyFixture(String resourcePath, String targetFileName) throws IOException {
    Path targetPath = tempDirectory.resolve(targetFileName);
    try (InputStream resourceStream =
        SqliteProtectedBookCompatibilityFixtureTest.class.getResourceAsStream(resourcePath)) {
      if (resourceStream == null) {
        throw new IOException("Missing committed protected-book fixture: " + resourcePath);
      }
      Files.copy(resourceStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
      return targetPath;
    }
  }

  private void assertProtectedBookVerificationFailure(Path fixtureCopy) {
    try (SqlitePostingFactStore wrongKeyStore = openStore(bookAccess(fixtureCopy))) {
      IllegalStateException exception =
          org.junit.jupiter.api.Assertions.assertThrows(
              IllegalStateException.class, wrongKeyStore::inspectBook);
      assertProtectedBookVerificationFailure(exception);
    }
  }

  private static ProtectedBookFormatContract fixtureMetadataFormat() throws IOException {
    JsonNode formatNode = fixtureMetadataDocument().path("protectedBookFormat");
    return new ProtectedBookFormatContract(
        ProtocolCatalog.runtime().protectedBookFormat().applicationId(),
        ProtocolCatalog.runtime().protectedBookFormat().formatVersion(),
        BookCipher.fromWireValue(requiredTextField(formatNode, "cipher")),
        formatNode.path("legacyMode").booleanValue(),
        formatNode.path("pageSize").intValue(),
        formatNode.path("reservedBytes").intValue(),
        formatNode.path("legacyPageSize").intValue(),
        formatNode.path("kdfIter").intValue(),
        formatNode.path("plaintextHeaderSize").intValue());
  }

  private static int fixtureMetadataBookFormatVersion() throws IOException {
    return requiredIntField(fixtureMetadataDocument(), "bookFormatVersion");
  }

  private static String fixtureMetadataSchemaFingerprint() throws IOException {
    return requiredTextField(fixtureMetadataDocument(), "schemaFingerprintSha256");
  }

  private static JsonNode fixtureMetadataDocument() throws IOException {
    try (InputStream resourceStream =
        SqliteProtectedBookCompatibilityFixtureTest.class.getResourceAsStream(
            CURRENT_DEFAULT_FIXTURE_METADATA_RESOURCE)) {
      if (resourceStream == null) {
        throw new IOException(
            "Missing committed protected-book fixture metadata: "
                + CURRENT_DEFAULT_FIXTURE_METADATA_RESOURCE);
      }
      return Objects.requireNonNull(JSON_MAPPER.readTree(resourceStream), "fixture metadata");
    }
  }

  private static String requiredTextField(JsonNode node, String fieldName) throws IOException {
    JsonNode fieldNode = node.path(fieldName);
    if (!fieldNode.isString()) {
      throw new IOException("Fixture metadata field `" + fieldName + "` must be a JSON string.");
    }
    return Objects.requireNonNull(
        fieldNode.stringValue(), "fixture metadata field `" + fieldName + "`");
  }

  private static int requiredIntField(JsonNode node, String fieldName) throws IOException {
    JsonNode fieldNode = node.path(fieldName);
    if (!fieldNode.isInt()) {
      throw new IOException("Fixture metadata field `" + fieldName + "` must be a JSON integer.");
    }
    return fieldNode.intValue();
  }
}
