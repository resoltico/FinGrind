package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Coverage proofs for the split SQLite session-factory and passphrase-resolution seams. */
class SqliteSessionFactoryCoverageTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void passphraseResolverDefaultOverload_forwardsTheBookAccessTuple() {
    Path bookPath = tempDirectory.resolve("passphrase-resolver-default.sqlite");
    BookAccess bookAccess = bookAccess(bookPath);
    AtomicReference<Path> resolvedBookPath = new AtomicReference<>();
    AtomicReference<BookAccess.PassphraseSource> resolvedSource = new AtomicReference<>();
    AtomicReference<SqlitePassphraseIntent> resolvedIntent = new AtomicReference<>();
    SqlitePassphraseResolver resolver =
        (candidateBookPath, passphraseSource, intent) -> {
          resolvedBookPath.set(candidateBookPath);
          resolvedSource.set(passphraseSource);
          resolvedIntent.set(intent);
          return SqliteBookKeyFile.loadDecision(
              SqliteStoreFixtureSupport.requireKeyFilePath(bookAccess));
        };

    try (SqliteBookPassphrase passphrase =
        resolver.resolve(bookAccess, SqlitePassphraseIntent.EXISTING_SECRET).requireAccepted()) {
      assertEquals(bookPath, resolvedBookPath.get());
      assertEquals(bookAccess.passphraseSource(), resolvedSource.get());
      assertEquals(SqlitePassphraseIntent.EXISTING_SECRET, resolvedIntent.get());
      assertEquals(TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8).length, passphrase.byteLength());
    }
  }

  @Test
  void bookSessionFactories_coverAllAccessModesAndCapabilityLookupBranches() {
    Path existingBookPath = tempDirectory.resolve("book-sessions-existing.sqlite");
    initializeBookOnDisk(existingBookPath);
    BookAccess existingBookAccess = bookAccess(existingBookPath);
    SqlitePassphraseResolver resolver = keyFileResolver();

    try (SqliteBookPassphrase passphrase = loadPassphrase(existingBookAccess);
        SqlitePostingFactStore createModeStore =
            SqliteBookSessions.openStore(existingBookPath, passphrase)) {
      assertEquals(SqliteStoreAccessMode.READ_WRITE_CREATE, createModeStore.accessMode());
      assertSame(createModeStore, SqliteCapabilitySessions.storeOf(createModeStore));
      assertTrue(createModeStore.inspectBook().initialized());
    }

    try (SqliteBookPassphrase passphrase = loadPassphrase(existingBookAccess);
        SqlitePostingFactStore readOnlyStore =
            SqliteBookSessions.openStore(
                existingBookPath, passphrase, SqliteBookSessionMode.READ_ONLY);
        SqliteAdministrationSession administrationSession =
            SqliteCapabilitySessions.administration(readOnlyStore)) {
      assertEquals(SqliteStoreAccessMode.READ_ONLY, readOnlyStore.accessMode());
      assertSame(readOnlyStore, SqliteCapabilitySessions.storeOf(administrationSession));
      assertTrue(administrationSession.inspectBook().initialized());
    }

    try (SqlitePostingFactStore existingModeStore =
        SqliteBookSessions.openResolvedStore(
                existingBookAccess,
                SqliteBookSessionMode.READ_WRITE_EXISTING,
                resolver,
                SqlitePassphraseIntent.EXISTING_SECRET)
            .requireAccepted()) {
      assertEquals(SqliteStoreAccessMode.READ_WRITE_EXISTING, existingModeStore.accessMode());
      assertTrue(existingModeStore.inspectBook().initialized());
    }

    Path missingPlanBookPath = tempDirectory.resolve("book-sessions-plan.sqlite");
    BookAccess missingPlanBookAccess = bookAccess(missingPlanBookPath);
    try (SqlitePostingFactStore planExecutionStore =
        SqliteBookSessions.openStore(
            missingPlanBookAccess,
            SqliteBookSessionMode.PLAN_EXECUTION,
            resolver,
            SqlitePassphraseIntent.EXISTING_SECRET)) {
      assertEquals(SqliteStoreAccessMode.PLAN_EXECUTION, planExecutionStore.accessMode());
      assertFalse(Files.exists(missingPlanBookPath));
      assertFalse(planExecutionStore.inspectBook().initialized());
    }
    assertFalse(Files.exists(missingPlanBookPath));
  }

  @Test
  void capabilitySpecificSessionFactories_openFilesystemAndLogicalAccessVariants() {
    Path bookPath = tempDirectory.resolve("session-factories.sqlite");
    initializeBookOnDisk(bookPath);
    BookAccess bookAccess = bookAccess(bookPath);
    SqlitePassphraseResolver resolver = keyFileResolver();

    try (SqliteBookPassphrase passphrase = loadPassphrase(bookAccess);
        SqliteAdministrationSession session =
            SqliteAdministrationSessions.open(bookPath, passphrase)) {
      assertTrue(session.inspectBook().initialized());
    }
    try (SqliteBookPassphrase passphrase = loadPassphrase(bookAccess);
        SqliteAdministrationSession session =
            SqliteAdministrationSessions.open(
                bookPath, passphrase, SqliteBookSessionMode.READ_WRITE_EXISTING)) {
      assertTrue(session.inspectBook().initialized());
    }
    try (SqliteAdministrationSession session =
        SqliteAdministrationSessions.open(
            bookAccess,
            SqliteBookSessionMode.READ_WRITE_EXISTING,
            resolver,
            SqlitePassphraseIntent.EXISTING_SECRET)) {
      assertTrue(session.inspectBook().initialized());
    }
    try (SqliteAdministrationSession session =
        SqliteAdministrationSessions.openResolved(
                bookAccess,
                SqliteBookSessionMode.READ_WRITE_EXISTING,
                resolver,
                SqlitePassphraseIntent.EXISTING_SECRET)
            .requireAccepted()) {
      assertTrue(session.inspectBook().initialized());
    }

    try (SqliteBookPassphrase passphrase = loadPassphrase(bookAccess);
        SqlitePostingSession session = SqlitePostingSessions.open(bookPath, passphrase)) {
      assertTrue(session.inspectBook().initialized());
    }
    try (SqliteBookPassphrase passphrase = loadPassphrase(bookAccess);
        SqlitePostingSession session =
            SqlitePostingSessions.open(
                bookPath, passphrase, SqliteBookSessionMode.READ_WRITE_EXISTING)) {
      assertTrue(session.inspectBook().initialized());
    }
    try (SqlitePostingSession session =
        SqlitePostingSessions.open(
            bookAccess,
            SqliteBookSessionMode.READ_WRITE_EXISTING,
            resolver,
            SqlitePassphraseIntent.EXISTING_SECRET)) {
      assertTrue(session.inspectBook().initialized());
    }
    try (SqlitePostingSession session =
        SqlitePostingSessions.openResolved(
                bookAccess,
                SqliteBookSessionMode.READ_WRITE_EXISTING,
                resolver,
                SqlitePassphraseIntent.EXISTING_SECRET)
            .requireAccepted()) {
      assertTrue(session.inspectBook().initialized());
    }

    try (SqliteBookPassphrase passphrase = loadPassphrase(bookAccess);
        SqliteReadSession session = SqliteReadSessions.open(bookPath, passphrase)) {
      assertTrue(session.inspectBook().initialized());
    }
    try (SqliteReadSession session =
        SqliteReadSessions.open(bookAccess, resolver, SqlitePassphraseIntent.EXISTING_SECRET)) {
      assertTrue(session.inspectBook().initialized());
    }
    try (SqliteReadSession session =
        SqliteReadSessions.openResolved(
                bookAccess, resolver, SqlitePassphraseIntent.EXISTING_SECRET)
            .requireAccepted()) {
      assertTrue(session.inspectBook().initialized());
    }

    try (SqliteReportingPeriodCloseSession session =
        SqliteReportingPeriodCloseSessions.open(
            bookAccess, resolver, SqlitePassphraseIntent.EXISTING_SECRET)) {
      assertTrue(session.inspectBook().initialized());
    }
    try (SqliteReportingPeriodCloseSession session =
        SqliteReportingPeriodCloseSessions.openResolved(
                bookAccess, resolver, SqlitePassphraseIntent.EXISTING_SECRET)
            .requireAccepted()) {
      assertTrue(session.inspectBook().initialized());
    }

    try (SqlitePlanExecutionSession session =
        SqlitePlanExecutionSessions.open(
            bookAccess, resolver, SqlitePassphraseIntent.EXISTING_SECRET)) {
      assertTrue(session.inspectBook().initialized());
      session.beginLedgerPlanTransaction();
      session.rollbackLedgerPlanTransaction();
    }
    try (SqlitePlanExecutionSession session =
        SqlitePlanExecutionSessions.openResolved(
                bookAccess, resolver, SqlitePassphraseIntent.EXISTING_SECRET)
            .requireAccepted()) {
      assertTrue(session.inspectBook().initialized());
    }

    try (SqliteRekeySession session =
        SqliteRekeySessions.open(bookAccess, resolver, SqlitePassphraseIntent.EXISTING_SECRET)) {
      assertNotNull(session);
    }
    try (SqliteRekeySession session =
        SqliteRekeySessions.openResolved(
                bookAccess, resolver, SqlitePassphraseIntent.EXISTING_SECRET)
            .requireAccepted()) {
      assertNotNull(session);
    }
  }

  private static SqlitePassphraseResolver keyFileResolver() {
    return (bookFilePath, passphraseSource, intent) ->
        switch (passphraseSource) {
          case BookAccess.PassphraseSource.KeyFile keyFile ->
              SqliteBookKeyFile.loadDecision(keyFile.bookKeyFilePath());
          case BookAccess.PassphraseSource.StandardInput source ->
              ContractDecision.rejected(unsupportedPassphraseSource(source));
          case BookAccess.PassphraseSource.InteractivePrompt source ->
              ContractDecision.rejected(unsupportedPassphraseSource(source));
        };
  }

  private static dev.erst.fingrind.contract.runtime.ContractFailure unsupportedPassphraseSource(
      BookAccess.PassphraseSource source) {
    return ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.failure(
        "SQLite same-package file-backed stores require a "
            + ProtocolOptions.BOOK_KEY_FILE
            + " access selection, not "
            + source.optionName()
            + ".",
        null,
        null);
  }
}
