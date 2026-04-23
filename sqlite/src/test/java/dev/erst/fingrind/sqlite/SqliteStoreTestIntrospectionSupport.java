package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.executor.PostingCommitResult;
import dev.erst.fingrind.executor.PostingDraft;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullUnmarked;

/** Shared SQLite store-introspection helpers layered on top of common posting fixtures. */
@NullUnmarked
class SqliteStoreTestIntrospectionSupport extends SqlitePostingFactFixtureSupport {
  static void setStoreDatabase(
      SqlitePostingFactStore postingFactStore,
      @org.jspecify.annotations.Nullable SqliteNativeDatabase database) {
    SqliteStoreTestAccess.publishNativeDatabase(postingFactStore, database);
  }

  static void setStoreBookPassphrase(
      SqlitePostingFactStore postingFactStore, SqliteBookPassphrase bookPassphrase) {
    SqliteStoreTestAccess.setPendingPassphrase(postingFactStore, bookPassphrase);
  }

  static void setStoreCachedBookState(
      SqlitePostingFactStore postingFactStore,
      @org.jspecify.annotations.Nullable SqliteBookStateSnapshot cachedBookState) {
    SqliteStoreTestAccess.setCachedState(postingFactStore, cachedBookState);
  }

  static boolean storeBooleanField(SqlitePostingFactStore postingFactStore, String fieldName) {
    return switch (fieldName) {
      case "closed" -> SqliteStoreTestAccess.closed(postingFactStore);
      case "ledgerPlanTransactionActive" ->
          SqliteStoreTestAccess.ledgerPlanTransactionActive(postingFactStore);
      case "ledgerPlanTransactionBegunInDatabase" ->
          SqliteStoreTestAccess.ledgerPlanTransactionBegunInDatabase(postingFactStore);
      default ->
          throw new IllegalArgumentException("Unsupported store boolean field: " + fieldName);
    };
  }

  static SqliteNativeDatabase storeDatabase(SqlitePostingFactStore postingFactStore) {
    return SqliteStoreTestAccess.currentDatabaseHandle(postingFactStore);
  }

  static SqliteNativeDatabase requireStoreDatabase(SqlitePostingFactStore postingFactStore) {
    SqliteNativeDatabase database = storeDatabase(postingFactStore);
    assertNotNull(database);
    return database;
  }

  static void closeStoreDatabase(SqlitePostingFactStore postingFactStore) {
    requireStoreDatabase(postingFactStore).close();
  }

  static byte[] passphraseBytes(SqliteBookPassphrase passphrase) {
    return passphrase.utf8BytesCopy();
  }

  static MethodHandle constantMethodHandle(Object value, Class<?>... parameterTypes) {
    return MethodHandles.dropArguments(
        MethodHandles.constant(constantType(value), value), 0, parameterTypes);
  }

  static Class<?> constantType(Object value) {
    return switch (value) {
      case Integer _ -> int.class;
      case Long _ -> long.class;
      case MemorySegment _ -> MemorySegment.class;
      default -> value.getClass();
    };
  }

  static void assertInvalidPlaintextBookFailure(IllegalStateException exception) {
    assertTrue(
        exception
            .getMessage()
            .contains(
                "FinGrind could not authenticate the selected protected book with the supplied passphrase source."));
    assertTrue(
        exception
            .getMessage()
            .contains(
                "FinGrind could not authenticate the selected protected book with the supplied passphrase source."));
    assertFalse(exception.getMessage().contains("SQLITE_NOTADB"));
  }

  static PostingCommitResult rejected(PostingRejection rejection) {
    return new PostingCommitResult.Rejected(rejection);
  }

  static PostingDraft postingDraft(
      String postingId,
      String idempotencyKey,
      Optional<ReversalReference> reversalReference,
      Optional<ReversalReason> reason) {
    PostingFact postingFact = postingFact(postingId, idempotencyKey, reversalReference, reason);
    return new PostingDraft(
        postingFact.journalEntry(), postingFact.postingLineage(), postingFact.provenance());
  }

  static PostingRejection.AccountStateViolations accountStateViolations(
      PostingRejection.AccountStateViolation... violations) {
    return new PostingRejection.AccountStateViolations(List.of(violations));
  }

  static ListAccountsQuery firstAccountPage() {
    return new ListAccountsQuery(50, Optional.empty());
  }

  @SafeVarargs
  static void assertInitializedQueryViewFailure(ThrowingRunnable... invocations) {
    for (ThrowingRunnable invocation : invocations) {
      IllegalStateException exception = assertThrows(IllegalStateException.class, invocation::run);
      org.junit.jupiter.api.Assertions.assertEquals(
          "The selected SQLite file is not initialized as a FinGrind book.",
          exception.getMessage());
    }
  }

  @SafeVarargs
  static void assertWrappedQueryViewNativeFailure(ThrowingRunnable... invocations) {
    for (ThrowingRunnable invocation : invocations) {
      IllegalStateException exception = assertThrows(IllegalStateException.class, invocation::run);
      assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
      assertTrue(exception.getMessage().contains("SQLITE_CANTOPEN"));
    }
  }

  static List<DeclaredAccount> listAccounts(SqlitePostingFactStore postingFactStore) {
    return postingFactStore.listAccounts(firstAccountPage()).accounts();
  }

  /** Assertion helper call that may surface reflective or native checked failures. */
  @FunctionalInterface
  interface ThrowingRunnable {
    void run() throws ReflectiveOperationException;
  }
}
