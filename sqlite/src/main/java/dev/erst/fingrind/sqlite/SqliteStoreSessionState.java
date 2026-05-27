package dev.erst.fingrind.sqlite;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Internal session-state model for a single SQLite-backed store lifecycle. */
sealed interface SqliteStoreSessionState
    permits SqliteIdleStoreSession,
        SqliteOpenedStoreSession,
        SqliteFailedStoreSession,
        SqliteClosedStoreSession {}

/** Session state before a database handle has been opened. */
record SqliteIdleStoreSession(@Nullable SqliteBookStateSnapshot cachedBookState)
    implements SqliteStoreSessionState {}

/** Session state with a live database handle. */
record SqliteOpenedStoreSession(
    SqliteNativeDatabase database, @Nullable SqliteBookStateSnapshot cachedBookState)
    implements SqliteStoreSessionState {
  SqliteOpenedStoreSession {
    Objects.requireNonNull(database, "database");
  }
}

/** Session state after a terminal lifecycle failure has been recorded. */
record SqliteFailedStoreSession(
    @Nullable SqliteNativeDatabase database,
    @Nullable SqliteBookStateSnapshot cachedBookState,
    IllegalStateException failure)
    implements SqliteStoreSessionState {
  SqliteFailedStoreSession {
    Objects.requireNonNull(failure, "failure");
  }
}

/** Session state after the lifecycle has been closed. */
record SqliteClosedStoreSession(@Nullable IllegalStateException closeFailure)
    implements SqliteStoreSessionState {}
