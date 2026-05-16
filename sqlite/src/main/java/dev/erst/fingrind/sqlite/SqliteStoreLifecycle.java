package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Owns the mutable database/session state for one SQLite-backed book session. */
class SqliteStoreLifecycle {
  private final SqliteStoreContext context;
  private final SqliteSessionSecret sessionSecret;
  private final SqliteThreadOwner threadOwner = new SqliteThreadOwner("SQLite book session");
  private final SqliteLedgerPlanTransactionCoordinator ledgerPlanTransactions;

  private SessionState sessionState = new IdleSession(null);

  SqliteStoreLifecycle(SqliteStoreContext context, SqliteSessionSecret sessionSecret) {
    this.context = Objects.requireNonNull(context, "context");
    this.sessionSecret = Objects.requireNonNull(sessionSecret, "sessionSecret");
    this.ledgerPlanTransactions = new SqliteLedgerPlanTransactionCoordinator(context);
  }

  void beginLedgerPlanTransaction() {
    threadOwner.requireOwnerThread();
    ensureOpen();
    ledgerPlanTransactions.begin(this::database, this::cleanupCreatedMissingBookArtifacts);
  }

  void commitLedgerPlanTransaction() {
    threadOwner.requireOwnerThread();
    ensureOpen();
    ledgerPlanTransactions.commit(this::database);
  }

  void rollbackLedgerPlanTransaction() {
    threadOwner.requireOwnerThread();
    ledgerPlanTransactions.rollback(
        publishedDatabaseValue(), this::cleanupCreatedMissingBookArtifacts);
  }

  void close() {
    threadOwner.requireOwnerThread();
    if (sessionState instanceof ClosedSession) {
      return;
    }
    SqliteNativeDatabase closingDatabase = publishedDatabaseValue();
    SqliteSessionSecret closingSecret = sessionSecret;
    boolean cleanupCreatedBookArtifacts = ledgerPlanTransactions.createdBookArtifacts();
    @Nullable Path preexistingAncestorDirectory =
        ledgerPlanTransactions.preexistingAncestorDirectory();
    sessionState = new ClosedSession(null);
    try (closingSecret;
        closingDatabase) {
      // Resources close on scope exit; any close failure poisons this session permanently.
    } catch (SqliteNativeException exception) {
      String detail = Objects.requireNonNullElse(exception.getMessage(), "SQLite native failure.");
      SqliteStorageFailureException closeFailure =
          new SqliteStorageFailureException(
              "Failed to close SQLite book connection. " + exception.resultName() + ": " + detail,
              exception);
      sessionState = new ClosedSession(closeFailure);
      ledgerPlanTransactions.reset();
      throw closeFailure;
    } catch (IllegalStateException exception) {
      IllegalStateException closeFailure =
          new IllegalStateException("Failed to close SQLite book connection.", exception);
      sessionState = new ClosedSession(closeFailure);
      ledgerPlanTransactions.reset();
      throw closeFailure;
    }
    try {
      if (cleanupCreatedBookArtifacts) {
        SqliteLedgerPlanArtifactCleanup.cleanupCreatedMissingBookArtifacts(
            context.bookPath(), preexistingAncestorDirectory, null);
      }
    } finally {
      ledgerPlanTransactions.reset();
    }
  }

  boolean isInitializedBook(SqliteNativeDatabase activeDatabase) {
    threadOwner.requireOwnerThread();
    return stateSnapshot(activeDatabase).state() == SqliteBookState.INITIALIZED_FINGRIND;
  }

  void requireInitializedBook(SqliteNativeDatabase activeDatabase) {
    threadOwner.requireOwnerThread();
    SqliteBookStateSnapshot snapshot = stateSnapshot(activeDatabase);
    snapshot
        .state()
        .requireInitialized(
            snapshot.userVersion(),
            context.bookFormatVersion(),
            context.notInitializedBookMessage());
  }

  SqliteBookStateSnapshot stateSnapshot(SqliteNativeDatabase activeDatabase) {
    threadOwner.requireOwnerThread();
    SqliteBookStateSnapshot snapshot = cachedBookState();
    if (snapshot != null) {
      return snapshot;
    }
    snapshot = context.bookStateReader().snapshot(activeDatabase);
    cacheState(snapshot);
    return snapshot;
  }

  SqliteNativeDatabase database() {
    threadOwner.requireOwnerThread();
    ensureOpen();
    SqliteNativeDatabase activeDatabase = publishedDatabaseValue();
    if (activeDatabase != null) {
      return activeDatabase;
    }
    ContractDecision<SqliteNativeDatabase> openedDatabase = openDatabase();
    return switch (openedDatabase) {
      case ContractDecision.Accepted<SqliteNativeDatabase>(SqliteNativeDatabase resolvedDatabase) ->
          resolvedDatabase;
      case ContractDecision.Rejected<SqliteNativeDatabase>(ContractFailure failure) ->
          throw rememberedRejectedFailure(failure);
    };
  }

