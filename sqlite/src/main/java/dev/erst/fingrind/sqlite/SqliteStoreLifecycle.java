package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.ContractFailure;
import dev.erst.fingrind.contract.ContractFailureException;
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

  private SessionState sessionState = new IdleSession(null);
  private LedgerPlanTransactionState ledgerPlanTransactionState = new NoLedgerPlanTransaction();

  SqliteStoreLifecycle(SqliteStoreContext context, SqliteSessionSecret sessionSecret) {
    this.context = Objects.requireNonNull(context, "context");
    this.sessionSecret = Objects.requireNonNull(sessionSecret, "sessionSecret");
  }

  void beginLedgerPlanTransaction() {
    threadOwner.requireOwnerThread();
    ensureOpen();
    context.accessMode().requireWritableMutation();
    if (ledgerPlanTransactionState instanceof ActiveLedgerPlanTransaction) {
      throw new IllegalStateException("Ledger plan transaction is already active.");
    }
    boolean missingBookAtStart = Files.notExists(context.bookPath());
    ArtifactCleanupState artifactCleanupState =
        missingBookAtStart
            ? new MissingBookArtifactsPending(
                SqliteLedgerPlanArtifactCleanup.nearestExistingAncestor(context.bookPath()))
            : new NoArtifactCleanup();
    ledgerPlanTransactionState =
        new ActiveLedgerPlanTransaction(new DatabaseTransactionDeferred(), artifactCleanupState);
    if (context.accessMode().defersMissingBookOpen() && missingBookAtStart) {
      return;
    }
    markLedgerPlanBookArtifactsMayMutate();
    SqliteBookSchemaBootstrap.ensureParentDirectory(context.bookPath());
    try {
      database();
    } catch (IllegalStateException exception) {
      try {
        cleanupCreatedMissingBookArtifactsIfPresent();
      } catch (RuntimeException cleanupFailure) {
        exception.addSuppressed(cleanupFailure);
      } finally {
        resetLedgerPlanTransactionState();
      }
      throw exception;
    }
  }

  void commitLedgerPlanTransaction() {
    threadOwner.requireOwnerThread();
    ensureOpen();
    if (!(ledgerPlanTransactionState instanceof ActiveLedgerPlanTransaction activeTransaction)) {
      throw new IllegalStateException("No ledger plan transaction is active.");
    }
    if (!activeTransaction.begunInDatabase()) {
      resetLedgerPlanTransactionState();
      return;
    }
    try {
      database().executeStatement("commit");
      resetLedgerPlanTransactionState();
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(
          "Failed to commit SQLite ledger plan transaction.", exception);
    } catch (IllegalStateException exception) {
      throw new IllegalStateException(
          "Failed to commit SQLite ledger plan transaction.", exception);
    }
  }

  void rollbackLedgerPlanTransaction() {
    threadOwner.requireOwnerThread();
    if (!(ledgerPlanTransactionState instanceof ActiveLedgerPlanTransaction activeTransaction)) {
      return;
    }
    boolean rollbackDatabase = activeTransaction.begunInDatabase();
    boolean cleanupCreatedBookArtifacts = activeTransaction.createdBookArtifacts();
    @Nullable Path preexistingAncestorDirectory = activeTransaction.preexistingAncestorDirectory();
    if (rollbackDatabase && publishedDatabaseValue() != null) {
      SqliteStoreOperations.rollbackQuietly(database());
    }
    try {
      if (cleanupCreatedBookArtifacts) {
        cleanupCreatedMissingBookArtifacts(preexistingAncestorDirectory);
      }
    } finally {
      resetLedgerPlanTransactionState();
    }
  }

  void close() {
    threadOwner.requireOwnerThread();
    if (sessionState instanceof ClosedSession) {
      return;
    }
    SqliteNativeDatabase closingDatabase = publishedDatabaseValue();
    SqliteSessionSecret closingSecret = sessionSecret;
    ActiveLedgerPlanTransaction activeTransaction = activeLedgerPlanTransaction();
    boolean cleanupCreatedBookArtifacts =
        activeTransaction != null && activeTransaction.createdBookArtifacts();
    @Nullable Path preexistingAncestorDirectory =
        activeTransaction == null ? null : activeTransaction.preexistingAncestorDirectory();
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
      resetLedgerPlanTransactionState();
      throw closeFailure;
    } catch (IllegalStateException exception) {
      IllegalStateException closeFailure =
          new IllegalStateException("Failed to close SQLite book connection.", exception);
      sessionState = new ClosedSession(closeFailure);
      resetLedgerPlanTransactionState();
      throw closeFailure;
    }
    try {
      if (cleanupCreatedBookArtifacts) {
        SqliteLedgerPlanArtifactCleanup.cleanupCreatedMissingBookArtifacts(
            context.bookPath(), preexistingAncestorDirectory, null);
      }
    } finally {
      resetLedgerPlanTransactionState();
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
    if (ledgerPlanTransactionState instanceof ActiveLedgerPlanTransaction) {
      beginLedgerPlanTransactionIfNeeded(activeDatabase);
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
    return ledgerPlanTransactionState instanceof ActiveLedgerPlanTransaction;
  }

  boolean ledgerPlanTransactionBegunInDatabase() {
    threadOwner.requireOwnerThread();
    ActiveLedgerPlanTransaction activeTransaction = activeLedgerPlanTransaction();
    return activeTransaction != null && activeTransaction.begunInDatabase();
  }

  private ContractDecision<SqliteNativeDatabase> openDatabase() {
    threadOwner.requireOwnerThread();
    try (SqliteOwnedPassphrase workingPassphrase = sessionSecret.borrowWorkingCopy()) {
      if (context.accessMode().createsFiles() && Files.notExists(context.bookPath())) {
        markLedgerPlanBookArtifactsMayMutate();
        SqliteBookSchemaBootstrap.ensureParentDirectory(context.bookPath());
      }
      SqliteNativeDatabase openedDatabase =
          context.openConfiguredDatabase(workingPassphrase.nativePassphrase());
      publishDatabase(openedDatabase);
      beginLedgerPlanTransactionIfNeeded(openedDatabase);
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

  private void beginLedgerPlanTransactionIfNeeded(SqliteNativeDatabase activeDatabase) {
    threadOwner.requireOwnerThread();
    if (ledgerPlanTransactionState instanceof ActiveLedgerPlanTransaction activeTransaction
        && !activeTransaction.begunInDatabase()) {
      activeDatabase.executeStatement("begin immediate");
      ledgerPlanTransactionState = activeTransaction.withBegunDatabase();
    }
  }

  private void cleanupCreatedMissingBookArtifactsIfPresent() {
    threadOwner.requireOwnerThread();
    ActiveLedgerPlanTransaction activeTransaction = activeLedgerPlanTransaction();
    if (activeTransaction == null || !activeTransaction.createdBookArtifacts()) {
      return;
    }
    cleanupCreatedMissingBookArtifacts(activeTransaction.preexistingAncestorDirectory());
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

  private void markLedgerPlanBookArtifactsMayMutate() {
    threadOwner.requireOwnerThread();
    if (ledgerPlanTransactionState instanceof ActiveLedgerPlanTransaction activeTransaction) {
      ledgerPlanTransactionState = activeTransaction.withCreatedBookArtifacts();
    }
  }

  private void resetLedgerPlanTransactionState() {
    threadOwner.requireOwnerThread();
    ledgerPlanTransactionState = new NoLedgerPlanTransaction();
  }

  private void cleanupCreatedMissingBookArtifacts(@Nullable Path preexistingAncestorDirectory) {
    SqliteLedgerPlanArtifactCleanup.cleanupCreatedMissingBookArtifacts(
        context.bookPath(), preexistingAncestorDirectory, detachPublishedDatabase());
  }

  private @Nullable ActiveLedgerPlanTransaction activeLedgerPlanTransaction() {
    return switch (ledgerPlanTransactionState) {
      case NoLedgerPlanTransaction ignored -> null;
      case ActiveLedgerPlanTransaction activeTransaction -> activeTransaction;
    };
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

  /** Internal ledger-plan transaction model for one store lifecycle instance. */
  private sealed interface LedgerPlanTransactionState
      permits NoLedgerPlanTransaction, ActiveLedgerPlanTransaction {}

  /** Lifecycle state when no ledger-plan transaction is active. */
  private record NoLedgerPlanTransaction() implements LedgerPlanTransactionState {}

  /** Active ledger-plan transaction with explicit database and artifact tracking state. */
  private record ActiveLedgerPlanTransaction(
      DatabaseTransactionState databaseTransactionState, ArtifactCleanupState artifactCleanupState)
      implements LedgerPlanTransactionState {
    private ActiveLedgerPlanTransaction {
      Objects.requireNonNull(databaseTransactionState, "databaseTransactionState");
      Objects.requireNonNull(artifactCleanupState, "artifactCleanupState");
    }

    boolean begunInDatabase() {
      return databaseTransactionState instanceof DatabaseTransactionBegun;
    }

    boolean createdBookArtifacts() {
      return artifactCleanupState instanceof MissingBookArtifactsCreated;
    }

    @Nullable Path preexistingAncestorDirectory() {
      return artifactCleanupState.preexistingAncestorDirectory();
    }

    ActiveLedgerPlanTransaction withBegunDatabase() {
      return new ActiveLedgerPlanTransaction(new DatabaseTransactionBegun(), artifactCleanupState);
    }

    ActiveLedgerPlanTransaction withCreatedBookArtifacts() {
      return switch (artifactCleanupState) {
        case NoArtifactCleanup ignored -> this;
        case MissingBookArtifactsPending pending ->
            new ActiveLedgerPlanTransaction(
                databaseTransactionState,
                new MissingBookArtifactsCreated(pending.preexistingAncestorDirectory()));
        case MissingBookArtifactsCreated ignored -> this;
      };
    }
  }

  /** Database-begin state for one active ledger-plan transaction. */
  private sealed interface DatabaseTransactionState
      permits DatabaseTransactionDeferred, DatabaseTransactionBegun {}

  /** Active ledger-plan transaction before the SQLite database transaction begins. */
  private record DatabaseTransactionDeferred() implements DatabaseTransactionState {}

  /** Active ledger-plan transaction after the SQLite database transaction begins. */
  private record DatabaseTransactionBegun() implements DatabaseTransactionState {}

  /** Missing-book artifact cleanup state for one active ledger-plan transaction. */
  private sealed interface ArtifactCleanupState
      permits NoArtifactCleanup, MissingBookArtifactsPending, MissingBookArtifactsCreated {
    /**
     * Returns the ancestor directory that predated missing-book artifact creation, when one exists.
     */
    @Nullable default Path preexistingAncestorDirectory() {
      return null;
    }
  }

  /** Active ledger-plan transaction that did not begin from a missing-book path. */
  private record NoArtifactCleanup() implements ArtifactCleanupState {}

  /** Missing-book transaction before schema/bootstrap work creates cleanup-eligible artifacts. */
  private record MissingBookArtifactsPending(@Nullable Path preexistingAncestorDirectory)
      implements ArtifactCleanupState {}

  /** Missing-book transaction after schema/bootstrap work creates cleanup-eligible artifacts. */
  private record MissingBookArtifactsCreated(@Nullable Path preexistingAncestorDirectory)
      implements ArtifactCleanupState {}

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
