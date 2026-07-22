package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.protocol.ProtocolBookAccessOptions;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.BookIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Owns the mutable database/session state for one SQLite-backed book session. */
class SqliteStoreLifecycle extends SqliteStoreSessionStateTracker {
  private final SqliteStoreContext context;
  private final SqliteThreadOwner threadOwner = new SqliteThreadOwner("SQLite book session");
  private final SqliteLedgerPlanTransactionCoordinator ledgerPlanTransactions;
  private final Transactions transactions = new Transactions();
  private @Nullable Path exclusiveNewBookPreexistingAncestorDirectory;
  private boolean ownsUninitializedExclusiveNewBook;

  SqliteStoreLifecycle(SqliteStoreContext context, SqliteSessionSecret sessionSecret) {
    super(sessionSecret);
    this.context = java.util.Objects.requireNonNull(context, "context");
    this.ledgerPlanTransactions = new SqliteLedgerPlanTransactionCoordinator(context);
  }

  void close() {
    threadOwner.requireOwnerThread();
    if (closed()) {
      return;
    }
    boolean cleanupPlanCreatedBookArtifacts = ledgerPlanTransactions.createdBookArtifacts();
    @Nullable Path planPreexistingAncestorDirectory =
        ledgerPlanTransactions.preexistingAncestorDirectory();
    boolean cleanupUninitializedExclusiveNewBook = ownsUninitializedExclusiveNewBook;
    @Nullable Path exclusiveNewBookPreexistingAncestorDirectory =
        this.exclusiveNewBookPreexistingAncestorDirectory;
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
      ledgerPlanTransactions.reset();
      throw closeFailure;
    } catch (IllegalStateException exception) {
      IllegalStateException closeFailure =
          new IllegalStateException("Failed to close SQLite book connection.", exception);
      markClosed(closeFailure);
      ledgerPlanTransactions.reset();
      throw closeFailure;
    }
    try {
      if (cleanupPlanCreatedBookArtifacts) {
        SqliteLedgerPlanArtifactCleanup.cleanupCreatedMissingBookArtifacts(
            context.bookPath(), planPreexistingAncestorDirectory, null);
      }
      if (cleanupUninitializedExclusiveNewBook) {
        SqliteLedgerPlanArtifactCleanup.deleteCreatedMissingBookArtifacts(
            context.bookPath(), exclusiveNewBookPreexistingAncestorDirectory);
      }
    } finally {
      ledgerPlanTransactions.reset();
      ownsUninitializedExclusiveNewBook = false;
      this.exclusiveNewBookPreexistingAncestorDirectory = null;
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

  void recordExclusiveNewBookInitialization() {
    threadOwner.requireOwnerThread();
    ownsUninitializedExclusiveNewBook = false;
    exclusiveNewBookPreexistingAncestorDirectory = null;
  }

  Transactions transactions() {
    return transactions;
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
      @Nullable Path preexistingAncestorDirectory = null;
      if (context.accessMode().createsFiles() && Files.notExists(context.bookPath())) {
        if (context.accessMode().requiresAbsentNewBookTarget()) {
          preexistingAncestorDirectory =
              SqliteLedgerPlanArtifactCleanup.nearestExistingAncestor(context.bookPath());
        }
        ledgerPlanTransactions.noteBookArtifactsMayMutate();
        SqliteBookSchemaBootstrap.ensureParentDirectory(context.bookPath());
      }
      SqliteNativeDatabase openedDatabase =
          SqliteStoreOperations.retryTransientLockFailures(
              () -> context.openConfiguredDatabase(workingPassphrase.nativePassphrase()));
      publishDatabase(openedDatabase);
      if (context.accessMode().requiresAbsentNewBookTarget()) {
        ownsUninitializedExclusiveNewBook = true;
        exclusiveNewBookPreexistingAncestorDirectory = preexistingAncestorDirectory;
      }
      return ContractDecision.accepted(openedDatabase);
    } catch (SqliteCallerPathContractException exception) {
      ContractFailure callerPathFailure =
          SqliteCallerPathFailureMapper.invalidBookFilePath(exception);
      rememberTerminalFailure(new ContractFailureException(callerPathFailure));
      return ContractDecision.rejected(callerPathFailure);
    } catch (SqliteNativeException exception) {
      if (context.accessMode().requiresAbsentNewBookTarget()
          && Files.exists(context.bookPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
        ContractFailure destinationFailure =
            ContractErrors.Descriptor.BOOK_DESTINATION_OCCUPIED.failureAt(
                context.bookPath(),
                "The selected --book-file destination became occupied while FinGrind was creating it; it was not opened or replaced.",
                "Choose a missing --book-file destination before opening a new book.",
                ProtocolBookAccessOptions.BOOK_FILE);
        rememberTerminalFailure(new ContractFailureException(destinationFailure));
        return ContractDecision.rejected(destinationFailure);
      }
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

  private void cleanupCreatedMissingBookArtifacts(@Nullable Path preexistingAncestorDirectory) {
    SqliteLedgerPlanArtifactCleanup.cleanupCreatedMissingBookArtifacts(
        context.bookPath(), preexistingAncestorDirectory, detachPublishedDatabase());
  }

  /** Coordinates ledger-plan transaction state for one open SQLite store lifecycle. */
  final class Transactions {
    private Transactions() {}

    void begin() {
      threadOwner.requireOwnerThread();
      ensureOpen();
      ledgerPlanTransactions.begin(
          SqliteStoreLifecycle.this::database,
          SqliteStoreLifecycle.this::cleanupCreatedMissingBookArtifacts);
    }

    void beginAttestedPlan() {
      threadOwner.requireOwnerThread();
      ensureOpen();
      ledgerPlanTransactions.beginAttestedPlan(
          SqliteStoreLifecycle.this::database,
          SqliteStoreLifecycle.this::cleanupCreatedMissingBookArtifacts);
    }

    void commit() {
      threadOwner.requireOwnerThread();
      ensureOpen();
      ledgerPlanTransactions.commit(SqliteStoreLifecycle.this::database);
    }

    void rollback() {
      threadOwner.requireOwnerThread();
      ledgerPlanTransactions.rollback(
          publishedDatabase(), SqliteStoreLifecycle.this::cleanupCreatedMissingBookArtifacts);
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

    SqliteAttestedWriteAdmission admitAttestedWrite(SqliteNativeDatabase activeDatabase) {
      threadOwner.requireOwnerThread();
      if (ledgerPlanTransactions.active()) {
        ledgerPlanTransactions.beginImmediateIfNeeded(activeDatabase);
        return new SqliteAttestedWriteAdmission(
            ledgerPlanTransactions.requireObservedAttestationHead(),
            SqliteTransactionOwnership.SHARED);
      }
      SqliteAttestationEvidenceStore.ObservedHead observedHead =
          SqliteAttestationEvidenceStore.observeRequired(activeDatabase);
      activeDatabase.executeStatement("begin immediate");
      return new SqliteAttestedWriteAdmission(observedHead, SqliteTransactionOwnership.OWNED);
    }

    boolean active() {
      threadOwner.requireOwnerThread();
      return ledgerPlanTransactions.active();
    }

    boolean begunInDatabase() {
      threadOwner.requireOwnerThread();
      return ledgerPlanTransactions.begunInDatabase();
    }

    SqliteAttestationEvidenceStore.ObservedHead requireObservedAttestationHead() {
      threadOwner.requireOwnerThread();
      return ledgerPlanTransactions.requireObservedAttestationHead();
    }

    void cleanupCreatedMissingBookArtifactsIfPresent() {
      threadOwner.requireOwnerThread();
      if (!ledgerPlanTransactions.createdBookArtifacts()) {
        return;
      }
      cleanupCreatedMissingBookArtifacts(ledgerPlanTransactions.preexistingAncestorDirectory());
    }
  }
}
