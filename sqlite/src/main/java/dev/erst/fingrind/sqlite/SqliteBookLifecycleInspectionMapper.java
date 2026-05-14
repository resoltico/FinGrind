package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Maps interpreted SQLite header snapshots into the executor-owned lifecycle model. */
final class SqliteBookLifecycleInspectionMapper {
  private SqliteBookLifecycleInspectionMapper() {}

  static BookLifecycleInspection fromMissingPath() {
    return new BookLifecycleInspection.Missing(SqliteBookContract.FORMAT_VERSION);
  }

  static BookLifecycleInspection fromSnapshot(
      SqliteBookStateSnapshot snapshot, SqliteNativeDatabase activeDatabase) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    return fromSnapshot(
        snapshot,
        snapshot.state() == SqliteBookState.INITIALIZED_FINGRIND
            ? SqliteStatementQueries.loadInitializedAt(activeDatabase)
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Initialized SQLite book is missing initialized-at metadata."))
            : null,
        snapshot.state() == SqliteBookState.INITIALIZED_FINGRIND
            ? SqliteStatementQueries.loadBookIdentity(activeDatabase)
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Initialized SQLite book is missing book-identity metadata."))
            : null);
  }

  static BookLifecycleInspection fromSnapshot(
      SqliteBookStateSnapshot snapshot,
      @Nullable Instant initializedAt,
      @Nullable BookIdentity bookIdentity) {
    Objects.requireNonNull(snapshot, "snapshot");
    return switch (snapshot.state()) {
      case BLANK_SQLITE ->
          new BookLifecycleInspection.Existing(
              BookLifecycleInspection.Status.BLANK_SQLITE,
              snapshot.applicationId(),
              snapshot.userVersion(),
              SqliteBookContract.FORMAT_VERSION);
      case INITIALIZED_FINGRIND ->
          new BookLifecycleInspection.Initialized(
              snapshot.applicationId(),
              snapshot.userVersion(),
              SqliteBookContract.FORMAT_VERSION,
              Objects.requireNonNull(
                  initializedAt, "initializedAt is required for initialized SQLite books."),
              Objects.requireNonNull(
                  bookIdentity, "bookIdentity is required for initialized SQLite books."));
      case FOREIGN_SQLITE ->
          new BookLifecycleInspection.Existing(
              BookLifecycleInspection.Status.FOREIGN_SQLITE,
              snapshot.applicationId(),
              snapshot.userVersion(),
              SqliteBookContract.FORMAT_VERSION);
      case UNSUPPORTED_FINGRIND_VERSION ->
          new BookLifecycleInspection.Existing(
              BookLifecycleInspection.Status.UNSUPPORTED_FORMAT_VERSION,
              snapshot.applicationId(),
              snapshot.userVersion(),
              SqliteBookContract.FORMAT_VERSION);
      case INCOMPLETE_FINGRIND ->
          new BookLifecycleInspection.Existing(
              BookLifecycleInspection.Status.INCOMPLETE_FINGRIND,
              snapshot.applicationId(),
              snapshot.userVersion(),
              SqliteBookContract.FORMAT_VERSION);
    };
  }
}
