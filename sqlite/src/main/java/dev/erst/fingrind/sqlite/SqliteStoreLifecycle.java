package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.protocol.ProtocolBookAccessOptions;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.BookIdentity;
import java.nio.file.Files;
import java.util.Optional;

/**
 * Owns the mutable database/session state for one SQLite-backed book session.
 *
 * <p>A failed create or initialization never deletes a caller-selected book pathname, SQLite
 * sidecar, or parent directory. SQLite rollback remains the accounting atomicity boundary; any
 * provisional filesystem residue is retained for explicit operator inspection and is never an
 * initialized-book success.
 */
class SqliteStoreLifecycle extends SqliteStoreSessionStateTracker {
  private final SqliteStoreContext context;
  private final SqliteThreadOwner threadOwner = new SqliteThreadOwner("SQLite book session");
  private final SqliteStoreLedgerPlanTransactions planTransactions;

  SqliteStoreLifecycle(SqliteStoreContext context, SqliteSessionSecret sessionSecret) {
    super(sessionSecret);
    this.context = java.util.Objects.requireNonNull(context, "context");
    this.planTransactions = new SqliteStoreLedgerPlanTransactions(this, context);
  }

  void close() {
    threadOwner.requireOwnerThread();
    if (closed()) {
      return;
    }
    try (SqliteStoreCloseSequence closeSequence =
        new SqliteStoreCloseSequence(sessionSecret()::close, publishedDatabase())) {
      java.util.Objects.requireNonNull(closeSequence, "closeSequence");
      markClosed(null);
      // Close sequence runs on scope exit.
    } catch (SqliteNativeException exception) {
      String detail =
          java.util.Objects.requireNonNullElse(exception.getMessage(), "SQLite native failure.");
      SqliteStorageFailureException closeFailure =
          new SqliteStorageFailureException(
              "Failed to close SQLite book connection. " + exception.resultName() + ": " + detail,
              exception);
      markClosed(closeFailure);
      planTransactions.reset();
      throw closeFailure;
    } catch (IllegalStateException exception) {
      IllegalStateException closeFailure =
          new IllegalStateException("Failed to close SQLite book connection.", exception);
      markClosed(closeFailure);
      planTransactions.reset();
      throw closeFailure;
    }
    // Closing one unfinished session must never become authority to unlink its caller path.
    planTransactions.reset();
  }

  boolean isInitializedBook(SqliteNativeDatabase activeDatabase) {
    threadOwner.requireOwnerThread();
    SqliteBookStateSnapshot snapshot = stateSnapshot(activeDatabase);
    if (snapshot.state() == SqliteBookState.BLANK_SQLITE) {
      return false;
    }
    snapshot
        .state()
        .requireInitialized(
            snapshot.userVersion(),
            context.bookFormatVersion(),
            context.notInitializedBookMessage());
    return true;
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
    snapshot =
        context.accessMode().usesOperationalBookStateGate()
            ? context.bookStateReader().operationalSnapshot(activeDatabase)
            : context.bookStateReader().snapshot(activeDatabase);
    cacheState(snapshot);
    return snapshot;
  }

  boolean allowsInitializedWorkflow() {
    threadOwner.requireOwnerThread();
    ensureOpen();
    if (Files.notExists(context.bookPath())) {
      return false;
    }
    try {
      return SqliteStoreOperations.retryTransientLockFailures(
          () -> {
            SqliteBookStateSnapshot snapshot = stateSnapshot(database());
            if (snapshot.state() == SqliteBookState.BLANK_SQLITE) {
              return false;
            }
            snapshot
                .state()
                .requireInitialized(
                    snapshot.userVersion(),
                    context.bookFormatVersion(),
                    context.notInitializedBookMessage());
            return true;
          });
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to access SQLite book.", exception);
    }
  }

  BookIdentity requireInitializedBookIdentity() {
    threadOwner.requireOwnerThread();
    ensureOpen();
    try {
      return SqliteStoreOperations.retryTransientLockFailures(
          () ->
              SqliteStatementQueries.loadBookIdentity(initializedQueryDatabase())
                  .orElseThrow(
                      () ->
                          new IllegalStateException(
                              "Initialized SQLite book is missing book identity.")));
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to access SQLite book.", exception);
    }
  }

  SqliteNativeDatabase database() {
    threadOwner.requireOwnerThread();
    ensureOpen();
    SqliteNativeDatabase activeDatabase = publishedDatabase();
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
    if (publishedDatabase() != null) {
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

  void requireOwnerThread() {
    threadOwner.requireOwnerThread();
  }

  SqliteStoreLedgerPlanTransactions transactions() {
    return planTransactions;
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

  private ContractDecision<SqliteNativeDatabase> openDatabase() {
    threadOwner.requireOwnerThread();
    try (SqliteOwnedPassphrase workingPassphrase = sessionSecret().borrowWorkingCopy()) {
      if (context.accessMode().createsFiles() && Files.notExists(context.bookPath())) {
        SqliteBookSchemaBootstrap.ensureParentDirectory(context.bookPath());
      }
      SqliteNativeDatabase openedDatabase =
          SqliteStoreOperations.retryTransientLockFailures(
              () -> context.openConfiguredDatabase(workingPassphrase.nativePassphrase()));
      publishDatabase(openedDatabase);
      return ContractDecision.accepted(openedDatabase);
    } catch (SqliteCallerPathContractException exception) {
      ContractFailure callerPathFailure =
          SqliteCallerPathFailureMapper.invalidBookFilePath(exception);
      rememberTerminalFailure(new ContractFailureException(callerPathFailure));
      return ContractDecision.rejected(callerPathFailure);
    } catch (SqliteNewBookDestinationOccupiedException exception) {
      ContractFailure destinationFailure =
          ContractErrors.Descriptor.BOOK_DESTINATION_OCCUPIED.failureAt(
              exception.targetPath(),
              "The selected --book-file destination became occupied while FinGrind was creating it; it was not opened or replaced.",
              "Choose a missing --book-file destination before opening a new book.",
              ProtocolBookAccessOptions.BOOK_FILE);
      rememberTerminalFailure(new ContractFailureException(destinationFailure));
      return ContractDecision.rejected(destinationFailure);
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
}
