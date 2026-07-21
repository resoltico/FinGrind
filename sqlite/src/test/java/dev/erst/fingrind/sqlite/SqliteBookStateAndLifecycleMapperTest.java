package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Field-tests the complete mapping from durable SQLite headers into book lifecycle meaning. */
class SqliteBookStateAndLifecycleMapperTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void bookStates_enforceTheirInitializationAndOpeningContracts() {
    String notInitialized = "book must be initialized";
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteBookState.BLANK_SQLITE.requireInitialized(
                0, SqliteBookContract.FORMAT_VERSION, notInitialized));
    assertDoesNotThrow(
        () ->
            SqliteBookState.INITIALIZED_FINGRIND.requireInitialized(
                SqliteBookContract.FORMAT_VERSION,
                SqliteBookContract.FORMAT_VERSION,
                notInitialized));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteBookState.FOREIGN_SQLITE.requireInitialized(
                0, SqliteBookContract.FORMAT_VERSION, notInitialized));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteBookState.UNSUPPORTED_FINGRIND_VERSION.requireInitialized(
                99, SqliteBookContract.FORMAT_VERSION, notInitialized));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteBookState.INCOMPLETE_FINGRIND.requireInitialized(
                SqliteBookContract.FORMAT_VERSION,
                SqliteBookContract.FORMAT_VERSION,
                notInitialized));

    assertEquals(Optional.empty(), SqliteBookState.BLANK_SQLITE.openBookResult(0));
    assertInstanceOf(
        BookOpeningOutcome.Rejected.class,
        SqliteBookState.INITIALIZED_FINGRIND
            .openBookResult(SqliteBookContract.FORMAT_VERSION)
            .orElseThrow());
    assertInstanceOf(
        BookOpeningOutcome.Rejected.class,
        SqliteBookState.FOREIGN_SQLITE.openBookResult(0).orElseThrow());
    assertThrows(
        IllegalStateException.class,
        () -> SqliteBookState.UNSUPPORTED_FINGRIND_VERSION.openBookResult(99));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteBookState.INCOMPLETE_FINGRIND.openBookResult(SqliteBookContract.FORMAT_VERSION));
  }

  @Test
  void lifecycleMapper_mapsEveryHeaderStateAndRejectsMissingInitializedMetadata() {
    int formatVersion = SqliteBookContract.FORMAT_VERSION;
    SqliteBookStateSnapshot blank = new SqliteBookStateSnapshot(0, 0, SqliteBookState.BLANK_SQLITE);
    SqliteBookStateSnapshot foreign =
        new SqliteBookStateSnapshot(1, 0, SqliteBookState.FOREIGN_SQLITE);
    SqliteBookStateSnapshot unsupported =
        new SqliteBookStateSnapshot(
            SqliteBookContract.APPLICATION_ID,
            formatVersion + 1,
            SqliteBookState.UNSUPPORTED_FINGRIND_VERSION);
    SqliteBookStateSnapshot incomplete =
        new SqliteBookStateSnapshot(
            SqliteBookContract.APPLICATION_ID, formatVersion, SqliteBookState.INCOMPLETE_FINGRIND);
    assertInstanceOf(
        BookLifecycleInspection.Missing.class,
        SqliteBookLifecycleInspectionMapper.fromMissingPath());
    assertExistingStatus(
        SqliteBookLifecycleInspectionMapper.fromSnapshot(blank, null, null),
        BookLifecycleInspection.Status.BLANK_SQLITE);
    assertExistingStatus(
        SqliteBookLifecycleInspectionMapper.fromSnapshot(foreign, null, null),
        BookLifecycleInspection.Status.FOREIGN_SQLITE);
    assertExistingStatus(
        SqliteBookLifecycleInspectionMapper.fromSnapshot(unsupported, null, null),
        BookLifecycleInspection.Status.UNSUPPORTED_FORMAT_VERSION);
    assertExistingStatus(
        SqliteBookLifecycleInspectionMapper.fromSnapshot(incomplete, null, null),
        BookLifecycleInspection.Status.INCOMPLETE_FINGRIND);

    Path initializedBookPath = tempDirectory.resolve("mapper-initialized.sqlite");
    Instant initializedAt = Instant.parse("2026-07-21T12:00:00Z");
    try (SqlitePostingFactStore store = openStore(bookAccess(initializedBookPath))) {
      store.openAttestedBook(
          initializedAt,
          SqlitePostingFactFixtureSupport.bookIdentity(),
          List.of(),
          SqliteAttestationTestSupport.genesis(
              SqlitePostingFactFixtureSupport.bookIdentity(), initializedAt));
      assertInstanceOf(
          BookLifecycleInspection.Initialized.class,
          SqliteBookLifecycleInspectionMapper.fromSnapshot(
              new SqliteBookStateSnapshot(
                  SqliteBookContract.APPLICATION_ID,
                  formatVersion,
                  SqliteBookState.INITIALIZED_FINGRIND),
              store.activeNativeDatabase()));
    }

    Path incompleteMetadataPath = tempDirectory.resolve("mapper-missing-metadata.sqlite");
    try (SqliteNativeDatabase database = openNativeDatabase(bookAccess(incompleteMetadataPath))) {
      SqliteBookSchemaBootstrap.initializeBook(database);
      SqliteBookStateSnapshot initializedSnapshot =
          new SqliteBookStateSnapshot(
              SqliteBookContract.APPLICATION_ID,
              formatVersion,
              SqliteBookState.INITIALIZED_FINGRIND);
      assertThrows(
          IllegalStateException.class,
          () -> SqliteBookLifecycleInspectionMapper.fromSnapshot(initializedSnapshot, database));
      SqliteStoreFixtureSupport.insertInitializedAtRow(database);
      assertThrows(
          IllegalStateException.class,
          () -> SqliteBookLifecycleInspectionMapper.fromSnapshot(initializedSnapshot, database));
      assertExistingStatus(
          SqliteBookLifecycleInspectionMapper.fromSnapshot(blank, database),
          BookLifecycleInspection.Status.BLANK_SQLITE);
    }
  }

  private static void assertExistingStatus(
      BookLifecycleInspection inspection, BookLifecycleInspection.Status expectedStatus) {
    assertEquals(
        expectedStatus,
        assertInstanceOf(BookLifecycleInspection.Existing.class, inspection).status());
  }
}
