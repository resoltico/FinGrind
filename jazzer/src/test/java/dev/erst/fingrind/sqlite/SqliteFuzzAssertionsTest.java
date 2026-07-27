package dev.erst.fingrind.sqlite;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.CliFuzzAccountFixtures;
import dev.erst.fingrind.cli.CliFuzzFixtures;
import dev.erst.fingrind.cli.CliFuzzHarnessTestSupport;
import dev.erst.fingrind.cli.CliFuzzWorkflowFixtures;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.core.PrivateOutputDirectory;
import dev.erst.fingrind.executor.BookAdministrationService;
import java.lang.reflect.Proxy;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers deterministic SQLite-specific assertions shared by Jazzer harnesses. */
class SqliteFuzzAssertionsTest {
  @TempDir Path tempDirectory;

  @Test
  void sqliteAssertions_cover_happy_path_state_transitions_and_missing_files() throws Exception {
    Path bookPath = tempDirectory.resolve("entity-book.sqlite");
    var command = CliFuzzFixtures.readPostEntryCommand(basicValidRequest().getBytes(UTF_8));

    try (SqlitePostingSession store = SqliteFuzzAssertions.openStore(bookPath)) {
      BookAdministrationService administrationService =
          CliFuzzWorkflowFixtures.administrationService(store);
      CliFuzzWorkflowFixtures.openBook(administrationService);
      java.util.List<DeclaredAccount> accounts =
          CliFuzzAccountFixtures.declarePostingAccounts(administrationService, command);

      SqliteFuzzAssertions.assertStoreConnectionHardening(store);
      SqliteFuzzAssertions.deactivateAccount(bookPath, accounts.getFirst().accountCode().value());
      assertFalse(store.findAccount(accounts.getFirst().accountCode()).orElseThrow().active());
      SqliteFuzzAssertions.activateAccount(bookPath, accounts.getFirst().accountCode().value());
      assertTrue(store.findAccount(accounts.getFirst().accountCode()).orElseThrow().active());
    }

    SqliteFuzzAssertions.assertCommittedBookUsesStrictTables(bookPath);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqliteFuzzAssertions.deactivateAccount(
                tempDirectory.resolve("missing.sqlite"), "1000"));
  }

  @Test
  void sqliteAssertions_reject_invalid_store_shapes_and_broken_schema_checks() throws Exception {
    Path bookPath = tempDirectory.resolve("entity-book.sqlite");
    try (SqlitePostingSession store = SqliteFuzzAssertions.openStore(bookPath)) {
      IllegalStateException noRow =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteFuzzAssertions.assertQueryInt(
                      SqliteFuzzAssertions.requireOwnedStore(store).activeNativeDatabase(),
                      "select 1 where 1 = 0",
                      1));
      assertTrue(String.valueOf(noRow.getMessage()).contains("Expected one SQLite row"));

      IllegalStateException manyRows =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteFuzzAssertions.assertQueryInt(
                      SqliteFuzzAssertions.requireOwnedStore(store).activeNativeDatabase(),
                      "select 1 union all select 2",
                      1));
      assertTrue(String.valueOf(manyRows.getMessage()).contains("Expected one SQLite row only"));

      IllegalStateException wrongIntValue =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteFuzzAssertions.assertQueryInt(
                      SqliteFuzzAssertions.requireOwnedStore(store).activeNativeDatabase(),
                      "select 2",
                      1));
      assertTrue(
          String.valueOf(wrongIntValue.getMessage())
              .contains("Unexpected SQLite pragma/query value"));

      IllegalStateException wrongTextValue =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteFuzzAssertions.assertQueryText(
                      SqliteFuzzAssertions.requireOwnedStore(store).activeNativeDatabase(),
                      "select 'wal'",
                      "delete"));
      assertTrue(
          String.valueOf(wrongTextValue.getMessage())
              .contains("Unexpected SQLite pragma/query value"));

      IllegalStateException noTextRow =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteFuzzAssertions.assertQueryText(
                      SqliteFuzzAssertions.requireOwnedStore(store).activeNativeDatabase(),
                      "select 'wal' where 1 = 0",
                      "delete"));
      assertTrue(String.valueOf(noTextRow.getMessage()).contains("Expected one SQLite row"));

      IllegalStateException manyTextRows =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteFuzzAssertions.assertQueryText(
                      SqliteFuzzAssertions.requireOwnedStore(store).activeNativeDatabase(),
                      "select 'a' union all select 'b'",
                      "a"));
      assertTrue(
          String.valueOf(manyTextRows.getMessage()).contains("Expected one SQLite row only"));
    }

    Path invalidBook = tempDirectory.resolve("invalid.sqlite");
    java.nio.file.Files.writeString(invalidBook, "not-a-book", UTF_8);
    IllegalStateException invalidSchema =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteFuzzAssertions.assertCommittedBookUsesStrictTables(invalidBook));
    assertTrue(String.valueOf(invalidSchema.getMessage()).contains("strict-schema invariant"));
    IllegalStateException invalidUpdate =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteFuzzAssertions.deactivateAccount(invalidBook, "1000"));
    assertTrue(
        String.valueOf(invalidUpdate.getMessage())
            .contains("Failed to update account active flag"));

    try (SqlitePostingSession unsupportedSession =
        (SqlitePostingSession)
            Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[] {SqlitePostingSession.class},
                (proxy, method, args) -> null)) {
      IllegalArgumentException unsupported =
          assertThrows(
              IllegalArgumentException.class,
              () -> SqliteFuzzAssertions.requireOwnedStore(unsupportedSession));
      assertTrue(
          String.valueOf(unsupported.getMessage())
              .contains("Unsupported owned SQLite store or capability wrapper"));
    }
    assertEquals("a''b", SqliteFuzzAssertions.escapeSqlLiteral("a'b"));
  }

  @Test
  void sqliteAssertions_wrap_connection_hardening_native_failures() throws Exception {
    Path bookPath = tempDirectory.resolve("synthetic-book.sqlite");
    try (SqlitePostingFactStore store =
        new SqlitePostingFactStore(bookPath, SqliteFuzzAssertions.bookPassphrase())) {
      SqliteStoreTestAccess.publishNativeDatabase(
          store,
          new ThrowingHandleSqliteNativeDatabase(
              SqliteNativeBootstrap.api(),
              new SqliteNativeException(1, "synthetic prepare failure")));

      IllegalStateException hardeningFailure =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteFuzzAssertions.assertStoreConnectionHardening(store));

      assertTrue(
          String.valueOf(hardeningFailure.getMessage()).contains("pragma-hardening invariant"));
    }
  }

  @Test
  void sqliteAssertions_requireOwnedStore_acceptsDirectStoreOwners() throws Exception {
    Path bookPath = tempDirectory.resolve("direct-store-owner.sqlite");
    try (SqlitePostingFactStore store =
        new SqlitePostingFactStore(bookPath, SqliteFuzzAssertions.bookPassphrase())) {
      assertEquals(store, SqliteFuzzAssertions.requireOwnedStore(store));
    }
  }

  @Test
  void sqliteAssertions_rewrite_deterministic_key_file_when_secure_file_already_exists()
      throws Exception {
    Path parentDirectory = createOwnerOnlyArtifactDirectory("existing-key-parent");
    Path keyFile = parentDirectory.resolve("entity.book-key");

    SqliteFuzzAssertions.writeDeterministicBookKeyFile(keyFile);
    String firstWrite = Files.readString(keyFile, UTF_8);

    SqliteFuzzAssertions.writeDeterministicBookKeyFile(keyFile);
    String secondWrite = Files.readString(keyFile, UTF_8);

    assertEquals("fingrind-jazzer-book-key", firstWrite);
    assertEquals(firstWrite, secondWrite);
  }

  @Test
  void sqliteAssertions_reject_key_file_writes_beneath_missing_parents() {
    Path keyFile = tempDirectory.resolve("missing-artifacts").resolve("entity.book-key");

    assertThrows(
        PrivateOutputDirectory.Violation.class,
        () -> SqliteFuzzAssertions.writeDeterministicBookKeyFile(keyFile));
  }

  @Test
  void sqliteAssertions_writeNewOwnerOnlyFixturePassphraseFile_forcesBoundedNewSecret()
      throws Exception {
    Path parentDirectory = createOwnerOnlyArtifactDirectory("passphrase-parent");
    Path passphraseFile = parentDirectory.resolve("founder.passphrase");

    SqliteFuzzAssertions.writeNewOwnerOnlyFixturePassphraseFile(passphraseFile, "fixture-secret");

    assertEquals("fixture-secret", Files.readString(passphraseFile, UTF_8));
    assertThrows(
        FileAlreadyExistsException.class,
        () ->
            SqliteFuzzAssertions.writeNewOwnerOnlyFixturePassphraseFile(
                passphraseFile, "second-secret"));
  }

  @Test
  void sqliteAssertions_writeNewOwnerOnlyFixturePassphraseFile_rejectsOversizedContent()
      throws Exception {
    Path parentDirectory = createOwnerOnlyArtifactDirectory("oversized-passphrase-parent");
    Path passphraseFile = parentDirectory.resolve("founder.passphrase");
    String oversizedPassphrase =
        "x".repeat(ProtocolInteractionLimits.BOOK_PASSPHRASE_MAX_UTF8_BYTES + 1);

    assertThrows(
        java.io.IOException.class,
        () ->
            SqliteFuzzAssertions.writeNewOwnerOnlyFixturePassphraseFile(
                passphraseFile, oversizedPassphrase));
    assertFalse(Files.exists(passphraseFile));
  }

  @Test
  void sqliteAssertions_createOwnerOnlyArtifactDirectory_createsOnceAndValidatesTheResult()
      throws Exception {
    Path artifactDirectory = createOwnerOnlyArtifactDirectory("new-artifacts");

    assertEquals(
        artifactDirectory.toAbsolutePath().normalize(),
        SqliteFuzzAssertions.requireOwnerOnlyArtifactDirectory(artifactDirectory));
    assertThrows(
        FileAlreadyExistsException.class,
        () -> SqliteFuzzAssertions.createOwnerOnlyArtifactDirectory(artifactDirectory));
  }

  @Test
  void
      sqliteAssertions_requireOwnerOnlyArtifactDirectory_rejectsExistingInsecureDirectoriesWithoutRepair()
      throws Exception {
    assumePosixDirectoryCreationSupported();
    Set<PosixFilePermission> insecurePermissions =
        PosixFilePermissions.fromString("rwxr-xr-x");
    Path artifactDirectory = tempDirectory.resolve("existing-insecure-artifacts");
    Files.createDirectory(
        artifactDirectory, PosixFilePermissions.asFileAttribute(insecurePermissions));

    assertThrows(
        PrivateOutputDirectory.Violation.class,
        () -> SqliteFuzzAssertions.requireOwnerOnlyArtifactDirectory(artifactDirectory));
    assertEquals(
        insecurePermissions, Files.getPosixFilePermissions(artifactDirectory));
  }

  @Test
  void sqliteAssertions_requireOwnerOnlyArtifactDirectory_rejects_missing_and_non_directory_paths()
      throws Exception {
    Path plainFile = tempDirectory.resolve("not-a-directory");
    Files.writeString(plainFile, "plain file", UTF_8);

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> SqliteFuzzAssertions.requireOwnerOnlyArtifactDirectory(plainFile));

    assertTrue(String.valueOf(exception.getMessage()).contains("existing real directory"));
    assertThrows(
        PrivateOutputDirectory.Violation.class,
        () ->
            SqliteFuzzAssertions.requireOwnerOnlyArtifactDirectory(
                tempDirectory.resolve("missing-artifact-directory")));
  }

  private Path createOwnerOnlyArtifactDirectory(String directoryName) throws Exception {
    assumePosixDirectoryCreationSupported();
    return SqliteFuzzAssertions.createOwnerOnlyArtifactDirectory(
        tempDirectory.resolve(directoryName));
  }

  private void assumePosixDirectoryCreationSupported() {
    Assumptions.assumeTrue(
        Files.getFileAttributeView(tempDirectory, PosixFileAttributeView.class) != null,
        "This assertion requires POSIX owner-only directory creation.");
  }

  private static String basicValidRequest() {
    return CliFuzzHarnessTestSupport.cashRevenueRequestJson(
        new CliFuzzHarnessTestSupport.CashRevenueRequestInput(
            "2026-04-07",
            "1000",
            "2000",
            "EUR",
            "1000",
            new CliFuzzHarnessTestSupport.RequestContext(
                "document-idem-1",
                "cash-receipt",
                "2026-04-07",
                "command-1",
                "idem-1",
                "cause-1",
                null)));
  }
}
