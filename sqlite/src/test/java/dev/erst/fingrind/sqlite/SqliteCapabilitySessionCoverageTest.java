package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferDraft;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferOutcome;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Coverage locks for the narrowed SQLite capability-session and lifecycle helpers. */
class SqliteCapabilitySessionCoverageTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void capabilitySessionsExposeOperationalLifecycleAndUnderlyingStoreContext() {
    Path bookPath = tempDirectory.resolve("capability-session-coverage.sqlite");
    initializeBookOnDisk(bookPath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath));
        SqliteReadSession readSession = SqliteCapabilitySessions.read(postingFactStore);
        SqliteAdministrationSession administrationSession =
            SqliteCapabilitySessions.administration(postingFactStore)) {
      assertTrue(postingFactStore.allowsInitializedWorkflow());
      assertEquals(bookIdentity(), postingFactStore.requireInitializedBookIdentity());

      assertTrue(readSession.allowsInitializedWorkflow());
      assertEquals(bookIdentity(), readSession.requireInitializedBookIdentity());
      assertSame(
          postingFactStore.storeContext(),
          assertInstanceOf(SqliteReadCapabilitySession.class, readSession).storeContext());

      assertTrue(administrationSession.allowsInitializedWorkflow());
      assertEquals(bookIdentity(), administrationSession.requireInitializedBookIdentity());
      assertSame(
          postingFactStore.storeLifecycle(),
          assertInstanceOf(SqliteAdministrationCapabilitySession.class, administrationSession)
              .storeLifecycle());
      assertSame(
          postingFactStore.storeContext(),
          assertInstanceOf(SqliteAdministrationCapabilitySession.class, administrationSession)
              .storeContext());
    }
  }

  @Test
  void periodResultTransferCapabilitySessionCoversDraftHelperAndStoreContext() {
    Path missingBookPath = tempDirectory.resolve("period-transfer-session-coverage.sqlite");
    LocalDate effectiveDate = LocalDate.parse("2026-04-07");
    Instant transferredAt = Instant.parse("2026-04-07T10:15:30Z");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(missingBookPath));
        SqlitePeriodResultTransferSession periodResultTransferSession =
            SqliteCapabilitySessions.periodResultTransfer(postingFactStore)) {
      assertSame(
          postingFactStore.storeContext(),
          assertInstanceOf(
                  SqlitePeriodResultTransferCapabilitySession.class, periodResultTransferSession)
              .storeContext());
      assertEquals(
          new PeriodResultTransferOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          assertInstanceOf(
                  SqlitePeriodResultTransferCapabilitySession.class, periodResultTransferSession)
              .transferPeriodResult(
                  emptyPeriodResultTransferDraft(effectiveDate, transferredAt),
                  unusedPostingIdGenerator()));
    }
  }

  @Test
  void postingFactStoreOperationalLifecycleBranchesRejectBlankBooksAndWrapStaleHandles()
      throws Exception {
    Path blankBookPath = tempDirectory.resolve("operational-lifecycle-blank.sqlite");
    createEmptySqliteFile(blankBookPath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(blankBookPath))) {
      assertFalse(postingFactStore.allowsInitializedWorkflow());
    }

    Path missingIdentityBookPath = tempDirectory.resolve("operational-lifecycle-missing.sqlite");
    createSchemaOnlyBook(missingIdentityBookPath);
    withStandaloneDatabase(
        bookAccess(missingIdentityBookPath), SqliteStoreFixtureSupport::insertInitializedAtRow);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(missingIdentityBookPath))) {
      setStoreCachedBookState(
          postingFactStore,
          new SqliteBookStateSnapshot(
              SqliteBookContract.APPLICATION_ID,
              SqliteBookContract.FORMAT_VERSION,
              SqliteBookState.INITIALIZED_FINGRIND));
      IllegalStateException missingIdentity =
          assertThrows(
              IllegalStateException.class, postingFactStore::requireInitializedBookIdentity);
      assertEquals(
          "Initialized SQLite book is missing book identity.", missingIdentity.getMessage());
    }

    Path staleBookPath = tempDirectory.resolve("operational-lifecycle-stale.sqlite");
    initializeBookOnDisk(staleBookPath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(staleBookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(staleBookPath));
      IllegalStateException workflowFailure =
          assertThrows(IllegalStateException.class, postingFactStore::allowsInitializedWorkflow);
      assertTrue(
          Objects.requireNonNull(workflowFailure.getMessage())
              .contains("Failed to access SQLite book."));
      IllegalStateException identityFailure =
          assertThrows(
              IllegalStateException.class, postingFactStore::requireInitializedBookIdentity);
      assertTrue(
          Objects.requireNonNull(identityFailure.getMessage())
              .contains("Failed to access SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void transactionValidationBookOperationalLifecycleHelpersCoverMissingIdentityAndStaleHandle()
      throws Exception {
    Path missingIdentityBookPath = tempDirectory.resolve("validation-missing-identity.sqlite");
    createSchemaOnlyBook(missingIdentityBookPath);
    withStandaloneDatabase(
        bookAccess(missingIdentityBookPath), SqliteStoreFixtureSupport::insertInitializedAtRow);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(missingIdentityBookPath))) {
      SqliteTransactionValidationBook validationBook =
          new SqliteTransactionValidationBook(
              postingFactStore.activeNativeDatabase(), postingFactStore.postingReader());
      IllegalStateException missingIdentity =
          assertThrows(IllegalStateException.class, validationBook::requireInitializedBookIdentity);
      assertEquals(
          "Initialized SQLite book is missing book identity.", missingIdentity.getMessage());
    }

    Path staleBookPath = tempDirectory.resolve("validation-stale-lifecycle.sqlite");
    initializeBookOnDisk(staleBookPath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(staleBookPath))) {
      SqliteTransactionValidationBook validationBook =
          new SqliteTransactionValidationBook(
              staleDatabaseHandle(staleBookPath), postingFactStore.postingReader());
      IllegalStateException workflowFailure =
          assertThrows(IllegalStateException.class, validationBook::allowsInitializedWorkflow);
      assertTrue(
          Objects.requireNonNull(workflowFailure.getMessage())
              .contains("Failed to access SQLite book."));
      IllegalStateException identityFailure =
          assertThrows(IllegalStateException.class, validationBook::requireInitializedBookIdentity);
      assertTrue(
          Objects.requireNonNull(identityFailure.getMessage())
              .contains("Failed to access SQLite book."));
    }
  }

  private static PostingIdGenerator unusedPostingIdGenerator() {
    return () -> new dev.erst.fingrind.core.PostingId("unused");
  }

  private static PeriodResultTransferDraft emptyPeriodResultTransferDraft(
      LocalDate effectiveDate, Instant transferredAt) {
    return new PeriodResultTransferDraft(
        new ReportingPeriod(effectiveDate, effectiveDate),
        new AccountCode("3200"),
        List.of(),
        transferredAt,
        List.of());
  }
}