  ContractDecision<SqliteStoreLifecycle> prime() {
    threadOwner.requireOwnerThread();
    ensureOpen();
    if (publishedDatabaseValue() != null) {
      return ContractDecision.accepted(this);
    }
    if (context.accessMode().defersMissingBookOpen() && Files.notExists(context.bookPath())) {
      return ContractDecision.accepted(this);
    }
    return openDatabase()
        .fold(ignored -> ContractDecision.accepted(this), ContractDecision::rejected);
  }

  void ensureOpenSession() {
    threadOwner.requireOwnerThread();
    ensureOpen();
  }

  SqliteNativeDatabase initializedQueryDatabase() {
    threadOwner.requireOwnerThread();
    if (Files.notExists(context.bookPath())) {
      throw new IllegalStateException(context.notInitializedBookMessage());
    }
    SqliteNativeDatabase activeDatabase = database();
    requireInitializedBook(activeDatabase);
    return activeDatabase;
  }

  SqliteTransactionOwnership beginImmediateIfNeeded(SqliteNativeDatabase activeDatabase) {
    threadOwner.requireOwnerThread();
    if (ledgerPlanTransactions.active()) {
      ledgerPlanTransactions.beginImmediateIfNeeded(activeDatabase);
      return SqliteTransactionOwnership.SHARED;
    }
    activeDatabase.executeStatement("begin immediate");
    return SqliteTransactionOwnership.OWNED;
  }

  void cacheState(SqliteBookStateSnapshot snapshot) {
    threadOwner.requireOwnerThread();
    sessionState =
        switch (sessionState) {
          case IdleSession ignored -> new IdleSession(Objects.requireNonNull(snapshot, "snapshot"));
          case OpenedSession opened ->
              new OpenedSession(opened.database(), Objects.requireNonNull(snapshot, "snapshot"));
          case FailedSession failed ->
              new FailedSession(
                  failed.database(),
                  Objects.requireNonNull(snapshot, "snapshot"),
                  failed.failure());
          case ClosedSession closed -> closed;
        };
  }

  void clearCachedState() {
    threadOwner.requireOwnerThread();
    sessionState =
        switch (sessionState) {
          case IdleSession ignored -> new IdleSession(null);
          case OpenedSession opened -> new OpenedSession(opened.database(), null);
          case FailedSession failed -> new FailedSession(failed.database(), null, failed.failure());
          case ClosedSession closed -> closed;
        };
  }

  void clearDatabaseState() {
    threadOwner.requireOwnerThread();
    sessionState =
        switch (sessionState) {
          case IdleSession ignored -> new IdleSession(null);
          case OpenedSession ignored -> new IdleSession(null);
          case FailedSession failed -> new FailedSession(null, null, failed.failure());
          case ClosedSession closed -> closed;
        };
  }

  void publishDatabase(SqliteNativeDatabase activeDatabase) {
    threadOwner.requireOwnerThread();
    SqliteNativeDatabase publishedDatabase =
        Objects.requireNonNull(activeDatabase, "activeDatabase");
    sessionState =
        switch (sessionState) {
          case IdleSession idle -> new OpenedSession(publishedDatabase, idle.cachedBookState());
          case OpenedSession opened ->
              new OpenedSession(publishedDatabase, opened.cachedBookState());
          case FailedSession failed ->
              new FailedSession(publishedDatabase, failed.cachedBookState(), failed.failure());
          case ClosedSession closed -> closed;
        };
  }

  void rotateSessionSecret(SqliteBookPassphrase replacementPassphrase) {
    threadOwner.requireOwnerThread();
    sessionSecret.rotateTo(replacementPassphrase);
  }

  @Nullable SqliteNativeDatabase publishedDatabase() {
    threadOwner.requireOwnerThread();
    return publishedDatabaseValue();
  }

  boolean closed() {
    threadOwner.requireOwnerThread();
    return sessionState instanceof ClosedSession;
  }

  boolean ledgerPlanTransactionActive() {
    threadOwner.requireOwnerThread();
    return ledgerPlanTransactions.active();
  }

  boolean ledgerPlanTransactionBegunInDatabase() {
    threadOwner.requireOwnerThread();
    return ledgerPlanTransactions.begunInDatabase();
  }

  private ContractDecision<SqliteNativeDatabase> openDatabase() {
    threadOwner.requireOwnerThread();
    try (SqliteOwnedPassphrase workingPassphrase = sessionSecret.borrowWorkingCopy()) {
      if (context.accessMode().createsFiles() && Files.notExists(context.bookPath())) {
        ledgerPlanTransactions.noteBookArtifactsMayMutate();
        SqliteBookSchemaBootstrap.ensureParentDirectory(context.bookPath());
      }
      SqliteNativeDatabase openedDatabase =
          context.openConfiguredDatabase(workingPassphrase.nativePassphrase());
      publishDatabase(openedDatabase);
      ledgerPlanTransactions.beginImmediateIfNeeded(openedDatabase);
      return ContractDecision.accepted(openedDatabase);
    } catch (SqliteNativeException exception) {
      Optional<ContractFailure> authenticationFailure =
          SqliteStoreOperations.protectedBookVerificationFailure(exception);
      if (authenticationFailure.isPresent()) {
        rememberTerminalFailure(new ContractFailureException(authenticationFailure.orElseThrow()));
        return ContractDecision.rejected(authenticationFailure.orElseThrow());
      }
      throw rememberTerminalFailure(SqliteStoreOperations.openRuntimeFailure(exception));
    } catch (IllegalStateException exception) {
      throw rememberTerminalFailure(exception);
    }
  }

