package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.sqlite.secret.SqliteBookPassphrase;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Tracks mutable in-memory SQLite session-state transitions for one lifecycle. */
class SqliteStoreSessionStateTracker {
  private final SqliteSessionSecret sessionSecret;
  private SqliteStoreSessionState sessionState = new SqliteIdleStoreSession(null);

  SqliteStoreSessionStateTracker(SqliteSessionSecret sessionSecret) {
    this.sessionSecret = Objects.requireNonNull(sessionSecret, "sessionSecret");
  }

  final void cacheState(SqliteBookStateSnapshot snapshot) {
    sessionState =
        switch (sessionState) {
          case SqliteIdleStoreSession ignored ->
              new SqliteIdleStoreSession(Objects.requireNonNull(snapshot, "snapshot"));
          case SqliteOpenedStoreSession opened ->
              new SqliteOpenedStoreSession(
                  opened.database(), Objects.requireNonNull(snapshot, "snapshot"));
          case SqliteFailedStoreSession failed ->
              new SqliteFailedStoreSession(
                  failed.database(),
                  Objects.requireNonNull(snapshot, "snapshot"),
                  failed.failure());
          case SqliteClosedStoreSession closed -> closed;
        };
  }

  final void clearCachedState() {
    sessionState =
        switch (sessionState) {
          case SqliteIdleStoreSession ignored -> new SqliteIdleStoreSession(null);
          case SqliteOpenedStoreSession opened ->
              new SqliteOpenedStoreSession(opened.database(), null);
          case SqliteFailedStoreSession failed ->
              new SqliteFailedStoreSession(failed.database(), null, failed.failure());
          case SqliteClosedStoreSession closed -> closed;
        };
  }

  final void clearDatabaseState() {
    sessionState =
        switch (sessionState) {
          case SqliteIdleStoreSession ignored -> new SqliteIdleStoreSession(null);
          case SqliteOpenedStoreSession ignored -> new SqliteIdleStoreSession(null);
          case SqliteFailedStoreSession failed ->
              new SqliteFailedStoreSession(null, null, failed.failure());
          case SqliteClosedStoreSession closed -> closed;
        };
  }

  final void publishDatabase(SqliteNativeDatabase activeDatabase) {
    SqliteNativeDatabase publishedDatabase =
        Objects.requireNonNull(activeDatabase, "activeDatabase");
    sessionState =
        switch (sessionState) {
          case SqliteIdleStoreSession idle ->
              new SqliteOpenedStoreSession(publishedDatabase, idle.cachedBookState());
          case SqliteOpenedStoreSession opened ->
              new SqliteOpenedStoreSession(publishedDatabase, opened.cachedBookState());
          case SqliteFailedStoreSession failed ->
              new SqliteFailedStoreSession(
                  publishedDatabase, failed.cachedBookState(), failed.failure());
          case SqliteClosedStoreSession closed -> closed;
        };
  }

  final void rotateSessionSecret(SqliteBookPassphrase replacementPassphrase) {
    sessionSecret.rotateTo(replacementPassphrase);
  }

  final @Nullable SqliteNativeDatabase publishedDatabase() {
    return switch (sessionState) {
      case SqliteIdleStoreSession ignored -> null;
      case SqliteOpenedStoreSession opened -> opened.database();
      case SqliteFailedStoreSession failed -> failed.database();
      case SqliteClosedStoreSession ignored -> null;
    };
  }

  final boolean closed() {
    return sessionState instanceof SqliteClosedStoreSession;
  }

  final @Nullable SqliteBookStateSnapshot cachedBookState() {
    return switch (sessionState) {
      case SqliteIdleStoreSession idle -> idle.cachedBookState();
      case SqliteOpenedStoreSession opened -> opened.cachedBookState();
      case SqliteFailedStoreSession failed -> failed.cachedBookState();
      case SqliteClosedStoreSession ignored -> null;
    };
  }

  final void ensureOpen() {
    if (sessionState instanceof SqliteFailedStoreSession failed) {
      throw failed.failure();
    }
    if (sessionState instanceof SqliteClosedStoreSession closed) {
      IllegalStateException closeFailure = closed.closeFailure();
      if (closeFailure != null) {
        throw closeFailure;
      }
      throw new IllegalStateException("SQLite book session is already closed.");
    }
  }

  final @Nullable SqliteNativeDatabase detachPublishedDatabase() {
    SqliteNativeDatabase detachedDatabase = publishedDatabase();
    sessionState =
        switch (sessionState) {
          case SqliteIdleStoreSession ignored -> new SqliteIdleStoreSession(null);
          case SqliteOpenedStoreSession ignored -> new SqliteIdleStoreSession(null);
          case SqliteFailedStoreSession failed ->
              new SqliteFailedStoreSession(null, null, failed.failure());
          case SqliteClosedStoreSession closed -> closed;
        };
    return detachedDatabase;
  }

  final IllegalStateException rememberTerminalFailure(IllegalStateException failure) {
    IllegalStateException rememberedFailure = Objects.requireNonNull(failure, "failure");
    sessionState =
        switch (sessionState) {
          case SqliteIdleStoreSession idle ->
              new SqliteFailedStoreSession(null, idle.cachedBookState(), rememberedFailure);
          case SqliteOpenedStoreSession opened ->
              new SqliteFailedStoreSession(
                  opened.database(), opened.cachedBookState(), rememberedFailure);
          case SqliteFailedStoreSession failed ->
              new SqliteFailedStoreSession(
                  failed.database(), failed.cachedBookState(), rememberedFailure);
          case SqliteClosedStoreSession ignored -> new SqliteClosedStoreSession(rememberedFailure);
        };
    return rememberedFailure;
  }

  final ContractFailureException rememberedRejectedFailure(ContractFailure failure) {
    Objects.requireNonNull(failure, "failure");
    if (sessionState instanceof SqliteFailedStoreSession failed
        && failed.failure() instanceof ContractFailureException stored) {
      return stored;
    }
    return new ContractFailureException(failure);
  }

  final void markClosed(@Nullable IllegalStateException closeFailure) {
    sessionState = new SqliteClosedStoreSession(closeFailure);
  }

  final SqliteSessionSecret sessionSecret() {
    return sessionSecret;
  }
}
