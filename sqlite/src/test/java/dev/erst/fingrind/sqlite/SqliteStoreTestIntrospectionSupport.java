package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingReadPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Shared SQLite store-introspection helpers layered on top of common posting fixtures. */
class SqliteStoreTestIntrospectionSupport extends SqlitePostingFactFixtureSupport {
  static void setStoreDatabase(
      SqlitePostingFactStore postingFactStore,
      @org.jspecify.annotations.Nullable SqliteNativeDatabase database) {
    SqliteStoreTestAccess.publishNativeDatabase(postingFactStore, database);
  }

  static void clearStoreSessionSecret(SqlitePostingFactStore postingFactStore) {
    SqliteStoreTestAccess.clearSessionSecret(postingFactStore);
  }

  static void setStoreCachedBookState(
      SqlitePostingFactStore postingFactStore,
      @org.jspecify.annotations.Nullable SqliteBookStateSnapshot cachedBookState) {
    SqliteStoreTestAccess.setCachedState(postingFactStore, cachedBookState);
  }

  static void clearPublishedDatabaseState(SqlitePostingFactStore postingFactStore) {
    SqliteStoreTestAccess.clearPublishedDatabaseState(postingFactStore);
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

  static @org.jspecify.annotations.Nullable SqliteNativeDatabase storeDatabase(
      SqlitePostingFactStore postingFactStore) {
    return SqliteStoreTestAccess.publishedDatabase(postingFactStore);
  }

  static SqliteStoreAccessMode storeAccessMode(SqlitePostingFactStore postingFactStore) {
    return SqliteStoreTestAccess.accessMode(postingFactStore);
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

  static void assertProtectedBookVerificationFailure(IllegalStateException exception) {
    assertTrue(exception instanceof ContractFailureException);
    ContractFailureException contractFailureException = (ContractFailureException) exception;
    assertEquals(
        ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED,
        contractFailureException.failure().descriptor());
    assertEquals(
        "FinGrind could not verify the selected protected book with the supplied passphrase source.",
        contractFailureException.failure().message());
    String hint = Objects.requireNonNull(contractFailureException.failure().hint());
    assertTrue(hint.contains("wrong secret"));
    assertTrue(hint.contains("damaged or truncated book file"));
    String message = Objects.requireNonNull(exception.getMessage());
    assertFalse(message.contains("SQLITE_NOTADB"));
    assertFalse(message.contains("SQLITE_IOERR_BADKEY"));
    assertFalse(message.contains("SQLITE_IOERR_CODEC"));
  }

  static PostingCommitResult rejected(BookkeepingPostingRejection rejection) {
    return new PostingCommitResult.Rejected(rejection);
  }

  static PostingDraft postingDraft(
      String postingId,
      String idempotencyKey,
      Optional<ReversalReference> reversalReference,
      Optional<ReversalReason> reason) {
    CommittedPosting postingFact =
        postingFact(postingId, idempotencyKey, reversalReference, reason);
    return new PostingDraft(
        postingFact.journalEntry(),
        postingFact.postingLineage(),
        postingFact.postingKind(),
        postingFact.provenance());
  }

  static BookkeepingPostingRejection.AccountStateViolations accountStateViolations(
      BookkeepingPostingRejection.AccountStateViolation... violations) {
    return new BookkeepingPostingRejection.AccountStateViolations(List.of(violations));
  }

  static AccountRegistryQuery firstAccountPage() {
    return new AccountRegistryQuery(50, Optional.empty());
  }

  static PostingHistoryQuery postingHistoryQuery(
      Optional<AccountCode> accountCode,
      @org.jspecify.annotations.Nullable LocalDate effectiveDateFrom,
      @org.jspecify.annotations.Nullable LocalDate effectiveDateTo,
      int limit,
      Optional<dev.erst.fingrind.executor.bookkeeping.PostingHistoryCursor> cursor) {
    return new PostingHistoryQuery(accountCode, effectiveDateFrom, effectiveDateTo, limit, cursor);
  }

  static AccountBalanceCriteria accountBalanceCriteria(
      AccountCode accountCode,
      @org.jspecify.annotations.Nullable LocalDate effectiveDateFrom,
      @org.jspecify.annotations.Nullable LocalDate effectiveDateTo) {
    return new AccountBalanceCriteria(accountCode, effectiveDateFrom, effectiveDateTo);
  }

  static TrialBalanceCriteria trialBalanceCriteria(Optional<LocalDate> effectiveDateTo) {
    return new TrialBalanceCriteria(effectiveDateTo);
  }

  static AccountLedgerCriteria accountLedgerCriteria(
      AccountCode accountCode,
      @org.jspecify.annotations.Nullable LocalDate effectiveDateFrom,
      @org.jspecify.annotations.Nullable LocalDate effectiveDateTo) {
    return new AccountLedgerCriteria(accountCode, effectiveDateFrom, effectiveDateTo);
  }

  static PeriodSummaryCriteria periodSummaryCriteria(
      LocalDate effectiveDateFrom, LocalDate effectiveDateTo) {
    return new PeriodSummaryCriteria(effectiveDateFrom, effectiveDateTo);
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
      String message = Objects.requireNonNull(exception.getMessage());
      assertTrue(message.contains("Failed to query SQLite book."));
      assertTrue(message.contains("SQLITE_CANTOPEN"));
    }
  }

  static List<DeclaredAccount> listAccounts(SqlitePostingFactStore postingFactStore) {
    return postingFactStore.listAccounts(firstAccountPage()).accounts().stream()
        .map(BookkeepingPublishedLanguageTranslator::toPublished)
        .toList();
  }

  static PostingPage published(PostingHistoryPage page) {
    return BookkeepingReadPublishedLanguageTranslator.toPublished(page);
  }

  static AccountBalanceSnapshot published(AccountBalanceView view) {
    return BookkeepingReadPublishedLanguageTranslator.toPublished(view);
  }

  static TrialBalanceReport published(TrialBalanceView view) {
    return BookkeepingReadPublishedLanguageTranslator.toPublished(view);
  }

  static AccountLedgerReport published(AccountLedgerView view) {
    return BookkeepingReadPublishedLanguageTranslator.toPublished(view);
  }

  static PeriodSummaryReport published(PeriodSummaryView view) {
    return BookkeepingReadPublishedLanguageTranslator.toPublished(view);
  }

  static List<DeclaredAccount> publishedAccounts(AccountRegistryPage page) {
    return page.accounts().stream()
        .map(BookkeepingPublishedLanguageTranslator::toPublished)
        .toList();
  }

  /** Assertion helper call that may surface reflective or native checked failures. */
  @FunctionalInterface
  interface ThrowingRunnable {
    void run() throws ReflectiveOperationException;
  }
}