  private void ensureOpen() {
    threadOwner.requireOwnerThread();
    if (sessionState instanceof FailedSession failed) {
      throw failed.failure();
    }
    if (sessionState instanceof ClosedSession closed) {
      IllegalStateException closeFailure = closed.closeFailure();
      if (closeFailure != null) {
        throw closeFailure;
      }
      throw new IllegalStateException("SQLite book session is already closed.");
    }
  }

  void cleanupCreatedMissingBookArtifactsIfPresent() {
    threadOwner.requireOwnerThread();
    if (!ledgerPlanTransactions.createdBookArtifacts()) {
      return;
    }
    cleanupCreatedMissingBookArtifacts(ledgerPlanTransactions.preexistingAncestorDirectory());
  }

  private @Nullable SqliteNativeDatabase detachPublishedDatabase() {
    threadOwner.requireOwnerThread();
    SqliteNativeDatabase detachedDatabase = publishedDatabaseValue();
    sessionState =
        switch (sessionState) {
          case IdleSession ignored -> new IdleSession(null);
          case OpenedSession ignored -> new IdleSession(null);
          case FailedSession failed -> new FailedSession(null, null, failed.failure());
          case ClosedSession closed -> closed;
        };
    return detachedDatabase;
  }

  private void cleanupCreatedMissingBookArtifacts(@Nullable Path preexistingAncestorDirectory) {
    SqliteLedgerPlanArtifactCleanup.cleanupCreatedMissingBookArtifacts(
        context.bookPath(), preexistingAncestorDirectory, detachPublishedDatabase());
  }

  private @Nullable SqliteBookStateSnapshot cachedBookState() {
    return switch (sessionState) {
      case IdleSession idle -> idle.cachedBookState();
      case OpenedSession opened -> opened.cachedBookState();
      case FailedSession failed -> failed.cachedBookState();
      case ClosedSession ignored -> null;
    };
  }

  private @Nullable SqliteNativeDatabase publishedDatabaseValue() {
    return switch (sessionState) {
      case IdleSession ignored -> null;
      case OpenedSession opened -> opened.database();
      case FailedSession failed -> failed.database();
      case ClosedSession ignored -> null;
    };
  }

  private IllegalStateException rememberTerminalFailure(IllegalStateException failure) {
    IllegalStateException rememberedFailure = Objects.requireNonNull(failure, "failure");
    sessionState =
        switch (sessionState) {
          case IdleSession idle ->
              new FailedSession(null, idle.cachedBookState(), rememberedFailure);
          case OpenedSession opened ->
              new FailedSession(opened.database(), opened.cachedBookState(), rememberedFailure);
          case FailedSession failed ->
              new FailedSession(failed.database(), failed.cachedBookState(), rememberedFailure);
          case ClosedSession ignored -> new ClosedSession(rememberedFailure);
        };
    return rememberedFailure;
  }

  private ContractFailureException rememberedRejectedFailure(ContractFailure failure) {
    Objects.requireNonNull(failure, "failure");
    if (sessionState instanceof FailedSession failed
        && failed.failure() instanceof ContractFailureException stored) {
      return stored;
    }
    return new ContractFailureException(failure);
  }

  /** Internal session-state model for a single store lifecycle instance. */
  private sealed interface SessionState
      permits IdleSession, OpenedSession, FailedSession, ClosedSession {}

  /** Session state before a database handle has been opened. */
  private record IdleSession(@Nullable SqliteBookStateSnapshot cachedBookState)
      implements SessionState {}

  /** Session state with a live database handle. */
  private record OpenedSession(
      SqliteNativeDatabase database, @Nullable SqliteBookStateSnapshot cachedBookState)
      implements SessionState {
    private OpenedSession {
      Objects.requireNonNull(database, "database");
    }
  }

  /** Session state after a terminal lifecycle failure has been recorded. */
  private record FailedSession(
      @Nullable SqliteNativeDatabase database,
      @Nullable SqliteBookStateSnapshot cachedBookState,
      IllegalStateException failure)
      implements SessionState {
    private FailedSession {
      Objects.requireNonNull(failure, "failure");
    }
  }

  /** Session state after the lifecycle has been closed. */
  private record ClosedSession(@Nullable IllegalStateException closeFailure)
      implements SessionState {}
}
